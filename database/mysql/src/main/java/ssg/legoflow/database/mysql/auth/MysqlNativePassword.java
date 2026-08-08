package ssg.legoflow.database.mysql.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * mysql_native_password authentication plugin.
 *
 * <p>Uses SHA1-based authentication:
 * <pre>
 * SHA1(password) XOR SHA1(scramble + SHA1(SHA1(password)))
 * </pre>
 *
 * <p>The stored hash is SHA1(SHA1(password)), which is what MySQL stores
 * in the user table (prefixed with '*' in hex form).
 *
 * @since 0.1.0
 */
public final class MysqlNativePassword implements AuthPlugin {

    /** The plugin name. */
    public static final String NAME = "mysql_native_password";

    /** Singleton instance. */
    public static final MysqlNativePassword INSTANCE = new MysqlNativePassword();

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
            var sha1 = MessageDigest.getInstance("SHA-1");

            // SHA1(password)
            byte[] hash1 = sha1.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // SHA1(SHA1(password))
            sha1.reset();
            byte[] hash2 = sha1.digest(hash1);

            // SHA1(scramble + SHA1(SHA1(password)))
            sha1.reset();
            sha1.update(scramble);
            byte[] hash3 = sha1.digest(hash2);

            // XOR SHA1(password) with hash3
            var result = new byte[hash1.length];
            for (int i = 0; i < hash1.length; i++) {
                result[i] = (byte) (hash1[i] ^ hash3[i]);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    @Override
    public boolean verify(byte[] authResponse, byte[] scramble, byte[] storedHash) {
        if (authResponse.length == 0 && storedHash.length == 0) {
            return true; // empty password
        }
        if (authResponse.length == 0 || storedHash.length == 0) {
            return false;
        }
        try {
            var sha1 = MessageDigest.getInstance("SHA-1");

            // SHA1(scramble + storedHash) where storedHash = SHA1(SHA1(password))
            sha1.update(scramble);
            byte[] hash3 = sha1.digest(storedHash);

            // authResponse XOR hash3 should give us SHA1(password)
            var hash1Candidate = new byte[authResponse.length];
            for (int i = 0; i < authResponse.length; i++) {
                hash1Candidate[i] = (byte) (authResponse[i] ^ hash3[i]);
            }

            // SHA1(hash1Candidate) should equal storedHash
            sha1.reset();
            byte[] hash2Candidate = sha1.digest(hash1Candidate);

            return Arrays.equals(hash2Candidate, storedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    /**
     * Computes the stored hash (SHA1(SHA1(password))) for a given password.
     *
     * <p>This is the value stored in the MySQL user table.
     *
     * @param password the plain-text password
     * @return the double-SHA1 hash
     */
    public static byte[] computeStoredHash(String password) {
        if (password == null || password.isEmpty()) {
            return new byte[0];
        }
        try {
            var sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash1 = sha1.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sha1.reset();
            return sha1.digest(hash1);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }
}
