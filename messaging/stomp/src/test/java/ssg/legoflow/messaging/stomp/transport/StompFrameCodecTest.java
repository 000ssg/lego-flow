package ssg.legoflow.messaging.stomp.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.messaging.stomp.core.StompCommand;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompHeaders;
import ssg.legoflow.messaging.stomp.core.StompCodec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class StompFrameCodecTest {

    private InMemoryTransport[] pair;
    private StompFrameCodec serverCodec;  // reads from pair[0] (receives what client sends)
    private StompFrameCodec clientCodec;  // reads from pair[1] (receives what server sends)

    @BeforeEach
    void setUp() {
        pair = InMemoryTransport.createPair();
        serverCodec = new StompFrameCodec(pair[0]);
        clientCodec = new StompFrameCodec(pair[1]);
    }

    @Test
    void testClientSendsServerReceives() throws Exception {
        var headers = new StompHeaders();
        headers.put("destination", "/topic/test");
        var frame = StompFrame.withText(StompCommand.SEND, headers, "Hello");
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.SEND);
        assertThat(received.header("destination")).isEqualTo("/topic/test");
        assertThat(received.bodyAsText()).isEqualTo("Hello");
    }

    @Test
    void testServerSendsClientReceives() throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.VERSION, "1.2");
        var frame = new StompFrame(StompCommand.CONNECTED, headers);
        serverCodec.send(frame);

        var received = clientCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.CONNECTED);
        assertThat(received.header(StompHeaders.VERSION)).isEqualTo("1.2");
    }

    @Test
    void testGetTransport() {
        assertThat(serverCodec.getTransport()).isEqualTo(pair[0]);
        assertThat(clientCodec.getTransport()).isEqualTo(pair[1]);
    }

    @Test
    void testTransportIsIndependent() {
        // Codec does not own transport lifecycle — transport stays open after codec use
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "test"));
        assertThat(pair[1].isOpen()).isTrue();
        assertThat(pair[0].isOpen()).isTrue();
    }

    @Test
    void testMultipleFrames() throws Exception {
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "msg1"));
        clientCodec.send(StompFrame.withText(StompCommand.SEND, new StompHeaders(), "msg2"));

        var f1 = serverCodec.receive();
        var f2 = serverCodec.receive();
        assertThat(f1.command()).isEqualTo(StompCommand.SEND);
        assertThat(f2.command()).isEqualTo(StompCommand.SEND);
        assertThat(f1.bodyAsText()).isEqualTo("msg1");
        assertThat(f2.bodyAsText()).isEqualTo("msg2");
    }

    @Test
    void testEncodeDecodeRoundTrip() throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "admin");
        headers.put(StompHeaders.PASSCODE, "secret");
        var frame = new StompFrame(StompCommand.CONNECT, headers);
        clientCodec.send(frame);

        // Server receives the frame
        var received = serverCodec.receive();
        assertThat(received.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(received.header(StompHeaders.LOGIN)).isEqualTo("admin");
        assertThat(received.header(StompHeaders.PASSCODE)).isEqualTo("secret");
    }

    @Test
    void testEncodeDecodeEmptyBody() throws Exception {
        var headers = new StompHeaders();
        var frame = new StompFrame(StompCommand.BEGIN, headers);
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received).isNotNull();
        assertThat(received.command()).isEqualTo(StompCommand.BEGIN);
    }

    @Test
    void testLargeBody() throws Exception {
        var headers = new StompHeaders();
        var body = "X".repeat(10000);
        var frame = StompFrame.withText(StompCommand.SEND, headers, body);
        clientCodec.send(frame);

        var received = serverCodec.receive();
        assertThat(received.bodyAsText()).hasSize(10000);
    }

    @Test
    void testReceiveTimeoutOnTransport() throws Exception {
        var buf = ByteBuffer.allocate(1024);
        int n = pair[1].receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    // --- Stream reassembly (TCP semantics: batched + split frames) ---

    /**
     * A transport that delivers exactly the chunks injected via
     * {@link #deliver(String)} — one chunk per read, like a TCP read.
     */
    static final class FrameChunkTransport implements StompTransport {
        final LinkedBlockingQueue<ByteBuffer> chunks = new LinkedBlockingQueue<>();
        volatile boolean open = true;
        volatile long receiveTimeoutMs = 5000;

        void deliver(String s) {
            chunks.offer(ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public void send(ByteBuffer data) {
            // No peer: outgoing frames are ignored.
        }

        @Override
        public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
            if (!open) return -1;
            try {
                ByteBuffer chunk = chunks.poll(Math.min(timeout, receiveTimeoutMs), unit);
                if (chunk == null) return -1;
                int n = Math.min(buffer.remaining(), chunk.remaining());
                ByteBuffer src = chunk;
                if (n < chunk.remaining()) {
                    src = chunk.duplicate();
                    src.limit(n);
                }
                buffer.put(src);
                return n;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    @Test
    void testBatchedFramesInSingleRead() throws Exception {
        // Two frames arriving in ONE read must both be delivered — the
        // original codec silently dropped the second frame here.
        var t = new FrameChunkTransport();
        var codec = new StompFrameCodec(t);
        t.deliver("SEND\n\nmsg-1\0SEND\n\nmsg-2\0");

        var f1 = codec.receive();
        var f2 = codec.receive();
        assertThat(f1.bodyAsText()).isEqualTo("msg-1");
        assertThat(f2.bodyAsText()).isEqualTo("msg-2");
    }

    @Test
    void testSplitFrameReassembly() throws Exception {
        // Frame split across two reads: partial, then the rest.
        var t = new FrameChunkTransport();
        var codec = new StompFrameCodec(t);
        t.deliver("SEND\ndestination:/q\n\nmsg");
        t.deliver("-1\0");

        var f = codec.receive();
        assertThat(f.command()).isEqualTo(StompCommand.SEND);
        assertThat(f.header("destination")).isEqualTo("/q");
        assertThat(f.bodyAsText()).isEqualTo("msg-1");
    }

    @Test
    void testSplitFrameWithTrailingNextFrame() throws Exception {
        // First read: split frame + beginning of the next one.
        // Second read: the tail of the next frame.
        var t = new FrameChunkTransport();
        var codec = new StompFrameCodec(t);
        t.deliver("SEND\n\nmsg-1\0SEND\n\nmsg-2\0");
        t.deliver("SEND\n\nmsg-3\0");

        var f1 = codec.receive();
        var f2 = codec.receive();
        var f3 = codec.receive();
        assertThat(f1.bodyAsText()).isEqualTo("msg-1");
        assertThat(f2.bodyAsText()).isEqualTo("msg-2");
        assertThat(f3.bodyAsText()).isEqualTo("msg-3");
    }

    @Test
    void testHeartbeatFollowedByFrame() throws Exception {
        // A heart-beat (EOL) and a real frame in one read.
        var t = new FrameChunkTransport();
        var codec = new StompFrameCodec(t);
        t.deliver("\nSEND\n\nmsg-1\0");

        var hb = codec.receive();
        assertThat(hb.isHeartbeat()).isTrue();

        var f = codec.receive();
        assertThat(f.bodyAsText()).isEqualTo("msg-1");
    }

    @Test
    void testTimeoutDoesNotKillReceiveLoop() throws Exception {
        // Partial frame (no NULL terminator) + a read that times out must NOT
        // end the receive loop; the next read completes the frame.
        var t = new FrameChunkTransport();
        t.receiveTimeoutMs = 50;
        var codec = new StompFrameCodec(t);
        t.deliver("SEND\n\nmsg-1"); // no NULL terminator: incomplete
        t.deliver("\0");

        var f = codec.receive();
        assertThat(f.bodyAsText()).isEqualTo("msg-1");
    }

    @Test
    void testClosedWithPartialFrameReturnsNull() throws Exception {
        var t = new FrameChunkTransport();
        var codec = new StompFrameCodec(t);
        t.deliver("SEND\n\nmsg-1"); // incomplete, then the transport dies
        t.close();

        assertThat(codec.receive()).isNull();
    }

    @Test
    void testCloseTransport() {
        pair[0].close();
        assertThat(pair[0].isOpen()).isFalse();
        // Pair[1] is independent
        assertThat(pair[1].isOpen()).isTrue();
    }
}
