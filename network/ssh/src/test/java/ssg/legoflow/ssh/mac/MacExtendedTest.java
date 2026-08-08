package ssg.legoflow.ssh.mac;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MacExtendedTest {

    @Test void hmacSha256Construction() throws Exception {
        var mac = new HmacSha256();
        assertThat(mac).isNotNull();
        assertThat(mac.macLength()).isGreaterThan(0);
        assertThat(mac.keyLength()).isGreaterThan(0);
    }

    @Test void hmacSha256InitAndCompute() throws Exception {
        var mac = new HmacSha256();
        byte[] key = "test-key-1234567890ab".getBytes();
        mac.init(key);
        byte[] data = "hello world".getBytes();
        byte[] result = mac.compute(0, data);
        assertThat(result).hasSize(mac.macLength());
    }

    @Test void hmacSha512InitAndCompute() throws Exception {
        var mac = new HmacSha512();
        byte[] key = "test-key-for-sha512-1234567890ab".getBytes();
        mac.init(key);
        byte[] data = "hello world".getBytes();
        byte[] result = mac.compute(0, data);
        assertThat(result).hasSize(mac.macLength());
    }

    @Test void hmacSha256EtmConstruction() throws Exception {
        var mac = new HmacSha256Etm();
        assertThat(mac.isEncryptThenMac()).isTrue();
    }

    @Test void hmacSha512EtmConstruction() throws Exception {
        var mac = new HmacSha512Etm();
        assertThat(mac.isEncryptThenMac()).isTrue();
    }

    @Test void nonEtmNotEncryptThenMac() throws Exception {
        var mac = new HmacSha256();
        assertThat(mac.isEncryptThenMac()).isFalse();
    }

    @Test void etmMacCompute() throws Exception {
        var mac = new HmacSha256Etm();
        byte[] key = "etm-test-key-1234567890".getBytes();
        mac.init(key);
        byte[] data = "encrypted payload here".getBytes();
        byte[] result = mac.compute(12345, data);
        assertThat(result).hasSize(mac.macLength());
    }

    @Test void sameKeySameDataProduceSameMac() throws Exception {
        var mac = new HmacSha256();
        byte[] key = "key-for-mac-test-123".getBytes();
        mac.init(key);
        byte[] data = "consistent".getBytes();
        assertThat(mac.compute(0, data)).isEqualTo(mac.compute(0, data));
    }

    @Test void differentSequenceNumberDifferentMac() throws Exception {
        var mac = new HmacSha256();
        mac.init("seq-test-key-12345".getBytes());
        byte[] data = "same data".getBytes();
        assertThat(mac.compute(0, data)).isNotEqualTo(mac.compute(1, data));
    }

    @Test void hmacSha256ReInitWithNewKey() throws Exception {
        var mac = new HmacSha256();
        mac.init("first-key-1234567890ab".getBytes());
        byte[] data = "test".getBytes();
        byte[] r1 = mac.compute(0, data);
        mac.init("second-key-abcdefghij".getBytes());
        byte[] r2 = mac.compute(0, data);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test void factoryCreateKnown() {
        var mac = MacFactory.create("hmac-sha2-256");
        assertThat(mac).isNotNull();
    }

    @Test void factorySupportedAlgorithms() {
        var supported = MacFactory.supportedAlgorithms();
        assertThat(supported).contains("hmac-sha2-256", "hmac-sha2-512");
    }

    @Test void factoryIsSupported() {
        assertThat(MacFactory.isSupported("hmac-sha2-256")).isTrue();
        assertThat(MacFactory.isSupported("unsupported-mac-algo")).isFalse();
    }

    @Test void factoryCreateUnsupportedThrows() {
        assertThatThrownBy(() -> MacFactory.create("unsupported"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
