package ssg.legoflow.xmpp.transport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import ssg.legoflow.xmpp.core.Stanza;
import ssg.legoflow.xmpp.stream.XmppCodec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the byte-level {@link XmppTransport} SPI: {@link InMemoryXmppTransport} pair
 * semantics (in main), {@link PipelineXmppTransport} ring/queue behavior over a mock
 * {@link DataChannel}, and a protocol round-trip (headless {@code XmppServer} core + client
 * stream) over an in-memory pair — mirroring the NATS {@code NatsTransportTest} reference form.
 */
class XmppTransportTest {

    private InMemoryXmppTransport[] pair;

    @BeforeEach
    void setUp() {
        pair = InMemoryXmppTransport.createPair();
    }

    @AfterEach
    void tearDown() {
        try {
            pair[0].close();
            pair[1].close();
        } catch (Exception ignored) {
        }
    }

    private static ByteBuffer bytes(String s) {
        // ByteBuffer.wrap() already yields position=0, limit=length — no flip() (that would
        // zero out remaining() and silently send nothing).
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InMemoryXmppTransport t, long timeoutMs) {
        var buf = new byte[8192];
        var bb = ByteBuffer.wrap(buf);
        int n = t.receiveWithTimeout(bb, timeoutMs, TimeUnit.MILLISECONDS);
        if (n <= 0) return null;
        bb.position(0);
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    // --- InMemoryXmppTransport ---

    @Test
    void testCreatePairConnectsBothWays() throws Exception {
        var latch = new CountDownLatch(1);
        var got = new AtomicReference<String>();
        Thread.startVirtualThread(() -> {
            try {
                got.set(readAll(pair[1], 2000));
            } finally {
                latch.countDown();
            }
        });
        pair[0].send(bytes("<message/>"));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(got.get()).isEqualTo("<message/>");
    }

    @Test
    void testPartialReadRequeuesTail() {
        pair[0].send(bytes("abcdef"));
        ByteBuffer buf = ByteBuffer.allocate(4);
        int n = pair[1].receiveWithTimeout(buf, 1000, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(4);
        buf.flip();
        byte[] part = new byte[4];
        buf.get(part);
        assertThat(new String(part, StandardCharsets.UTF_8)).isEqualTo("abcd");

        ByteBuffer rest = ByteBuffer.allocate(8);
        int n2 = pair[1].receiveWithTimeout(rest, 1000, TimeUnit.MILLISECONDS);
        assertThat(n2).isEqualTo(2);
        rest.flip();
        byte[] tail = new byte[2];
        rest.get(tail);
        assertThat(new String(tail, StandardCharsets.UTF_8)).isEqualTo("ef");
    }

    @Test
    void testTimeoutIsNotEof() throws Exception {
        // No data, no close: a timeout returns -1 but the transport stays open — a silent
        // peer must never surface as a spurious EOF.
        int n = pair[0].receiveWithTimeout(ByteBuffer.allocate(16), 100, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
        assertThat(pair[0].isOpen()).isTrue();
    }

    @Test
    void testCloseWakesBlockedPeerReader() throws Exception {
        var result = new int[]{-99};
        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            result[0] = pair[0].receiveWithTimeout(ByteBuffer.allocate(16), 5000, TimeUnit.MILLISECONDS);
            latch.countDown();
        });
        Thread.sleep(50);
        pair[1].close();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result[0]).isEqualTo(-1);
        assertThat(pair[0].isOpen()).isFalse();
    }

    @Test
    void testSendAfterCloseIsNoOp() {
        pair[0].close();
        pair[0].send(bytes("gone"));
        // The peer reader must observe -1 (closed), never the stale "gone" bytes.
        assertThat(pair[1].receiveWithTimeout(ByteBuffer.allocate(16), 100, TimeUnit.MILLISECONDS)).isEqualTo(-1);
    }

    @Test
    void testCloseIsIdempotent() {
        pair[0].close();
        assertThatCode(() -> pair[0].close()).doesNotThrowAnyException();
        pair[1].close();
    }

    @Test
    void testBufferedDataReadableAfterClose() {
        pair[0].send(bytes("tail"));
        pair[0].close();
        var buf = ByteBuffer.allocate(16);
        int n = pair[1].receiveWithTimeout(buf, 100, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(4); // buffered bytes still readable before EOF
    }

    // --- PipelineXmppTransport (production transport over a DataChannel) ---

    static class MockChannel implements DataChannel {
        private volatile boolean open = true;
        private volatile SelectionKey key;

        @Override public int read(ByteBuffer buffer) throws IOException { return 0; }
        @Override public int write(ByteBuffer buffer) throws IOException {
            if (!open) throw new IOException("closed");
            return buffer.remaining();
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { open = false; }
        @Override public SelectionKey getSelectionKey() { return key; }
        void setKey(SelectionKey k) { key = k; }
    }

    @Test
    void testPipelineAddAndFetch() {
        var transport = new PipelineXmppTransport(new MockChannel());
        int added = transport.add(ByteBuffer.wrap("Hello".getBytes()));
        assertThat(added).isEqualTo(5);
        assertThat(transport.peek()).isEqualTo(5);

        var buf = ByteBuffer.allocate(1024);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(5);
        assertThat(transport.peek()).isZero();
    }

    @Test
    void testPipelineOnReadThenReceive() throws Exception {
        var transport = new PipelineXmppTransport(new MockChannel());
        transport.onRead(null, ByteBuffer.wrap("data".getBytes()));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test
    void testPipelineReceiveTimeout() {
        var transport = new PipelineXmppTransport(new MockChannel());
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1); // nothing to read, still open -> timeout
    }

    @Test
    void testPipelineSendEnqueues() {
        var transport = new PipelineXmppTransport(new MockChannel());
        transport.send(ByteBuffer.wrap("test".getBytes()));
        assertThat(transport.isOpen()).isTrue();
    }

    @Test
    void testPipelineClose() throws Exception {
        var mockChannel = new MockChannel();
        var transport = new PipelineXmppTransport(mockChannel);
        transport.close();
        assertThat(transport.isOpen()).isFalse();
    }

    @Test
    void testPipelineReceiveAfterClose() {
        var transport = new PipelineXmppTransport(new MockChannel());
        transport.close();
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test
    void testPipelineGetChannel() {
        var mockChannel = new MockChannel();
        var transport = new PipelineXmppTransport(mockChannel);
        assertThat(transport.getChannel()).isSameAs(mockChannel);
    }

    @Test
    void testPipelineLargeData() {
        var transport = new PipelineXmppTransport(new MockChannel());
        byte[] data = "X".repeat(10000).getBytes();
        int added = transport.add(ByteBuffer.wrap(data));
        assertThat(added).isEqualTo(10000);

        var buf = ByteBuffer.allocate(10000);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(10000);
    }

    // --- Protocol round-trip over an in-memory pair (no sockets) ---

    @Test
    void testClientToServerStanzaRoundTrip() throws Exception {
        var codec = new XmppCodec();

        var client = new ssg.legoflow.xmpp.client.XmppClient(pair[0]);
        var config = ssg.legoflow.xmpp.client.XmppClientConfig.defaults("test.example", "test.example");
        client.connect(config).join();

        var server = new ssg.legoflow.xmpp.server.XmppServer(0);
        var received = new CountDownLatch(1);
        var serverMsg = new AtomicReference<Stanza>();
        server.addStanzaHandler("echo", s -> {
            if (s instanceof MessageStanza) {
                serverMsg.set(s);
                received.countDown();
            }
        });
        server.handleConnection(pair[1]);
        server.start();

        try {
            assertThat(client.isConnected()).isTrue();
            assertThat(client.login("user", "pass").join()).isTrue();
            client.sendMessage(new JID("bob", "test.example", null), "hello");

            assertThat(received.await(5, TimeUnit.SECONDS))
                    .as("server should receive the stanza over the in-memory pair").isTrue();
            assertThat(serverMsg.get()).isInstanceOf(MessageStanza.class);
            assertThat(((MessageStanza) serverMsg.get()).body()).contains("hello");
        } finally {
            client.close();
            server.close();
        }
    }
}
