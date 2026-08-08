package ssg.legoflow.http.auth.oidc;

import ssg.legoflow.http.auth.token.JwtClaims;
import ssg.legoflow.http.auth.token.JwtTokenProvider;

import java.util.Map;
import java.util.Optional;

/**
 * OpenID Connect ID Token with standard claims.
 *
 * @since 0.1.0
 */
public class IdToken {

    private final String rawToken;
    private final JwtClaims claims;

    /**
     * Creates an ID token from a raw JWT string.
     *
     * @param rawToken the raw JWT token
     * @param claims   the parsed claims
     * @since 0.1.0
     */
    public IdToken(String rawToken, JwtClaims claims) {
        this.rawToken = rawToken;
        this.claims = claims;
    }

    /**
     * Parses an ID token from a raw JWT string (without verification).
     *
     * @param rawToken the raw JWT token
     * @return the ID token, or empty if malformed
     * @since 0.1.0
     */
    public static Optional<IdToken> parse(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String[] parts = rawToken.split("\\.");
        if (parts.length != 3) return Optional.empty();
        try {
            String payloadJson = new String(JwtTokenProvider.base64UrlDecode(parts[1]));
            JwtClaims claims = JwtClaims.fromJson(payloadJson);
            return Optional.of(new IdToken(rawToken, claims));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates the ID token using a JWT provider.
     *
     * @param provider the JWT token provider
     * @return the validated claims, or empty if invalid
     * @since 0.1.0
     */
    public Optional<Map<String, Object>> validate(JwtTokenProvider provider) {
        return provider.validateToken(rawToken);
    }

    // Standard OIDC claims

    /** Returns the subject (user ID). */
    public String getSubject() { return claims.getSubject(); }
    /** Returns the issuer. */
    public String getIssuer() { return claims.getIssuer(); }
    /** Returns the audience. */
    public String getAudience() { return claims.getAudience(); }
    /** Returns the expiration time. */
    public Long getExpiresAt() { return claims.getExpiresAt(); }
    /** Returns the issued-at time. */
    public Long getIssuedAt() { return claims.getIssuedAt(); }
    /** Returns the nonce. */
    public String getNonce() { return claims.getStringClaim("nonce"); }
    /** Returns the authentication time. */
    public Long getAuthTime() { return claims.getLongClaim("auth_time"); }
    /** Returns the name. */
    public String getName() { return claims.getStringClaim("name"); }
    /** Returns the email. */
    public String getEmail() { return claims.getStringClaim("email"); }
    /** Returns whether email is verified. */
    public String getEmailVerified() { return claims.getStringClaim("email_verified"); }
    /** Returns the picture URL. */
    public String getPicture() { return claims.getStringClaim("picture"); }

    /** Returns the raw token. */
    public String getRawToken() { return rawToken; }
    /** Returns the claims. */
    public JwtClaims getClaims() { return claims; }
}
