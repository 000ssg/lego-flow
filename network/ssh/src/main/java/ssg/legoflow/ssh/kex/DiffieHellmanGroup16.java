package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Diffie-Hellman Group 16 key exchange with SHA-512 (RFC 8268).
 *
 * <p>Uses the 4096-bit MODP group from RFC 3526 section 5.
 *
 * @since 1.0.0
 */
public final class DiffieHellmanGroup16 implements KexAlgorithm {

    /** The 4096-bit MODP prime from RFC 3526 section 5. */
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
            "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
            "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
            "F12FFA06D98A0864D87602733EC86A64521F2B18177B200CB" +
            "BE117577A615D6C770988C0BAD946E208E24FA074E5AB3143" +
            "DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7888" +
            "11A71A49F9B48C8B2C05D74ADEECB5BA16BEF905BFBC5BA8" +
            "3DFCE5DCDD8DF0C000C7F88A13EDDC4D09CABC1942F5823" +
            "06551BDCCA42ABDC4FCB5C8F4D2B511C47B3D36AB8D1BDE4" +
            "B7813E0F1E79E5FA2FE28EAEE3682816A3C7C2D11C62F15" +
            "1CCF42A19E9F0F4850D83F8405F1DD85E5ACBAC6D8FB5F63" +
            "4F4D02C2872AE62DAC97B46C5A78631F3D9FA9B56D73E357" +
            "AB75DDC0ACA78CCECF6BE1F849B39BCDFBACEA70F7F5CE09" +
            "4CC3068F96B11ABFE67D6E78EFCE3CABDA5C37EEFFFFFFFFFFFF" +
            "FFFF", 16);

    /** The generator g = 2. */
    public static final BigInteger G = BigInteger.TWO;

    private final SecureRandom random = new SecureRandom();
    private BigInteger privateKey;
    private BigInteger publicKey;

    @Override
    public String name() {
        return "diffie-hellman-group16-sha512";
    }

    @Override
    public String hashAlgorithm() {
        return "SHA-512";
    }

    @Override
    public void init() {
        int bitLength = P.bitLength() - 1;
        do {
            privateKey = new BigInteger(bitLength, random);
        } while (privateKey.compareTo(BigInteger.ONE) <= 0
                || privateKey.compareTo(P.subtract(BigInteger.ONE)) >= 0);
        publicKey = G.modPow(privateKey, P);
    }

    @Override
    public byte[] localPublicValue() {
        return DiffieHellmanGroup14.toMpint(publicKey);
    }

    @Override
    public byte[] computeSharedSecret(byte[] remotePublicValue) {
        BigInteger f = DiffieHellmanGroup14.fromMpint(remotePublicValue);
        if (f.compareTo(BigInteger.ONE) <= 0 || f.compareTo(P.subtract(BigInteger.ONE)) >= 0) {
            throw new IllegalArgumentException("Invalid DH public value");
        }
        BigInteger K = f.modPow(privateKey, P);
        return DiffieHellmanGroup14.toMpint(K);
    }

    @Override
    public byte[] computeExchangeHash(
            String clientVersion, String serverVersion,
            byte[] clientKexInit, byte[] serverKexInit,
            byte[] hostKey, byte[] e, byte[] f, byte[] sharedSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
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
            throw new RuntimeException("SHA-512 not available", ex);
        }
    }

    private static void writeBuf(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }
}
