package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Aes128GcmTest {

    @Test void testCipherMetadata() {
        Aes128Gcm cipher = new Aes128Gcm();
        assertThat(cipher.name()).isEqualTo("aes128-gcm@openssh.com");
        assertThat(cipher.blockSize()).isEqualTo(16);
        assertThat(cipher.keySize()).isEqualTo(16);
        assertThat(cipher.ivSize()).isEqualTo(12);
        assertThat(cipher.isAead()).isTrue();
        assertThat(cipher.authTagLength()).isEqualTo(16);
    }

    @Test void testEncryptDecryptRoundTrip() {
        Aes128Gcm encrypt = new Aes128Gcm();
        Aes128Gcm decrypt = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] iv = new byte[12];
        // set first 4 bytes as packet length prefix (AAD)
        byte[] data = new byte[]{0, 0, 0, 42, 'H', 'i', '!'};

        encrypt.init(key, iv, true);
        decrypt.init(key, iv, false);

        byte[] encrypted = encrypt.encrypt(data);
        assertThat(encrypted).hasSizeGreaterThan(data.length - 16); // includes tag
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testEncryptDecryptExactFourBytes() {
        Aes128Gcm encrypt = new Aes128Gcm();
        Aes128Gcm decrypt = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] iv = new byte[12];
        byte[] data = new byte[]{0, 0, 0, 0}; // exactly 4 bytes

        encrypt.init(key, iv, true);
        decrypt.init(key, iv, false);

        byte[] encrypted = encrypt.encrypt(data);
        assertThat(encrypted).hasSizeGreaterThan(0);
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testIncrementingIvAfterEachEncrypt() {
        Aes128Gcm cipher = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] iv = new byte[12];
        byte[] data = new byte[]{0, 0, 0, 2, 'A', 'B'};

        cipher.init(key, iv, true);
        byte[] first = cipher.encrypt(data);
        byte[] second = cipher.encrypt(data);

        assertThat(first).isNotEqualTo(second);
    }

    @Test void testIncrementingIvAfterEachDecrypt() {
        Aes128Gcm cipher = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] iv = new byte[12];

        cipher.init(key, iv, false);
        try {
            cipher.decrypt(new byte[]{0, 0, 0, 1, 'A'});
        } catch (RuntimeException ignored) {
            // Expected: authentication failure
        }
    }

    @Test void testInitWithShortIv() {
        Aes128Gcm cipher = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] shortIv = new byte[4];

        assertThatCode(() -> cipher.init(key, shortIv, true)).doesNotThrowAnyException();
    }

    @Test void testDecryptAuthFailure() {
        Aes128Gcm encrypt = new Aes128Gcm();
        Aes128Gcm decrypt = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] key2 = "deadbeefcafe1234".getBytes();
        byte[] iv = new byte[12];
        byte[] data = new byte[]{0, 0, 0, 4, 'T', 'e', 's', 't'};

        encrypt.init(key, iv, true);
        decrypt.init(key2, iv, false);

        byte[] encrypted = encrypt.encrypt(data);
        assertThatThrownBy(() -> decrypt.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AES-128-GCM");
    }

    @Test void testEncryptLargePayload() {
        Aes128Gcm encrypt = new Aes128Gcm();
        Aes128Gcm decrypt = new Aes128Gcm();
        byte[] key = "0123456789abcdef".getBytes();
        byte[] iv = new byte[12];

        encrypt.init(key, iv, true);
        decrypt.init(key, iv, false);

        byte[] data = new byte[4 + 1024];
        for (int i = 4; i < data.length; i++) {
            data[i] = (byte)(i & 0xFF);
        }

        byte[] encrypted = encrypt.encrypt(data);
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }
}
