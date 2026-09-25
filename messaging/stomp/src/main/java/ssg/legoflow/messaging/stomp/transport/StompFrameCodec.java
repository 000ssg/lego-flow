package ssg.legoflow.messaging.stomp.transport;

import ssg.legoflow.messaging.stomp.core.StompCodec;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompProtocolException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Frame-level codec that sits on top of a byte-level {@link StompTransport}.
 *
 * <p>Handles frame encoding/decoding using {@link StompCodec} and provides
 * frame-level {@code send(StompFrame)} / {@code receive()} convenience methods.
 * The STOMP protocol core (broker, client) uses this wrapper instead of raw bytes.
 *
 * <p><b>Stream reassembly.</b> A TCP read can carry a partial frame, several
 * complete frames, or both. This codec accumulates all incoming bytes in an
 * internal reassembler and decodes exactly one complete frame per
 * {@code receive()} call: partial frames wait for more bytes (via
 * {@link StompCodec#findFrameEnd}, which throws
 * {@link StompCodec.FrameIncompleteException} until the terminator arrives),
 * and trailing frames beyond the first stay in the reassembler for the next
 * call. Nothing ever gets dropped.
 *
 * <p>Frame boundary detection: after the body, STOMP expects a NULL byte
 * ({@code \0}). For WebSocket transport, the NULL terminator is optional
 * (WebSocket provides message boundaries), so {@code strictNull} can be
 * disabled.
 */
public final class StompFrameCodec {

    private static final int READ_SIZE = 65536;
    private static final int MAX_ACCUMULATOR = 8 * 1024 * 1024;

    private final StompTransport transport;
    private final boolean strictNull;
    private final ByteArrayOutputStream accumulator = new ByteArrayOutputStream(8192);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_SIZE);

    public StompFrameCodec(StompTransport transport) {
        this(transport, true);
    }

    public StompFrameCodec(StompTransport transport, boolean strictNull) {
        this.transport = transport;
        this.strictNull = strictNull;
    }

    public StompTransport getTransport() {
        return transport;
    }

    /** Sends a STOMP frame by encoding it to bytes and sending via transport. */
    public void send(StompFrame frame) {
        byte[] data = StompCodec.encode(frame);
        transport.send(ByteBuffer.wrap(data));
    }

    /**
     * Receives the next complete STOMP frame, reassembling the byte stream as needed.
     *
     * <p>All bytes returned by a read are retained: bytes after a complete frame
     * (subsequent frames) and bytes of an incomplete frame (a NULL terminator or
     * the rest of a content-length body not yet arrived) are kept for the next
     * {@code receive()} call.
     *
     * @return the next complete frame, or {@code null} when the transport is
     *         closed with no complete frame left to deliver
     * @throws StompProtocolException if the byte stream is malformed (unknown
     *         command, unterminated header line, invalid content-length)
     */
    public StompFrame receive() {
        while (true) {
            // Try to complete a frame from what we already have.
            while (accumulator.size() > 0) {
                byte[] data = accumulator.toByteArray();
                int end;
                try {
                    end = StompCodec.findFrameEnd(data, strictNull);
                } catch (StompCodec.FrameIncompleteException e) {
                    break; // Partial frame — need more bytes.
                }
                if (end == 0) {
                    // Buffer holds nothing decodable (defensive); wait for more.
                    break;
                }
                StompFrame frame = StompCodec.decode(data, 0, end, strictNull);
                // Keep everything after this frame for the next call.
                accumulator.reset();
                int kept = data.length - end;
                if (kept > 0) {
                    accumulator.write(data, end, kept);
                }
                return frame;
            }

            // No complete frame: block for the next read.
            readBuffer.clear();
            int n = transport.receiveWithTimeout(readBuffer, 5, java.util.concurrent.TimeUnit.SECONDS);
            if (n < 0) {
                if (!transport.isOpen()) {
                    // Closed: a partial frame can never complete.
                    return null;
                }
                // Timed out with no data — keep waiting, do not kill the
                // caller's receive loop on broker silence.
                continue;
            }
            if (n == 0) {
                continue;
            }
            byte[] chunk = new byte[n];
            readBuffer.flip();
            readBuffer.get(chunk);
            if (accumulator.size() + n > MAX_ACCUMULATOR) {
                throw new StompProtocolException(
                        "STOMP frame larger than " + MAX_ACCUMULATOR + " bytes");
            }
            accumulator.write(chunk, 0, n);
        }
    }

    /** Encode a frame to a ByteBuffer. */
    public ByteBuffer encodeFrame(StompFrame frame) {
        byte[] data = StompCodec.encode(frame);
        return ByteBuffer.wrap(data);
    }

    /** Decode bytes to a STOMP frame. */
    public StompFrame decodeFrame(byte[] data) {
        return StompCodec.decode(data, strictNull);
    }
}
