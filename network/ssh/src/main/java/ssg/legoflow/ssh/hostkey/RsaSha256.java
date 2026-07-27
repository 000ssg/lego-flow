package ssg.legoflow.ssh.hostkey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * RSA host key algorithm with SHA-256 signature (rsa-sha2-256).
 *
 * @since 1.0.0
 */
public final class RsaSha256 implements HostKeyAlgorithm {

    private static final int KEY_SIZE = 3072;

    @Override public String name() { return "rsa-sha2-256"; }

    @Override
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(KEY_SIZE);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA not available", e);
        }
    }

    @Override
    public byte[] sign(KeyPair keyPair, byte[] data) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            byte[] sigBytes = sig.sign();
            // Wrap in SSH signature format: string "rsa-sha2-256" + string signature
            return wrapSignature("rsa-sha2-256", sigBytes);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("RSA signing failed", e);
        }
    }

    @Override
    public boolean verify(byte[] publicKeyBlob, byte[] data, byte[] signature) {
        try {
            RSAPublicKey pubKey = decodePublicKey(publicKeyBlob);
            // Unwrap SSH signature format
            byte[] sigBytes = unwrapSignature(signature);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pubKey);
            sig.update(data);
            return sig.verify(sigBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public byte[] encodePublicKey(KeyPair keyPair) {
        RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        byte[] e = pub.getPublicExponent().toByteArray();
        byte[] n = pub.getModulus().toByteArray();
        byte[] keyType = "ssh-rsa".getBytes();

        ByteBuffer buf = ByteBuffer.allocate(4 + keyType.length + 4 + e.length + 4 + n.length);
        buf.putInt(keyType.length);
        buf.put(keyType);
        buf.putInt(e.length);
        buf.put(e);
        buf.putInt(n.length);
        buf.put(n);
        return buf.array();
    }

    private RSAPublicKey decodePublicKey(byte[] blob) throws GeneralSecurityException {
        ByteBuffer buf = ByteBuffer.wrap(blob);
        int typeLen = buf.getInt();
        byte[] typeBytes = new byte[typeLen];
        buf.get(typeBytes);
        int eLen = buf.getInt();
        byte[] eBytes = new byte[eLen];
        buf.get(eBytes);
        int nLen = buf.getInt();
        byte[] nBytes = new byte[nLen];
        buf.get(nBytes);

        BigInteger e = new BigInteger(eBytes);
        BigInteger n = new BigInteger(nBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(n, e));
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
        buf.position(buf.position() + algLen); // skip algorithm name
        int sigLen = buf.getInt();
        byte[] sigBytes = new byte[sigLen];
        buf.get(sigBytes);
        return sigBytes;
    }
}
