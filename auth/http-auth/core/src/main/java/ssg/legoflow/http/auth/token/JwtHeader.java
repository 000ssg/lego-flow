package ssg.legoflow.http.auth.token;

import java.util.Objects;

/**
 * JWT header containing algorithm and type information.
 *
 * @param alg the signing algorithm (e.g., "HS256", "RS256")
 * @param typ the token type (typically "JWT")
 * @since 0.1.0
 */
public record JwtHeader(String alg, String typ) {

    /**
     * Creates a JWT header.
     *
     * @param alg the signing algorithm
     * @param typ the token type
     * @since 0.1.0
     */
    public JwtHeader {
        Objects.requireNonNull(alg, "alg must not be null");
        if (typ == null) typ = "JWT";
    }

    /**
     * Creates a JWT header with the default type "JWT".
     *
     * @param alg the signing algorithm
     * @return the header
     * @since 0.1.0
     */
    public static JwtHeader of(String alg) {
        return new JwtHeader(alg, "JWT");
    }

    /**
     * Serializes this header to a JSON string.
     *
     * @return the JSON representation
     * @since 0.1.0
     */
    public String toJson() {
        return "{\"alg\":\"" + alg + "\",\"typ\":\"" + typ + "\"}";
    }

    /**
     * Parses a JWT header from a JSON string.
     *
     * @param json the JSON string
     * @return the parsed header
     * @since 0.1.0
     */
    public static JwtHeader fromJson(String json) {
        String alg = extractJsonString(json, "alg");
        String typ = extractJsonString(json, "typ");
        return new JwtHeader(alg != null ? alg : "none", typ);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
