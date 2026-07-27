package ssg.legoflow.http.auth.session;

import java.util.Objects;

/**
 * Configuration for session cookies including name, path, security attributes,
 * and SameSite policy.
 *
 * @since 1.0.0
 */
public class SessionCookie {

    /**
     * SameSite cookie attribute values.
     *
     * @since 1.0.0
     */
    public enum SameSite {
        STRICT("Strict"),
        LAX("Lax"),
        NONE("None");

        private final String value;

        SameSite(String value) {
            this.value = value;
        }

        /**
         * Returns the cookie attribute value.
         *
         * @return the value string
         * @since 1.0.0
         */
        public String value() {
            return value;
        }
    }

    private final String name;
    private final String path;
    private final String domain;
    private final boolean secure;
    private final boolean httpOnly;
    private final SameSite sameSite;
    private final int maxAge;

    /**
     * Creates a session cookie configuration.
     *
     * @param name     the cookie name
     * @param path     the cookie path
     * @param domain   the cookie domain (null for default)
     * @param secure   whether the cookie requires HTTPS
     * @param httpOnly whether the cookie is HTTP-only (not accessible from JavaScript)
     * @param sameSite the SameSite policy
     * @param maxAge   the max age in seconds (-1 for session cookie)
     * @since 1.0.0
     */
    public SessionCookie(String name, String path, String domain, boolean secure,
                         boolean httpOnly, SameSite sameSite, int maxAge) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.path = path != null ? path : "/";
        this.domain = domain;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.sameSite = sameSite != null ? sameSite : SameSite.LAX;
        this.maxAge = maxAge;
    }

    /**
     * Creates a default session cookie configuration with sensible defaults.
     *
     * @return the default session cookie config
     * @since 1.0.0
     */
    public static SessionCookie defaults() {
        return new SessionCookie("LFSESSION", "/", null, true, true, SameSite.LAX, -1);
    }

    /**
     * Builds the Set-Cookie header value for this session cookie.
     *
     * @param sessionId the session ID value
     * @return the Set-Cookie header value
     * @since 1.0.0
     */
    public String buildSetCookieHeader(String sessionId) {
        var sb = new StringBuilder();
        sb.append(name).append('=').append(sessionId);
        sb.append("; Path=").append(path);
        if (domain != null) {
            sb.append("; Domain=").append(domain);
        }
        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        sb.append("; SameSite=").append(sameSite.value());
        return sb.toString();
    }

    /**
     * Builds a Set-Cookie header that expires (deletes) this cookie.
     *
     * @return the Set-Cookie header value for deletion
     * @since 1.0.0
     */
    public String buildDeleteCookieHeader() {
        return name + "=; Path=" + path + "; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT";
    }

    /**
     * Extracts the session ID from a Cookie header value.
     *
     * @param cookieHeader the Cookie header value
     * @return the session ID, or null if not found
     * @since 1.0.0
     */
    public String extractSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        String prefix = name + "=";
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }

    // Getters

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getDomain() { return domain; }
    public boolean isSecure() { return secure; }
    public boolean isHttpOnly() { return httpOnly; }
    public SameSite getSameSite() { return sameSite; }
    public int getMaxAge() { return maxAge; }
}
