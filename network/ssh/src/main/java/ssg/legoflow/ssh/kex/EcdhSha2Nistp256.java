package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;

import javax.crypto.KeyAgreement;

/**
 * ECDH key exchange using nistp256 curve with SHA-256 (RFC 5656).
 *
 * @since 0.1.0
 */
public final class EcdhSha2Nistp256 implements KexAlgorithm {

    private static final String CURVE_NAME = "secp256r1";
    private KeyPair keyPair;
    private byte[] sharedSecret;

    @Override
    public String name() {
        return "ecdh-sha2-nistp256";
    }

    @Override
    public String hashAlgorithm() {
        return "SHA-256";
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
        return encodeEcPoint(pub.getW(), 32);
    }

    @Override
    public byte[] computeSharedSecret(byte[] remotePublicValue) {
        try {
            ECPublicKey remotePub = decodeEcPublicKey(remotePublicValue, CURVE_NAME);
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }

    /**
     * Encodes an EC point in uncompressed format (0x04 || x || y).
     *
     * @param point     the EC point
     * @param fieldSize the field size in bytes (32 for P-256, 48 for P-384, 66 for P-521)
     * @return the encoded point
     */
    public static byte[] encodeEcPoint(ECPoint point, int fieldSize) {
        byte[] x = toFixedLength(point.getAffineX().toByteArray(), fieldSize);
        byte[] y = toFixedLength(point.getAffineY().toByteArray(), fieldSize);
        byte[] encoded = new byte[1 + fieldSize * 2];
        encoded[0] = 0x04;
        System.arraycopy(x, 0, encoded, 1, fieldSize);
        System.arraycopy(y, 0, encoded, 1 + fieldSize, fieldSize);
        return encoded;
    }

    /**
     * Decodes an EC public key from uncompressed point format.
     *
     * @param encoded   the encoded point (0x04 || x || y)
     * @param curveName the curve name (e.g., "secp256r1")
     * @return the EC public key
     */
    public static ECPublicKey decodeEcPublicKey(byte[] encoded, String curveName)
            throws GeneralSecurityException {
        if (encoded[0] != 0x04) {
            throw new IllegalArgumentException("Only uncompressed EC points are supported");
        }
        int fieldSize = (encoded.length - 1) / 2;
        byte[] xBytes = new byte[fieldSize];
        byte[] yBytes = new byte[fieldSize];
        System.arraycopy(encoded, 1, xBytes, 0, fieldSize);
        System.arraycopy(encoded, 1 + fieldSize, yBytes, 0, fieldSize);
        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);

        KeyFactory kf = KeyFactory.getInstance("EC");
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(curveName));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(new ECPoint(x, y), spec);
        return (ECPublicKey) kf.generatePublic(pubSpec);
    }

    public static byte[] toFixedLength(byte[] input, int length) {
        if (input.length == length) return input;
        byte[] result = new byte[length];
        if (input.length > length) {
            // Strip leading zeros
            System.arraycopy(input, input.length - length, result, 0, length);
        } else {
            // Pad with leading zeros
            System.arraycopy(input, 0, result, length - input.length, input.length);
        }
        return result;
    }

    private static void writeBuf(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }
}
