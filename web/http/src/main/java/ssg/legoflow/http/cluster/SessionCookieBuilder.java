package ssg.legoflow.http.cluster;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Builds Set-Cookie header values for sticky session affinity.
 *
 * <p>Generates RFC 6265-compliant cookie strings with configurable
 * attributes (path, max-age, secure, httpOnly).
 *
 * @since 0.2.0
 */
public final class SessionCookieBuilder {

    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final String cookieName;
    private String path = "/";
    private Duration maxAge;
    private boolean secure = false;
    private boolean httpOnly = true;
    private String sameSite = "Lax";

    /**
     * Creates a builder with the given cookie name.
     *
     * @param cookieName the cookie name
     */
    public SessionCookieBuilder(String cookieName) {
        this.cookieName = Objects.requireNonNull(cookieName, "cookieName must not be null");
    }

    /**
     * Creates a builder from the affinity config.
     *
     * @param config the session affinity configuration
     */
    public static SessionCookieBuilder fromConfig(SessionAffinityConfig config) {
        var builder = new SessionCookieBuilder(config.cookieName())
                .path(config.path())
                .maxAge(config.maxAge())
                .secure(config.secure())
                .httpOnly(config.httpOnly());
        return builder;
    }

    /**
     * Sets the cookie path.
     */
    public SessionCookieBuilder path(String path) {
        this.path = Objects.requireNonNull(path);
        return this;
    }

    /**
     * Sets the cookie max-age in seconds.
     *
     * @param seconds the max-age in seconds
     */
    public SessionCookieBuilder maxAgeSeconds(long seconds) {
        this.maxAge = Duration.ofSeconds(seconds);
        return this;
    }

    /**
     * Sets the cookie max-age as a Duration.
     */
    public SessionCookieBuilder maxAge(Duration maxAge) {
        this.maxAge = Objects.requireNonNull(maxAge);
        return this;
    }

    /**
     * Sets the Secure flag.
     */
    public SessionCookieBuilder secure(boolean secure) {
        this.secure = secure;
        return this;
    }

    /**
     * Sets the HttpOnly flag.
     */
    public SessionCookieBuilder httpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    /**
     * Sets the SameSite attribute.
     *
     * @param sameSite one of "Strict", "Lax", or "None"
     */
    public SessionCookieBuilder sameSite(String sameSite) {
        this.sameSite = Objects.requireNonNull(sameSite);
        return this;
    }

    /**
     * Builds the Set-Cookie header value for the given node ID.
     *
     * <p>The cookie value is the node ID (URL-safe string).
     *
     * @param nodeId the target node identifier
     * @return the Set-Cookie header value
     */
    public String build(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(cookieName).append("=").append(nodeId);

        if (maxAge != null) {
            if (maxAge.isNegative() || maxAge.isZero()) {
                // Session cookie — no Max-Age, no Expires
                sb.append("; Path=").append(path);
            } else {
                long seconds = maxAge.getSeconds();
                sb.append("; Max-Age=").append(seconds);
                ZonedDateTime expires = ZonedDateTime.now(ZoneOffset.UTC)
                        .plusSeconds(seconds);
                sb.append("; Expires=").append(HTTP_DATE.format(expires));
                sb.append("; Path=").append(path);
            }
        } else {
            sb.append("; Path=").append(path);
        }

        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        sb.append("; SameSite=").append(sameSite);

        return sb.toString();
    }
}
