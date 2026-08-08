package ssg.legoflow.messaging.kafka.auth;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores user credentials for SASL authentication.
 *
 * <p>Supports both PLAIN (cleartext password validation) and SCRAM-SHA-256
 * (derived key validation) credential types.
 *
 * @since 0.1.0
 */
public final class CredentialStore {

    /**
     * Pre-computed SCRAM credential for a user.
     *
     * @param salt      the random salt
     * @param storedKey the StoredKey = H(ClientKey)
     * @param serverKey the ServerKey = HMAC(SaltedPassword, "Server Key")
     * @param iterations the PBKDF2 iteration count
     */
    public record ScramCredential(byte[] salt, byte[] storedKey, byte[] serverKey, int iterations) {}

    private static final int DEFAULT_SALT_LENGTH = 16;

    private final ConcurrentHashMap<String, String> plainCredentials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScramCredential> scramCredentials = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Adds a user with a plaintext password for PLAIN authentication.
     *
     * @param username the username
     * @param password the password
     */
    public void addPlainUser(String username, String password) {
        plainCredentials.put(username, password);
    }

    /**
     * Validates a PLAIN username/password pair.
     *
     * @param username the username
     * @param password the password
     * @return true if credentials are valid
     */
    public boolean validatePlain(String username, String password) {
        String stored = plainCredentials.get(username);
        return stored != null && stored.equals(password);
    }

    /**
     * Adds a user with SCRAM-SHA-256 derived credentials.
     *
     * <p>Generates a random salt and derives the StoredKey and ServerKey using PBKDF2.
     *
     * @param username   the username
     * @param password   the password
     * @param iterations the PBKDF2 iteration count (minimum 4096)
     */
    public void addScramUser(String username, String password, int iterations) {
        byte[] salt = new byte[DEFAULT_SALT_LENGTH];
        secureRandom.nextBytes(salt);
        addScramUserWithSalt(username, password, salt, iterations);
    }

    /**
     * Adds a user with SCRAM-SHA-256 credentials using a specific salt.
     *
     * @param username   the username
     * @param password   the password
     * @param salt       the salt bytes
     * @param iterations the iteration count
     */
    public void addScramUserWithSalt(String username, String password, byte[] salt, int iterations) {
        try {
            byte[] saltedPassword = hi(password, salt, iterations);
            byte[] clientKey = hmacSha256(saltedPassword, "Client Key".getBytes());
            byte[] storedKey = sha256(clientKey);
            byte[] serverKey = hmacSha256(saltedPassword, "Server Key".getBytes());
            scramCredentials.put(username, new ScramCredential(salt, storedKey, serverKey, iterations));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive SCRAM credentials", e);
        }
    }

    /**
     * Returns the SCRAM credential for a user.
     *
     * @param username the username
     * @return the credential, or null if not found
     */
    public ScramCredential getScramCredential(String username) {
        return scramCredentials.get(username);
    }

    /**
     * Returns whether any PLAIN credentials have been configured.
     *
     * @return true if PLAIN users exist
     */
    public boolean hasPlainUsers() {
        return !plainCredentials.isEmpty();
    }

    /**
     * Returns whether any SCRAM credentials have been configured.
     *
     * @return true if SCRAM users exist
     */
    public boolean hasScramUsers() {
        return !scramCredentials.isEmpty();
    }

    // --- Crypto helpers ---

    /**
     * PBKDF2-HMAC-SHA-256 key derivation (Hi function in SCRAM spec).
     */
    static byte[] hi(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * HMAC-SHA-256.
     */
    static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /**
     * SHA-256 hash.
     */
    static byte[] sha256(byte[] data) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(data);
    }
}
