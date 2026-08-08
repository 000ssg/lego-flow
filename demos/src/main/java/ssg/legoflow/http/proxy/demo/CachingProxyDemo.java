package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.cache.*;
import ssg.legoflow.http.proxy.reverse.*;

import java.util.List;

/**
 * Caching reverse proxy demo.
 *
 * <p>Demonstrates HTTP caching with Cache-Control, ETag, and conditional requests.</p>
 *
 * @since 0.1.0
 */
public class CachingProxyDemo {

    private final CachingProxy cachingProxy;

    /**
     * Creates the demo with a caching proxy backed by an in-memory cache.
     *
     * @since 0.1.0
     */
    public CachingProxyDemo() {
        var config = new ReverseProxyConfig();
        config.setProxyName("demo-caching-proxy");

        var reverseProxy = new ReverseProxy(config);
        var backend = new BackendServer("backend", 8080);
        reverseProxy.addRoute(ProxyRoute.of("/", backend));

        // Set up a forwarder that returns cacheable responses
        reverseProxy.setRequestForwarder((req, be) -> {
            HttpResponse response = HttpResponse.of(HttpStatus.OK, "Content for " + req.getUri());
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "max-age=60");
            response.getHeaders().set(HttpHeaders.ETAG, "\"etag-" + req.getUri().hashCode() + "\"");
            return response;
        });

        var cacheStore = new InMemoryProxyCacheStore(1000, 10 * 1024 * 1024);
        var cacheConfig = new ProxyCacheConfig();

        this.cachingProxy = new CachingProxy(reverseProxy, cacheStore, cacheConfig);
    }

    /**
     * Runs the demo showing cache hits, misses, and conditional requests.
     *
     * @return the results summary
     * @since 0.1.0
     */
    public String run() {
        var sb = new StringBuilder();

        // First request: cache miss
        var req1 = HttpRequest.of(HttpMethod.GET, "/data");
        var resp1 = cachingProxy.handleRequest(req1);
        sb.append("1st GET /data -> ").append(resp1.getStatus().code())
                .append(" (miss)\n");

        // Second request: cache hit
        var req2 = HttpRequest.of(HttpMethod.GET, "/data");
        var resp2 = cachingProxy.handleRequest(req2);
        sb.append("2nd GET /data -> ").append(resp2.getStatus().code())
                .append(" (hit: ").append(resp2.getHeaders().get("x-cache")).append(")\n");

        // POST invalidates cache
        var req3 = HttpRequest.of(HttpMethod.POST, "/data");
        var resp3 = cachingProxy.handleRequest(req3);
        sb.append("POST /data -> ").append(resp3.getStatus().code())
                .append(" (invalidates cache)\n");

        // After invalidation: cache miss again
        var req4 = HttpRequest.of(HttpMethod.GET, "/data");
        var resp4 = cachingProxy.handleRequest(req4);
        sb.append("3rd GET /data -> ").append(resp4.getStatus().code())
                .append(" (miss after invalidation)\n");

        sb.append("Cache hits: ").append(cachingProxy.getCacheHits()).append("\n");
        sb.append("Cache misses: ").append(cachingProxy.getCacheMisses()).append("\n");

        return sb.toString();
    }

    /**
     * Returns the caching proxy instance.
     *
     * @return the caching proxy
     * @since 0.1.0
     */
    public CachingProxy getCachingProxy() {
        return cachingProxy;
    }
}
