package ssg.legoflow.messaging.amqp091.transport;

import ssg.legoflow.messaging.amqp091.common.Amqp091Constants;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encodes and decodes AMQP 0-9-1 frames per the RabbitMQ specification.
 *
 * <p>Wire format (big-endian):
 * <pre>
 *  Non-heartbeat frame:
 *    TYPE   byte    Frame type (0x08=method, 0x09=header, 0x0A=body)
 *    CHAN   uint16  Channel number (big-endian)
 *    SIZE   uint32  Payload size (big-endian)
 *    PAYLOAD byte[] Payload bytes
 *    END    byte    0xCE (frame end)
 *
 *  Heartbeat frame:
 *    TYPE   byte    0x08
 *    END    byte    0xCE
 *    (SIZE=0 and CHAN are omitted for heartbeats)
 * </pre>
 *
 * <p>Frame sizes:
 * <ul>
 *   <li>Non-heartbeat: 1 + 2 + 4 + payload + 1 = 8 + payload</li>
 *   <li>Heartbeat: 1 + 1 = 2</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class Amqp091FrameCodec {

    private Amqp091FrameCodec() {}

    /**
     * Encode a method frame (channel 0, for connection/negotiation).
     */
    public static ByteBuffer encodeMethod(int methodNumber, byte[] payload) {
        int payloadSize = payload.length;
        int totalSize = 1 + 2 + 4 + payloadSize + 1; // type + chan + size + payload + end
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.BIG_ENDIAN);

        buf.put(Amqp091Constants.FRAME_METHOD);  // type
        buf.putShort((short) 0);                   // channel 0
        buf.putInt(payloadSize);                   // payload size
        buf.put(payload);
        buf.put(Amqp091Constants.FRAME_END);       // end
        buf.flip();
        return buf;
    }

    /**
     * Encode a heartbeat frame.
     * Heartbeats are 2 bytes: TYPE + END.
     */
    public static ByteBuffer encodeHeartbeat() {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put(Amqp091Constants.FRAME_METHOD);
        buf.put(Amqp091Constants.FRAME_END);
        buf.flip();
        return buf;
    }

    /**
     * Encode a generic frame (method, header, or body) for a specific channel.
     */
    public static ByteBuffer encodeFrame(int channel, byte frameType, int payloadSize,
                                         byte[] payload) {
        int totalSize = 1 + 2 + 4 + payloadSize + 1; // type + chan + size + payload + end
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.BIG_ENDIAN);

        buf.put(frameType);                       // type
        buf.putShort((short) channel);             // channel
        buf.putInt(payloadSize);                   // payload size
        buf.put(payload);
        buf.put(Amqp091Constants.FRAME_END);       // end
        buf.flip();
        return buf;
    }

    /**
     * Decode an AMQP 0-9-1 frame from a ByteBuffer.
     *
     * <p>Reads: TYPE(1) + CHAN(2) + SIZE(4) + PAYLOAD(N) + END(1)
     * For heartbeats (TYPE=0x08, SIZE=0): reads TYPE(1) + END(1).
     *
     * @param buf buffer positioned at frame start
     * @return the decoded frame, or null if not enough data
     */
    public static Amqp091Frame decode(ByteBuffer buf) {
        // Need at least 5 bytes: type(1) + chan(2) + size(4) would need more,
        // but minimum for heartbeat is 2 bytes
        if (buf.remaining() < 2) {
            return null;
        }

        // Save position to potentially rewind
        int startPos = buf.position();

        // Read type first
        byte frameType = buf.get();

        // Validate frame type
        if (frameType != Amqp091Constants.FRAME_METHOD
                && frameType != Amqp091Constants.FRAME_HEADER
                && frameType != Amqp091Constants.FRAME_BODY) {
            throw new RuntimeException(
                "Invalid AMQP 0-9-1 frame type: 0x" + Integer.toHexString(frameType & 0xFF));
        }

        // Heartbeat: type=0x08 and only needs TYPE + END = 2 bytes
        if (frameType == Amqp091Constants.FRAME_METHOD && buf.remaining() >= 1) {
            byte nextByte = buf.get();
            if (nextByte == Amqp091Constants.FRAME_END) {
                return Amqp091Frame.builder()
                    .type(frameType)
                    .payloadSize(0)
                    .build();
            }
        }

        // Rewind for normal frame parsing: type(1) + chan(2) + size(4) + payload + end
        buf.position(startPos);

        // Need at least: type(1) + chan(2) + size(4) = 7 bytes to read header
        if (buf.remaining() < 7) {
            return null;
        }

        // Read type
        frameType = buf.get();

        // Read channel
        int channel = buf.getShort() & 0xFFFF;

        // Read payload size
        int payloadSize = buf.getInt() & 0xFFFFFFFF;

        // Need: payload(SIZE) + end(1)
        if (buf.remaining() < payloadSize + 1) {
            return null;
        }

        // Read payload
        byte[] payload = new byte[payloadSize];
        if (payloadSize > 0) {
            buf.get(payload);
        }

        // Verify end octet
        byte end = buf.get();
        if (end != Amqp091Constants.FRAME_END) {
            throw new RuntimeException(
                "Missing frame end octet (0xCE) — got 0x" + Integer.toHexString(end & 0xFF));
        }

        return Amqp091Frame.builder()
            .type(frameType)
            .payloadSize(payloadSize)
            .payload(payload)
            .channel(channel)
            .build();
    }
}
