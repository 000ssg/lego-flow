package ssg.legoflow.ssh.auth;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authentication context that validates credentials.
 *
 * <p>Maintains per-user authentication state and supports multiple
 * authentication methods.
 *
 * @since 1.0.0
 */
public final class AuthContext {

    /**
     * Functional interface for password validation.
     */
    @FunctionalInterface
    public interface PasswordValidator {
        boolean validate(String username, String password);
    }

    /**
     * Functional interface for public key validation.
     */
    @FunctionalInterface
    public interface PublicKeyValidator {
        boolean validate(String username, byte[] publicKeyBlob);
    }

    private PasswordValidator passwordValidator;
    private PublicKeyValidator publicKeyValidator;
    private final Set<String> allowedMethods;
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private int maxFailures = 6;
    private String banner;

    /**
     * Creates a new authentication context.
     */
    public AuthContext() {
        this.allowedMethods = new LinkedHashSet<>(List.of("publickey", "password"));
    }

    /**
     * Sets the password validator.
     *
     * @param validator the password validation function
     * @return this context for chaining
     */
    public AuthContext setPasswordValidator(PasswordValidator validator) {
        this.passwordValidator = validator;
        return this;
    }

    /**
     * Sets the public key validator.
     *
     * @param validator the public key validation function
     * @return this context for chaining
     */
    public AuthContext setPublicKeyValidator(PublicKeyValidator validator) {
        this.publicKeyValidator = validator;
        return this;
    }

    /**
     * Sets the authentication banner.
     *
     * @param banner the banner message
     * @return this context for chaining
     */
    public AuthContext setBanner(String banner) {
        this.banner = banner;
        return this;
    }

    /**
     * Sets the maximum number of authentication failures before disconnect.
     *
     * @param max the maximum failures
     * @return this context for chaining
     */
    public AuthContext setMaxFailures(int max) {
        this.maxFailures = max;
        return this;
    }

    /**
     * Authenticates with password.
     *
     * @param username the username
     * @param password the password
     * @return the authentication result
     */
    public AuthResult authenticatePassword(String username, String password) {
        if (passwordValidator == null || !allowedMethods.contains("password")) {
            return new AuthResult.Failure(List.copyOf(allowedMethods), false);
        }
        if (passwordValidator.validate(username, password)) {
            failureCount.remove(username);
            return new AuthResult.Success();
        }
        return recordFailure(username);
    }

    /**
     * Authenticates with public key.
     *
     * @param username      the username
     * @param publicKeyBlob the public key blob
     * @return the authentication result
     */
    public AuthResult authenticatePublicKey(String username, byte[] publicKeyBlob) {
        if (publicKeyValidator == null || !allowedMethods.contains("publickey")) {
            return new AuthResult.Failure(List.copyOf(allowedMethods), false);
        }
        if (publicKeyValidator.validate(username, publicKeyBlob)) {
            failureCount.remove(username);
            return new AuthResult.Success();
        }
        return recordFailure(username);
    }

    /**
     * Returns the authentication banner.
     *
     * @return the banner, or null
     */
    public String banner() { return banner; }

    /**
     * Returns allowed authentication methods.
     *
     * @return set of method names
     */
    public Set<String> allowedMethods() { return Collections.unmodifiableSet(allowedMethods); }

    /**
     * Returns the maximum failures allowed.
     *
     * @return max failures
     */
    public int maxFailures() { return maxFailures; }

    /**
     * Returns failure count for a user.
     *
     * @param username the username
     * @return number of failures
     */
    public int failureCount(String username) {
        return failureCount.getOrDefault(username, 0);
    }

    /**
     * Checks if a user has exceeded the maximum failure count.
     *
     * @param username the username
     * @return true if exceeded
     */
    public boolean isLocked(String username) {
        return failureCount(username) >= maxFailures;
    }

    private AuthResult recordFailure(String username) {
        int count = failureCount.merge(username, 1, Integer::sum);
        return new AuthResult.Failure(List.copyOf(allowedMethods), false);
    }
}
