package ssg.legoflow.ssh.mac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HmacSha512Etm}.
 */
class HmacSha512EtmTest {

    @Test
    void name() {
        HmacSha512Etm mac = new HmacSha512Etm();
        assertEquals("hmac-sha2-512-etm@openssh.com", mac.name());
    }

    @Test
    void macLength() {
        HmacSha512Etm mac = new HmacSha512Etm();
        assertEquals(64, mac.macLength());
    }

    @Test
    void keyLength() {
        HmacSha512Etm mac = new HmacSha512Etm();
        assertEquals(64, mac.keyLength());
    }

    @Test
    void isEncryptThenMac() {
        HmacSha512Etm mac = new HmacSha512Etm();
        assertTrue(mac.isEncryptThenMac());
    }

    @Test
    void computeAndVerify() {
        HmacSha512Etm mac = new HmacSha512Etm();
        byte[] key = new byte[64];
        for (int i = 0; i < 64; i++) {
            key[i] = (byte) i;
        }

        mac.init(key);

        byte[] data = "test data".getBytes();
        byte[] mac1 = mac.compute(0, data);
        byte[] mac2 = mac.compute(0, data);

        assertArrayEquals(mac1, mac2);
        assertEquals(64, mac1.length);
    }

    @Test
    void differentSequenceNumbersProduceDifferentMac() {
        HmacSha512Etm mac = new HmacSha512Etm();
        byte[] key = new byte[64];
        for (int i = 0; i < 64; i++) {
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
        HmacSha512Etm mac = new HmacSha512Etm();
        byte[] key = new byte[64];
        for (int i = 0; i < 64; i++) {
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
        HmacSha512Etm mac = new HmacSha512Etm();
        byte[] shortKey = new byte[]{1, 2, 3};

        mac.init(shortKey);

        byte[] data = "test".getBytes();
        byte[] macResult = mac.compute(0, data);
        assertNotNull(macResult);
        assertEquals(64, macResult.length);
    }
}
