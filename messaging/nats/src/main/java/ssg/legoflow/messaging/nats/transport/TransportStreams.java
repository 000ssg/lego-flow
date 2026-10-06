package ssg.legoflow.messaging.nats.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Adapts a byte-level {@link NatsTransport} to the character {@link InputStream}/{@link OutputStream}
 * pair the (line/text-framed) NATS codec and protocol core already use via
 * {@code BufferedReader}/{@code BufferedWriter}.
 *
 * <p>This is the seam that lets the proven line-based protocol logic run over any transport —
 * in-memory pairs in tests, {@link PipelineNatsTransport} in production — without the protocol core
 * touching sockets.
 *
 * <p><b>Blocking reads:</b> {@link TransportInputStream#read(byte[], int, int)} keeps calling
 * {@link NatsTransport#receiveWithTimeout} until at least one byte is available. A read
 * timeout (a silent broker) is retried, so it never surfaces as a spurious EOF; the stream
 * returns {@code -1} only when the transport is actually closed. This is what lets
 * {@code BufferedReader.readLine()} block correctly on the NATS handshake and PING/PONG.
 *
 * @since 0.1.0
 */
public final class TransportStreams implements AutoCloseable {

    /** Internal polling interval; never surfaced to the caller as EOF. */
    static final long READ_TIMEOUT_MILLIS = 5_000;

    private final NatsTransport transport;
    private final TransportInputStream in;
    private final TransportOutputStream out;

    public TransportStreams(NatsTransport transport) {
        this.transport = transport;
        this.in = new TransportInputStream();
        this.out = new TransportOutputStream();
    }

    public InputStream inputStream() {
        return in;
    }

    public OutputStream outputStream() {
        return out;
    }

    public NatsTransport transport() {
        return transport;
    }

    @Override
    public void close() {
        transport.close();
    }

    /** Convenience for encoding a UTF-8 string to a transport buffer. */
    public static ByteBuffer bytes(String s) {
        // ByteBuffer.wrap() already yields position=0, limit=length — no flip() (that would
        // zero out remaining() and silently send nothing).
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private final class TransportInputStream extends InputStream {
        private final byte[] chunk = new byte[8192];

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            // Block until at least one byte is available; -1 only on close.
            while (transport.isOpen()) {
                ByteBuffer buf = ByteBuffer.wrap(chunk, 0, Math.min(len, chunk.length));
                int n = transport.receiveWithTimeout(buf, READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (n > 0) {
                    int take = Math.min(n, len);
                    System.arraycopy(chunk, 0, b, off, take);
                    return take;
                }
                if (n < 0 && !transport.isOpen()) {
                    return -1; // closed
                }
                // n <= 0 while still open: read timeout — keep waiting, never a false EOF.
            }
            return -1;
        }
    }

    private final class TransportOutputStream extends OutputStream {
        @Override
        public void write(int b) {
            transport.send(ByteBuffer.wrap(new byte[]{(byte) b}));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            var buf = ByteBuffer.allocate(len);
            buf.put(b, off, len);
            buf.flip();
            transport.send(buf);
        }

        @Override
        public void flush() {
            // Sends are queued per-write on the transport; nothing to flush here.
        }
    }
}
