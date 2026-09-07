package ssg.legoflow.messaging.stomp.transport;

import ssg.legoflow.messaging.stomp.core.StompCodec;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompCommand;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Frame-level codec that sits on top of a byte-level {@link StompTransport}.
 *
 * <p>Handles frame encoding/decoding using {@link StompCodec} and provides
 * frame-level {@code send(StompFrame)} / {@code receive()} convenience methods.
 * The STOMP protocol core (broker, client) uses this wrapper instead of raw bytes.
 *
 * <p>Frame boundary detection: after the body, STOMP expects a NULL byte ({@code \0}).
 * For WebSocket transport, the NULL terminator is optional (WebSocket provides
 * message boundaries), so {@code strictNull} can be disabled.
 */
public final class StompFrameCodec {

    private final StompTransport transport;
    private final boolean strictNull;

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
     * Receives a STOMP frame by reading bytes and decoding.
     * Handles NULL byte termination and multi-frame accumulation.
     */
    public StompFrame receive() {
        ByteBuffer buf = ByteBuffer.allocate(65536);
        int total = transport.receive(buf);
        if (total <= 0) return null;

        buf.flip();
        byte[] data = new byte[total];
        buf.get(data);
        return StompCodec.decode(data, strictNull);
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
