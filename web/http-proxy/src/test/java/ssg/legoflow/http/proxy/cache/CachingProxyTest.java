package ssg.legoflow.http.proxy.cache;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.reverse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CachingProxyTest {

    private CachingProxy cachingProxy;
    private ReverseProxy reverseProxy;
    private InMemoryProxyCacheStore cacheStore;
    private int upstreamCallCount;

    @BeforeEach
    void setUp() {
        upstreamCallCount = 0;
        var config = new ReverseProxyConfig();
        reverseProxy = new ReverseProxy(config);
        var backend = new BackendServer("backend", 8080);
        reverseProxy.addRoute(ProxyRoute.of("/", backend));
        reverseProxy.setRequestForwarder((req, be) -> {
            upstreamCallCount++;
            HttpResponse response = HttpResponse.of(HttpStatus.OK, "content-" + req.getUri());
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "max-age=300");
            response.getHeaders().set(HttpHeaders.ETAG, "\"etag-123\"");
            return response;
        });

        cacheStore = new InMemoryProxyCacheStore(100, 1024 * 1024);
        cachingProxy = new CachingProxy(reverseProxy, cacheStore, new ProxyCacheConfig());
    }

    @Test
    void testCacheMissOnFirstRequest() {
        var request = HttpRequest.of(HttpMethod.GET, "/data");
        var response = cachingProxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(cachingProxy.getCacheMisses()).isEqualTo(1);
        assertThat(cachingProxy.getCacheHits()).isEqualTo(0);
    }

    @Test
    void testCacheHitOnSecondRequest() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        assertThat(cachingProxy.getCacheHits()).isEqualTo(1);
        assertThat(upstreamCallCount).isEqualTo(1);
    }

    @Test
    void testCacheHitHeaderSet() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        var response = cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        assertThat(response.getHeaders().get("x-cache")).isEqualTo("HIT");
    }

    @Test
    void testPostInvalidatesCache() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.POST, "/data"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));
        assertThat(upstreamCallCount).isEqualTo(3); // GET, POST, GET again (miss)
    }

    @Test
    void testPutInvalidatesCache() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/resource"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.PUT, "/resource"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/resource"));
        assertThat(upstreamCallCount).isEqualTo(3);
    }

    @Test
    void testDeleteInvalidatesCache() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/item"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.DELETE, "/item"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/item"));
        assertThat(upstreamCallCount).isEqualTo(3);
    }

    @Test
    void testConditionalRequestEtag() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));

        var conditionalReq = HttpRequest.of(HttpMethod.GET, "/data");
        conditionalReq.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"etag-123\"");
        var response = cachingProxy.handleRequest(conditionalReq);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(cachingProxy.getConditionalHits()).isEqualTo(1);
    }

    @Test
    void testNoCacheHeaderBypassesCache() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/data"));

        var noCacheReq = HttpRequest.of(HttpMethod.GET, "/data");
        noCacheReq.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-cache");
        cachingProxy.handleRequest(noCacheReq);
        assertThat(upstreamCallCount).isEqualTo(2);
    }

    @Test
    void testNoStoreResponseNotCached() {
        reverseProxy.setRequestForwarder((req, be) -> {
            upstreamCallCount++;
            HttpResponse response = HttpResponse.of(HttpStatus.OK, "no-store");
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            return response;
        });

        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/secret"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/secret"));
        assertThat(upstreamCallCount).isEqualTo(2); // both requests hit upstream
    }

    @Test
    void testDifferentUrlsDifferentCacheEntries() {
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/a"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/b"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/a"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/b"));
        assertThat(upstreamCallCount).isEqualTo(2); // each URL only once
        assertThat(cachingProxy.getCacheHits()).isEqualTo(2);
    }

    @Test
    void testExcludedPathNotCached() {
        var cacheConfig = new ProxyCacheConfig();
        cacheConfig.setExcludedPaths(java.util.Set.of("/admin"));
        cachingProxy = new CachingProxy(reverseProxy, cacheStore, cacheConfig);

        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/admin/dashboard"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/admin/dashboard"));
        assertThat(upstreamCallCount).isEqualTo(2); // not cached
    }

    @Test
    void testIncludedPathCached() {
        var cacheConfig = new ProxyCacheConfig();
        cacheConfig.setIncludedPaths(java.util.Set.of("/api"));
        cachingProxy = new CachingProxy(reverseProxy, cacheStore, cacheConfig);

        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/api/data"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/api/data"));
        assertThat(upstreamCallCount).isEqualTo(1);
    }

    @Test
    void testIncludedPathExcludesOthers() {
        var cacheConfig = new ProxyCacheConfig();
        cacheConfig.setIncludedPaths(java.util.Set.of("/api"));
        cachingProxy = new CachingProxy(reverseProxy, cacheStore, cacheConfig);

        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/web/page"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/web/page"));
        assertThat(upstreamCallCount).isEqualTo(2); // not in included paths
    }

    @Test
    void testNon200StatusNotCached() {
        reverseProxy.setRequestForwarder((req, be) -> {
            upstreamCallCount++;
            return HttpResponse.of(HttpStatus.NOT_FOUND, "not found");
        });

        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/missing"));
        cachingProxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/missing"));
        assertThat(upstreamCallCount).isEqualTo(2);
    }

    @Test
    void testCloseProxy() {
        cachingProxy.close();
        assertThat(cacheStore.size()).isEqualTo(0);
    }
}
