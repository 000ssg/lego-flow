package ssg.legoflow.http.auth.oauth2;

import java.time.Instant;

/**
 * OAuth 2.0 token response containing the access token and related fields.
 *
 * @param accessToken  the access token
 * @param tokenType    the token type (typically "Bearer")
 * @param expiresIn    the token lifetime in seconds
 * @param refreshToken the refresh token (may be null)
 * @param scope        the granted scope
 * @param issuedAt     when the token was issued
 * @since 0.1.0
 */
public record OAuth2TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        String scope,
        Instant issuedAt) {

    /**
     * Creates a token response with the current time as issued-at.
     *
     * @param accessToken  the access token
     * @param tokenType    the token type
     * @param expiresIn    the lifetime in seconds
     * @param refreshToken the refresh token
     * @param scope        the scope
     * @since 0.1.0
     */
    public OAuth2TokenResponse(String accessToken, String tokenType, long expiresIn,
                               String refreshToken, String scope) {
        this(accessToken, tokenType, expiresIn, refreshToken, scope, Instant.now());
    }

    /**
     * Checks if the access token has expired.
     *
     * @return true if expired
     * @since 0.1.0
     */
    public boolean isExpired() {
        if (expiresIn <= 0) return false;
        return Instant.now().isAfter(issuedAt.plusSeconds(expiresIn));
    }

    /**
     * Returns the expiration instant.
     *
     * @return the expiration time
     * @since 0.1.0
     */
    public Instant expiresAt() {
        return issuedAt.plusSeconds(expiresIn);
    }

    /**
     * Serializes to a simple JSON string.
     *
     * @return the JSON representation
     * @since 0.1.0
     */
    public String toJson() {
        var sb = new StringBuilder("{");
        sb.append("\"access_token\":\"").append(accessToken).append("\"");
        sb.append(",\"token_type\":\"").append(tokenType).append("\"");
        sb.append(",\"expires_in\":").append(expiresIn);
        if (refreshToken != null) {
            sb.append(",\"refresh_token\":\"").append(refreshToken).append("\"");
        }
        if (scope != null) {
            sb.append(",\"scope\":\"").append(scope).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses a token response from JSON.
     *
     * @param json the JSON string
     * @return the token response
     * @since 0.1.0
     */
    public static OAuth2TokenResponse fromJson(String json) {
        String accessToken = extractJsonString(json, "access_token");
        String tokenType = extractJsonString(json, "token_type");
        String expiresInStr = extractJsonValue(json, "expires_in");
        String refreshToken = extractJsonString(json, "refresh_token");
        String scope = extractJsonString(json, "scope");

        long expiresIn = 0;
        if (expiresInStr != null) {
            try { expiresIn = Long.parseLong(expiresInStr); } catch (NumberFormatException ignored) {}
        }

        return new OAuth2TokenResponse(accessToken, tokenType, expiresIn, refreshToken, scope);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        return end >= 0 ? json.substring(start, end) : null;
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }
}
