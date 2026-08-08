package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * MySQL length-encoded string encoding and decoding.
 *
 * <p>A length-encoded string is a length-encoded integer followed by that many bytes
 * of string data. NULL is represented by the NULL marker (0xFB) with no following data.
 *
 * @since 0.1.0
 */
public final class LengthEncodedString {

    private LengthEncodedString() {}

    /**
     * Reads a length-encoded string from the buffer.
     *
     * @param buf the byte buffer to read from
     * @return the decoded string, or null for NULL
     */
    public static String read(ByteBuffer buf) {
        long length = LengthEncodedInt.read(buf);
        if (length < 0) {
            return null;
        }
        var bytes = new byte[(int) length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a length-encoded byte array from the buffer.
     *
     * @param buf the byte buffer to read from
     * @return the decoded bytes, or null for NULL
     */
    public static byte[] readBytes(ByteBuffer buf) {
        long length = LengthEncodedInt.read(buf);
        if (length < 0) {
            return null;
        }
        var bytes = new byte[(int) length];
        buf.get(bytes);
        return bytes;
    }

    /**
     * Writes a length-encoded string to the buffer.
     *
     * @param buf the byte buffer to write to
     * @param value the string to encode, or null for NULL
     */
    public static void write(ByteBuffer buf, String value) {
        if (value == null) {
            buf.put((byte) LengthEncodedInt.NULL_MARKER);
            return;
        }
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        LengthEncodedInt.write(buf, bytes.length);
        buf.put(bytes);
    }

    /**
     * Writes length-encoded bytes to the buffer.
     *
     * @param buf the byte buffer to write to
     * @param value the bytes to encode, or null for NULL
     */
    public static void writeBytes(ByteBuffer buf, byte[] value) {
        if (value == null) {
            buf.put((byte) LengthEncodedInt.NULL_MARKER);
            return;
        }
        LengthEncodedInt.write(buf, value.length);
        buf.put(value);
    }

    /**
     * Reads a null-terminated string from the buffer.
     *
     * @param buf the byte buffer to read from
     * @return the decoded string (without null terminator)
     */
    public static String readNullTerminated(ByteBuffer buf) {
        int start = buf.position();
        while (buf.hasRemaining() && buf.get() != 0) {
            // advance
        }
        int length = buf.position() - start - 1;
        var bytes = new byte[length];
        buf.position(start);
        buf.get(bytes);
        buf.get(); // skip null terminator
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a null-terminated string to the buffer.
     *
     * @param buf the byte buffer to write to
     * @param value the string to encode
     */
    public static void writeNullTerminated(ByteBuffer buf, String value) {
        buf.put(value.getBytes(StandardCharsets.UTF_8));
        buf.put((byte) 0);
    }

    /**
     * Reads a fixed-length string from the buffer.
     *
     * @param buf the byte buffer to read from
     * @param length the number of bytes to read
     * @return the decoded string
     */
    public static String readFixedLength(ByteBuffer buf, int length) {
        var bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads the remaining bytes from the buffer as a string.
     *
     * @param buf the byte buffer to read from
     * @return the decoded string
     */
    public static String readRestOfPacket(ByteBuffer buf) {
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads the remaining bytes from the buffer.
     *
     * @param buf the byte buffer to read from
     * @return the remaining bytes
     */
    public static byte[] readRestOfPacketBytes(ByteBuffer buf) {
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
