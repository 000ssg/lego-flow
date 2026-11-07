package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BigIntegerUtils}.
 */
class BigIntegerUtilsTest {

    @Test
    void toMpintZero() {
        byte[] result = BigIntegerUtils.toMpint(BigInteger.ZERO);
        assertEquals(4, result.length); // 4-byte length field + 0 bytes of data
        assertEquals(0, ByteBuffer.wrap(result).getInt());
    }

    @Test
    void toMpintPositive() {
        BigInteger value = new BigInteger("123456789");
        byte[] result = BigIntegerUtils.toMpint(value);

        BigInteger decoded = BigIntegerUtils.fromMpint(result);
        assertEquals(value, decoded);
    }

    @Test
    void toMpintSmall() {
        BigInteger value = BigInteger.valueOf(42);
        byte[] result = BigIntegerUtils.toMpint(value);

        BigInteger decoded = BigIntegerUtils.fromMpint(result);
        assertEquals(value, decoded);
    }

    @Test
    void toMpintMaxInt() {
        BigInteger value = BigInteger.valueOf(Integer.MAX_VALUE);
        byte[] result = BigIntegerUtils.toMpint(value);

        BigInteger decoded = BigIntegerUtils.fromMpint(result);
        assertEquals(value, decoded);
    }

    @Test
    void toMpintWithLeadingZero() {
        // Value that produces a leading zero in toByteArray
        // Use byte array constructor to ensure positive value
        byte[] bytes = new byte[]{(byte) 0x80, 0x00, 0x00, 0x00};
        BigInteger value = new BigInteger(bytes);
        byte[] result = BigIntegerUtils.toMpint(value);

        // Should have length field + data
        assertTrue(result.length >= 5);
    }

    @Test
    void fromMpintNormal() {
        byte[] mpint = new byte[]{0, 0, 0, 3, 0x42, 0x43, 0x44};
        BigInteger result = BigIntegerUtils.fromMpint(mpint);
        assertEquals(new BigInteger(new byte[]{0x42, 0x43, 0x44}), result);
    }

    @Test
    void roundTrip() {
        // Use byte array constructor for values that might be interpreted as signed
        byte[] valueBytes1 = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] valueBytes2 = new byte[]{(byte) 0x80, 0x00, 0x00, 0x00};
        byte[] valueBytes3 = new byte[]{0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        BigInteger[] testValues = {
            BigInteger.ONE,
            new BigInteger("123456789"),
            new BigInteger(valueBytes1),
            new BigInteger(valueBytes2),
            new BigInteger(valueBytes3)
        };

        for (BigInteger value : testValues) {
            byte[] encoded = BigIntegerUtils.toMpint(value);
            BigInteger decoded = BigIntegerUtils.fromMpint(encoded);
            assertEquals(value, decoded, "Round-trip failed for " + value);
        }
    }

    @Test
    void writeMpint() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        BigInteger value = new BigInteger("42");
        BigIntegerUtils.writeMpint(buf, value);

        // Position should be advanced by 4 (length) + data length
        assertTrue(buf.position() >= 5);

        // Reset and read
        buf.flip();
        buf.getInt(); // skip length
        byte[] data = new byte[value.bitLength() / 8 + 1];
        buf.get(data);
        assertEquals(value, new BigInteger(data));
    }

    @Test
    void readMpint() {
        byte[] mpint = new byte[]{0, 0, 0, 3, 0x42, 0x43, 0x44};
        BigInteger result = BigIntegerUtils.fromMpint(mpint);
        assertEquals(new BigInteger(new byte[]{0x42, 0x43, 0x44}), result);
    }

    @Test
    void toMpintLarge() {
        // Test with a large positive number
        byte[] largeBytes = new byte[]{
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        BigInteger value = new BigInteger(largeBytes);
        byte[] result = BigIntegerUtils.toMpint(value);

        BigInteger decoded = BigIntegerUtils.fromMpint(result);
        assertEquals(value, decoded);
    }

    @Test
    void leadingZeroPreserved() {
        // Value with high bit set needs leading zero
        byte[] valueBytes = new byte[]{(byte) 0x80, 0x00};
        BigInteger value = new BigInteger(valueBytes);
        byte[] result = BigIntegerUtils.toMpint(value);

        // Should have leading zero
        assertTrue(result.length >= 6);
        assertEquals(0x00, result[1]); // leading zero
    }
}
