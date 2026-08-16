package ssg.legoflow.http.cluster;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for HTTP cache coherence across cluster nodes.
 *
 * <p>Controls which HTTP methods trigger invalidation, the scope
 * of cache entries to invalidate, and the coherence protocol used.
 *
 * @param invalidationMethods HTTP methods that trigger cache invalidation
 * @param invalidationScope   scope of invalidation: PATH, PREFIX, or ALL
 * @propagationTimeout       how long to wait for invalidation propagation
 * @since 0.2.0
 */
public record CacheCoherenceConfig(
        java.util.Set<ssg.legoflow.http.core.HttpMethod> invalidationMethods,
        InvalidationScope invalidationScope,
        Duration propagationTimeout
) {

    /**
     * Scope of cache invalidation.
     */
    public enum InvalidationScope {
        /** Invalidate only entries matching the exact request path. */
        PATH,
        /** Invalidate entries matching the path prefix (directory). */
        PREFIX,
        /** Invalidate all cached entries. */
        ALL
    }

    private static final java.util.Set<ssg.legoflow.http.core.HttpMethod> DEFAULT_INVAL_METHODS =
            java.util.Set.of(
                    ssg.legoflow.http.core.HttpMethod.PUT,
                    ssg.legoflow.http.core.HttpMethod.POST,
                    ssg.legoflow.http.core.HttpMethod.DELETE,
                    ssg.legoflow.http.core.HttpMethod.PATCH
            );

    private static final Duration DEFAULT_PROPAGATION_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Creates a builder with defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CacheCoherenceConfig.
     */
    public static class Builder {
        private java.util.Set<ssg.legoflow.http.core.HttpMethod> invalidationMethods = DEFAULT_INVAL_METHODS;
        private InvalidationScope invalidationScope = InvalidationScope.PREFIX;
        private Duration propagationTimeout = DEFAULT_PROPAGATION_TIMEOUT;

        public Builder invalidationMethods(java.util.Set<ssg.legoflow.http.core.HttpMethod> methods) {
            this.invalidationMethods = Objects.requireNonNull(methods);
            return this;
        }

        public Builder invalidationScope(InvalidationScope scope) {
            this.invalidationScope = Objects.requireNonNull(scope);
            return this;
        }

        public Builder propagationTimeout(Duration timeout) {
            this.propagationTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        public CacheCoherenceConfig build() {
            return new CacheCoherenceConfig(invalidationMethods, invalidationScope, propagationTimeout);
        }
    }
}
