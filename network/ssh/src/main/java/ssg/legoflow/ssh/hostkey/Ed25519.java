package ssg.legoflow.ssh.hostkey;

import java.nio.ByteBuffer;
import java.security.*;

/**
 * Ed25519 host key algorithm (ssh-ed25519).
 *
 * <p>Uses the Ed25519 signature scheme from RFC 8032.
 *
 * @since 0.1.0
 */
public final class Ed25519 implements HostKeyAlgorithm {

    @Override public String name() { return "ssh-ed25519"; }

    @Override
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 not available", e);
        }
    }

    @Override
    public byte[] sign(KeyPair keyPair, byte[] data) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            byte[] sigBytes = sig.sign();
            return wrapSignature("ssh-ed25519", sigBytes);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Ed25519 signing failed", e);
        }
    }

    @Override
    public boolean verify(byte[] publicKeyBlob, byte[] data, byte[] signature) {
        try {
            PublicKey pubKey = decodePublicKey(publicKeyBlob);
            byte[] sigBytes = unwrapSignature(signature);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pubKey);
            sig.update(data);
            return sig.verify(sigBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public byte[] encodePublicKey(KeyPair keyPair) {
        byte[] keyType = "ssh-ed25519".getBytes();
        // Extract raw 32-byte Ed25519 public key from X.509 encoding
        // X.509 BIT STRING for Ed25519: [padding 0x00][32 bytes key]
        // The raw key is the last 32 bytes of the encoded key
        byte[] encoded = keyPair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);

        ByteBuffer buf = ByteBuffer.allocate(4 + keyType.length + 4 + raw.length);
        buf.putInt(keyType.length);
        buf.put(keyType);
        buf.putInt(raw.length);
        buf.put(raw);
        return buf.array();
    }

    private PublicKey decodePublicKey(byte[] blob) throws GeneralSecurityException {
        ByteBuffer buf = ByteBuffer.wrap(blob);
        int typeLen = buf.getInt();
        buf.position(buf.position() + typeLen); // skip "ssh-ed25519"
        int keyLen = buf.getInt();
        byte[] rawKey = new byte[keyLen];
        buf.get(rawKey);

        // Build X.509 SubjectPublicKeyInfo for Ed25519
        byte[] prefix = new byte[]{
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
        };
        byte[] x509 = new byte[prefix.length + 32];
        System.arraycopy(prefix, 0, x509, 0, prefix.length);
        System.arraycopy(rawKey, 0, x509, prefix.length, Math.min(rawKey.length, 32));

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        return kf.generatePublic(new java.security.spec.X509EncodedKeySpec(x509));
    }

    private byte[] wrapSignature(String algorithm, byte[] sigBytes) {
        byte[] algBytes = algorithm.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(4 + algBytes.length + 4 + sigBytes.length);
        buf.putInt(algBytes.length);
        buf.put(algBytes);
        buf.putInt(sigBytes.length);
        buf.put(sigBytes);
        return buf.array();
    }

    private byte[] unwrapSignature(byte[] wrapped) {
        ByteBuffer buf = ByteBuffer.wrap(wrapped);
        int algLen = buf.getInt();
        buf.position(buf.position() + algLen);
        int sigLen = buf.getInt();
        byte[] sigBytes = new byte[sigLen];
        buf.get(sigBytes);
        return sigBytes;
    }
}
