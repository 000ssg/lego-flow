package ssg.legoflow.ssh.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-128 in CTR mode cipher for SSH (aes128-ctr).
 *
 * @since 0.1.0
 */
public final class Aes128Ctr implements SshCipher {

    private Cipher cipher;

    @Override public String name() { return "aes128-ctr"; }
    @Override public int blockSize() { return 16; }
    @Override public int keySize() { return 16; }
    @Override public int ivSize() { return 16; }
    @Override public boolean isAead() { return false; }
    @Override public int authTagLength() { return 0; }

    @Override
    public void init(byte[] key, byte[] iv, boolean encrypt) {
        try {
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, 0, keySize(), "AES"),
                    new IvParameterSpec(iv, 0, ivSize()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize AES-128-CTR", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] data) {
        try {
            return cipher.update(data);
        } catch (Exception e) {
            throw new RuntimeException("AES-128-CTR encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data) {
        try {
            return cipher.update(data);
        } catch (Exception e) {
            throw new RuntimeException("AES-128-CTR decryption failed", e);
        }
    }
}
