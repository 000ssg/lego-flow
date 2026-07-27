package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.assertj.core.api.Assertions.*;

class CipherTest {

    private final SecureRandom random = new SecureRandom();

    private byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        random.nextBytes(b);
        return b;
    }

    @Test
    void testAes128CtrProperties() {
        Aes128Ctr c = new Aes128Ctr();
        assertThat(c.name()).isEqualTo("aes128-ctr");
        assertThat(c.blockSize()).isEqualTo(16);
        assertThat(c.keySize()).isEqualTo(16);
        assertThat(c.ivSize()).isEqualTo(16);
        assertThat(c.isAead()).isFalse();
        assertThat(c.authTagLength()).isEqualTo(0);
    }

    @Test
    void testAes128CtrEncryptDecrypt() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16);
        byte[] data = "Hello AES-128-CTR".getBytes();

        Aes128Ctr enc = new Aes128Ctr();
        enc.init(key, iv, true);
        byte[] encrypted = enc.encrypt(data);

        Aes128Ctr dec = new Aes128Ctr();
        dec.init(key, iv, false);
        byte[] decrypted = dec.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(data);
    }

    @Test
    void testAes192CtrEncryptDecrypt() {
        byte[] key = randomBytes(24);
        byte[] iv = randomBytes(16);
        byte[] data = "Hello AES-192-CTR".getBytes();

        Aes192Ctr enc = new Aes192Ctr();
        enc.init(key, iv, true);
        byte[] encrypted = enc.encrypt(data);

        Aes192Ctr dec = new Aes192Ctr();
        dec.init(key, iv, false);
        assertThat(dec.decrypt(encrypted)).isEqualTo(data);
    }

    @Test
    void testAes256CtrEncryptDecrypt() {
        byte[] key = randomBytes(32);
        byte[] iv = randomBytes(16);
        byte[] data = "Hello AES-256-CTR".getBytes();

        Aes256Ctr enc = new Aes256Ctr();
        enc.init(key, iv, true);
        byte[] encrypted = enc.encrypt(data);

        Aes256Ctr dec = new Aes256Ctr();
        dec.init(key, iv, false);
        assertThat(dec.decrypt(encrypted)).isEqualTo(data);
    }

    @Test
    void testAes256CtrProperties() {
        Aes256Ctr c = new Aes256Ctr();
        assertThat(c.keySize()).isEqualTo(32);
    }

    @Test
    void testAes128GcmProperties() {
        Aes128Gcm c = new Aes128Gcm();
        assertThat(c.name()).isEqualTo("aes128-gcm@openssh.com");
        assertThat(c.isAead()).isTrue();
        assertThat(c.authTagLength()).isEqualTo(16);
        assertThat(c.ivSize()).isEqualTo(12);
    }

    @Test
    void testAes256GcmProperties() {
        Aes256Gcm c = new Aes256Gcm();
        assertThat(c.name()).isEqualTo("aes256-gcm@openssh.com");
        assertThat(c.keySize()).isEqualTo(32);
        assertThat(c.isAead()).isTrue();
    }

    @Test
    void testChaCha20Poly1305Properties() {
        ChaCha20Poly1305 c = new ChaCha20Poly1305();
        assertThat(c.name()).isEqualTo("chacha20-poly1305@openssh.com");
        assertThat(c.keySize()).isEqualTo(64);
        assertThat(c.isAead()).isTrue();
        assertThat(c.authTagLength()).isEqualTo(16);
    }

    @Test
    void testChaCha20Poly1305EncryptDecrypt() {
        byte[] key = randomBytes(64);
        ChaCha20Poly1305 enc = new ChaCha20Poly1305();
        enc.init(key, new byte[0], true);
        // 4 bytes packet length + payload
        byte[] data = new byte[]{0, 0, 0, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] encrypted = enc.encrypt(data);
        assertThat(encrypted).isNotEqualTo(data);

        ChaCha20Poly1305 dec = new ChaCha20Poly1305();
        dec.init(key, new byte[0], false);
        byte[] decrypted = dec.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test
    void testCipherFactoryCreate() {
        assertThat(CipherFactory.create("aes128-ctr")).isInstanceOf(Aes128Ctr.class);
        assertThat(CipherFactory.create("aes192-ctr")).isInstanceOf(Aes192Ctr.class);
        assertThat(CipherFactory.create("aes256-ctr")).isInstanceOf(Aes256Ctr.class);
        assertThat(CipherFactory.create("aes128-gcm@openssh.com")).isInstanceOf(Aes128Gcm.class);
        assertThat(CipherFactory.create("aes256-gcm@openssh.com")).isInstanceOf(Aes256Gcm.class);
    }

    @Test
    void testCipherFactoryUnsupported() {
        assertThatThrownBy(() -> CipherFactory.create("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCipherFactoryIsSupported() {
        assertThat(CipherFactory.isSupported("aes256-ctr")).isTrue();
        assertThat(CipherFactory.isSupported("unknown")).isFalse();
    }

    @Test
    void testCipherFactorySupportedAlgorithms() {
        assertThat(CipherFactory.supportedAlgorithms())
                .contains("aes128-ctr", "aes256-ctr", "aes128-gcm@openssh.com");
    }

    @Test
    void testCtrStreamCipherMultipleBlocks() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16);
        byte[] data = randomBytes(100);

        Aes128Ctr enc = new Aes128Ctr();
        enc.init(key, iv, true);
        byte[] encrypted = enc.encrypt(data);
        assertThat(encrypted).hasSize(100);
        assertThat(encrypted).isNotEqualTo(data);
    }
}
