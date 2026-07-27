package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;

/**
 * ECDH key exchange using nistp384 curve with SHA-384 (RFC 5656).
 *
 * @since 1.0.0
 */
public final class EcdhSha2Nistp384 implements KexAlgorithm {

    private static final String CURVE_NAME = "secp384r1";
    private KeyPair keyPair;

    @Override
    public String name() {
        return "ecdh-sha2-nistp384";
    }

    @Override
    public String hashAlgorithm() {
        return "SHA-384";
    }

    @Override
    public void init() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec(CURVE_NAME));
            keyPair = kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize ECDH", e);
        }
    }

    @Override
    public byte[] localPublicValue() {
        ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
        return EcdhSha2Nistp256.encodeEcPoint(pub.getW(), 48);
    }

    @Override
    public byte[] computeSharedSecret(byte[] remotePublicValue) {
        try {
            ECPublicKey remotePub = EcdhSha2Nistp256.decodeEcPublicKey(remotePublicValue, CURVE_NAME);
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(keyPair.getPrivate());
            ka.doPhase(remotePub, true);
            byte[] secret = ka.generateSecret();
            BigInteger K = new BigInteger(1, secret);
            return DiffieHellmanGroup14.toMpint(K);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("ECDH computation failed", e);
        }
    }

    @Override
    public byte[] computeExchangeHash(
            String clientVersion, String serverVersion,
            byte[] clientKexInit, byte[] serverKexInit,
            byte[] hostKey, byte[] e, byte[] f, byte[] sharedSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            ByteBuffer buf = ByteBuffer.allocate(
                    4 + clientVersion.length() + 4 + serverVersion.length()
                    + 4 + clientKexInit.length + 4 + serverKexInit.length
                    + 4 + hostKey.length + 4 + e.length + 4 + f.length + 4 + sharedSecret.length + 256);
            writeBuf(buf, clientVersion.getBytes(StandardCharsets.UTF_8));
            writeBuf(buf, serverVersion.getBytes(StandardCharsets.UTF_8));
            writeBuf(buf, clientKexInit);
            writeBuf(buf, serverKexInit);
            writeBuf(buf, hostKey);
            writeBuf(buf, e);
            writeBuf(buf, f);
            writeBuf(buf, sharedSecret);
            buf.flip();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            return digest.digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-384 not available", ex);
        }
    }

    private static void writeBuf(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }
}
