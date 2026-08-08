package ssg.legoflow.http.auth;

import java.util.Objects;

/**
 * Result of an authentication attempt. Can be one of SUCCESS (with principal),
 * FAILURE (with reason), or CHALLENGE (scheme should issue a challenge).
 *
 * @since 0.1.0
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.Failure, AuthResult.Challenge {

    /**
     * Successful authentication with an authenticated principal.
     *
     * @param principal the authenticated principal
     * @since 0.1.0
     */
    record Success(AuthPrincipal principal) implements AuthResult {
        public Success {
            Objects.requireNonNull(principal, "principal must not be null");
        }
    }

    /**
     * Failed authentication with a reason message.
     *
     * @param reason the failure reason
     * @since 0.1.0
     */
    record Failure(String reason) implements AuthResult {
        public Failure {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * Authentication challenge — the scheme should issue a WWW-Authenticate challenge.
     *
     * @param schemeName the scheme that should issue the challenge
     * @since 0.1.0
     */
    record Challenge(String schemeName) implements AuthResult {
        public Challenge {
            Objects.requireNonNull(schemeName, "schemeName must not be null");
        }
    }

    /**
     * Creates a successful result.
     *
     * @param principal the authenticated principal
     * @return the success result
     * @since 0.1.0
     */
    static AuthResult success(AuthPrincipal principal) {
        return new Success(principal);
    }

    /**
     * Creates a failure result.
     *
     * @param reason the failure reason
     * @return the failure result
     * @since 0.1.0
     */
    static AuthResult failure(String reason) {
        return new Failure(reason);
    }

    /**
     * Creates a challenge result.
     *
     * @param schemeName the scheme name
     * @return the challenge result
     * @since 0.1.0
     */
    static AuthResult challenge(String schemeName) {
        return new Challenge(schemeName);
    }
}
