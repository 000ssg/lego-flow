package ssg.legoflow.ssh.mac;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.assertj.core.api.Assertions.*;

class MacTest {

    @Test
    void testHmacSha256Properties() {
        HmacSha256 mac = new HmacSha256();
        assertThat(mac.name()).isEqualTo("hmac-sha2-256");
        assertThat(mac.macLength()).isEqualTo(32);
        assertThat(mac.keyLength()).isEqualTo(32);
        assertThat(mac.isEncryptThenMac()).isFalse();
    }

    @Test
    void testHmacSha256Compute() {
        HmacSha256 mac = new HmacSha256();
        mac.init(new byte[32]);
        byte[] result = mac.compute(0, "test data".getBytes());
        assertThat(result).hasSize(32);
    }

    @Test
    void testHmacSha256Verify() {
        HmacSha256 mac = new HmacSha256();
        mac.init(new byte[32]);
        byte[] data = "test data".getBytes();
        byte[] computed = mac.compute(0, data);
        assertThat(mac.verify(0, data, computed)).isTrue();
    }

    @Test
    void testHmacSha256VerifyFail() {
        HmacSha256 mac = new HmacSha256();
        mac.init(new byte[32]);
        byte[] data = "test data".getBytes();
        byte[] computed = mac.compute(0, data);
        computed[0] ^= 0xFF;
        assertThat(mac.verify(0, data, computed)).isFalse();
    }

    @Test
    void testHmacSha256DifferentSequenceNumbers() {
        HmacSha256 mac = new HmacSha256();
        mac.init(new byte[32]);
        byte[] data = "test".getBytes();
        byte[] mac0 = mac.compute(0, data);
        byte[] mac1 = mac.compute(1, data);
        assertThat(mac0).isNotEqualTo(mac1);
    }

    @Test
    void testHmacSha512Properties() {
        HmacSha512 mac = new HmacSha512();
        assertThat(mac.name()).isEqualTo("hmac-sha2-512");
        assertThat(mac.macLength()).isEqualTo(64);
        assertThat(mac.keyLength()).isEqualTo(64);
    }

    @Test
    void testHmacSha512Compute() {
        HmacSha512 mac = new HmacSha512();
        mac.init(new byte[64]);
        byte[] result = mac.compute(0, "test data".getBytes());
        assertThat(result).hasSize(64);
    }

    @Test
    void testHmacSha256EtmProperties() {
        HmacSha256Etm mac = new HmacSha256Etm();
        assertThat(mac.name()).isEqualTo("hmac-sha2-256-etm@openssh.com");
        assertThat(mac.isEncryptThenMac()).isTrue();
    }

    @Test
    void testHmacSha512EtmProperties() {
        HmacSha512Etm mac = new HmacSha512Etm();
        assertThat(mac.name()).isEqualTo("hmac-sha2-512-etm@openssh.com");
        assertThat(mac.isEncryptThenMac()).isTrue();
        assertThat(mac.macLength()).isEqualTo(64);
    }

    @Test
    void testHmacSha256EtmComputeAndVerify() {
        HmacSha256Etm mac = new HmacSha256Etm();
        mac.init(new byte[32]);
        byte[] data = "encrypted data".getBytes();
        byte[] computed = mac.compute(42, data);
        assertThat(mac.verify(42, data, computed)).isTrue();
        assertThat(mac.verify(43, data, computed)).isFalse();
    }

    @Test
    void testMacFactoryCreate() {
        assertThat(MacFactory.create("hmac-sha2-256")).isInstanceOf(HmacSha256.class);
        assertThat(MacFactory.create("hmac-sha2-512")).isInstanceOf(HmacSha512.class);
        assertThat(MacFactory.create("hmac-sha2-256-etm@openssh.com")).isInstanceOf(HmacSha256Etm.class);
        assertThat(MacFactory.create("hmac-sha2-512-etm@openssh.com")).isInstanceOf(HmacSha512Etm.class);
    }

    @Test
    void testMacFactoryUnsupported() {
        assertThatThrownBy(() -> MacFactory.create("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMacFactoryIsSupported() {
        assertThat(MacFactory.isSupported("hmac-sha2-256")).isTrue();
        assertThat(MacFactory.isSupported("unknown")).isFalse();
    }

    @Test
    void testMacConsistency() {
        HmacSha256 mac = new HmacSha256();
        mac.init(new byte[32]);
        byte[] data = "same data".getBytes();
        byte[] mac1 = mac.compute(5, data);
        byte[] mac2 = mac.compute(5, data);
        assertThat(mac1).isEqualTo(mac2);
    }
}
