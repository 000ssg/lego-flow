package ssg.legoflow.http.proxy.cache;

import java.util.Optional;

/**
 * Interface for cache storage in the caching proxy.
 *
 * <p>Implementations provide the storage backend for cached responses.
 * Entries are keyed by a cache key (typically method + URI) and store
 * serialized response data with metadata.</p>
 *
 * @since 1.0.0
 */
public interface ProxyCacheStore {

    /**
     * Retrieves a cached entry by key.
     *
     * @param key the cache key
     * @return the cached entry, or empty if not found or expired
     * @since 1.0.0
     */
    Optional<CacheEntry> get(String key);

    /**
     * Stores an entry in the cache.
     *
     * @param key the cache key
     * @param entry the cache entry
     * @since 1.0.0
     */
    void put(String key, CacheEntry entry);

    /**
     * Removes an entry from the cache.
     *
     * @param key the cache key
     * @since 1.0.0
     */
    void remove(String key);

    /**
     * Removes all entries from the cache.
     *
     * @since 1.0.0
     */
    void clear();

    /**
     * Returns the number of entries in the cache.
     *
     * @return the entry count
     * @since 1.0.0
     */
    int size();

    /**
     * Returns the total size in bytes of all cached entries.
     *
     * @return the total size in bytes
     * @since 1.0.0
     */
    long sizeInBytes();

    /**
     * Represents a cached response entry.
     *
     * @param statusCode the HTTP status code
     * @param headers the response headers as a serialized map
     * @param body the response body bytes
     * @param etag the ETag header value, or null
     * @param lastModified the Last-Modified header value, or null
     * @param createdAt the creation timestamp in milliseconds
     * @param expiresAt the expiration timestamp in milliseconds
     * @since 1.0.0
     */
    record CacheEntry(int statusCode, java.util.Map<String, String> headers, byte[] body,
                      String etag, String lastModified, long createdAt, long expiresAt) {

        /**
         * Returns whether this entry has expired.
         *
         * @return true if expired
         * @since 1.0.0
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        /**
         * Returns the body size in bytes.
         *
         * @return the size
         * @since 1.0.0
         */
        public int bodySize() {
            return body != null ? body.length : 0;
        }
    }
}
