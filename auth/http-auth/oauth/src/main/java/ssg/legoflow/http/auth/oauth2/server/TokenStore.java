package ssg.legoflow.http.auth.oauth2.server;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage for OAuth 2.0 access and refresh tokens issued by the authorization server.
 *
 * @since 1.0.0
 */
public class TokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, StoredToken> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, StoredToken> refreshTokens = new ConcurrentHashMap<>();
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;

    /**
     * A stored token with metadata.
     *
     * @param token     the token string
     * @param clientId  the client ID
     * @param subject   the token subject (user)
     * @param scopes    the granted scopes
     * @param issuedAt  when the token was issued
     * @param expiresAt when the token expires
     * @since 1.0.0
     */
    public record StoredToken(
            String token,
            String clientId,
            String subject,
            Set<String> scopes,
            Instant issuedAt,
            Instant expiresAt) {

        /**
         * Checks if this token has expired.
         *
         * @return true if expired
         * @since 1.0.0
         */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Creates a token store with default lifetimes (1 hour access, 30 days refresh).
     *
     * @since 1.0.0
     */
    public TokenStore() {
        this(Duration.ofHours(1), Duration.ofDays(30));
    }

    /**
     * Creates a token store with custom lifetimes.
     *
     * @param accessTokenLifetime  the access token lifetime
     * @param refreshTokenLifetime the refresh token lifetime
     * @since 1.0.0
     */
    public TokenStore(Duration accessTokenLifetime, Duration refreshTokenLifetime) {
        this.accessTokenLifetime = accessTokenLifetime;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    /**
     * Issues a new access token.
     *
     * @param clientId the client ID
     * @param subject  the subject
     * @param scopes   the scopes
     * @return the stored token
     * @since 1.0.0
     */
    public StoredToken issueAccessToken(String clientId, String subject, Set<String> scopes) {
        String token = generateToken();
        Instant now = Instant.now();
        var stored = new StoredToken(token, clientId, subject,
                scopes != null ? Set.copyOf(scopes) : Set.of(),
                now, now.plus(accessTokenLifetime));
        accessTokens.put(token, stored);
        return stored;
    }

    /**
     * Issues a new refresh token.
     *
     * @param clientId the client ID
     * @param subject  the subject
     * @param scopes   the scopes
     * @return the stored token
     * @since 1.0.0
     */
    public StoredToken issueRefreshToken(String clientId, String subject, Set<String> scopes) {
        String token = generateToken();
        Instant now = Instant.now();
        var stored = new StoredToken(token, clientId, subject,
                scopes != null ? Set.copyOf(scopes) : Set.of(),
                now, now.plus(refreshTokenLifetime));
        refreshTokens.put(token, stored);
        return stored;
    }

    /**
     * Validates an access token.
     *
     * @param token the token string
     * @return the stored token if valid, empty otherwise
     * @since 1.0.0
     */
    public Optional<StoredToken> validateAccessToken(String token) {
        var stored = accessTokens.get(token);
        if (stored == null || stored.isExpired()) return Optional.empty();
        return Optional.of(stored);
    }

    /**
     * Validates a refresh token.
     *
     * @param token the token string
     * @return the stored token if valid, empty otherwise
     * @since 1.0.0
     */
    public Optional<StoredToken> validateRefreshToken(String token) {
        var stored = refreshTokens.get(token);
        if (stored == null || stored.isExpired()) return Optional.empty();
        return Optional.of(stored);
    }

    /**
     * Revokes an access token.
     *
     * @param token the token to revoke
     * @return true if the token was found and revoked
     * @since 1.0.0
     */
    public boolean revokeAccessToken(String token) {
        return accessTokens.remove(token) != null;
    }

    /**
     * Revokes a refresh token.
     *
     * @param token the token to revoke
     * @return true if the token was found and revoked
     * @since 1.0.0
     */
    public boolean revokeRefreshToken(String token) {
        return refreshTokens.remove(token) != null;
    }

    /**
     * Revokes all tokens for a subject (user).
     *
     * @param subject the subject
     * @since 1.0.0
     */
    public void revokeBySubject(String subject) {
        accessTokens.values().removeIf(t -> subject.equals(t.subject()));
        refreshTokens.values().removeIf(t -> subject.equals(t.subject()));
    }

    /**
     * Removes all expired tokens.
     *
     * @return the number of tokens removed
     * @since 1.0.0
     */
    public int cleanExpired() {
        int count = 0;
        count += accessTokens.values().removeIf(StoredToken::isExpired) ? 1 : 0;
        count += refreshTokens.values().removeIf(StoredToken::isExpired) ? 1 : 0;
        return count;
    }

    /**
     * Returns the access token lifetime.
     *
     * @return the lifetime
     * @since 1.0.0
     */
    public Duration getAccessTokenLifetime() {
        return accessTokenLifetime;
    }

    /**
     * Returns the number of active access tokens.
     *
     * @return the count
     * @since 1.0.0
     */
    public int accessTokenCount() {
        return accessTokens.size();
    }

    /**
     * Returns the number of active refresh tokens.
     *
     * @return the count
     * @since 1.0.0
     */
    public int refreshTokenCount() {
        return refreshTokens.size();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
