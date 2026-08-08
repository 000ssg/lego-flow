package ssg.legoflow.network.common.ber;

import java.nio.ByteBuffer;

/**
 * BER length encoding and decoding.
 *
 * <p>Supports three forms:
 * <ul>
 *   <li><b>Short form</b>: single byte, value 0-127</li>
 *   <li><b>Long form</b>: first byte = 0x80 | N, followed by N bytes of length</li>
 *   <li><b>Indefinite form</b>: single byte 0x80, content terminated by 0x00 0x00</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class BerLength {

    /** Sentinel value indicating indefinite length encoding. */
    public static final int INDEFINITE = -1;

    private BerLength() {}

    /**
     * Encodes a definite length value into the buffer.
     *
     * @param length the content length (must be non-negative)
     * @param buffer the output buffer
     */
    public static void encode(int length, ByteBuffer buffer) {
        if (length < 0) {
            throw new IllegalArgumentException("Length must be non-negative: " + length);
        }
        if (length <= 127) {
            buffer.put((byte) length);
        } else {
            int numBytes = bytesNeeded(length);
            buffer.put((byte) (0x80 | numBytes));
            for (int i = numBytes - 1; i >= 0; i--) {
                buffer.put((byte) ((length >> (8 * i)) & 0xFF));
            }
        }
    }

    /**
     * Encodes an indefinite length marker (0x80) into the buffer.
     *
     * @param buffer the output buffer
     */
    public static void encodeIndefinite(ByteBuffer buffer) {
        buffer.put((byte) 0x80);
    }

    /**
     * Encodes end-of-contents octets (0x00 0x00) for indefinite length.
     *
     * @param buffer the output buffer
     */
    public static void encodeEndOfContents(ByteBuffer buffer) {
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
    }

    /**
     * Decodes a length from the buffer.
     *
     * @param buffer the input buffer
     * @return the decoded length, or {@link #INDEFINITE} for indefinite form
     * @throws BerDecodingException if the buffer is exhausted
     */
    public static int decode(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new BerDecodingException("Buffer exhausted while reading length");
        }
        int first = buffer.get() & 0xFF;

        if (first <= 127) {
            // Short form
            return first;
        }

        if (first == 0x80) {
            // Indefinite form
            return INDEFINITE;
        }

        // Long form
        int numBytes = first & 0x7F;
        if (numBytes > 4) {
            throw new BerDecodingException("Length encoding too large: " + numBytes + " bytes");
        }
        int length = 0;
        for (int i = 0; i < numBytes; i++) {
            if (!buffer.hasRemaining()) {
                throw new BerDecodingException("Buffer exhausted while reading long-form length");
            }
            length = (length << 8) | (buffer.get() & 0xFF);
        }
        return length;
    }

    /**
     * Returns the number of bytes needed to encode the given length.
     *
     * @param length the content length
     * @return the encoded length field size in bytes
     */
    public static int encodedLength(int length) {
        if (length <= 127) {
            return 1;
        }
        return 1 + bytesNeeded(length);
    }

    private static int bytesNeeded(int length) {
        if (length <= 0xFF) return 1;
        if (length <= 0xFFFF) return 2;
        if (length <= 0xFFFFFF) return 3;
        return 4;
    }
}
