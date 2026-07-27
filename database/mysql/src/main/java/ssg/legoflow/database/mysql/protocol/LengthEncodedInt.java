package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MySQL length-encoded integer encoding and decoding.
 *
 * <p>Length-encoded integers use a variable number of bytes:
 * <ul>
 *   <li>0-250: 1 byte</li>
 *   <li>251: NULL column value (0xFB)</li>
 *   <li>252 + 2-byte little-endian: values up to 2^16-1</li>
 *   <li>253 + 3-byte little-endian: values up to 2^24-1</li>
 *   <li>254 + 8-byte little-endian: values up to 2^64-1</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class LengthEncodedInt {

    /** Marker byte indicating a NULL value. */
    public static final int NULL_MARKER = 0xFB;

    /** Marker byte indicating a 2-byte integer follows. */
    public static final int TWO_BYTE_MARKER = 0xFC;

    /** Marker byte indicating a 3-byte integer follows. */
    public static final int THREE_BYTE_MARKER = 0xFD;

    /** Marker byte indicating an 8-byte integer follows. */
    public static final int EIGHT_BYTE_MARKER = 0xFE;

    private LengthEncodedInt() {}

    /**
     * Reads a length-encoded integer from the buffer at current position.
     *
     * @param buf the byte buffer to read from
     * @return the decoded long value, or -1 for NULL
     * @throws IllegalArgumentException if the buffer has insufficient data
     */
    public static long read(ByteBuffer buf) {
        int first = buf.get() & 0xFF;
        return switch (first) {
            case NULL_MARKER -> -1L;
            case TWO_BYTE_MARKER -> readLittleEndian(buf, 2);
            case THREE_BYTE_MARKER -> readLittleEndian(buf, 3);
            case EIGHT_BYTE_MARKER -> readLittleEndian(buf, 8);
            default -> first;
        };
    }

    /**
     * Writes a length-encoded integer to the buffer at current position.
     *
     * @param buf the byte buffer to write to
     * @param value the value to encode (must be >= 0)
     * @throws IllegalArgumentException if value is negative
     */
    public static void write(ByteBuffer buf, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Length-encoded integer cannot be negative: " + value);
        }
        if (value < 251) {
            buf.put((byte) value);
        } else if (value < (1L << 16)) {
            buf.put((byte) TWO_BYTE_MARKER);
            writeLittleEndian(buf, value, 2);
        } else if (value < (1L << 24)) {
            buf.put((byte) THREE_BYTE_MARKER);
            writeLittleEndian(buf, value, 3);
        } else {
            buf.put((byte) EIGHT_BYTE_MARKER);
            writeLittleEndian(buf, value, 8);
        }
    }

    /**
     * Returns the number of bytes required to encode the given value.
     *
     * @param value the value to measure
     * @return byte count (1, 3, 4, or 9)
     */
    public static int encodedLength(long value) {
        if (value < 251) return 1;
        if (value < (1L << 16)) return 3;
        if (value < (1L << 24)) return 4;
        return 9;
    }

    /**
     * Encodes a length-encoded integer into a new byte array.
     *
     * @param value the value to encode
     * @return the encoded bytes
     */
    public static byte[] encode(long value) {
        var buf = ByteBuffer.allocate(encodedLength(value));
        write(buf, value);
        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a length-encoded integer from a byte array.
     *
     * @param data the encoded bytes
     * @return the decoded value
     */
    public static long decode(byte[] data) {
        return read(ByteBuffer.wrap(data));
    }

    private static long readLittleEndian(ByteBuffer buf, int bytes) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            value |= (long) (buf.get() & 0xFF) << (i * 8);
        }
        return value;
    }

    private static void writeLittleEndian(ByteBuffer buf, long value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            buf.put((byte) ((value >> (i * 8)) & 0xFF));
        }
    }
}
