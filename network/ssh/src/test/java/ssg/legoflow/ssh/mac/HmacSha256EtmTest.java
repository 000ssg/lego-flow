package ssg.legoflow.ssh.mac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HmacSha256Etm}.
 */
class HmacSha256EtmTest {

    @Test
    void name() {
        HmacSha256Etm mac = new HmacSha256Etm();
        assertEquals("hmac-sha2-256-etm@openssh.com", mac.name());
    }

    @Test
    void macLength() {
        HmacSha256Etm mac = new HmacSha256Etm();
        assertEquals(32, mac.macLength());
    }

    @Test
    void keyLength() {
        HmacSha256Etm mac = new HmacSha256Etm();
        assertEquals(32, mac.keyLength());
    }

    @Test
    void isEncryptThenMac() {
        HmacSha256Etm mac = new HmacSha256Etm();
        assertTrue(mac.isEncryptThenMac());
    }

    @Test
    void computeAndVerify() {
        HmacSha256Etm mac = new HmacSha256Etm();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }

        mac.init(key);

        byte[] data = "test data".getBytes();
        byte[] mac1 = mac.compute(0, data);
        byte[] mac2 = mac.compute(0, data);

        assertArrayEquals(mac1, mac2);
        assertEquals(32, mac1.length);
    }

    @Test
    void differentSequenceNumbersProduceDifferentMac() {
        HmacSha256Etm mac = new HmacSha256Etm();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }

        mac.init(key);

        byte[] data = "test data".getBytes();
        byte[] mac1 = mac.compute(0, data);
        byte[] mac2 = mac.compute(1, data);

        assertNotEquals(0, java.util.Arrays.compare(mac1, mac2));
    }

    @Test
    void differentDataProducesDifferentMac() {
        HmacSha256Etm mac = new HmacSha256Etm();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }

        mac.init(key);

        byte[] data1 = "test data 1".getBytes();
        byte[] data2 = "test data 2".getBytes();
        byte[] mac1 = mac.compute(0, data1);
        byte[] mac2 = mac.compute(0, data2);

        assertNotEquals(0, java.util.Arrays.compare(mac1, mac2));
    }

    @Test
    void shortKeyIsPadded() {
        HmacSha256Etm mac = new HmacSha256Etm();
        byte[] shortKey = new byte[]{1, 2, 3};

        // Should not throw
        mac.init(shortKey);

        byte[] data = "test".getBytes();
        byte[] macResult = mac.compute(0, data);
        assertNotNull(macResult);
        assertEquals(32, macResult.length);
    }

    @Test
    void longKeyIsTruncated() {
        HmacSha256Etm mac = new HmacSha256Etm();
        byte[] longKey = new byte[64];
        for (int i = 0; i < 64; i++) {
            longKey[i] = (byte) i;
        }

        // Should not throw
        mac.init(longKey);

        byte[] data = "test".getBytes();
        byte[] macResult = mac.compute(0, data);
        assertNotNull(macResult);
        assertEquals(32, macResult.length);
    }
}
