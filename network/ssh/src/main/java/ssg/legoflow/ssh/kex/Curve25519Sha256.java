package ssg.legoflow.ssh.kex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.NamedParameterSpec;

import javax.crypto.KeyAgreement;

/**
 * Curve25519 key exchange with SHA-256 (RFC 8731).
 *
 * <p>Uses the X25519 key agreement scheme. The public values are 32-byte
 * Curve25519 points exchanged as opaque byte strings.
 *
 * @since 0.1.0
 */
public final class Curve25519Sha256 implements KexAlgorithm {

    private KeyPair keyPair;

    @Override
    public String name() {
        return "curve25519-sha256";
    }

    @Override
    public String hashAlgorithm() {
        return "SHA-256";
    }

    @Override
    public void init() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            keyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("X25519 not available", e);
        }
    }

    @Override
    public byte[] localPublicValue() {
        // X25519 public key raw bytes
        byte[] encoded = keyPair.getPublic().getEncoded();
        // The raw 32-byte key starts at offset 12 in the X.509 encoding
        if (encoded.length > 32) {
            byte[] raw = new byte[32];
            System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
            return raw;
        }
        return encoded;
    }

    @Override
    public byte[] computeSharedSecret(byte[] remotePublicValue) {
        try {
            KeyFactory kf = KeyFactory.getInstance("X25519");
            // Build an X25519 public key from raw bytes
            java.security.spec.X509EncodedKeySpec x509Spec = buildX25519PublicKeySpec(remotePublicValue);
            PublicKey remotePub = kf.generatePublic(x509Spec);

            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(keyPair.getPrivate());
            ka.doPhase(remotePub, true);
            byte[] secret = ka.generateSecret();

            // Check for all-zeros (rejected per RFC 8731)
            boolean allZero = true;
            for (byte b : secret) {
                if (b != 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                throw new IllegalArgumentException("X25519 shared secret is all zeros");
            }

            BigInteger K = new BigInteger(1, secret);
            return DiffieHellmanGroup14.toMpint(K);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("X25519 computation failed", e);
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
     * Builds an X.509-encoded X25519 public key spec from raw 32-byte key.
     */
    private static java.security.spec.X509EncodedKeySpec buildX25519PublicKeySpec(byte[] rawKey) {
        // X.509 SubjectPublicKeyInfo wrapping for X25519
        // 30 2A 30 05 06 03 2B 65 6E 03 21 00 <32 bytes>
        byte[] prefix = new byte[]{
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x6E, 0x03, 0x21, 0x00
        };
        byte[] encoded = new byte[prefix.length + 32];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(rawKey, 0, encoded, prefix.length, Math.min(rawKey.length, 32));
        return new java.security.spec.X509EncodedKeySpec(encoded);
    }

    private static void writeBuf(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }
}
