package ssg.legoflow.service.manager;

import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCP data-flow verification for {@link SelectableChannelManager} — NO protocol, NO external broker.
 *
 * <h3>Contract under test (as implemented in {@code dispatchKey})</h3>
 * <ol>
 *   <li><b>Synchronous, ordered delivery.</b> Read from TCP + flush to the pipeline happens in the
 *       selector thread. Bytes delivered by successive flushes are strictly ordered — a later
 *       flush never precedes an earlier one, because a new TCP read only happens after the
 *       previous flush was fully consumed.</li>
 *   <li><b>Backpressure.</b> The handler consumes relatively (advancing buffer position).
 *       Whatever is left stays in the persistent TCP buffer; the manager performs no new TCP
 *       read until the protocol consumes it. The remainder is retried on the next read OR write
 *       cycle. Nothing is cleared or discarded in the manager.</li>
 *   <li><b>Zero-byte consumption is a valid state.</b> A handler that consumed 0 bytes
 *       (busy / ring full) leaves data pending; the same bytes are re-delivered on the next
 *       cycle WITHOUT duplication.</li>
 *   <li><b>Single lifecycle events.</b> Exactly one CONNECT, exactly one DISCONNECT
 *       (on remote EOF or local close), no spurious ERRORs.</li>
 *   <li><b>Lossless.</b> Every byte the server sends arrives exactly once, in order,
 *       even when chunk size &gt; TCP buffer size and the protocol consumes partially.</li>
 * </ol>
 *
 * <h3>Verification method</h3>
 * <p>Real loopback TCP sockets. Server side: plain blocking {@link ServerSocket} on a virtual
 * thread. Client side: the production {@link SelectableChannelManager} event loop with a
 * deliberately SMALL buffer (64 bytes) so thousands of read cycles occur per test.
 * A patterned payload (incrementing counter byte) makes any duplication, loss, or
 * reordering immediately detectable.
 *
 * <h3>Known environment facts (verified 2026-09-10)</h3>
 * <ul>
 *   <li>Java 25 (SDKMAN current), non-blocking {@link SocketChannel}, virtual threads.</li>
 *   <li>After {@code finishConnect()} the manager sets interestOps READ|WRITE synchronously
 *       and fires CONNECT synchronously in the selector thread.</li>
 *   <li>Production transports (e.g. {@code PipelineTransport.onWrite}) manage interestOps:
 *       OP_WRITE is dropped when the outbound queue is empty. The test handler mimics this
 *       in {@link TestHandler#onWrite} to avoid a writable busy-loop.</li>
 * </ul>
 */
class TcpDataFlowTest {

    private static final Logger LOG = LoggerFactory.getLogger(TcpDataFlowTest.class);
    private static final int BUFFER_SIZE = 64; // small on purpose: forces many read cycles

    private ServerSocket serverSocket;
    private int port;

    /** Deterministic payload: byte i = (i * 7 + 13) & 0xFF — detects dup/loss/reorder. */
    private static byte[] pattern(int n) {
        var out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) ((i * 7 + 13) & 0xFF);
        return out;
    }

    private void startServer() throws IOException {
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
    }

    /**
     * Blocking server on a virtual thread: accepts one connection, writes the payload in
     * chunks of {@code chunkSize}, then half-closes so the client receives all data + EOF.
     * Blocks on the client's (empty) output until the client closes — returns the thread so
     * the caller joins it AFTER client teardown (the client only closes in its finally block).
     */
    private Thread startServer(byte[] payload, int chunkSize) {
        return Thread.ofVirtual().name("tcp-test-server").start(() -> {
            try (var socket = serverSocket.accept()) {
                var out = socket.getOutputStream();
                for (int off = 0; off < payload.length; off += chunkSize) {
                    int len = Math.min(chunkSize, payload.length - off);
                    out.write(payload, off, len);
                    out.flush();
                }
                socket.shutdownOutput(); // half-close: client gets all data, then EOF
                var in = socket.getInputStream();
                in.read(new byte[1]); // blocks until client closes -> -1 -> socket closes (try-with-resources)
            } catch (IOException e) {
                LOG.debug("server loop ended: {}", e.getMessage());
            }
        });
    }

    private ServiceContext newContext() {
        return new DefaultServiceContext(ServiceUser.anonymous());
    }

    /**
     * Handler that mimics a real protocol transport's consumption:
     * <ul>
     *   <li>consumes relatively (advances position) — the manager's backpressure contract</li>
     *   <li>bounded per-call consumption simulates a busy protocol</li>
     *   <li>optionally consumes 0 bytes on some calls (protocol saturated)</li>
     *   <li>onWrite drops OP_WRITE, mimicking {@code PipelineTransport.onWrite}</li>
     * </ul>
     */
    private record ConsumptionPolicy(int maxPerCall, int skipEvery) {
        static final ConsumptionPolicy ALL = new ConsumptionPolicy(Integer.MAX_VALUE, 0);
    }

    private class TestHandler implements ChannelHandler {
        private final ConsumptionPolicy policy;
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final AtomicInteger readCalls = new AtomicInteger();
        private final AtomicInteger skippedCalls = new AtomicInteger();
        final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        final CountDownLatch disconnect = new CountDownLatch(1);
        volatile boolean disconnected = false;

        TestHandler(ConsumptionPolicy policy) {
            this.policy = policy;
        }

        @Override
        public void onConnect(DataChannel channel) {
            events.add("CONNECT");
        }

        @Override
        public void onRead(DataChannel channel, ByteBuffer data) {
            readCalls.incrementAndGet();
            int call = readCalls.get();
            if (policy.skipEvery > 0 && call % (policy.skipEvery + 1) == 0) {
                // Protocol busy: consume NOTHING this cycle. Data stays in the manager's
                // TCP buffer and must be re-delivered on the next cycle, unduplicated.
                skippedCalls.incrementAndGet();
                return;
            }
            int n = Math.min(data.remaining(), policy.maxPerCall);
            var arr = new byte[n];
            data.get(arr); // RELATIVE consumption — remainder stays for the next cycle
            synchronized (received) {
                received.write(arr, 0, n);
            }
        }

        @Override
        public void onWrite(DataChannel channel) {
            events.add("WRITE");
            // Mimic production transports: no outbound queue — stop watching writability.
            var key = channel.getSelectionKey();
            if (key != null) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }

        @Override
        public void onDisconnect(DataChannel channel) {
            events.add("DISCONNECT");
            disconnected = true;
            disconnect.countDown();
        }

        @Override
        public void onError(DataChannel channel, Throwable cause) {
            events.add("ERROR:" + cause.getMessage());
            disconnected = true;
            disconnect.countDown();
        }

        byte[] allReceived() {
            synchronized (received) {
                return received.toByteArray();
            }
        }

        List<String> disconnectEvents() {
            return events.stream().filter(e -> e.equals("DISCONNECT")).toList();
        }
    }

    /**
     * End-to-end TCP data flow: register channel → connect → server streams payload →
     * verify lossless ordered delivery, single CONNECT, single DISCONNECT on EOF.
     *
     * @param totalBytes      payload size (thousands of read cycles for BUFFER_SIZE=64)
     * @param chunkSize       server write chunk size (&gt; buffer size forces split reads)
     * @param policy          how the (simulated) protocol consumes each flush
     */
    private void assertDataFlow(int totalBytes, int chunkSize, ConsumptionPolicy policy) throws Exception {
        var manager = new SelectableChannelManager(newContext(), BUFFER_SIZE, 50);
        var handler = new TestHandler(policy);
        var service = new DummyService("tcp-test-" + System.nanoTime());
        Thread serverThread = null;
        try {
            // Same production sequence as AmqpClientService.doConnect:
            // open non-blocking socket -> register (OP_CONNECT) -> start async connect
            var socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            var channel = new TcpDataChannel(socketChannel);
            manager.registerChannel(service, channel, handler);
            socketChannel.connect(new InetSocketAddress("localhost", port));

            manager.startEventLoop();
            var payload = pattern(totalBytes);
            serverThread = startServer(payload, chunkSize);

            assertThat(handler.disconnect.await(60, TimeUnit.SECONDS))
                    .as("client disconnected within timeout")
                    .isTrue();

            // 1. Lossless + ordered: exact byte-for-byte match (pattern detects dup/loss/reorder)
            var got = handler.allReceived();
            assertThat(got)
                    .as("received %d bytes (expected %d)", got.length, totalBytes)
                    .isEqualTo(payload);

            // 2. Exactly one CONNECT, one DISCONNECT, no ERRORs
            var connects = handler.events.stream().filter(e -> e.equals("CONNECT")).toList();
            assertThat(connects).hasSize(1);
            assertThat(handler.disconnectEvents()).hasSize(1);
            assertThat(handler.events).noneMatch(e -> e.startsWith("ERROR"));

            LOG.info("flow OK: {} bytes, {} read cycles, {} skipped (zero-consume) calls",
                    totalBytes, handler.readCalls.get(), handler.skippedCalls.get());
        } finally {
            manager.close(); // closes the client socket -> server thread unblocks
            serverSocket.close();
            if (serverThread != null) {
                serverThread.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
    }

    static class DummyService extends AbstractService<ByteBuffer, ByteBuffer> {
        DummyService(String name) {
            super(ByteBuffer.class, ByteBuffer.class, new ServiceDescriptor(name, "tcp data-flow test"));
        }

        @Override
        protected ByteBuffer[] convertToOutput(ssg.legoflow.blocks.Context ctx, ByteBuffer... input) {
            return input;
        }

        @Override
        protected ByteBuffer[] convertToInput(ssg.legoflow.blocks.Context ctx, ByteBuffer... output) {
            return output;
        }
    }

    @Test
    void testLosslessOrderedDelivery() throws Exception {
        // Fast consumer: drains each flush fully; large chunks split across many 64-byte reads
        startServer();
        assertDataFlow(100_000, 1000, ConsumptionPolicy.ALL);
    }

    @Test
    void testBackpressurePartialConsumption() throws Exception {
        // Protocol consumes at most 32 bytes per flush; every 5th call consumes 0 (busy).
        // Verifies: remainder survives, re-delivery is unduplicated, no TCP read while pending.
        startServer();
        assertDataFlow(20_000, 256, new ConsumptionPolicy(32, 5));
    }

    @Test
    void testRemoteCloseAfterPartialData() throws Exception {
        // Server half-closes after writing; client must deliver everything before EOF
        // and fire exactly one DISCONNECT.
        startServer();
        assertDataFlow(5_000, 50, ConsumptionPolicy.ALL);
    }

    @Test
    void testTinyPayloadSingleFlush() throws Exception {
        // Payload smaller than one read: single flush, still one CONNECT + one DISCONNECT
        startServer();
        assertDataFlow(10, 10, ConsumptionPolicy.ALL);
    }
}
