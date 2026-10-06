package ssg.legoflow.messaging.nats.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the byte-level {@link NatsTransport} SPI: {@link InMemoryNatsTransport} pair
 * semantics (in main) and {@link PipelineNatsTransport} ring/queue behavior over a
 * mock {@link DataChannel} (mirrors the STOMP {@code PipelineTransportTest} reference form).
 */
class NatsTransportTest {

    private static ByteBuffer bytes(String s) {
        // ByteBuffer.wrap() already yields position=0, limit=length — no flip() (that would
        // zero out remaining() and silently send nothing).
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String readLine(NatsTransport t) {
        StringBuilder sb = new StringBuilder();
        var buf = new byte[1];
        while (true) {
            var bb = ByteBuffer.wrap(buf);
            int n = t.receive(bb);
            if (n <= 0) break;
            char c = (char) (buf[0] & 0xFF);
            if (c == '\n') break;
            if (c != '\r') sb.append(c);
        }
        return sb.toString();
    }

    // --- InMemoryNatsTransport ---

    @Test
    void testCreatePairConnectsBothWays() throws Exception {
        var pair = InMemoryNatsTransport.createPair();
        try {
            var latch = new CountDownLatch(1);
            var got = new String[]{null};
            Thread.startVirtualThread(() -> {
                try {
                    got[0] = readLine(pair[1]);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
            pair[0].send(bytes("PING\r\n"));
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(got[0]).isEqualTo("PING");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testPartialReadRequeuesTail() {
        var pair = InMemoryNatsTransport.createPair();
        try {
            pair[0].send(bytes("abcdef"));
            ByteBuffer buf = ByteBuffer.allocate(4);
            int n = pair[1].receive(buf);
            assertThat(n).isEqualTo(4);
            buf.flip();
            byte[] part = new byte[4];
            buf.get(part);
            assertThat(new String(part, StandardCharsets.UTF_8)).isEqualTo("abcd");

            ByteBuffer rest = ByteBuffer.allocate(8);
            int n2 = pair[1].receive(rest);
            assertThat(n2).isEqualTo(2);
            rest.flip();
            byte[] tail = new byte[2];
            rest.get(tail);
            assertThat(new String(tail, StandardCharsets.UTF_8)).isEqualTo("ef");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testCloseWakesBlockedPeerReader() throws Exception {
        var pair = InMemoryNatsTransport.createPair();
        var result = new int[]{-99};
        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            result[0] = pair[0].receive(ByteBuffer.allocate(16));
            latch.countDown();
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse(); // still blocked
        pair[1].close();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result[0]).isEqualTo(-1);
        assertThat(pair[0].isOpen()).isFalse();
    }

    @Test
    void testCloseWakesBlockedOwnReader() throws Exception {
        var pair = InMemoryNatsTransport.createPair();
        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            pair[1].close(); // own-side close while blocked
            latch.countDown();
        });
        int n = pair[1].receive(ByteBuffer.allocate(16));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(n).isEqualTo(-1);
        assertThat(pair[1].isOpen()).isFalse();
    }

    @Test
    void testSendAfterCloseIsNoOp() {
        var pair = InMemoryNatsTransport.createPair();
        pair[0].close();
        pair[0].send(bytes("gone"));
        // The peer reader must observe -1 (closed), never the stale "gone" bytes.
        assertThat(pair[1].receive(ByteBuffer.allocate(16))).isEqualTo(-1);
    }

    @Test
    void testCloseIsIdempotent() {
        var pair = InMemoryNatsTransport.createPair();
        pair[0].close();
        assertThatCode(() -> pair[0].close()).doesNotThrowAnyException();
        pair[1].close();
    }

    // --- TransportStreams (line adapter) ---

    @Test
    void testStreamAdapterRoundTrip() throws Exception {
        var pair = InMemoryNatsTransport.createPair();
        try {
            var streams = new TransportStreams(pair[0]);
            var peerStreams = new TransportStreams(pair[1]);

            streams.outputStream().write("SUB foo 1\r\n".getBytes(StandardCharsets.UTF_8));
            streams.outputStream().flush();

            var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(peerStreams.inputStream(), StandardCharsets.UTF_8));
            var latch = new CountDownLatch(1);
            String[] line = {null};
            Thread.startVirtualThread(() -> {
                try {
                    line[0] = reader.readLine();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(line[0]).isEqualTo("SUB foo 1");
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    @Test
    void testStreamReadBlocksUntilCloseReturnsEof() throws Exception {
        var pair = InMemoryNatsTransport.createPair();
        var streams = new TransportStreams(pair[0]);
        var latch = new CountDownLatch(1);
        int[] rc = {-99};
        Thread.startVirtualThread(() -> {
            try {
                rc[0] = streams.inputStream().read();
            } catch (IOException ignored) {
            } finally {
                latch.countDown();
            }
        });
        Thread.sleep(100);
        assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse(); // still blocking — no false EOF
        pair[1].close();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(rc[0]).isEqualTo(-1);
    }

    @Test
    void testBytesHelper() {
        var buf = TransportStreams.bytes("ab");
        assertThat(buf.remaining()).isEqualTo(2);
    }

    // --- PipelineNatsTransport (production transport over a DataChannel) ---

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

    @Test void testPipelineAddAndFetch() {
        var transport = new PipelineNatsTransport(new MockChannel());
        int added = transport.add(ByteBuffer.wrap("Hello".getBytes()));
        assertThat(added).isEqualTo(5);
        assertThat(transport.peek()).isEqualTo(5);

        var buf = ByteBuffer.allocate(1024);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(5);
        assertThat(transport.peek()).isZero();
    }

    @Test void testPipelineOnReadThenReceive() throws Exception {
        var transport = new PipelineNatsTransport(new MockChannel());
        transport.onRead(null, ByteBuffer.wrap("data".getBytes()));
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 1, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(4);
    }

    @Test void testPipelineReceiveTimeout() {
        var transport = new PipelineNatsTransport(new MockChannel());
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1); // nothing to read, still open -> timeout
    }

    @Test void testPipelineSendEnqueues() {
        var transport = new PipelineNatsTransport(new MockChannel());
        transport.send(ByteBuffer.wrap("test".getBytes()));
        assertThat(transport.isOpen()).isTrue();
    }

    @Test void testPipelineClose() throws Exception {
        var mockChannel = new MockChannel();
        var transport = new PipelineNatsTransport(mockChannel);
        transport.close();
        assertThat(transport.isOpen()).isFalse();
    }

    @Test void testPipelineReceiveAfterClose() {
        var transport = new PipelineNatsTransport(new MockChannel());
        transport.close();
        var buf = ByteBuffer.allocate(1024);
        int n = transport.receiveWithTimeout(buf, 50, TimeUnit.MILLISECONDS);
        assertThat(n).isEqualTo(-1);
    }

    @Test void testPipelineGetChannel() {
        var mockChannel = new MockChannel();
        var transport = new PipelineNatsTransport(mockChannel);
        assertThat(transport.getChannel()).isSameAs(mockChannel);
    }

    @Test void testPipelineLargeData() {
        var transport = new PipelineNatsTransport(new MockChannel());
        byte[] data = "X".repeat(10000).getBytes();
        int added = transport.add(ByteBuffer.wrap(data));
        assertThat(added).isEqualTo(10000);

        var buf = ByteBuffer.allocate(10000);
        int fetched = transport.fetch(buf);
        assertThat(fetched).isEqualTo(10000);
    }
}
