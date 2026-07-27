package ssg.legoflow.ssh.mac;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * HMAC-SHA2-256 MAC for SSH (hmac-sha2-256).
 *
 * @since 1.0.0
 */
public final class HmacSha256 implements SshMac {

    private Mac mac;

    @Override public String name() { return "hmac-sha2-256"; }
    @Override public int macLength() { return 32; }
    @Override public int keyLength() { return 32; }
    @Override public boolean isEncryptThenMac() { return false; }

    @Override
    public void init(byte[] key) {
        try {
            mac = Mac.getInstance("HmacSHA256");
            byte[] macKey = new byte[keyLength()];
            System.arraycopy(key, 0, macKey, 0, Math.min(key.length, keyLength()));
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize HMAC-SHA-256", e);
        }
    }

    @Override
    public byte[] compute(long sequenceNumber, byte[] data) {
        try {
            Mac m = (Mac) mac.clone();
            ByteBuffer seqBuf = ByteBuffer.allocate(4);
            seqBuf.putInt((int) sequenceNumber);
            m.update(seqBuf.array());
            m.update(data);
            return m.doFinal();
        } catch (CloneNotSupportedException e) {
            // Fallback: reinitialize
            synchronized (this) {
                ByteBuffer seqBuf = ByteBuffer.allocate(4);
                seqBuf.putInt((int) sequenceNumber);
                mac.update(seqBuf.array());
                mac.update(data);
                return mac.doFinal();
            }
        }
    }
}
