package ssg.legoflow.service.passthrough;

import java.nio.ByteBuffer;

/**
 * Utility methods for working with byte buffers and byte arrays.
 *
 * @since 0.1.0
 */
public final class BufferUtils {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private BufferUtils() {
        // utility class
    }

    /**
     * Produces a hex dump string of the remaining bytes in the given buffer.
     * <p>
     * The buffer's position and limit are not modified. Each byte is represented
     * as two lowercase hex characters separated by spaces.
     *
     * @param buffer the buffer to dump, must not be null
     * @return hex dump string, e.g. "48 65 6c 6c 6f"
     * @throws IllegalArgumentException if buffer is null
     */
    public static String dumpHex(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        int remaining = buffer.remaining();
        if (remaining == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(remaining * 3 - 1);
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            if (i > buffer.position()) {
                sb.append(' ');
            }
            byte b = buffer.get(i);
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * Searches for the first occurrence of a byte pattern within a byte array,
     * starting from the given offset.
     *
     * @param data    the byte array to search in
     * @param pattern the pattern to search for
     * @param offset  the starting offset in the data array
     * @return the index of the first occurrence, or {@code -1} if not found
     * @throws IllegalArgumentException if data or pattern is null, or offset is negative
     */
    public static int indexOf(byte[] data, byte[] pattern, int offset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        if (pattern.length == 0) {
            return offset <= data.length ? offset : -1;
        }
        if (offset + pattern.length > data.length) {
            return -1;
        }

        outer:
        for (int i = offset; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
