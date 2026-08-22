package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

/**
 * Diffie-Hellman Group 14 key exchange with SHA-256 (RFC 4253, RFC 8268).
 *
 * <p>Uses the 2048-bit MODP group from RFC 3526 section 3.
 * Relies on Java JCE for all DH crypto operations.
 *
 * @since 0.1.0
 */
public final class DiffieHellmanGroup14 implements KexAlgorithm {

    /** The 2048-bit MODP prime from RFC 3526 section 3. */
    public static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);

    /** The generator g = 2. */
    public static final BigInteger G = BigInteger.TWO;

    private KeyPair keyPair;

    @Override
    public String name() {
        return "diffie-hellman-group14-sha256";
    }

    @Override
    public String hashAlgorithm() {
        return "SHA-256";
    }

    @Override
    public void init() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
            kpg.initialize(new DHParameterSpec(P, G));
            keyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("DH algorithm not available", e);
        }
    }

    @Override
    public byte[] localPublicValue() {
        DHPublicKey pub = (DHPublicKey) keyPair.getPublic();
        return toMpint(pub.getY());
    }

    @Override
    public byte[] computeSharedSecret(byte[] remotePublicValue) {
        BigInteger f = fromMpint(remotePublicValue);

        // Validate: 1 < f < p - 1
        DHPrivateKey priv = (DHPrivateKey) keyPair.getPrivate();
        DHParameterSpec param = priv.getParams();
        if (f.compareTo(BigInteger.ONE) <= 0
                || f.compareTo(param.getP().subtract(BigInteger.ONE)) >= 0) {
            throw new IllegalArgumentException("Invalid DH public value");
        }

        try {
            DHPublicKeySpec pubSpec = new DHPublicKeySpec(f, param.getP(), param.getG());
            KeyFactory kf = KeyFactory.getInstance("DH");
            KeyAgreement ka = KeyAgreement.getInstance("DH");
            ka.init(keyPair.getPrivate());
            ka.doPhase(kf.generatePublic(pubSpec), true);
            byte[] rawSecret = ka.generateSecret();
            // Return raw big-endian bytes (not mpint)
            // computeExchangeHash wraps with writeBuf → correct mpint [4-len][raw-bytes]
            byte[] rawBytes = new BigInteger(1, rawSecret).toByteArray();
            if (rawBytes.length > 0 && rawBytes[0] == 0) {
                byte[] trimmed = new byte[rawBytes.length - 1];
                System.arraycopy(rawBytes, 1, trimmed, 0, trimmed.length);
                return trimmed;
            }
            return rawBytes;
        } catch (Exception e) {
            throw new RuntimeException("DH key agreement failed", e);
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
            writeString(buf, clientVersion);
            writeString(buf, serverVersion);
            writeBinary(buf, clientKexInit);
            writeBinary(buf, serverKexInit);
            writeBinary(buf, hostKey);
            writeBinary(buf, e);
            writeBinary(buf, f);
            writeBinary(buf, sharedSecret);
            buf.flip();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            return digest.digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }

    /**
     * Converts a BigInteger to SSH mpint format.
     *
     * @param value the value to convert
     * @return the mpint bytes (length + value, with leading zero if needed)
     */
    static byte[] toMpint(BigInteger value) {
        byte[] bytes = value.toByteArray();
        ByteBuffer buf = ByteBuffer.allocate(4 + bytes.length);
        buf.putInt(bytes.length);
        buf.put(bytes);
        return buf.array();
    }

    /**
     * Converts SSH mpint bytes to BigInteger.
     *
     * @param mpint the mpint bytes (length-prefixed)
     * @return the BigInteger value
     */
    static BigInteger fromMpint(byte[] mpint) {
        ByteBuffer buf = ByteBuffer.wrap(mpint);
        int len = buf.getInt();
        byte[] data = new byte[len];
        buf.get(data);
        return new BigInteger(data);
    }

    private static void writeString(ByteBuffer buf, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        buf.putInt(data.length);
        buf.put(data);
    }

    private static void writeBinary(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }
}
