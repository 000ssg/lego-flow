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

    /** Creates payload-only data: [padLen:1][payload][padding].
     * No pktLen prefix — the codec handles packet format. */
    private byte[] makePayload(int payloadLen) {
        int padLen = 8; // minimum padding to block size
        byte[] data = new byte[1 + payloadLen + padLen];
        data[0] = (byte) padLen;  // padLen byte
        for (int i = 1; i < data.length; i++) {
            data[i] = (byte) ('A' + (i - 1) % 26);
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

    @Test void testEncryptPayloadMinimalSize() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, true);

        // Minimum payload: padLen(1) + at least MIN_PADDING bytes
        byte[] payload = new byte[1 + 4]; // padLen + 4 bytes (minimum padding)
        byte[] encrypted = cipher.encryptPayload(payload);
        // Output should be payload + 16-byte tag
        assertThat(encrypted).hasSize(payload.length + 16);
    }

    @Test void testDecryptPayloadWithShortData() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, false);

        byte[] shortData = new byte[10]; // < 16 byte minimum (tag only)
        assertThatThrownBy(() -> cipher.decryptPayload(shortData))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void testEncryptDecryptRoundTrip() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);
        // Same sequence number for both
        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        byte[] payload = makePayload(16);
        byte[] encrypted = encrypt.encryptPayload(payload);
        // Output: [encPayload][tag]
        assertThat(encrypted).hasSize(payload.length + 16);
        byte[] decrypted = decrypt.decryptPayload(encrypted);
        assertThat(decrypted).isEqualTo(payload);
    }

    @Test void testEncryptDecryptLargerPayload() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);
        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        byte[] payload = makePayload(256);
        byte[] encrypted = encrypt.encryptPayload(payload);
        byte[] decrypted = decrypt.decryptPayload(encrypted);
        assertThat(decrypted).isEqualTo(payload);
    }

    @Test void testDecryptWithWrongKeyFails() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key1 = makeKey();
        byte[] key2 = makeKey();
        key2[32] ^= 0xFF; // flip bits in polyKey range (bytes 32-63) so tag verification fails

        encrypt.init(key1, null, true);
        decrypt.init(key2, null, false);
        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        byte[] payload = makePayload(8);
        byte[] encrypted = encrypt.encryptPayload(payload);

        assertThatThrownBy(() -> decrypt.decryptPayload(encrypted))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("tag verification failed");
    }

    @Test void testEncryptedDataIsDifferentFromPlaintext() {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(makeKey(), null, true);
        cipher.setSequenceNumber(0);

        byte[] payload = makePayload(8);
        byte[] encrypted = cipher.encryptPayload(payload);
        assertThat(encrypted).isNotEqualTo(payload);
    }

    @Test void testInitEncryptingFlag() {
        ChaCha20Poly1305 encryptCipher = new ChaCha20Poly1305();
        ChaCha20Poly1305 decryptCipher = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encryptCipher.init(key, null, true);
        decryptCipher.init(key, null, false);
        encryptCipher.setSequenceNumber(0);
        decryptCipher.setSequenceNumber(0);

        byte[] payload = makePayload(8);
        byte[] encrypted = encryptCipher.encryptPayload(payload);
        assertThatCode(() -> decryptCipher.decryptPayload(encrypted)).doesNotThrowAnyException();
    }

    @Test void testDecryptMinimalPayload() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);
        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        // Minimal payload: padLen(1) + MIN_PADDING(4) = 5 bytes
        byte[] payload = new byte[5];
        payload[0] = 4; // padLen
        byte[] encrypted = encrypt.encryptPayload(payload);
        byte[] decrypted = decrypt.decryptPayload(encrypted);
        assertThat(decrypted).isEqualTo(payload);
    }

    @Test void testMultipleSequenceNumbers() {
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);

        byte[] payload = makePayload(32);
        for (long seq = 0; seq < 10; seq++) {
            encrypt.setSequenceNumber(seq);
            decrypt.setSequenceNumber(seq);
            byte[] encrypted = encrypt.encryptPayload(payload);
            byte[] decrypted = decrypt.decryptPayload(encrypted);
            assertThat(decrypted).as("seq=%d", seq).isEqualTo(payload);
        }
    }

    @Test void testPayloadWithPadLen() {
        // Verify that the padLen byte is preserved through encrypt/decrypt
        ChaCha20Poly1305 encrypt = new ChaCha20Poly1305();
        ChaCha20Poly1305 decrypt = new ChaCha20Poly1305();
        byte[] key = makeKey();

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);
        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        // padLen = 10, payload = 8 bytes
        byte[] payload = new byte[1 + 8 + 10];
        payload[0] = 10; // padLen byte
        for (int i = 1; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        byte[] encrypted = encrypt.encryptPayload(payload);
        byte[] decrypted = decrypt.decryptPayload(encrypted);
        assertThat(decrypted).isEqualTo(payload);
        assertThat(decrypted[0]).isEqualTo((byte) 10); // padLen preserved
    }
}
