package ssg.legoflow.ssh.mac;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MacFactoryTest {

    @Test void testCreateHmacSha256() {
        var mac = MacFactory.create("hmac-sha2-256");
        assertThat(mac).isInstanceOf(HmacSha256.class);
        assertThat(mac.name()).isEqualTo("hmac-sha2-256");
        assertThat(mac.macLength()).isEqualTo(32);
    }

    @Test void testCreateHmacSha512() {
        var mac = MacFactory.create("hmac-sha2-512");
        assertThat(mac).isInstanceOf(HmacSha512.class);
        assertThat(mac.macLength()).isEqualTo(64);
    }

    @Test void testCreateEtmMac() {
        var mac = MacFactory.create("hmac-sha2-256-etm@openssh.com");
        assertThat(mac).isInstanceOf(HmacSha256Etm.class);
        assertThat(mac.isEncryptThenMac()).isTrue();
    }

    @Test void testNonEtmMac() {
        var mac = MacFactory.create("hmac-sha2-256");
        assertThat(mac.isEncryptThenMac()).isFalse();
    }

    @Test void testIsSupported() {
        assertThat(MacFactory.isSupported("hmac-sha2-256")).isTrue();
        assertThat(MacFactory.isSupported("unknown-mac")).isFalse();
    }

    @Test void testSupportedAlgorithms() {
        var algos = MacFactory.supportedAlgorithms();
        assertThat(algos).hasSize(4);
    }

    @Test void testMacComputeAndVerify() throws Exception {
        var mac = MacFactory.create("hmac-sha2-256");
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;

        mac.init(key);
        byte[] data = "test data".getBytes();
        long seqNo = 1;

        byte[] computed = mac.compute(seqNo, data);
        assertThat(computed).hasSize(32);

        assertThat(mac.verify(seqNo, data, computed)).isTrue();

        // Tampered MAC should fail
        byte[] badMac = computed.clone();
        badMac[0] ^= 0xFF;
        assertThat(mac.verify(seqNo, data, badMac)).isFalse();
    }
}
