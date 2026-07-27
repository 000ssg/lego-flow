package ssg.legoflow.media.rtsp.interleaved;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Codec for interleaved binary frames in the RTSP TCP stream.
 *
 * <p>Encodes and decodes frames with the format: {@code $ <channel:1> <length:2> <data:length>}
 *
 * @since 1.0.0
 */
public final class InterleavedFrameCodec {

    private InterleavedFrameCodec() {}

    /**
     * Encodes an interleaved frame to bytes.
     *
     * @param frame the frame to encode
     * @return the encoded bytes including the 4-byte header
     */
    public static byte[] encode(InterleavedFrame frame) {
        byte[] data = frame.data();
        ByteBuffer buf = ByteBuffer.allocate(InterleavedFrame.HEADER_SIZE + data.length);
        buf.put(InterleavedFrame.MAGIC);
        buf.put((byte) frame.channel());
        buf.putShort((short) data.length);
        buf.put(data);
        return buf.array();
    }

    /**
     * Attempts to decode an interleaved frame from a byte buffer.
     *
     * <p>The buffer's position must be at the '$' magic byte. If the buffer
     * does not contain enough data for a complete frame, returns empty and
     * the buffer position is unchanged.
     *
     * @param buffer the source buffer
     * @return the decoded frame, or empty if insufficient data
     */
    public static Optional<InterleavedFrame> decode(ByteBuffer buffer) {
        if (buffer.remaining() < InterleavedFrame.HEADER_SIZE) {
            return Optional.empty();
        }

        buffer.mark();
        byte magic = buffer.get();
        if (magic != InterleavedFrame.MAGIC) {
            buffer.reset();
            throw new IllegalArgumentException(
                    "Expected '$' (0x24) but got 0x" + Integer.toHexString(magic & 0xFF));
        }

        int channel = buffer.get() & 0xFF;
        int length = buffer.getShort() & 0xFFFF;

        if (buffer.remaining() < length) {
            buffer.reset();
            return Optional.empty();
        }

        byte[] data = new byte[length];
        buffer.get(data);
        return Optional.of(new InterleavedFrame(channel, data));
    }

    /**
     * Decodes an interleaved frame from a byte array.
     *
     * @param bytes  the source array
     * @param offset the start offset
     * @return the decoded frame
     * @throws IllegalArgumentException if the data is invalid
     */
    public static InterleavedFrame decode(byte[] bytes, int offset) {
        if (bytes.length - offset < InterleavedFrame.HEADER_SIZE) {
            throw new IllegalArgumentException("Insufficient data for interleaved frame header");
        }
        if (bytes[offset] != InterleavedFrame.MAGIC) {
            throw new IllegalArgumentException(
                    "Expected '$' (0x24) but got 0x" + Integer.toHexString(bytes[offset] & 0xFF));
        }
        int channel = bytes[offset + 1] & 0xFF;
        int length = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
        if (bytes.length - offset - InterleavedFrame.HEADER_SIZE < length) {
            throw new IllegalArgumentException("Insufficient data for interleaved frame payload");
        }
        byte[] data = new byte[length];
        System.arraycopy(bytes, offset + InterleavedFrame.HEADER_SIZE, data, 0, length);
        return new InterleavedFrame(channel, data);
    }
}
