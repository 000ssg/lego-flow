package ssg.legoflow.http.proxy.cache;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the caching proxy.
 *
 * @since 1.0.0
 */
public class ProxyCacheConfig {

    private long maxSizeBytes = 64 * 1024 * 1024; // 64 MB
    private int maxEntries = 10_000;
    private Duration defaultTtl = Duration.ofMinutes(5);
    private Set<String> includedPaths = new HashSet<>();
    private Set<String> excludedPaths = new HashSet<>();
    private boolean cachePrivate = false;
    private boolean respectCacheControl = true;

    /**
     * Creates a new cache configuration with defaults.
     *
     * @since 1.0.0
     */
    public ProxyCacheConfig() {
    }

    /**
     * Returns the maximum cache size in bytes.
     *
     * @return the max size in bytes
     * @since 1.0.0
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /**
     * Sets the maximum cache size in bytes.
     *
     * @param maxSizeBytes the max size in bytes
     * @since 1.0.0
     */
    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * Returns the maximum number of cached entries.
     *
     * @return the max entries
     * @since 1.0.0
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * Sets the maximum number of cached entries.
     *
     * @param maxEntries the max entries
     * @since 1.0.0
     */
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Returns the default TTL for cached responses.
     *
     * @return the default TTL
     * @since 1.0.0
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * Sets the default TTL for cached responses.
     *
     * @param defaultTtl the default TTL
     * @since 1.0.0
     */
    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * Returns the set of included path prefixes.
     * If non-empty, only these paths are cached.
     *
     * @return the included paths
     * @since 1.0.0
     */
    public Set<String> getIncludedPaths() {
        return includedPaths;
    }

    /**
     * Sets the included path prefixes.
     *
     * @param includedPaths the included paths
     * @since 1.0.0
     */
    public void setIncludedPaths(Set<String> includedPaths) {
        this.includedPaths = new HashSet<>(includedPaths);
    }

    /**
     * Returns the set of excluded path prefixes.
     *
     * @return the excluded paths
     * @since 1.0.0
     */
    public Set<String> getExcludedPaths() {
        return excludedPaths;
    }

    /**
     * Sets the excluded path prefixes.
     *
     * @param excludedPaths the excluded paths
     * @since 1.0.0
     */
    public void setExcludedPaths(Set<String> excludedPaths) {
        this.excludedPaths = new HashSet<>(excludedPaths);
    }

    /**
     * Returns whether private responses are cached.
     *
     * @return true if private responses are cached
     * @since 1.0.0
     */
    public boolean isCachePrivate() {
        return cachePrivate;
    }

    /**
     * Sets whether private responses are cached.
     *
     * @param cachePrivate true to cache private responses
     * @since 1.0.0
     */
    public void setCachePrivate(boolean cachePrivate) {
        this.cachePrivate = cachePrivate;
    }

    /**
     * Returns whether Cache-Control headers are respected.
     *
     * @return true if Cache-Control is respected
     * @since 1.0.0
     */
    public boolean isRespectCacheControl() {
        return respectCacheControl;
    }

    /**
     * Sets whether Cache-Control headers are respected.
     *
     * @param respectCacheControl true to respect
     * @since 1.0.0
     */
    public void setRespectCacheControl(boolean respectCacheControl) {
        this.respectCacheControl = respectCacheControl;
    }
}
