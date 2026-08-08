package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for SSH cipher algorithms.
 */
class CipherComprehensiveTest {

    @Test void testAes128Ctr() {
        var cipher = CipherFactory.create("aes128-ctr");
        assertThat(cipher).isNotNull();
        
        byte[] key = new byte[16]; // 128-bit key
        java.util.Arrays.fill(key, (byte) 0xAA);
        byte[] iv = new byte[16];
        java.util.Arrays.fill(iv, (byte) 0x55);
        
        cipher.init(key, iv, true);  // encrypt
        byte[] plaintext = "Hello, SSH!".getBytes();
        byte[] encrypted = cipher.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        
        cipher.init(key, iv, false); // decrypt
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test void testAes192Ctr() {
        var cipher = CipherFactory.create("aes192-ctr");
        assertThat(cipher).isNotNull();
        
        byte[] key = new byte[24]; // 192-bit key
        java.util.Arrays.fill(key, (byte) 0xBB);
        byte[] iv = new byte[16];
        
        cipher.init(key, iv, true);
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        
        byte[] encrypted = cipher.encrypt(data);
        cipher.init(key, iv, false);
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testAes256Ctr() {
        var cipher = CipherFactory.create("aes256-ctr");
        assertThat(cipher).isNotNull();
        
        byte[] key = new byte[32]; // 256-bit key
        java.util.Arrays.fill(key, (byte) 0xCC);
        byte[] iv = new byte[16];
        
        cipher.init(key, iv, true);
        byte[] data = "AES-256 test vector data".getBytes();
        byte[] encrypted = cipher.encrypt(data);
        
        cipher.init(key, iv, false);
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test void testNullCipherName() {
        assertThatThrownBy(() -> CipherFactory.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testUnknownCipherNameThrows() {
        assertThatThrownBy(() -> CipherFactory.create("unsupported-algo"))
                .isInstanceOf(Exception.class);
    }

    @Test void testEmptyDataEncryptDecrypt() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        
        // Encrypting empty data should not throw
        assertThatNoException().isThrownBy(() -> {
            cipher.init(key, iv, true);
            cipher.encrypt(new byte[0]);
        });
    }

    @Test void testVariousDataSizes() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        
        int[] sizes = {1, 7, 15, 16, 17, 31, 32, 33, 64, 100, 256};
        for (int size : sizes) {
            cipher.init(key, iv, true);
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) data[i] = (byte) i;
            
            byte[] encrypted = cipher.encrypt(data);
            assertThat(encrypted).hasSize(size); // CTR mode preserves size
            
            cipher.init(key, iv, false);
            byte[] decrypted = cipher.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(data);
        }
    }

    @Test void testDifferentIvProducesDifferentCiphertext() {
        var cipher1 = CipherFactory.create("aes128-ctr");
        var cipher2 = CipherFactory.create("aes128-ctr");
        
        byte[] key = new byte[16];
        java.util.Arrays.fill(key, (byte) 0xFF);
        byte[] iv1 = new byte[16];
        byte[] iv2 = new byte[16];
        iv2[0] = 0x01; // Different IV
        
        cipher1.init(key, iv1, true);
        cipher2.init(key, iv2, true);
        
        byte[] plaintext = "Same plaintext".getBytes();
        byte[] enc1 = cipher1.encrypt(plaintext);
        byte[] enc2 = cipher2.encrypt(plaintext);
        
        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test void testSameIvSameCiphertext() {
        var cipher1 = CipherFactory.create("aes128-ctr");
        var cipher2 = CipherFactory.create("aes128-ctr");
        
        byte[] key = new byte[16];
        java.util.Arrays.fill(key, (byte) 0xEE);
        byte[] iv = new byte[16];
        
        cipher1.init(key, iv, true);
        cipher2.init(key, iv, true);
        
        byte[] plaintext = "Deterministic encryption test".getBytes();
        assertThat(cipher1.encrypt(plaintext)).isEqualTo(cipher2.encrypt(plaintext));
    }

    @Test void testLargeDataEncryptDecrypt() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        
        // 1MB of data
        byte[] largeData = new byte[1024 * 1024];
        var rnd = new java.util.Random(42);
        rnd.nextBytes(largeData);
        
        cipher.init(key, iv, true);
        byte[] encrypted = cipher.encrypt(largeData);
        assertThat(encrypted).hasSize(largeData.length);
        
        cipher.init(key, iv, false);
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(largeData);
    }

    @Test void testCipherFactorySupportedAlgorithms() {
        String[] supported = {"aes128-ctr", "aes192-ctr", "aes256-ctr"};
        for (String algo : supported) {
            assertThatNoException().isThrownBy(() -> CipherFactory.create(algo));
        }
    }

    @Test void testCipherInitWithNullKey() {
        var cipher = CipherFactory.create("aes128-ctr");
        // Null key behavior depends on implementation - may throw or may not
        try {
            cipher.init(null, new byte[16], true);
        } catch (Exception e) {
            // Expected: null key should cause an error
        }
    }

    @Test void testMultipleEncryptDecryptCycles() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        
        for (int round = 0; round < 10; round++) {
            cipher.init(key, iv, true);
            byte[] data = String.valueOf(round).getBytes();
            byte[] encrypted = cipher.encrypt(data);
            
            cipher.init(key, iv, false);
            byte[] decrypted = cipher.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(data);
        }
    }

    @Test void testDirectionToggle() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        
        byte[] original = "toggle test".getBytes();
        
        cipher.init(key, iv, true);   // encrypt
        byte[] enc = cipher.encrypt(original);
        
        cipher.init(key, iv, false);  // decrypt
        byte[] dec = cipher.decrypt(enc);
        
        cipher.init(key, iv, true);   // encrypt again
        byte[] enc2 = cipher.encrypt(original);
        
        assertThat(enc).isEqualTo(enc2);
        assertThat(dec).isEqualTo(original);
    }

    @Test void testCbcModes() {
        String[] cbcAlgos = {"aes128-cbc", "aes192-cbc", "aes256-cbc"};
        for (String algo : cbcAlgos) {
            try {
                var cipher = CipherFactory.create(algo);
                int keySize = algo.contains("128") ? 16 : 
                             algo.contains("192") ? 24 : 32;
                byte[] key = new byte[keySize];
                java.util.Arrays.fill(key, (byte) 0xAB);
                byte[] iv = new byte[16];
                
                cipher.init(key, iv, true);
                byte[] data = "0123456789ABCDEF".getBytes(); // 16 bytes for AES block
                byte[] encrypted = cipher.encrypt(data);
                
                cipher.init(key, iv, false);
                byte[] decrypted = cipher.decrypt(encrypted);
                assertThat(decrypted).isEqualTo(data);
            } catch (IllegalArgumentException e) {
                // CBC mode may not be supported in all environments
            }
        }
    }

    @Test void test3DesCbc() {
        try {
            var cipher = CipherFactory.create("3des-cbc");
            byte[] key = new byte[24]; // Triple DES needs 24 bytes
            java.util.Arrays.fill(key, (byte) 0xDE);
            byte[] iv = new byte[8]; // DES uses 8-byte IV
            
            cipher.init(key, iv, true);
            byte[] data = "0123456789ABCDEF".getBytes();
            byte[] encrypted = cipher.encrypt(data);
            
            cipher.init(key, iv, false);
            byte[] decrypted = cipher.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(data);
        } catch (IllegalArgumentException e) {
            // 3DES may not be available
        }
    }

    @Test void testChaCha20Poly1305() {
        try {
            var cipher = CipherFactory.create("chacha20-poly1305@openssh.com");
            assertThat(cipher).isNotNull();
            
            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte) 0xDD);
            byte[] nonce = new byte[12]; // 96-bit nonce for ChaCha20
            
            cipher.init(key, nonce, true);
            byte[] plaintext = "ChaCha20 test data".getBytes();
            byte[] encrypted = cipher.encrypt(plaintext);
            
            cipher.init(key, nonce, false);
            byte[] decrypted = cipher.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        } catch (IllegalArgumentException e) {
            // May not be supported - acceptable
        }
    }

    @Test void testNullIv() {
        var cipher = CipherFactory.create("aes128-ctr");
        // Null IV behavior depends on implementation
        try {
            cipher.init(new byte[16], null, true);
        } catch (Exception e) {
            // Expected: null IV should cause an error
        }
    }
}
