package ssg.legoflow.http.cluster;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for HTTP sticky sessions.
 *
 * <p>Controls cookie naming, TTL, and fallback behavior when
 * the target node is unavailable.
 *
 * @param cookieName    name of the session affinity cookie
 * @param maxAge        cookie lifetime; negative means session cookie
 * @param secure        whether the cookie is restricted to HTTPS
 * @param httpOnly      whether the cookie is inaccessible to JavaScript
 * @param path          cookie path scope
 * @param fallback      strategy when the sticky node is down
 * @since 0.2.0
 */
public record SessionAffinityConfig(
        String cookieName,
        Duration maxAge,
        boolean secure,
        boolean httpOnly,
        String path,
        FallbackStrategy fallback
) {

    /**
     * Fallback strategy when the target sticky node is unavailable.
     */
    public enum FallbackStrategy {
        /** Rehash to an available node and set a new cookie. */
        REHASH,
        /** Redirect the client to the new node (307 Temporary Redirect). */
        REDIRECT,
        /** Return 503 Service Unavailable. */
        ERROR
    }

    private static final String DEFAULT_COOKIE_NAME = "X-Session-Node";
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);
    private static final String DEFAULT_PATH = "/";

    /**
     * Creates a builder with sensible defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SessionAffinityConfig.
     */
    public static class Builder {
        private String cookieName = DEFAULT_COOKIE_NAME;
        private Duration maxAge = DEFAULT_MAX_AGE;
        private boolean secure = false;
        private boolean httpOnly = true;
        private String path = DEFAULT_PATH;
        private FallbackStrategy fallback = FallbackStrategy.REHASH;

        public Builder cookieName(String name) {
            this.cookieName = Objects.requireNonNull(name);
            return this;
        }

        public Builder maxAge(Duration maxAge) {
            this.maxAge = Objects.requireNonNull(maxAge);
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public Builder httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        public Builder path(String path) {
            this.path = Objects.requireNonNull(path);
            return this;
        }

        public Builder fallback(FallbackStrategy fallback) {
            this.fallback = Objects.requireNonNull(fallback);
            return this;
        }

        public SessionAffinityConfig build() {
            return new SessionAffinityConfig(cookieName, maxAge, secure, httpOnly, path, fallback);
        }
    }
}
