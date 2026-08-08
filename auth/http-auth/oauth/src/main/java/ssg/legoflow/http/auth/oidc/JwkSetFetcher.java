package ssg.legoflow.http.auth.oidc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches a JWK Set from a JWKS URI endpoint. Supports automatic
 * cache expiry for key rotation scenarios.
 *
 * <p>This class does not perform HTTP requests itself -- it accepts a JSON string
 * provider (e.g., a lambda wrapping an HTTP client call) to decouple from
 * specific HTTP implementations.</p>
 *
 * @since 0.1.0
 */
public class JwkSetFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(JwkSetFetcher.class);

    private final String jwksUri;
    private final Duration cacheDuration;
    private final AtomicReference<CachedJwkSet> cached = new AtomicReference<>();

    /**
     * A functional interface for fetching JSON content from a URL.
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    public interface JsonFetcher {
        /**
         * Fetches JSON content from the given URL.
         *
         * @param url the URL to fetch
         * @return the JSON string
         * @throws Exception if fetching fails
         * @since 0.1.0
         */
        String fetch(String url) throws Exception;
    }

    private record CachedJwkSet(JwkSet jwkSet, Instant fetchedAt) {
        boolean isExpired(Duration cacheDuration) {
            return Instant.now().isAfter(fetchedAt.plus(cacheDuration));
        }
    }

    /**
     * Creates a JWK Set fetcher.
     *
     * @param jwksUri       the JWKS endpoint URI
     * @param cacheDuration how long to cache the fetched key set
     * @since 0.1.0
     */
    public JwkSetFetcher(String jwksUri, Duration cacheDuration) {
        this.jwksUri = Objects.requireNonNull(jwksUri, "jwksUri must not be null");
        this.cacheDuration = cacheDuration != null ? cacheDuration : Duration.ofHours(1);
    }

    /**
     * Creates a JWK Set fetcher with 1-hour cache.
     *
     * @param jwksUri the JWKS endpoint URI
     * @since 0.1.0
     */
    public JwkSetFetcher(String jwksUri) {
        this(jwksUri, Duration.ofHours(1));
    }

    /**
     * Gets the cached JWK Set, fetching if needed using the provided fetcher.
     *
     * @param fetcher the JSON fetcher to use if cache is expired or empty
     * @return the JWK Set
     * @since 0.1.0
     */
    public JwkSet getJwkSet(JsonFetcher fetcher) {
        var current = cached.get();
        if (current != null && !current.isExpired(cacheDuration)) {
            return current.jwkSet();
        }

        try {
            String json = fetcher.fetch(jwksUri);
            var jwkSet = JwkSet.fromJson(json);
            cached.set(new CachedJwkSet(jwkSet, Instant.now()));
            LOG.debug("Fetched JWK Set from {} with {} keys", jwksUri, jwkSet.size());
            return jwkSet;
        } catch (Exception e) {
            LOG.error("Failed to fetch JWK Set from {}", jwksUri, e);
            // Return cached if available, even if expired
            if (current != null) {
                LOG.warn("Using expired JWK Set cache");
                return current.jwkSet();
            }
            return JwkSet.fromJson("{}");
        }
    }

    /**
     * Loads a JWK Set from a pre-fetched JSON string (no HTTP call).
     *
     * @param json the JWK Set JSON
     * @return the parsed JWK Set
     * @since 0.1.0
     */
    public JwkSet loadFromJson(String json) {
        var jwkSet = JwkSet.fromJson(json);
        cached.set(new CachedJwkSet(jwkSet, Instant.now()));
        return jwkSet;
    }

    /**
     * Looks up a key by kid, fetching the JWK Set if needed.
     *
     * @param kid     the key ID
     * @param fetcher the JSON fetcher
     * @return the public key, or empty
     * @since 0.1.0
     */
    public Optional<PublicKey> getKey(String kid, JsonFetcher fetcher) {
        var jwkSet = getJwkSet(fetcher);
        var key = jwkSet.getKey(kid);
        if (key.isEmpty()) {
            // Key not found — force refresh in case of key rotation
            invalidateCache();
            jwkSet = getJwkSet(fetcher);
            key = jwkSet.getKey(kid);
        }
        return key;
    }

    /**
     * Invalidates the cached JWK Set.
     *
     * @since 0.1.0
     */
    public void invalidateCache() {
        cached.set(null);
    }

    /**
     * Returns the JWKS URI.
     *
     * @return the URI
     * @since 0.1.0
     */
    public String getJwksUri() {
        return jwksUri;
    }

    /**
     * Returns the cache duration.
     *
     * @return the duration
     * @since 0.1.0
     */
    public Duration getCacheDuration() {
        return cacheDuration;
    }
}
