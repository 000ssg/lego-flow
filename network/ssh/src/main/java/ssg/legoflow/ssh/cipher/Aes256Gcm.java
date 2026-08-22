package ssg.legoflow.ssh.cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * AES-256 in GCM mode for SSH (aes256-gcm@openssh.com).
 *
 * <p>AEAD cipher using standard JCA AES/GCM. Per OpenSSH wire format:
 * <ul>
 *   <li>Encrypt: full packet encrypted with pktLen as AAD, returns [ct][tag]</li>
 *   <li>Decrypt: uses pktLen (from wire format) as AAD, returns [plaintext]</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class Aes256Gcm implements SshCipher {

    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int NONCE_LEN = 12;
    private static final byte[] GCM_PREFIX = {'G', 'C', 'M', ' '};

    private static final Logger LOG = LoggerFactory.getLogger(Aes256Gcm.class);
    private SecretKeySpec keySpec;
    private byte[] baseNonce;
    private long storedSeq = -1;  // -1 means need to set via setSequenceNumber
    private byte[] aad = new byte[4];

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02x ", b));
        return sb.toString().trim();
    }

    @Override public String name() { return "aes256-gcm@openssh.com"; }
    @Override public int blockSize() { return 16; }
    @Override public int keySize() { return 32; }
    @Override public int ivSize() { return 12; }
    @Override public boolean isAead() { return true; }
    @Override public int authTagLength() { return TAG_LENGTH_BYTES; }
    @Override public int nonceLen() { return NONCE_LEN; }

    @Override
    public void init(byte[] key, byte[] iv, boolean encrypt) {
        LOG.debug("[AES256GCM-INIT] name=" + this.name() + " keyLen=" + key.length + " ivLen=" + iv.length + " encrypt=" + encrypt);
        this.keySpec = new SecretKeySpec(key, 0, keySize(), "AES");
        this.baseNonce = new byte[NONCE_LEN];
        System.arraycopy(GCM_PREFIX, 0, this.baseNonce, 0, 4);
        int ivCopyLen = Math.min(iv.length, NONCE_LEN - 4);
        System.arraycopy(iv, 0, this.baseNonce, 4, ivCopyLen);
        this.storedSeq = 0;
        LOG.debug("[AES256GCM-INIT] encrypt=" + encrypt + " keyLen=" + key.length + " ivLen=" + iv.length + " baseNonce=" + bytesToHex(this.baseNonce) + " storedSeq=" + this.storedSeq);
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
        this.storedSeq = seq;
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
            long seq = storedSeq;
            byte[] nonce = makeSeqNonce(seq);
            LOG.debug("[AES256GCM-ENC] encrypting seq=" + seq + " nonce=" + bytesToHex(nonce) + " aad=" + bytesToHex(aad));
            LOG.debug("[AES256GCM-ENC] pktBytes=" + bytesToHex(packet) + " pktLen=" + packet.length);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            storedSeq = seq + 1;
            cipher.updateAAD(aad);
            return cipher.doFinal(packet);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-256-GCM encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decryptWithAad(byte[] ctWithTag, byte[] aad) {
        try {
            long seq = storedSeq;
            byte[] nonce = makeSeqNonce(seq);
            LOG.debug("[AES256GCM-DEC] seq=" + seq + " ctLen=" + ctWithTag.length + " aad=" + bytesToHex(aad));
            LOG.debug("[AES256GCM-DEC] nonce=" + bytesToHex(nonce) + " baseNonce=" + bytesToHex(baseNonce));
            LOG.debug("[AES256GCM-DEC] ctWithTag=" + bytesToHex(ctWithTag));
            LOG.debug("[AES256GCM-DEC] keyLen=" + keySpec.getAlgorithm());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (ctWithTag.length < TAG_LENGTH_BYTES) {
                throw new RuntimeException("AES-256-GCM decrypt: data too short");
            }
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            storedSeq = seq + 1;
            cipher.updateAAD(aad);
            return cipher.doFinal(ctWithTag);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES-256-GCM decryption failed: " + e.getMessage(), e);
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
