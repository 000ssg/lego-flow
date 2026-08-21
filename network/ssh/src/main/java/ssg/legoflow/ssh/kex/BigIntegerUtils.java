package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Utility methods for SSH BigInteger encoding/decoding per RFC 4251 §5.
 *
 * @since 0.1.0
 */
public final class BigIntegerUtils {

    private BigIntegerUtils() {}

    /**
     * Converts a BigInteger to SSH mpint wire format.
     * SSH mpint = [4-byte length][big-endian value with leading zero if high bit set].
     */
    public static byte[] toMpint(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // toByteArray returns sign-magnitude: leading 0x00 if high bit set
        int offset = 0;
        int length = bytes.length;
        if (bytes.length > 0 && bytes[0] == 0) {
            // Leading zero will be re-added by writeMpint if needed
            offset = 1;
            length = bytes.length - 1;
        }
        ByteBuffer buf = ByteBuffer.allocate(4 + length);
        buf.putInt(length);
        buf.put(bytes, offset, length);
        return buf.array();
    }

    /**
     * Converts SSH mpint wire format back to BigInteger.
     */
    public static BigInteger fromMpint(byte[] mpint) {
        ByteBuffer buf = ByteBuffer.wrap(mpint);
        int len = buf.getInt();
        byte[] data = new byte[len];
        buf.get(data);
        return new BigInteger(data);
    }

    /**
     * Writes an SSH mpint into the given ByteBuffer.
     * @return ByteBuffer with mpint written, position advanced past the mpint.
     */
    public static ByteBuffer writeMpint(ByteBuffer buf, BigInteger value) {
        byte[] bytes = value.toByteArray();
        // bytes[0] is leading zero if positive and high bit set
        int offset = 0;
        int length = bytes.length;
        boolean hadLeadingZero = bytes.length > 0 && bytes[0] == 0;
        if (hadLeadingZero) {
            offset = 1;
            length = bytes.length - 1;
        }
        // If the original value had a leading zero (high bit set), we need it back
        if (hadLeadingZero) {
            length++; // include the leading zero
        }
        buf.putInt(length);
        buf.put(bytes, offset, length);
        return buf;
    }
}
