package ssg.legoflow.http.auth.oauth2;

/**
 * OAuth 2.0 error response (RFC 6749 Section 5.2).
 *
 * @param error            the error code
 * @param errorDescription human-readable description
 * @param errorUri         URI for more information
 * @since 0.1.0
 */
public record OAuth2Error(String error, String errorDescription, String errorUri) {

    /** Invalid request error. */
    public static final String INVALID_REQUEST = "invalid_request";
    /** Invalid client error. */
    public static final String INVALID_CLIENT = "invalid_client";
    /** Invalid grant error. */
    public static final String INVALID_GRANT = "invalid_grant";
    /** Unauthorized client error. */
    public static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    /** Unsupported grant type error. */
    public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    /** Invalid scope error. */
    public static final String INVALID_SCOPE = "invalid_scope";
    /** Access denied error. */
    public static final String ACCESS_DENIED = "access_denied";
    /** Unsupported response type error. */
    public static final String UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type";
    /** Server error. */
    public static final String SERVER_ERROR = "server_error";
    /** Temporarily unavailable error. */
    public static final String TEMPORARILY_UNAVAILABLE = "temporarily_unavailable";

    /**
     * Creates an error with just the code.
     *
     * @param error the error code
     * @since 0.1.0
     */
    public OAuth2Error(String error) {
        this(error, null, null);
    }

    /**
     * Creates an error with code and description.
     *
     * @param error       the error code
     * @param description the description
     * @since 0.1.0
     */
    public OAuth2Error(String error, String description) {
        this(error, description, null);
    }

    /**
     * Serializes to JSON.
     *
     * @return the JSON string
     * @since 0.1.0
     */
    public String toJson() {
        var sb = new StringBuilder("{\"error\":\"").append(error).append("\"");
        if (errorDescription != null) {
            sb.append(",\"error_description\":\"").append(errorDescription).append("\"");
        }
        if (errorUri != null) {
            sb.append(",\"error_uri\":\"").append(errorUri).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses an OAuth2 error from JSON.
     *
     * @param json the JSON string
     * @return the error
     * @since 0.1.0
     */
    public static OAuth2Error fromJson(String json) {
        String error = extractJsonString(json, "error");
        String desc = extractJsonString(json, "error_description");
        String uri = extractJsonString(json, "error_uri");
        return new OAuth2Error(error, desc, uri);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        return end >= 0 ? json.substring(start, end) : null;
    }
}
