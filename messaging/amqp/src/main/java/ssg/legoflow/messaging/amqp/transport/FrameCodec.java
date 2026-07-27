package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import java.nio.ByteBuffer;

/**
 * Encodes and decodes AMQP 1.0 frames to/from the wire format.
 *
 * <p>Frame layout (section 2.3.1):
 * <pre>
 *  0       1       2       3       4       5       6       7       8     ...
 * +-------+-------+-------+-------+-------+-------+-------+-------+------...
 * | SIZE (uint32)                 | DOFF  | TYPE  | CHANNEL (uint16)  | BODY
 * +-------+-------+-------+-------+-------+-------+-------+-------+------...
 * </pre>
 *
 * @since 1.0.0
 */
public final class FrameCodec {

    private FrameCodec() {}

    /**
     * Encodes an AMQP frame into a ByteBuffer.
     *
     * @param frame        the frame to encode
     * @param maxFrameSize the maximum frame size in bytes
     * @return a ByteBuffer containing the encoded frame, ready for transmission
     * @throws AmqpException if the frame exceeds the maximum frame size
     */
    public static ByteBuffer encode(AmqpFrame frame, int maxFrameSize) {
        ByteBuffer performativeBuf = null;
        if (frame.performative() != null) {
            performativeBuf = TypeCodec.encode(frame.performative());
        }
        int performativeSize = performativeBuf != null ? performativeBuf.remaining() : 0;
        int payloadSize = frame.payload() != null ? frame.payload().remaining() : 0;
        int totalSize = AmqpConstants.FRAME_HEADER_SIZE + performativeSize + payloadSize;

        if (totalSize > maxFrameSize) {
            throw new AmqpException(AmqpError.FRAME_SIZE_TOO_SMALL,
                    "Frame size " + totalSize + " exceeds max " + maxFrameSize);
        }

        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.putInt(totalSize);
        buf.put((byte) 2); // DOFF = 2 (header is 8 bytes = 2 * 4-byte words)
        buf.put(frame.type());
        buf.putShort((short) frame.channel());
        if (performativeBuf != null) {
            buf.put(performativeBuf);
        }
        if (frame.payload() != null) {
            buf.put(frame.payload().duplicate());
        }
        buf.flip();
        return buf;
    }

    /**
     * Decodes an AMQP frame from a ByteBuffer.
     *
     * <p>The buffer must be positioned at the start of a complete frame.
     * On return, the buffer position will be advanced past the frame.
     *
     * @param buf the source buffer
     * @return the decoded frame
     * @throws AmqpException if the frame is malformed
     */
    public static AmqpFrame decode(ByteBuffer buf) {
        if (buf.remaining() < AmqpConstants.FRAME_HEADER_SIZE) {
            throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Insufficient data for frame header: " + buf.remaining() + " bytes");
        }

        int size = buf.getInt();
        if (size < AmqpConstants.FRAME_HEADER_SIZE) {
            throw new AmqpException(AmqpError.FRAMING_ERROR,
                    "Frame size " + size + " is less than minimum header size");
        }

        int doff = buf.get() & 0xFF;
        byte type = buf.get();
        int channel = buf.getShort() & 0xFFFF;

        // Skip extended header (if DOFF > 2)
        int extendedHeaderSize = (doff * 4) - AmqpConstants.FRAME_HEADER_SIZE;
        if (extendedHeaderSize > 0) {
            buf.position(buf.position() + extendedHeaderSize);
        }

        int bodySize = size - (doff * 4);
        if (bodySize == 0) {
            // Empty frame (heartbeat)
            return AmqpFrame.heartbeat();
        }

        // Decode the performative
        int bodyStart = buf.position();
        AmqpType performative = TypeCodec.decode(buf);
        int performativeEnd = buf.position();
        int performativeSize = performativeEnd - bodyStart;

        // Remaining bytes in the frame are the payload
        int payloadSize = bodySize - performativeSize;
        ByteBuffer payload = null;
        if (payloadSize > 0) {
            byte[] payloadData = new byte[payloadSize];
            buf.get(payloadData);
            payload = ByteBuffer.wrap(payloadData);
        }

        return new AmqpFrame(channel, type, performative, payload);
    }

    /**
     * Checks whether the given buffer contains a complete frame.
     *
     * @param buf the buffer to check (does not modify position)
     * @return true if a complete frame is available
     */
    public static boolean hasCompleteFrame(ByteBuffer buf) {
        if (buf.remaining() < AmqpConstants.FRAME_HEADER_SIZE) {
            return false;
        }
        int size = buf.getInt(buf.position());
        return buf.remaining() >= size;
    }

    /**
     * Reads the frame size from the buffer without advancing the position.
     *
     * @param buf the buffer (position not modified)
     * @return the frame size in bytes
     */
    public static int peekFrameSize(ByteBuffer buf) {
        return buf.getInt(buf.position());
    }
}
