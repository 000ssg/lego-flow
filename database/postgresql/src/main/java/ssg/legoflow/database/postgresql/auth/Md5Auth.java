package ssg.legoflow.database.postgresql.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MD5 password authentication.
 *
 * <p>The server sends a 4-byte random salt. The client computes:
 * {@code "md5" + md5(md5(password + username) + salt)}
 *
 * @since 1.0.0
 */
public final class Md5Auth implements PgAuthenticator {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a new MD5 authenticator.
     */
    public Md5Auth() {}

    /**
     * Registers a user with a password.
     *
     * @param username the username
     * @param password the password
     * @return this authenticator for chaining
     */
    public Md5Auth addUser(String username, String password) {
        credentials.put(username, password);
        return this;
    }

    @Override
    public String method() {
        return "md5";
    }

    @Override
    public boolean authenticate(String username, String password) {
        return credentials.containsKey(username)
                && credentials.get(username).equals(password);
    }

    /**
     * Generates a random 4-byte salt.
     *
     * @return the salt bytes
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[4];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Computes the MD5-hashed password as sent by the client.
     *
     * @param password the cleartext password
     * @param username the username
     * @param salt     the 4-byte salt
     * @return the MD5 hash string including "md5" prefix
     */
    public static String computeMd5(String password, String username, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Phase 1: md5(password + username)
            md.update(password.getBytes(StandardCharsets.UTF_8));
            md.update(username.getBytes(StandardCharsets.UTF_8));
            String phase1 = bytesToHex(md.digest());

            // Phase 2: md5(phase1 + salt)
            md.reset();
            md.update(phase1.getBytes(StandardCharsets.UTF_8));
            md.update(salt);
            return "md5" + bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Validates an MD5-hashed password against stored credentials.
     *
     * @param username     the username
     * @param md5Password  the MD5 password from client (prefixed with "md5")
     * @param salt         the salt that was sent to the client
     * @return true if the password matches
     */
    public boolean validateMd5(String username, String md5Password, byte[] salt) {
        String storedPassword = credentials.get(username);
        if (storedPassword == null) return false;
        String expected = computeMd5(storedPassword, username, salt);
        return expected.equals(md5Password);
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
