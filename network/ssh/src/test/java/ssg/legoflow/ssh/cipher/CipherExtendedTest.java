package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CipherExtendedTest {

    @Test void factoryCreateAes128() throws Exception {
        var cipher = CipherFactory.create("aes128-ctr");
        assertThat(cipher).isNotNull();
    }

    @Test void aesCtrEncryptDecryptRoundTrip() throws Exception {
        var cipher = new Aes128Ctr();
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        cipher.init(key, iv, true);
        byte[] plaintext = "Hello World!!".getBytes();
        byte[] encrypted = cipher.encrypt(plaintext);
        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test void aesGcmEncryptDecryptRoundTrip() throws Exception {
        var cipher = new Aes128Gcm();
        byte[] key = new byte[16];
        byte[] nonce = new byte[12];
        cipher.init(key, nonce, true);
        byte[] plaintext = "Hello World!".getBytes();
        byte[] result = cipher.encrypt(plaintext);
        cipher.init(key, nonce, false);
        assertThat(cipher.decrypt(result)).isEqualTo(plaintext);
    }

    @Test void aes256CtrEncryptDecrypt() throws Exception {
        var cipher = new Aes256Ctr();
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        cipher.init(key, iv, true);
        byte[] plaintext = "Hello World!!".getBytes();
        byte[] encrypted = cipher.encrypt(plaintext);
        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test void aes256GcmEncryptDecrypt() throws Exception {
        var cipher = new Aes256Gcm();
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        cipher.init(key, nonce, true);
        byte[] plaintext = "Hello World!".getBytes();
        byte[] result = cipher.encrypt(plaintext);
        cipher.init(key, nonce, false);
        assertThat(cipher.decrypt(result)).isEqualTo(plaintext);
    }

    @Test void aes192CtrEncryptDecrypt() throws Exception {
        var cipher = new Aes192Ctr();
        byte[] key = new byte[24];
        byte[] iv = new byte[16];
        cipher.init(key, iv, true);
        byte[] plaintext = "Hello World!!".getBytes();
        byte[] encrypted = cipher.encrypt(plaintext);
        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test void chacha20Poly1305EncryptDecrypt() throws Exception {
        var cipher = new ChaCha20Poly1305();
        byte[] key = new byte[64];  // 32 encrypt + 32 MAC key
        byte[] nonce = new byte[12];
        cipher.init(key, nonce, true);
        byte[] plaintext = "Hello World!".getBytes();
        byte[] result = cipher.encrypt(plaintext);
        cipher.init(key, nonce, false);
        assertThat(cipher.decrypt(result)).isEqualTo(plaintext);
    }

    @Test void factoryCreateUnsupported() {
        assertThatThrownBy(() -> CipherFactory.create("unknown-algo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void factoryIsSupported() {
        assertThat(CipherFactory.isSupported("aes128-ctr")).isTrue();
        assertThat(CipherFactory.isSupported("unknown")).isFalse();
    }

    @Test void factorySupportedList() {
        var supported = CipherFactory.supportedAlgorithms();
        assertThat(supported).contains("aes128-ctr", "aes256-ctr");
    }

    @Test void aesCtrBlockSizeAndSizes() {
        var cipher = new Aes128Ctr();
        assertThat(cipher.keySize()).isEqualTo(16);
        assertThat(cipher.ivSize()).isEqualTo(16);
        assertThat(cipher.blockSize()).isEqualTo(16);
        assertThat(cipher.isAead()).isFalse();
    }

    @Test void aesGcmBlockSizeAndSizes() {
        var cipher = new Aes128Gcm();
        assertThat(cipher.keySize()).isEqualTo(16);
        assertThat(cipher.isAead()).isTrue();
        assertThat(cipher.authTagLength()).isEqualTo(16);
    }

    @Test void chacha20BlockSizeAndSizes() {
        var cipher = new ChaCha20Poly1305();
        assertThat(cipher.keySize()).isEqualTo(64);  // 32+32 for SSH
        assertThat(cipher.isAead()).isTrue();
    }
}
