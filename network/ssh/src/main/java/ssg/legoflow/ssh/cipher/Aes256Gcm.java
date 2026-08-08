package ssg.legoflow.ssh.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-256 in GCM mode for SSH (aes256-gcm@openssh.com).
 *
 * <p>AEAD cipher providing both encryption and authentication.
 *
 * @since 0.1.0
 */
public final class Aes256Gcm implements SshCipher {

    private static final int TAG_LENGTH_BITS = 128;
    private SecretKeySpec keySpec;
    private byte[] iv;
    private boolean encrypting;

    @Override public String name() { return "aes256-gcm@openssh.com"; }
    @Override public int blockSize() { return 16; }
    @Override public int keySize() { return 32; }
    @Override public int ivSize() { return 12; }
    @Override public boolean isAead() { return true; }
    @Override public int authTagLength() { return 16; }

    @Override
    public void init(byte[] key, byte[] iv, boolean encrypt) {
        this.keySpec = new SecretKeySpec(key, 0, keySize(), "AES");
        this.iv = new byte[ivSize()];
        System.arraycopy(iv, 0, this.iv, 0, Math.min(iv.length, ivSize()));
        this.encrypting = encrypt;
    }

    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            if (data.length >= 4) {
                cipher.updateAAD(data, 0, 4);
                byte[] aadPart = new byte[4];
                System.arraycopy(data, 0, aadPart, 0, 4);
                byte[] encrypted = cipher.doFinal(data, 4, data.length - 4);
                byte[] result = new byte[4 + encrypted.length];
                System.arraycopy(aadPart, 0, result, 0, 4);
                System.arraycopy(encrypted, 0, result, 4, encrypted.length);
                incrementIv();
                return result;
            }
            byte[] result = cipher.doFinal(data);
            incrementIv();
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-256-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            if (data.length >= 4) {
                cipher.updateAAD(data, 0, 4);
                byte[] decrypted = cipher.doFinal(data, 4, data.length - 4);
                byte[] result = new byte[4 + decrypted.length];
                System.arraycopy(data, 0, result, 0, 4);
                System.arraycopy(decrypted, 0, result, 4, decrypted.length);
                incrementIv();
                return result;
            }
            byte[] result = cipher.doFinal(data);
            incrementIv();
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    private void incrementIv() {
        for (int i = iv.length - 1; i >= iv.length - 8; i--) {
            if (++iv[i] != 0) break;
        }
    }
}
