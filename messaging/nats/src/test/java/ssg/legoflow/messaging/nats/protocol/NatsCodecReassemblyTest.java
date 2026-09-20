package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/**
 * Stream-reassembly tests for {@link NatsCodec} over a real byte stream
 * (the STOMP lesson: never trust a single read).
 *
 * <p>Covers: multiple operations in one read, a frame split across many reads,
 * and byte-exact payload fidelity (UTF-8: N bytes == N chars, so the codec's
 * length-counted payload read cannot drift).
 */
class NatsCodecReassemblyTest {

    /**
     * A stream that delivers at most {@code chunk} bytes per read, feeding
     * {@code data} in arbitrary slices — simulating TCP segmentation.
     */
    private static class ChunkedStream extends java.io.InputStream {
        private final byte[] data;
        private final int chunk;
        private int pos;

        ChunkedStream(byte[] data, int chunk) {
            this.data = data;
            this.chunk = chunk;
        }

        @Override
        public int read() {
            if (pos >= data.length) return -1;
            return data[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = Math.min(Math.min(chunk, len), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    private static BufferedReader readerOf(String protocol) {
        var bytes = protocol.getBytes(StandardCharsets.UTF_8);
        return new BufferedReader(
                new InputStreamReader(new ChunkedStream(bytes, 1), StandardCharsets.UTF_8));
    }

    private static void writeAll(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testMultipleOpsInOneRead() throws IOException {
        // PUB (with payload) + PING + SUB + PONG in a single byte stream.
        String stream = NatsCodec.encodePub("a.b", null, "hi".getBytes(StandardCharsets.UTF_8))
                + NatsCodec.encodePing()
                + NatsCodec.encodeSub("c.d", null, "7")
                + NatsCodec.encodePong();
        var reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));

        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.subject()).isEqualTo("a.b");
        assertThat(pub.payload()).isEqualTo("hi".getBytes(StandardCharsets.UTF_8));
        assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Ping.class);
        var sub = (NatsCodec.ParsedOp.Sub) NatsCodec.readOp(reader);
        assertThat(sub.subject()).isEqualTo("c.d");
        assertThat(sub.sid()).isEqualTo("7");
        assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Pong.class);
    }

    @Test
    void testFrameSplitByteByByte() throws IOException {
        var headers = new NatsHeaders();
        headers.set("X-K", "V");
        byte[] payload = "split-me-across-reads".getBytes(StandardCharsets.UTF_8);
        String stream = NatsCodec.encodeHpub("s.p", "_INBOX.1", headers, payload)
                + NatsCodec.encodePong();
        var reader = readerOf(stream);

        var hpub = (NatsCodec.ParsedOp.Hpub) NatsCodec.readOp(reader);
        assertThat(hpub.subject()).isEqualTo("s.p");
        assertThat(hpub.replyTo()).isEqualTo("_INBOX.1");
        assertThat(hpub.headers().getFirst("X-K")).isEqualTo("V");
        assertThat(hpub.payload()).isEqualTo(payload);
        assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Pong.class);
    }

    @Test
    void testPubWithPayloadSplitByteByByte() throws IOException {
        byte[] payload = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        String stream = NatsCodec.encodePub("t", null, payload);
        var reader = readerOf(stream);

        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.payload()).isEqualTo(payload);
    }

    @Test
    void testMsgWithReplySplitByteByByte() throws IOException {
        byte[] payload = "reply-payload".getBytes(StandardCharsets.UTF_8);
        String stream = NatsCodec.encodeMsg("m.s", "9", "_INBOX.r", payload);
        var reader = readerOf(stream);

        var msg = (NatsCodec.ParsedOp.Msg) NatsCodec.readOp(reader);
        assertThat(msg.subject()).isEqualTo("m.s");
        assertThat(msg.sid()).isEqualTo("9");
        assertThat(msg.replyTo()).isEqualTo("_INBOX.r");
        assertThat(msg.payload()).isEqualTo(payload);
    }

    @Test
    void testZeroLengthPayloadStillConsumesTrailingCrlf() throws IOException {
        // PUB ... 0\r\n\r\n — the empty-payload path must still consume the CRLF line.
        String stream = NatsCodec.encodePub("z", null, new byte[0]) + NatsCodec.encodePing();
        var reader = readerOf(stream);

        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.payload()).isEmpty();
        assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Ping.class);
    }

    @Test
    void testPayloadByteCountContractRoundTrip() throws IOException {
        // The NATS protocol declares payload size in BYTES; the codec reads it via a char
        // stream. For ASCII the two coincide, so reassembly is byte-exact (the contract
        // the codec is written against). Split byte-by-byte.
        byte[] payload = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String stream = NatsCodec.encodePub("c.1", null, payload);
        var reader = readerOf(stream);

        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.payload()).isEqualTo(payload);
    }

    @Test
    void testPayloadContainingCrlfReassembles() throws IOException {
        // The nastiest stream case: the payload itself contains CR/LF bytes. The codec must
        // consume exactly `size` bytes and then find the real trailing CRLF + next op —
        // it must NOT mis-terminate on the embedded newlines.
        byte[] payload = "line1\r\nline2\r\ntail".getBytes(StandardCharsets.UTF_8); // 18 bytes
        String stream = NatsCodec.encodePub("n.1", null, payload) + NatsCodec.encodePing();
        var reader = readerOf(stream);

        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.payload()).isEqualTo(payload);
        assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Ping.class);
    }

    @Test
    void testHmsgSplitByteByByte() throws IOException {
        var headers = new NatsHeaders();
        headers.set("H1", "v1");
        headers.set("H2", "v2");
        byte[] payload = "hmsg-payload".getBytes(StandardCharsets.UTF_8);
        String stream = NatsCodec.encodeHmsg("h.s", "3", "_INBOX.x", headers, payload);
        var reader = readerOf(stream);

        var hmsg = (NatsCodec.ParsedOp.Hmsg) NatsCodec.readOp(reader);
        assertThat(hmsg.subject()).isEqualTo("h.s");
        assertThat(hmsg.sid()).isEqualTo("3");
        assertThat(hmsg.headers().getFirst("H1")).isEqualTo("v1");
        assertThat(hmsg.headers().getFirst("H2")).isEqualTo("v2");
        assertThat(hmsg.payload()).isEqualTo(payload);
    }

    @Test
    void testManyOpsBackToBackOverChunkedStream() throws IOException {
        // 50 PING/PONG pairs + a PUB in one stream, read in 7-byte chunks.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < 50; i++) {
            writeAll(bos, NatsCodec.encodePing());
            writeAll(bos, NatsCodec.encodePong());
        }
        writeAll(bos, NatsCodec.encodePub("end", null, "last".getBytes(StandardCharsets.UTF_8)));

        var bytes = bos.toByteArray();
        var reader = new BufferedReader(new InputStreamReader(
                new ChunkedStream(bytes, 7), StandardCharsets.UTF_8));
        var pings = new AtomicInteger();
        var pongs = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Ping.class);
            pings.incrementAndGet();
            assertThat(NatsCodec.readOp(reader)).isInstanceOf(NatsCodec.ParsedOp.Pong.class);
            pongs.incrementAndGet();
        }
        var pub = (NatsCodec.ParsedOp.Pub) NatsCodec.readOp(reader);
        assertThat(pub.payload()).isEqualTo("last".getBytes(StandardCharsets.UTF_8));
        assertThat(pings.get()).isEqualTo(50);
        assertThat(pongs.get()).isEqualTo(50);
        assertThat(NatsCodec.readOp(reader)).isNull(); // clean EOF
    }

    @Test
    void testEndToEndOverInMemoryTransportPair() throws Exception {
        // The full seam: writer on one end, NatsCodec reader on the other —
        // exactly how NatsClient/NatsServer talk over InMemoryNatsTransport.
        var pair = ssg.legoflow.messaging.nats.transport.InMemoryNatsTransport.createPair();
        try {
            var in = new ssg.legoflow.messaging.nats.transport.TransportStreams(pair[0]);
            var out = new ssg.legoflow.messaging.nats.transport.TransportStreams(pair[1]);
            var reader = new BufferedReader(new InputStreamReader(
                    out.inputStream(), StandardCharsets.UTF_8));
            var writer = new BufferedWriter(new OutputStreamWriter(in.outputStream(), StandardCharsets.UTF_8));

            var latch = new CountDownLatch(1);
            var got = new String[]{null};
            var thread = Thread.startVirtualThread(() -> {
                try {
                    got[0] = readHeaderLine(reader);
                    writer.write(NatsCodec.encodePong());
                    writer.flush();
                } catch (Exception e) {
                    // reader hit EOF
                } finally {
                    latch.countDown();
                }
            });

            writer.write(NatsCodec.encodePing());
            writer.flush();
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(got[0]).isEqualTo("PING");
            // Read the PONG on the other side.
            var pong = NatsCodec.readOp(reader);
            assertThat(pong).isInstanceOf(NatsCodec.ParsedOp.Pong.class);
        } finally {
            pair[0].close();
            pair[1].close();
        }
    }

    private static String readHeaderLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        return line == null ? null : line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
