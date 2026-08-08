package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChaCha20Poly1305Test {

    private byte[] makeKey() {
        byte[] key = new byte[64];
        for (int i = 0; i < 64; i++) {
            key[i] = (byte) (i & 0xFF);
        }
        return key;
    }

    private byte[] makeData(int payloadLen) {
        byte[] data = new byte[4 + payloadLen];
        data[0] = 0; data[1] = 0;
        data[2] = (byte)(payloadLen >> 8);
        data[3] = (byte)(payloadLen & 0xFF);
        for (int i = 4; i < data.length; i++) {
            data[i] = (byte)('A' + (i - 4));
        }
        return data;
    }

    @Test void testCipherMetadata() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        assertThat(cipher.name()).isEqualTo("chacha20-poly1305@openssh.com");
        assertThat(cipher.blockSize()).isEqualTo(8);
        assertThat(cipher.keySize()).isEqualTo(64);
        assertThat(cipher.ivSize()).isEqualTo(0);
        assertThat(cipher.isAead()).isTrue();
        assertThat(cipher.authTagLength()).isEqualTo(16);
    }

    @Test void testInitWithShortKey() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        byte[] shortKey = new byte[32];

        assertThatThrownBy(() -> cipher.init(shortKey, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires 64 bytes");
    }

    @Test void testEncryptWithShortData() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, true);

        byte[] shortData = new byte[]{1, 2, 3}; // < 4 bytes
        assertThatThrownBy(() -> cipher.encrypt(shortData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ChaCha20-Poly1305 encryption failed");
    }

    @Test void testDecryptWithShortData() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, false);

        byte[] shortData = new byte[19]; // < 20 minimum
        assertThatThrownBy(() -> cipher.decrypt(shortData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ChaCha20-Poly1305 decryption failed");
    }

    @Test void testEncryptDecryptRoundTrip() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);

        byte[] data = makeData(16);
        byte[] encrypted = encrypt.encrypt(data);
        assertThat(encrypted).hasSizeGreaterThan(data.length);
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testEncryptDecryptLargerPayload() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);

        byte[] data = makeData(256);
        byte[] encrypted = encrypt.encrypt(data);
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testDecryptWithWrongKeyFails() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key1 = makeKey();
        byte[] key2 = makeKey();
        key2[0] ^= 0xFF; // flip bits in first key

        encrypt.init(key1, null, true);
        decrypt.init(key2, null, false);

        byte[] data = makeData(8);
        byte[] encrypted = encrypt.encrypt(data);
        
        assertThatThrownBy(() -> decrypt.decrypt(encrypted))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Poly1305 tag verification failed");
    }

    @Test void testEncryptedDataIsDifferentFromPlaintext() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, true);

        byte[] data = makeData(8);
        byte[] encrypted = cipher.encrypt(data);
        assertThat(encrypted).isNotEqualTo(data);
    }

    @Test void testInitEncryptingFlag() {
        ChaCha20Poly1305 encryptCipher = new ChaCha20Poly1305();
        ChaCha20Poly1305 decryptCipher = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encryptCipher.init(key, null, true);
        decryptCipher.init(key, null, false);

        byte[] data = makeData(8);
        byte[] encrypted = encryptCipher.encrypt(data);
        assertThatCode(() -> decryptCipher.decrypt(encrypted)).doesNotThrowAnyException();
    }

    @Test void testDecryptMinimalPayload() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);

        byte[] data = new byte[]{0, 0, 0, 0};
        byte[] encrypted = encrypt.encrypt(data);
        byte[] decrypted = decrypt.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testDecryptZeroPayloadLength() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        byte[] key = makeKey();
        cipher.init(key, null, true);

        byte[] data = new byte[]{0, 0, 0, 0};
        assertThatCode(() -> cipher.encrypt(data)).doesNotThrowAnyException();
    }
}
