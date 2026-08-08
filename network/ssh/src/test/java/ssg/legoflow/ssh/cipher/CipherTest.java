package ssg.legoflow.ssh.cipher;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CipherTest {
    @Test void testAes128CtrRoundTrip() {
        var cipher = CipherFactory.create("aes128-ctr");
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        for (int i = 0; i < 16; i++) key[i] = (byte) i;

        cipher.init(key, iv, true);
        byte[] plain = "Hello SSH encryption!".getBytes();
        byte[] encrypted = cipher.encrypt(plain.clone());

        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test void testAes256GcmRoundTrip() {
        var cipher = CipherFactory.create("aes256-gcm@openssh.com");
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        byte[] iv = new byte[12];

        cipher.init(key, iv, true);
        byte[] plain = "AEAD test data".getBytes();
        byte[] encrypted = cipher.encrypt(plain.clone());

        cipher.init(key, iv, false);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }
}
