package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CipherFactoryTest {

    @Test void testCreateAes128Ctr() {
        var cipher = CipherFactory.create("aes128-ctr");
        assertThat(cipher).isInstanceOf(Aes128Ctr.class);
        assertThat(cipher.keySize()).isEqualTo(16);
        assertThat(cipher.blockSize()).isEqualTo(16);
        assertThat(cipher.name()).isEqualTo("aes128-ctr");
        assertThat(cipher.isAead()).isFalse();
    }

    @Test void testCreateAllCiphers() {
        var aes192 = CipherFactory.create("aes192-ctr");
        assertThat(aes192.keySize()).isEqualTo(24);

        var aes256 = CipherFactory.create("aes256-ctr");
        assertThat(aes256.keySize()).isEqualTo(32);

        var aes128gcm = CipherFactory.create("aes128-gcm@openssh.com");
        assertThat(aes128gcm.isAead()).isTrue();

        var aes256gcm = CipherFactory.create("aes256-gcm@openssh.com");
        assertThat(aes256gcm.isAead()).isTrue();

        var chacha = CipherFactory.create("chacha20-poly1305@openssh.com");
        assertThat(chacha.isAead()).isTrue();
    }

    @Test void testCreateUnsupportedThrows() {
        assertThatThrownBy(() -> CipherFactory.create("unknown-algo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testIsSupported() {
        assertThat(CipherFactory.isSupported("aes128-ctr")).isTrue();
        assertThat(CipherFactory.isSupported("unknown")).isFalse();
    }

    @Test void testSupportedAlgorithms() {
        var algos = CipherFactory.supportedAlgorithms();
        assertThat(algos).hasSize(6);
        assertThat(algos).contains("aes128-ctr", "aes256-ctr");
    }

    @Test void testAes128CtrRoundTrip() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        for (int i = 0; i < 16; i++) key[i] = (byte) i;

        cipher.init(key, iv, true);
        byte[] plain = "Hello SSH encryption!".getBytes();
        byte[] encrypted = cipher.encrypt(plain.clone());

        cipher.init(key, iv, false);
        byte[] decrypted = cipher.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test void testAes256CtrRoundTrip() {
        var cipher = CipherFactory.create("aes256-ctr");
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;

        cipher.init(key, iv, true);
        byte[] plain = "AES-256 CTR test data".getBytes();
        byte[] encrypted = cipher.encrypt(plain.clone());

        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }
}
