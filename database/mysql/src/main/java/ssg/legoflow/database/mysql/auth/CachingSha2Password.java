package ssg.legoflow.database.mysql.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * caching_sha2_password authentication plugin.
 *
 * <p>Uses SHA-256 based authentication. The full protocol includes RSA public
 * key exchange for secure password transmission, but this implementation
 * provides the simplified fast-auth path:
 * <pre>
 * SHA256(password) XOR SHA256(SHA256(SHA256(password)) + scramble)
 * </pre>
 *
 * <p>For full auth, the server may send AuthMoreData (0x01 + 0x04) requesting
 * the client to send the password encrypted with the server's RSA public key.
 * This implementation supports the fast-auth path only.
 *
 * @since 0.1.0
 */
public final class CachingSha2Password implements AuthPlugin {

    /** The plugin name. */
    public static final String NAME = "caching_sha2_password";

    /** Fast auth success indicator. */
    public static final byte FAST_AUTH_SUCCESS = 0x03;

    /** Full auth required indicator. */
    public static final byte FULL_AUTH_REQUIRED = 0x04;

    /** Singleton instance. */
    public static final CachingSha2Password INSTANCE = new CachingSha2Password();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public byte[] generateAuthResponse(String password, byte[] scramble) {
        if (password == null || password.isEmpty()) {
            return new byte[0];
        }
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");

            // SHA256(password)
            byte[] hash1 = sha256.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // SHA256(SHA256(password))
            sha256.reset();
            byte[] hash2 = sha256.digest(hash1);

            // SHA256(SHA256(SHA256(password)) + scramble)
            sha256.reset();
            sha256.update(hash2);
            byte[] hash3 = sha256.digest(scramble);

            // XOR
            var result = new byte[hash1.length];
            for (int i = 0; i < hash1.length; i++) {
                result[i] = (byte) (hash1[i] ^ hash3[i]);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean verify(byte[] authResponse, byte[] scramble, byte[] storedHash) {
        if (authResponse.length == 0 && storedHash.length == 0) {
            return true;
        }
        if (authResponse.length == 0 || storedHash.length == 0) {
            return false;
        }
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");

            // SHA256(storedHash + scramble) where storedHash = SHA256(SHA256(password))
            sha256.update(storedHash);
            byte[] hash3 = sha256.digest(scramble);

            // authResponse XOR hash3 should give SHA256(password)
            var hash1Candidate = new byte[authResponse.length];
            for (int i = 0; i < authResponse.length; i++) {
                hash1Candidate[i] = (byte) (authResponse[i] ^ hash3[i]);
            }

            // SHA256(hash1Candidate) should equal storedHash
            sha256.reset();
            byte[] hash2Candidate = sha256.digest(hash1Candidate);

            return Arrays.equals(hash2Candidate, storedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Computes the stored hash (SHA256(SHA256(password))) for a given password.
     *
     * @param password the plain-text password
     * @return the double-SHA256 hash
     */
    public static byte[] computeStoredHash(String password) {
        if (password == null || password.isEmpty()) {
            return new byte[0];
        }
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = sha256.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sha256.reset();
            return sha256.digest(hash1);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
