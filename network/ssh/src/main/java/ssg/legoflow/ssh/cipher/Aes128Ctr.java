package ssg.legoflow.ssh.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-128 in CTR mode cipher for SSH (aes128-ctr).
 *
 * <p>Cipher handles ONLY encrypt/decrypt of packet data. No MAC or packet
 * format awareness — these are handled by the codec layer.
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
    @Override public int nonceLen() { return 16; }

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
            byte[] partial = cipher.update(data);
            byte[] last = cipher.doFinal();
            if (last.length == 0) return partial;
            byte[] result = new byte[partial.length + last.length];
            System.arraycopy(partial, 0, result, 0, partial.length);
            System.arraycopy(last, 0, result, partial.length, last.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("AES-128-CTR encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data) {
        try {
            byte[] partial = cipher.update(data);
            byte[] last = cipher.doFinal();
            if (last.length == 0) return partial;
            byte[] result = new byte[partial.length + last.length];
            System.arraycopy(partial, 0, result, 0, partial.length);
            System.arraycopy(last, 0, result, partial.length, last.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("AES-128-CTR decryption failed", e);
        }
    }
}
