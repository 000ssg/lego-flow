package ssg.legoflow.messaging.amqp091.transport;

import ssg.legoflow.messaging.amqp091.common.Amqp091Constants;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * AMQP 0-9-1 frame on the wire.
 *
 * <p>Frame layout:
 * <pre>
 *  Byte 0:    Frame type (1=method, 2=header, 3=body)
 *  Bytes 1-4: Payload size (uint32 big-endian)
 *  Bytes 5-6: Frame body type (uint16 big-endian) — only for method frames
 *  Bytes 7..N: Payload
 *  Last byte:  0xCE (frame end)
 * </pre>
 *
 * @since 0.2.0
 */
public final class Amqp091Frame {

    private final byte type;
    private final int payloadSize;
    private final byte[] payload;
    private final int channel;

    private Amqp091Frame(Builder builder) {
        this.type = builder.type;
        this.payloadSize = builder.payloadSize;
        this.payload = builder.payload;
        this.channel = builder.channel;
    }

    public byte type() { return type; }
    public int payloadSize() { return payloadSize; }
    public int channel() { return channel; }
    public ByteBuffer payload() { return payload != null ? ByteBuffer.wrap(payload) : ByteBuffer.allocate(0); }

    /** Check if this is a method frame. */
    public boolean isMethod() { return type == Amqp091Constants.FRAME_METHOD; }

    /** Check if this is a header frame. */
    public boolean isHeader() { return type == Amqp091Constants.FRAME_HEADER; }

    /** Check if this is a body frame. */
    public boolean isBody() { return type == Amqp091Constants.FRAME_BODY; }

    /** Check if this is a heartbeat frame (empty payload). */
    public boolean isHeartbeat() { return payloadSize == 0 && !isBody(); }

    @Override
    public String toString() {
        String typeName = switch (type) {
            case Amqp091Constants.FRAME_METHOD -> "method";
            case Amqp091Constants.FRAME_HEADER -> "header";
            case Amqp091Constants.FRAME_BODY -> "body";
            default -> "unknown(0x" + Integer.toHexString(type & 0xFF) + ")";
        };
        return "Amqp091Frame{type=" + typeName + ", size=" + payloadSize + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private byte type = Amqp091Constants.FRAME_METHOD;
        private int payloadSize = 0;
        private byte[] payload;
        private int channel;

        public Builder type(byte type) { this.type = type; return this; }
        public Builder payloadSize(int payloadSize) { this.payloadSize = payloadSize; return this; }
        public Builder payload(byte[] payload) { this.payload = payload; return this; }
        public Builder channel(int channel) { this.channel = channel; return this; }
        public Amqp091Frame build() { return new Amqp091Frame(this); }
    }
}
