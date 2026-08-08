package ssg.legoflow.http.auth.token;

import java.time.Instant;
import java.util.*;

/**
 * Standard JWT claims (RFC 7519). Supports standard registered claims (iss, sub, aud,
 * exp, nbf, iat, jti) and arbitrary custom claims.
 *
 * @since 0.1.0
 */
public class JwtClaims {

    private final Map<String, Object> claims;

    /**
     * Creates JWT claims from a map.
     *
     * @param claims the claims map
     * @since 0.1.0
     */
    public JwtClaims(Map<String, Object> claims) {
        this.claims = new LinkedHashMap<>(claims);
    }

    /**
     * Creates empty JWT claims.
     *
     * @since 0.1.0
     */
    public JwtClaims() {
        this.claims = new LinkedHashMap<>();
    }

    // Standard registered claim setters

    /** Sets the issuer claim. */
    public JwtClaims issuer(String iss) { claims.put("iss", iss); return this; }
    /** Sets the subject claim. */
    public JwtClaims subject(String sub) { claims.put("sub", sub); return this; }
    /** Sets the audience claim. */
    public JwtClaims audience(String aud) { claims.put("aud", aud); return this; }
    /** Sets the expiration time as epoch seconds. */
    public JwtClaims expiresAt(long exp) { claims.put("exp", exp); return this; }
    /** Sets the expiration time. */
    public JwtClaims expiresAt(Instant exp) { claims.put("exp", exp.getEpochSecond()); return this; }
    /** Sets the not-before time as epoch seconds. */
    public JwtClaims notBefore(long nbf) { claims.put("nbf", nbf); return this; }
    /** Sets the not-before time. */
    public JwtClaims notBefore(Instant nbf) { claims.put("nbf", nbf.getEpochSecond()); return this; }
    /** Sets the issued-at time as epoch seconds. */
    public JwtClaims issuedAt(long iat) { claims.put("iat", iat); return this; }
    /** Sets the issued-at time. */
    public JwtClaims issuedAt(Instant iat) { claims.put("iat", iat.getEpochSecond()); return this; }
    /** Sets the JWT ID. */
    public JwtClaims jwtId(String jti) { claims.put("jti", jti); return this; }

    /**
     * Sets a custom claim.
     *
     * @param name  the claim name
     * @param value the claim value
     * @return this for chaining
     * @since 0.1.0
     */
    public JwtClaims claim(String name, Object value) {
        claims.put(name, value);
        return this;
    }

    // Standard registered claim getters

    /** Returns the issuer. */
    public String getIssuer() { return getStringClaim("iss"); }
    /** Returns the subject. */
    public String getSubject() { return getStringClaim("sub"); }
    /** Returns the audience. */
    public String getAudience() { return getStringClaim("aud"); }
    /** Returns the expiration time as epoch seconds. */
    public Long getExpiresAt() { return getLongClaim("exp"); }
    /** Returns the not-before time as epoch seconds. */
    public Long getNotBefore() { return getLongClaim("nbf"); }
    /** Returns the issued-at time as epoch seconds. */
    public Long getIssuedAt() { return getLongClaim("iat"); }
    /** Returns the JWT ID. */
    public String getJwtId() { return getStringClaim("jti"); }

    /**
     * Returns a claim value as a string.
     *
     * @param name the claim name
     * @return the value, or null
     * @since 0.1.0
     */
    public String getStringClaim(String name) {
        Object v = claims.get(name);
        return v != null ? v.toString() : null;
    }

    /**
     * Returns a claim value as a long.
     *
     * @param name the claim name
     * @return the value, or null
     * @since 0.1.0
     */
    public Long getLongClaim(String name) {
        Object v = claims.get(name);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * Returns all claims as a map.
     *
     * @return unmodifiable claims map
     * @since 0.1.0
     */
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(claims);
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if expired
     * @since 0.1.0
     */
    public boolean isExpired() {
        Long exp = getExpiresAt();
        if (exp == null) return false;
        return Instant.now().getEpochSecond() > exp;
    }

    /**
     * Checks if the token is not yet valid (nbf is in the future).
     *
     * @return true if not yet valid
     * @since 0.1.0
     */
    public boolean isNotYetValid() {
        Long nbf = getNotBefore();
        if (nbf == null) return false;
        return Instant.now().getEpochSecond() < nbf;
    }

    /**
     * Serializes claims to a JSON string.
     *
     * @return the JSON representation
     * @since 0.1.0
     */
    public String toJson() {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : claims.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses claims from a JSON string.
     *
     * @param json the JSON string
     * @return the parsed claims
     * @since 0.1.0
     */
    public static JwtClaims fromJson(String json) {
        var claims = new JwtClaims();
        if (json == null || json.length() < 2) return claims;

        // Simple JSON parser for flat key-value maps
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        int i = 0;
        while (i < content.length()) {
            // Skip whitespace and commas
            while (i < content.length() && (content.charAt(i) == ' ' || content.charAt(i) == ',' || content.charAt(i) == '\n' || content.charAt(i) == '\r' || content.charAt(i) == '\t')) i++;
            if (i >= content.length()) break;

            // Parse key
            if (content.charAt(i) != '"') break;
            i++;
            int keyEnd = content.indexOf('"', i);
            if (keyEnd < 0) break;
            String key = content.substring(i, keyEnd);
            i = keyEnd + 1;

            // Skip colon
            while (i < content.length() && (content.charAt(i) == ' ' || content.charAt(i) == ':')) i++;
            if (i >= content.length()) break;

            // Parse value
            if (content.charAt(i) == '"') {
                // String value
                i++;
                int valueEnd = findUnescapedQuote(content, i);
                if (valueEnd < 0) break;
                claims.claim(key, unescapeJson(content.substring(i, valueEnd)));
                i = valueEnd + 1;
            } else if (content.charAt(i) == 't' || content.charAt(i) == 'f') {
                // Boolean
                if (content.startsWith("true", i)) {
                    claims.claim(key, true);
                    i += 4;
                } else {
                    claims.claim(key, false);
                    i += 5;
                }
            } else if (content.charAt(i) == 'n') {
                i += 4; // null
            } else {
                // Number
                int numEnd = i;
                while (numEnd < content.length() && (Character.isDigit(content.charAt(numEnd)) || content.charAt(numEnd) == '-' || content.charAt(numEnd) == '.')) numEnd++;
                String numStr = content.substring(i, numEnd);
                if (numStr.contains(".")) {
                    claims.claim(key, Double.parseDouble(numStr));
                } else {
                    claims.claim(key, Long.parseLong(numStr));
                }
                i = numEnd;
            }
        }
        return claims;
    }

    private static int findUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escapeJson(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
