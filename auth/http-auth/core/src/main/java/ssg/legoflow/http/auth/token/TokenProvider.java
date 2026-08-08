package ssg.legoflow.http.auth.token;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for token generation and validation. Implementations handle specific
 * token formats (JWT, opaque tokens, etc.).
 *
 * @since 0.1.0
 */
public interface TokenProvider {

    /**
     * Generates a token for the given subject with claims.
     *
     * @param subject the token subject (typically a user ID or username)
     * @param claims  additional claims to include in the token
     * @return the generated token string
     * @since 0.1.0
     */
    String generateToken(String subject, Map<String, Object> claims);

    /**
     * Generates a token for the given subject with default claims.
     *
     * @param subject the token subject
     * @return the generated token string
     * @since 0.1.0
     */
    default String generateToken(String subject) {
        return generateToken(subject, Map.of());
    }

    /**
     * Validates a token and returns the claims if valid.
     *
     * @param token the token to validate
     * @return the claims if the token is valid, empty otherwise
     * @since 0.1.0
     */
    Optional<Map<String, Object>> validateToken(String token);

    /**
     * Extracts the subject from a token without full validation.
     *
     * @param token the token
     * @return the subject, or empty if the token is malformed
     * @since 0.1.0
     */
    Optional<String> getSubject(String token);

    /**
     * Checks if a token has expired.
     *
     * @param token the token
     * @return true if expired
     * @since 0.1.0
     */
    boolean isExpired(String token);
}
