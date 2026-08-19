package ssg.legoflow.http.auth.basic;

import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Password store using SHA-256 hashed passwords with per-user salt.
 * Passwords are stored as salt:hash where both are hex-encoded.
 *
 * @since 0.1.0
 */
public class HashedPasswordStore implements BasicUserStore, AuthContext.UserStore {

    private static final Logger LOG = LoggerFactory.getLogger(HashedPasswordStore.class);
    private static final int SALT_LENGTH = 16;

    private final Map<String, HashedUser> users = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private record HashedUser(byte[] salt, byte[] hash, Set<String> roles) {}

    /**
     * Creates an empty hashed password store.
     *
     * @since 0.1.0
     */
    public HashedPasswordStore() {
    }

    /**
     * Adds a user with a plaintext password (hashed on storage).
     *
     * @param username the username
     * @param password the plaintext password
     * @param roles    the user roles
     * @return this store for chaining
     * @since 0.1.0
     */
    public HashedPasswordStore addUser(String username, String password, Set<String> roles) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] hash = hashPassword(password, salt);
        users.put(username, new HashedUser(salt, hash, roles != null ? Set.copyOf(roles) : Set.of()));
        return this;
    }

    /**
     * Adds a user with a plaintext password (no roles).
     *
     * @param username the username
     * @param password the plaintext password
     * @return this store for chaining
     * @since 0.1.0
     */
    public HashedPasswordStore addUser(String username, String password) {
        return addUser(username, password, Set.of());
    }

    /**
     * Adds a user with a pre-hashed password.
     *
     * @param username   the username
     * @param saltHex    the hex-encoded salt
     * @param hashHex    the hex-encoded hash
     * @param roles      the user roles
     * @return this store for chaining
     * @since 0.1.0
     */
    public HashedPasswordStore addHashedUser(String username, String saltHex, String hashHex,
                                             Set<String> roles) {
        users.put(username, new HashedUser(hexToBytes(saltHex), hexToBytes(hashHex),
                roles != null ? Set.copyOf(roles) : Set.of()));
        return this;
    }

    @Override
    public Optional<AuthPrincipal> authenticate(String username, String password) {
        var entry = users.get(username);
        if (entry == null) return Optional.empty();

        byte[] computedHash = hashPassword(password, entry.salt());
        if (MessageDigest.isEqual(computedHash, entry.hash())) {
            return Optional.of(new AuthPrincipal(username, entry.roles(), Map.of()));
        }
        return Optional.empty();
    }

    @Override
    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    @Override
    public Optional<AuthPrincipal> findByUsername(String username) {
        var entry = users.get(username);
        if (entry == null) return Optional.empty();
        return Optional.of(new AuthPrincipal(username, entry.roles(), Map.of()));
    }

    @Override
    public Optional<String> getPassword(String username) {
        // Cannot return plaintext from hashed store
        return Optional.empty();
    }

    /**
     * Hashes a password with the given salt using SHA-256.
     *
     * @param password the plaintext password
     * @param salt     the salt bytes
     * @return the hash bytes
     * @since 0.1.0
     */
    public static byte[] hashPassword(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Converts bytes to a hex string.
     *
     * @param bytes the bytes
     * @return the hex string
     * @since 0.1.0
     */
    public static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Converts a hex string to bytes.
     *
     * @param hex the hex string
     * @return the bytes
     * @since 0.1.0
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Returns the number of users.
     *
     * @return the user count
     * @since 0.1.0
     */
    public int size() {
        return users.size();
    }
}
