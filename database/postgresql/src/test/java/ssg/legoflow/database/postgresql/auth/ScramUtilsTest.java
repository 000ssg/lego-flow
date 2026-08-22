package ssg.legoflow.database.postgresql.auth;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ScramUtils}.
 */
class ScramUtilsTest {

    @Test
    void testHmacDeterministic() {
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] hmac1 = ScramUtils.hmac(key, data);
        byte[] hmac2 = ScramUtils.hmac(key, data);
        assertThat(hmac1).isEqualTo(hmac2);
    }

    @Test
    void testHmacLength() {
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] hmac = ScramUtils.hmac(key, data);
        assertThat(hmac).hasSize(32); // SHA-256 = 32 bytes
    }

    @Test
    void testHmacDifferentKeys() {
        byte[] data = "same data".getBytes(StandardCharsets.UTF_8);
        byte[] hmac1 = ScramUtils.hmac("key1".getBytes(StandardCharsets.UTF_8), data);
        byte[] hmac2 = ScramUtils.hmac("key2".getBytes(StandardCharsets.UTF_8), data);
        assertThat(hmac1).isNotEqualTo(hmac2);
    }

    @Test
    void testHashDeterministic() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] hash1 = ScramUtils.hash(data);
        byte[] hash2 = ScramUtils.hash(data);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testHashLength() {
        byte[] hash = ScramUtils.hash("test".getBytes(StandardCharsets.UTF_8));
        assertThat(hash).hasSize(32);
    }

    @Test
    void testHashDifferentData() {
        byte[] hash1 = ScramUtils.hash("data1".getBytes(StandardCharsets.UTF_8));
        byte[] hash2 = ScramUtils.hash("data2".getBytes(StandardCharsets.UTF_8));
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testHiDeterministic() {
        byte[] password = "password".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "salt1234".getBytes(StandardCharsets.UTF_8);
        byte[] result1 = ScramUtils.hi(password, salt, 4096);
        byte[] result2 = ScramUtils.hi(password, salt, 4096);
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void testHiLength() {
        byte[] password = "password".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
        byte[] result = ScramUtils.hi(password, salt, 100);
        assertThat(result).hasSize(32);
    }

    @Test
    void testHiDifferentIterations() {
        byte[] password = "password".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
        byte[] result1 = ScramUtils.hi(password, salt, 100);
        byte[] result2 = ScramUtils.hi(password, salt, 200);
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void testXor() {
        byte[] a = {0x0F, 0x0F, (byte) 0xFF};
        byte[] b = {0x0F, (byte) 0xF0, 0x00};
        byte[] result = ScramUtils.xor(a, b);
        assertThat(result).containsExactly(0x00, (byte) 0xFF, (byte) 0xFF);
    }

    @Test
    void testXorWithSelf() {
        byte[] a = {0x12, 0x34, 0x56};
        byte[] result = ScramUtils.xor(a, a);
        assertThat(result).containsExactly(0x00, 0x00, 0x00);
    }

    @Test
    void testGenerateNonce() {
        String nonce = ScramUtils.generateNonce();
        assertThat(nonce).isNotEmpty();
        assertThat(nonce.length()).isGreaterThan(10);
    }

    @Test
    void testGenerateNonceUnique() {
        String nonce1 = ScramUtils.generateNonce();
        String nonce2 = ScramUtils.generateNonce();
        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    @Test
    void testBase64RoundTrip() {
        byte[] original = {1, 2, 3, 4, 5, 6, 7, 8};
        String encoded = ScramUtils.base64Encode(original);
        byte[] decoded = ScramUtils.base64Decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBase64EncodeKnown() {
        // "n,," -> "biws"
        String encoded = ScramUtils.base64Encode("n,,".getBytes(StandardCharsets.UTF_8));
        assertThat(encoded).isEqualTo("biws");
    }

    @Test
    void testToBytes() {
        byte[] bytes = ScramUtils.toBytes("hello");
        assertThat(bytes).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }
}
