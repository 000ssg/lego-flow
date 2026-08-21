package ssg.legoflow.ssh.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-128 in GCM mode for SSH (aes128-gcm@openssh.com).
 *
 * <p>AEAD cipher using standard JCA AES/GCM. Per OpenSSH wire format:
 * <ul>
 *   <li>Encrypt: full packet encrypted with pktLen as AAD, returns [ct][tag]</li>
 *   <li>Decrypt: uses pktLen (from wire format) as AAD, returns [plaintext]</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class Aes128Gcm implements SshCipher {

    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int NONCE_LEN = 12;
    private static final byte[] GCM_PREFIX = {'G', 'C', 'M', ' '};

    private SecretKeySpec keySpec;
    private byte[] baseNonce;
    private long autoSeq = 0;
    private byte[] aad = new byte[4];

    @Override public String name() { return "aes128-gcm@openssh.com"; }
    @Override public int blockSize() { return 16; }
    @Override public int keySize() { return 16; }
    @Override public int ivSize() { return 12; }
    @Override public boolean isAead() { return true; }
    @Override public int authTagLength() { return TAG_LENGTH_BYTES; }
    @Override public int nonceLen() { return NONCE_LEN; }

    @Override
    public void init(byte[] key, byte[] iv, boolean encrypt) {
        this.keySpec = new SecretKeySpec(key, 0, keySize(), "AES");
        this.baseNonce = new byte[NONCE_LEN];
        System.arraycopy(GCM_PREFIX, 0, this.baseNonce, 0, 4);
        int ivCopyLen = Math.min(iv.length, NONCE_LEN - 4);
        System.arraycopy(iv, 0, this.baseNonce, 4, ivCopyLen);
        this.autoSeq = 0;
    }

    private byte[] makeSeqNonce(long seq) {
        byte[] n = new byte[NONCE_LEN];
        System.arraycopy(baseNonce, 0, n, 0, NONCE_LEN);
        for (int i = 0; i < 4; i++) {
            n[i] = (byte) (GCM_PREFIX[i] ^ ((seq >> (24 - 8 * i)) & 0xFF));
        }
        return n;
    }

    @Override
    public void setSequenceNumber(long seq) {
        this.autoSeq = seq;
    }

    @Override
    public void setAad(byte[] aad) {
        System.arraycopy(aad, 0, this.aad, 0, Math.min(4, aad.length));
    }

    @Override
    public void clearAad() {
        java.util.Arrays.fill(this.aad, (byte) 0);
    }

    @Override
    public byte[] encryptWithAad(byte[] packet, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_LENGTH_BITS, makeSeqNonce(autoSeq++)));
            cipher.updateAAD(aad);
            return cipher.doFinal(packet);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-128-GCM encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decryptWithAad(byte[] ctWithTag, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (ctWithTag.length < TAG_LENGTH_BYTES) {
                throw new RuntimeException("AES-128-GCM decrypt: data too short");
            }
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_LENGTH_BITS, makeSeqNonce(autoSeq++)));
            cipher.updateAAD(aad);
            return cipher.doFinal(ctWithTag);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-128-GCM decryption failed: " + e.getMessage(), e);
        }
    }

    // Delegate encrypt/decrypt to encryptedWithAad/decryptWithAad for AEAD mode
    @Override
    public byte[] encrypt(byte[] data) {
        return encryptWithAad(data, aad);
    }

    @Override
    public byte[] decrypt(byte[] data) {
        return decryptWithAad(data, aad);
    }
}
