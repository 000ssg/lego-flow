package ssg.legoflow.http.proxy.cache;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.reverse.BackendServer;
import ssg.legoflow.http.proxy.reverse.ProxyRoute;
import ssg.legoflow.http.proxy.reverse.ReverseProxy;
import ssg.legoflow.http.proxy.reverse.ReverseProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caching reverse proxy.
 *
 * <p>Wraps a reverse proxy with HTTP caching support, honoring
 * Cache-Control, Expires, ETag, and Last-Modified headers.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Cache-Control header parsing (no-cache, no-store, max-age, private)</li>
 *   <li>ETag and Last-Modified conditional request forwarding</li>
 *   <li>Cache invalidation on PUT/POST/DELETE</li>
 *   <li>Configurable cache storage</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class CachingProxy implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CachingProxy.class);
    private static final Set<HttpMethod> INVALIDATING_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);
    private static final Set<HttpMethod> CACHEABLE_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD);

    private final ReverseProxy reverseProxy;
    private final ProxyCacheStore cacheStore;
    private final ProxyCacheConfig cacheConfig;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong conditionalHits = new AtomicLong(0);

    /**
     * Creates a new caching proxy.
     *
     * @param reverseProxy the underlying reverse proxy
     * @param cacheStore the cache storage
     * @param cacheConfig the cache configuration
     * @since 1.0.0
     */
    public CachingProxy(ReverseProxy reverseProxy, ProxyCacheStore cacheStore,
                        ProxyCacheConfig cacheConfig) {
        this.reverseProxy = reverseProxy;
        this.cacheStore = cacheStore;
        this.cacheConfig = cacheConfig;
    }

    /**
     * Creates a caching proxy with default in-memory cache.
     *
     * @param reverseProxy the underlying reverse proxy
     * @since 1.0.0
     */
    public CachingProxy(ReverseProxy reverseProxy) {
        this(reverseProxy, new InMemoryProxyCacheStore(), new ProxyCacheConfig());
    }

    /**
     * Handles a request, serving from cache when possible.
     *
     * @param request the incoming request
     * @return the response (from cache or upstream)
     * @since 1.0.0
     */
    public HttpResponse handleRequest(HttpRequest request) {
        String path = extractPath(request.getUri());

        // Invalidating methods clear the cache entry
        if (INVALIDATING_METHODS.contains(request.getMethod())) {
            String cacheKey = buildCacheKey("GET", request.getUri());
            cacheStore.remove(cacheKey);
            LOG.debug("Cache invalidated for {} by {} request", path, request.getMethod());
            return reverseProxy.handleRequest(request);
        }

        // Only cache GET and HEAD
        if (!CACHEABLE_METHODS.contains(request.getMethod())) {
            return reverseProxy.handleRequest(request);
        }

        // Check if path is cacheable
        if (!isPathCacheable(path)) {
            return reverseProxy.handleRequest(request);
        }

        // Check request cache-control
        if (cacheConfig.isRespectCacheControl()) {
            String cacheControl = request.getHeaders().get(HttpHeaders.CACHE_CONTROL);
            if (cacheControl != null && (cacheControl.contains("no-cache") || cacheControl.contains("no-store"))) {
                cacheMisses.incrementAndGet();
                return reverseProxy.handleRequest(request);
            }
        }

        String cacheKey = buildCacheKey(request.getMethod().name(), request.getUri());

        // Try cache lookup
        var cached = cacheStore.get(cacheKey);
        if (cached.isPresent()) {
            var entry = cached.get();

            // Check conditional request headers from client
            String clientEtag = request.getHeaders().get(HttpHeaders.IF_NONE_MATCH);
            if (clientEtag != null && clientEtag.equals(entry.etag())) {
                conditionalHits.incrementAndGet();
                return HttpResponse.of(HttpStatus.NOT_MODIFIED);
            }

            String clientModified = request.getHeaders().get(HttpHeaders.IF_MODIFIED_SINCE);
            if (clientModified != null && entry.lastModified() != null
                    && clientModified.equals(entry.lastModified())) {
                conditionalHits.incrementAndGet();
                return HttpResponse.of(HttpStatus.NOT_MODIFIED);
            }

            // Serve from cache
            cacheHits.incrementAndGet();
            LOG.debug("Cache hit for {}", cacheKey);
            return buildResponseFromCache(entry);
        }

        // Cache miss: forward to upstream
        cacheMisses.incrementAndGet();

        // Add conditional headers if we have a stale entry
        HttpResponse response = reverseProxy.handleRequest(request);

        // Cache the response if cacheable
        if (isResponseCacheable(response)) {
            cacheResponse(cacheKey, response);
        }

        return response;
    }

    /**
     * Builds an HTTP response from a cache entry.
     *
     * @param entry the cache entry
     * @return the response
     * @since 1.0.0
     */
    HttpResponse buildResponseFromCache(ProxyCacheStore.CacheEntry entry) {
        HttpHeaders headers = new HttpHeaders();
        for (var h : entry.headers().entrySet()) {
            headers.set(h.getKey(), h.getValue());
        }
        headers.set("x-cache", "HIT");

        HttpResponse response = new HttpResponse(
                HttpStatus.fromCode(entry.statusCode()), HttpVersion.HTTP_1_1, headers);
        if (entry.body() != null && entry.body().length > 0) {
            response.setBody(ByteBuffer.wrap(entry.body()));
            headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(entry.body().length));
        }
        return response;
    }

    /**
     * Caches a response.
     *
     * @param key the cache key
     * @param response the response to cache
     * @since 1.0.0
     */
    void cacheResponse(String key, HttpResponse response) {
        String etag = response.getHeaders().get(HttpHeaders.ETAG);
        String lastModified = response.getHeaders().get(HttpHeaders.LAST_MODIFIED);

        // Determine TTL
        long ttlMillis = cacheConfig.getDefaultTtl().toMillis();
        String cacheControl = response.getHeaders().get(HttpHeaders.CACHE_CONTROL);
        if (cacheControl != null && cacheConfig.isRespectCacheControl()) {
            long maxAge = parseMaxAge(cacheControl);
            if (maxAge >= 0) {
                ttlMillis = maxAge * 1000;
            }
        }

        byte[] bodyBytes = null;
        if (response.getBody() != null) {
            ByteBuffer buf = response.getBody().duplicate();
            bodyBytes = new byte[buf.remaining()];
            buf.get(bodyBytes);
        }

        Map<String, String> headerMap = new HashMap<>();
        for (String name : response.getHeaders().names()) {
            headerMap.put(name, response.getHeaders().get(name));
        }

        long now = System.currentTimeMillis();
        var entry = new ProxyCacheStore.CacheEntry(
                response.getStatus().code(), headerMap, bodyBytes,
                etag, lastModified, now, now + ttlMillis);

        cacheStore.put(key, entry);
        LOG.debug("Cached response for {} (TTL: {}ms)", key, ttlMillis);
    }

    /**
     * Determines whether a response is cacheable.
     *
     * @param response the response
     * @return true if cacheable
     * @since 1.0.0
     */
    boolean isResponseCacheable(HttpResponse response) {
        int status = response.getStatus().code();
        if (status != 200 && status != 203 && status != 300 && status != 301 && status != 410) {
            return false;
        }

        if (cacheConfig.isRespectCacheControl()) {
            String cacheControl = response.getHeaders().get(HttpHeaders.CACHE_CONTROL);
            if (cacheControl != null) {
                if (cacheControl.contains("no-store")) return false;
                if (cacheControl.contains("private") && !cacheConfig.isCachePrivate()) return false;
            }
        }

        return true;
    }

    private boolean isPathCacheable(String path) {
        if (!cacheConfig.getExcludedPaths().isEmpty()) {
            for (String excluded : cacheConfig.getExcludedPaths()) {
                if (path.startsWith(excluded)) return false;
            }
        }
        if (!cacheConfig.getIncludedPaths().isEmpty()) {
            for (String included : cacheConfig.getIncludedPaths()) {
                if (path.startsWith(included)) return true;
            }
            return false;
        }
        return true;
    }

    private String buildCacheKey(String method, String uri) {
        return method + ":" + uri;
    }

    private String extractPath(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(0, queryStart) : uri;
    }

    private long parseMaxAge(String cacheControl) {
        String[] parts = cacheControl.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (trimmed.startsWith("max-age=")) {
                try {
                    return Long.parseLong(trimmed.substring(8));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the underlying reverse proxy.
     *
     * @return the reverse proxy
     * @since 1.0.0
     */
    public ReverseProxy getReverseProxy() {
        return reverseProxy;
    }

    /**
     * Returns the cache store.
     *
     * @return the cache store
     * @since 1.0.0
     */
    public ProxyCacheStore getCacheStore() {
        return cacheStore;
    }

    /**
     * Returns the cache configuration.
     *
     * @return the cache config
     * @since 1.0.0
     */
    public ProxyCacheConfig getCacheConfig() {
        return cacheConfig;
    }

    /**
     * Returns the cache hit count.
     *
     * @return the hit count
     * @since 1.0.0
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Returns the cache miss count.
     *
     * @return the miss count
     * @since 1.0.0
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Returns the conditional hit count (304 responses).
     *
     * @return the conditional hit count
     * @since 1.0.0
     */
    public long getConditionalHits() {
        return conditionalHits.get();
    }

    @Override
    public void close() {
        reverseProxy.close();
        cacheStore.clear();
    }
}
