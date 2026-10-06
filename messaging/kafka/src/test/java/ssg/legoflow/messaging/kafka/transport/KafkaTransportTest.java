package ssg.legoflow.messaging.kafka.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the byte-level {@link KafkaTransport} SPI: {@link InMemoryKafkaTransport}
 * pair semantics (in main) and {@link PipelineKafkaTransport} ring/queue behavior over a
 * mock {@link DataChannel} (mirrors the NATS {@code NatsTransportTest} reference form).
 *
 * <p>Key contract points covered: partial reads keep the remainder at the stream head (a
 * frame split across reads must reassemble, and the remainder must arrive before any later
 * send), and {@code -1} is reported only on close — a timeout-while-open must wake the
 * blocked reader without consuming queued bytes.
 */
class KafkaTransportTest {

    private static ByteBuffer bytes(String s) {
        // ByteBuffer.wrap() already yields position=0, limit=length — no flip() needed.
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    // --- InMemoryKafkaTransport ---

    @Test
    void testCreatePairConnectsBothWays() throws Exception {
        var pair = InMemoryKafkaTransport.createPair();
        try {
            var latch = new CountDownLatch(1);
            var got = new StringBuilder();
            var sink = ByteBuffer.allocate(64);
            Thread.startVirtualThread(() -> {
                try {
                    int n = pair[1].receiveWithTimeout(sink, 2, TimeUnit.SECONDS);
                    got.append(n >= 0 ? new String(sink.array(), 0, n, StandardCharsets.UTF_8) : "EOF");
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
            pair[0].send(bytes("HELLO-KAFKA"));
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(got.toString()).isEqualTo("HELLO-KAFKA");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testPartialReadRequeuesTail() {
        var pair = InMemoryKafkaTransport.createPair();
        try {
            pair[0].send(bytes("abcdefgh"));
            // Read 4 bytes — the remainder must be kept, not dropped.
            var buf = ByteBuffer.allocate(4);
            assertThat(pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS)).isEqualTo(4);
            buf.flip();
            byte[] part = new byte[4];
            buf.get(part);
            assertThat(new String(part, StandardCharsets.UTF_8)).isEqualTo("abcd");

            var rest = ByteBuffer.allocate(8);
            assertThat(pair[1].receiveWithTimeout(rest, 1, TimeUnit.SECONDS)).isEqualTo(4);
            rest.flip();
            byte[] tail = new byte[4];
            rest.get(tail);
            assertThat(new String(tail, StandardCharsets.UTF_8)).isEqualTo("efgh");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testPartialReadRemainderPrecedesLaterSend() {
        var pair = InMemoryKafkaTransport.createPair();
        try {
            // Regression: two in-flight sends + a partial read. After the broker reads part of
            // the first buffer, the remainder must drain BEFORE the later send — preserving
            // byte-stream order exactly like TCP. Re-queueing the tail at the queue's back
            // would rotate the stream ("XYZ" would arrive ahead of "cdefgh") and corrupt
            // length-prefix framing (KafkaBrokerTest wire-reassembly tests).
            pair[0].send(bytes("abcdefgh"));
            var head = ByteBuffer.allocate(2);
            assertThat(pair[1].receiveWithTimeout(head, 1, TimeUnit.SECONDS)).isEqualTo(2);
            head.flip();
            assertThat(new String(head.array(), 0, 2, StandardCharsets.UTF_8)).isEqualTo("ab");

            pair[0].send(bytes("XYZ")); // arrives AFTER the partial read

            var rest = ByteBuffer.allocate(9);
            int n = pair[1].receiveWithTimeout(rest, 1, TimeUnit.SECONDS);
            assertThat(n).isEqualTo(6); // the first send's tail comes first
            rest.flip();
            assertThat(new String(rest.array(), 0, 6, StandardCharsets.UTF_8)).isEqualTo("cdefgh");

            var next = ByteBuffer.allocate(3);
            assertThat(pair[1].receiveWithTimeout(next, 1, TimeUnit.SECONDS)).isEqualTo(3);
            next.flip();
            assertThat(new String(next.array(), 0, 3, StandardCharsets.UTF_8)).isEqualTo("XYZ");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testCloseWakesBlockedPeerReader() throws Exception {
        var pair = InMemoryKafkaTransport.createPair();
        var result = new int[]{-99};
        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            result[0] = pair[0].receiveWithTimeout(ByteBuffer.allocate(16), 5, TimeUnit.SECONDS);
            latch.countDown();
        });
        Thread.sleep(50);
        assertThat(latch.getCount()).isEqualTo(1); // still blocked
        pair[1].close();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result[0]).isEqualTo(-1);
        assertThat(pair[0].isOpen()).isFalse();
    }

    @Test
    void testCloseWakesBlockedOwnReader() throws Exception {
        var pair = InMemoryKafkaTransport.createPair();
        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            pair[1].close();
            latch.countDown();
        });
        int n = pair[1].receiveWithTimeout(ByteBuffer.allocate(16), 5, TimeUnit.SECONDS);
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(n).isEqualTo(-1);
        assertThat(pair[1].isOpen()).isFalse();
    }

    @Test
    void testSendAfterCloseIsNoOp() {
        var pair = InMemoryKafkaTransport.createPair();
        pair[0].close();
        pair[0].send(bytes("gone"));
        // The peer reader must observe -1 (closed), never the stale "gone" bytes.
        assertThat(pair[1].receiveWithTimeout(ByteBuffer.allocate(16), 1, TimeUnit.SECONDS)).isEqualTo(-1);
    }

    @Test
    void testCloseDrainsQueuedBytesBeforeEof() {
        var pair = InMemoryKafkaTransport.createPair();
        // Send real data, then close — the peer must read the bytes BEFORE the EOF.
        pair[0].send(bytes("final-frame"));
        pair[0].close();
        var buf = ByteBuffer.allocate(16);
        int n = pair[1].receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(11);
        buf.flip();
        byte[] got = new byte[11];
        buf.get(got);
        assertThat(new String(got, StandardCharsets.UTF_8)).isEqualTo("final-frame");
        // ... and only then report EOF.
        assertThat(pair[1].receiveWithTimeout(ByteBuffer.allocate(16), 1, TimeUnit.SECONDS)).isEqualTo(-1);
    }

    @Test
    void testCloseIsIdempotent() {
        var pair = InMemoryKafkaTransport.createPair();
        pair[0].close();
        assertThatCode(() -> pair[0].close()).doesNotThrowAnyException();
        pair[1].close();
    }

    // --- PipelineKafkaTransport (production transport over a DataChannel) ---

    /**
     * Mock {@link DataChannel} whose write accepts at most {@code writeLimit} bytes per
     * call — forces the transport to enqueue the remainder in its outbound queue.
     */
    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        private volatile SelectionKey key;
        private final int writeLimit;
        private final StringBuilder written = new StringBuilder();
        private volatile int writeCalls = 0;

        MockChannel(int writeLimit) {
            this.writeLimit = writeLimit;
        }

        int writeCalls() { return writeCalls; }
        String written() { return written.toString(); }

        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException {
            if (!open) throw new IOException("closed");
            writeCalls++;
            int n = Math.min(writeLimit, buffer.remaining());
            if (n > 0) written.append(new String(buffer.array(), buffer.position(), n, StandardCharsets.UTF_8));
            buffer.position(buffer.position() + n);
            return n;
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return key; }
    }

    @Test
    void testPipelineAddAndFetch() {
        var transport = new PipelineKafkaTransport(new MockChannel(1024));
        int added = transport.add(bytes("Hello"));
        assertThat(added).isEqualTo(5);
        assertThat(transport.peek()).isEqualTo(5);

        var buf = ByteBuffer.allocate(1024);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(5);
        assertThat(transport.peek()).isZero();
    }

    @Test
    void testPipelineOnReadThenReceive() {
        var transport = new PipelineKafkaTransport(new MockChannel(1024));
        transport.onRead(null, bytes("data"));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test
    void testPipelineReceiveTimeout() {
        var transport = new PipelineKafkaTransport(new MockChannel(1024));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1); // nothing to read, still open -> timeout
    }

    @Test
    void testPipelineSendImmediateWrite() {
        var channel = new MockChannel(1024);
        var transport = new PipelineKafkaTransport(channel);
        transport.send(bytes("direct"));
        assertThat(channel.written()).isEqualTo("direct");
        assertThat(transport.isOpen()).isTrue();
    }

    @Test
    void testPipelineSendEnqueuesRemainderThenOnWriteDrains() {
        // writeLimit=3 -> first send writes 3 bytes immediately, enqueues the rest.
        var channel = new MockChannel(3);
        var transport = new PipelineKafkaTransport(channel);
        transport.send(bytes("12345678"));
        // Immediate write path handled the first 3 bytes; the remainder waits in the queue.
        assertThat(channel.written()).isEqualTo("123");
        // onWrite drains in writeLimit-sized chunks — the selector fires repeated writable
        // events until the queue is empty, so we loop the same way.
        int guard = 0;
        while (channel.written().length() < 8 && guard++ < 10) {
            transport.onWrite(channel);
        }
        assertThat(channel.written()).isEqualTo("12345678");
        assertThat(channel.writeCalls()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testPipelineReceivePartialReassembles() {
        var transport = new PipelineKafkaTransport(new MockChannel(1024));
        transport.onRead(null, bytes("abcdef"));
        // Read 4, then the rest — the ring must hand out the tail on the next call.
        var buf = ByteBuffer.allocate(4);
        int n1 = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n1).isEqualTo(4);
        buf = ByteBuffer.allocate(8);
        int n2 = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n2).isEqualTo(2);
    }

    @Test
    void testPipelineRingTailWrapReassembles() {
        var channel = new MockChannel(1024);
        var transport = new PipelineKafkaTransport(channel);
        // Layout after each step (BUFFER_SIZE=65536):
        //   add 50_000 'X'  -> start=0,     end=50_000, count=50_000
        //   fetch 1_000     -> start=1_000, end=50_000, count=49_000   (X occupies [1_000,50_000))
        //   add 16_000 'Y'  -> count+n=65_000 <= 65_536, so NO compact; start stays 1_000.
        //     first=min(16_000, 65_536-50_000=15_536)=15_536 -> Y at buffer[50_000..65_536)
        //     then 464      -> wraps to buffer[0..464); end=464, count=65_000
        // So the single logical stream is: 49_000 'X' (ring[1_000..50_000)) then 16_000 'Y'.
        // The physical ring wrap (buffer end -> 0) falls 15_536 bytes into the Y region.
        byte[] xs = new byte[50_000];
        java.util.Arrays.fill(xs, (byte) 'X');
        assertThat(transport.add(ByteBuffer.wrap(xs))).isEqualTo(50_000);
        var probe = ByteBuffer.allocate(1_000);
        assertThat(transport.fetch(probe)).isEqualTo(1_000);
        byte[] ys = new byte[16_000];
        java.util.Arrays.fill(ys, (byte) 'Y');
        assertThat(transport.add(ByteBuffer.wrap(ys))).isEqualTo(16_000);
        assertThat(transport.peek()).isEqualTo(65_000);

        // A single fetch must reassemble all 65_000 bytes in logical order, crossing the
        // physical ring wrap. `all[i]` = the i-th logical byte.
        var dst = ByteBuffer.allocate(65_000);
        int n = transport.fetch(dst);
        assertThat(n).isEqualTo(65_000);
        dst.flip();
        byte[] all = new byte[65_000];
        dst.get(all);
        assertThat(all[0]).isEqualTo((byte) 'X');        // stream head
        assertThat(all[48_999]).isEqualTo((byte) 'X');   // last of the 49_000 X
        assertThat(all[49_000]).isEqualTo((byte) 'Y');   // X/Y transition at 49_000
        // Physical ring wrap sits at logical index 64_535/64_536 (first chunk = ring[1_000..65_536)).
        assertThat(all[64_535]).isEqualTo((byte) 'Y');   // last byte before physical wrap
        assertThat(all[64_536]).isEqualTo((byte) 'Y');   // first byte after physical wrap
        assertThat(all[64_999]).isEqualTo((byte) 'Y');   // stream tail
        assertThat(transport.peek()).isZero();
        assertThat(channel.writeCalls()).isZero();
    }

    @Test
    void testPipelineClose() {
        var mockChannel = new MockChannel(1024);
        var transport = new PipelineKafkaTransport(mockChannel);
        transport.close();
        assertThat(transport.isOpen()).isFalse();
        assertThat(mockChannel.isOpen()).isFalse();
    }

    @Test
    void testPipelineReceiveAfterClose() {
        var transport = new PipelineKafkaTransport(new MockChannel(1024));
        transport.close();
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testPipelineGetChannel() {
        var mockChannel = new MockChannel(1024);
        var transport = new PipelineKafkaTransport(mockChannel);
        assertThat(transport.getChannel()).isSameAs(mockChannel);
    }
}
