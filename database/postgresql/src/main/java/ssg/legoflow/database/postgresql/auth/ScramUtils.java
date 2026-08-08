package ssg.legoflow.database.postgresql.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic utilities for SCRAM-SHA-256 authentication (RFC 5802).
 *
 * <p>Provides HMAC-SHA-256, SHA-256 hash, PBKDF2 (Hi function), XOR,
 * and nonce generation.
 *
 * @since 0.1.0
 */
public final class ScramUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ScramUtils() {}

    /**
     * Computes HMAC-SHA-256.
     *
     * @param key  the HMAC key
     * @param data the data to authenticate
     * @return the HMAC result
     */
    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 not available", e);
        }
    }

    /**
     * Computes SHA-256 hash.
     *
     * @param data the data to hash
     * @return the hash result
     */
    public static byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Computes Hi (PBKDF2-HMAC-SHA-256) as defined in RFC 5802.
     *
     * @param password   the password bytes
     * @param salt       the salt bytes
     * @param iterations the iteration count
     * @return the derived key
     */
    public static byte[] hi(byte[] password, byte[] salt, int iterations) {
        // U1 = HMAC(password, salt || INT(1))
        byte[] saltPlus = new byte[salt.length + 4];
        System.arraycopy(salt, 0, saltPlus, 0, salt.length);
        saltPlus[saltPlus.length - 1] = 1; // INT(1) in big-endian

        byte[] u = hmac(password, saltPlus);
        byte[] result = u.clone();

        for (int i = 2; i <= iterations; i++) {
            u = hmac(password, u);
            xorInPlace(result, u);
        }
        return result;
    }

    /**
     * XORs two byte arrays of equal length.
     *
     * @param a first array
     * @param b second array
     * @return the XOR result
     */
    public static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * XORs b into a in place.
     *
     * @param a destination array (modified in place)
     * @param b source array
     */
    private static void xorInPlace(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] ^= b[i];
        }
    }

    /**
     * Generates a random nonce string (24 bytes, base64-encoded).
     *
     * @return the nonce string
     */
    public static String generateNonce() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 encodes bytes.
     *
     * @param data the bytes
     * @return the base64 string
     */
    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 decodes a string.
     *
     * @param data the base64 string
     * @return the decoded bytes
     */
    public static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    /**
     * Converts a string to UTF-8 bytes.
     *
     * @param s the string
     * @return the UTF-8 bytes
     */
    public static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
