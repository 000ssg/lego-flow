package ssg.legoflow.http.auth.oauth2.server;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for OAuth 2.0 authorization codes with expiry.
 * Authorization codes are single-use and expire after a configurable duration.
 *
 * @since 1.0.0
 */
public class AuthorizationCodeStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Duration codeLifetime;
    private final Map<String, AuthorizationCode> codes = new ConcurrentHashMap<>();

    /**
     * Stored authorization code with metadata.
     *
     * @param code            the authorization code
     * @param clientId        the client ID
     * @param redirectUri     the redirect URI
     * @param scopes          the granted scopes
     * @param subject         the authenticated user
     * @param codeChallenge   the PKCE code challenge (null if not using PKCE)
     * @param challengeMethod the PKCE challenge method
     * @param createdAt       when the code was created
     * @since 1.0.0
     */
    public record AuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String subject,
            String codeChallenge,
            String challengeMethod,
            Instant createdAt) {

        /**
         * Checks if this code has expired.
         *
         * @param lifetime the code lifetime
         * @return true if expired
         * @since 1.0.0
         */
        public boolean isExpired(Duration lifetime) {
            return Instant.now().isAfter(createdAt.plus(lifetime));
        }
    }

    /**
     * Creates an authorization code store with a 10-minute code lifetime.
     *
     * @since 1.0.0
     */
    public AuthorizationCodeStore() {
        this(Duration.ofMinutes(10));
    }

    /**
     * Creates an authorization code store with the specified lifetime.
     *
     * @param codeLifetime the code lifetime
     * @since 1.0.0
     */
    public AuthorizationCodeStore(Duration codeLifetime) {
        this.codeLifetime = codeLifetime;
    }

    /**
     * Generates and stores a new authorization code.
     *
     * @param clientId        the client ID
     * @param redirectUri     the redirect URI
     * @param scopes          the granted scopes
     * @param subject         the authenticated user
     * @param codeChallenge   the PKCE code challenge (null if not using PKCE)
     * @param challengeMethod the PKCE challenge method
     * @return the authorization code
     * @since 1.0.0
     */
    public AuthorizationCode generate(String clientId, String redirectUri, Set<String> scopes,
                                       String subject, String codeChallenge, String challengeMethod) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var authCode = new AuthorizationCode(code, clientId, redirectUri,
                scopes != null ? Set.copyOf(scopes) : Set.of(),
                subject, codeChallenge, challengeMethod, Instant.now());
        codes.put(code, authCode);
        return authCode;
    }

    /**
     * Consumes an authorization code (single-use). Returns the code if valid and removes it.
     *
     * @param code the authorization code
     * @return the code if valid and not expired, empty otherwise
     * @since 1.0.0
     */
    public Optional<AuthorizationCode> consume(String code) {
        var authCode = codes.remove(code);
        if (authCode == null) return Optional.empty();
        if (authCode.isExpired(codeLifetime)) return Optional.empty();
        return Optional.of(authCode);
    }

    /**
     * Removes expired codes.
     *
     * @return the number of codes removed
     * @since 1.0.0
     */
    public int cleanExpired() {
        var expired = codes.entrySet().stream()
                .filter(e -> e.getValue().isExpired(codeLifetime))
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(codes::remove);
        return expired.size();
    }

    /**
     * Returns the number of stored codes.
     *
     * @return the code count
     * @since 1.0.0
     */
    public int size() {
        return codes.size();
    }
}
