package ssg.legoflow.http.auth.oidc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenID Connect UserInfo response containing standard claims about the authenticated user.
 *
 * @since 0.1.0
 */
public class UserInfo {

    private final Map<String, Object> claims;

    /**
     * Creates a UserInfo from claims.
     *
     * @param claims the claims map
     * @since 0.1.0
     */
    public UserInfo(Map<String, Object> claims) {
        this.claims = new LinkedHashMap<>(claims);
    }

    /**
     * Parses UserInfo from a JSON string.
     *
     * @param json the JSON string
     * @return the UserInfo
     * @since 0.1.0
     */
    public static UserInfo fromJson(String json) {
        // Reuse JwtClaims parser for simple flat JSON
        var jwtClaims = ssg.legoflow.http.auth.token.JwtClaims.fromJson(json);
        return new UserInfo(jwtClaims.toMap());
    }

    // Standard claims

    /** Returns the subject. */
    public String getSubject() { return getStringClaim("sub"); }
    /** Returns the name. */
    public String getName() { return getStringClaim("name"); }
    /** Returns the given name. */
    public String getGivenName() { return getStringClaim("given_name"); }
    /** Returns the family name. */
    public String getFamilyName() { return getStringClaim("family_name"); }
    /** Returns the preferred username. */
    public String getPreferredUsername() { return getStringClaim("preferred_username"); }
    /** Returns the email. */
    public String getEmail() { return getStringClaim("email"); }
    /** Returns whether the email is verified. */
    public String getEmailVerified() { return getStringClaim("email_verified"); }
    /** Returns the picture URL. */
    public String getPicture() { return getStringClaim("picture"); }
    /** Returns the locale. */
    public String getLocale() { return getStringClaim("locale"); }
    /** Returns the timezone info. */
    public String getZoneinfo() { return getStringClaim("zoneinfo"); }
    /** Returns the phone number. */
    public String getPhoneNumber() { return getStringClaim("phone_number"); }

    /**
     * Returns a string claim by name.
     *
     * @param name the claim name
     * @return the claim value, or null
     * @since 0.1.0
     */
    public String getStringClaim(String name) {
        Object v = claims.get(name);
        return v != null ? v.toString() : null;
    }

    /**
     * Returns all claims.
     *
     * @return unmodifiable claims map
     * @since 0.1.0
     */
    public Map<String, Object> getClaims() {
        return Collections.unmodifiableMap(claims);
    }

    /**
     * Serializes to JSON.
     *
     * @return the JSON string
     * @since 0.1.0
     */
    public String toJson() {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : claims.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String s) {
                sb.append('"').append(s).append('"');
            } else {
                sb.append(entry.getValue());
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
