package ssg.legoflow.ssh.hostkey;

import ssg.legoflow.ssh.kex.EcdhSha2Nistp256;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
/**
 * ECDSA host key algorithm with nistp384 curve (ecdsa-sha2-nistp384).
 *
 * @since 0.1.0
 */
public final class EcdsaSha2Nistp384 implements HostKeyAlgorithm {

    private static final String CURVE = "secp384r1";
    private static final String IDENTIFIER = "nistp384";

    @Override public String name() { return "ecdsa-sha2-nistp384"; }

    @Override
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec(CURVE));
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("EC key generation failed", e);
        }
    }

    @Override
    public byte[] sign(KeyPair keyPair, byte[] data) {
        try {
            Signature sig = Signature.getInstance("SHA384withECDSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            byte[] sigBytes = sig.sign();
            return wrapSignature(name(), sigBytes);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("ECDSA signing failed", e);
        }
    }

    @Override
    public boolean verify(byte[] publicKeyBlob, byte[] data, byte[] signature) {
        try {
            ECPublicKey pubKey = decodePublicKey(publicKeyBlob);
            byte[] sigBytes = unwrapSignature(signature);
            Signature sig = Signature.getInstance("SHA384withECDSA");
            sig.initVerify(pubKey);
            sig.update(data);
            return sig.verify(sigBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public byte[] encodePublicKey(KeyPair keyPair) {
        ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
        byte[] keyType = name().getBytes();
        byte[] curveName = IDENTIFIER.getBytes();
        byte[] point = EcdhSha2Nistp256.encodeEcPoint(pub.getW(), 48);

        ByteBuffer buf = ByteBuffer.allocate(4 + keyType.length + 4 + curveName.length + 4 + point.length);
        buf.putInt(keyType.length);
        buf.put(keyType);
        buf.putInt(curveName.length);
        buf.put(curveName);
        buf.putInt(point.length);
        buf.put(point);
        return buf.array();
    }

    private ECPublicKey decodePublicKey(byte[] blob) throws GeneralSecurityException {
        ByteBuffer buf = ByteBuffer.wrap(blob);
        int typeLen = buf.getInt();
        buf.position(buf.position() + typeLen);
        int curveLen = buf.getInt();
        buf.position(buf.position() + curveLen);
        int pointLen = buf.getInt();
        byte[] point = new byte[pointLen];
        buf.get(point);
        return EcdhSha2Nistp256.decodeEcPublicKey(point, CURVE);
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
