package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyFilter;
import ssg.legoflow.http.proxy.ProxyHeaders;
import ssg.legoflow.http.proxy.cache.CachingProxy;
import ssg.legoflow.http.proxy.cache.InMemoryProxyCacheStore;
import ssg.legoflow.http.proxy.cache.ProxyCacheConfig;
import ssg.legoflow.http.proxy.forward.ConnectTunnel;
import ssg.legoflow.http.proxy.forward.ForwardProxy;
import ssg.legoflow.http.proxy.forward.ForwardProxyConfig;
import ssg.legoflow.http.proxy.forward.ProxyAccessControl;
import ssg.legoflow.http.proxy.reverse.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Comprehensive demo of all HTTP proxy module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house proxy</b> -- No external dependencies.
 * Runs anywhere without installation. Uses the proxy module's forward proxy,
 * reverse proxy, caching proxy, and load balancer components internally.</p>
 *
 * <p><b>Alternative: External proxy (Squid, Nginx, HAProxy)</b> -- Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with process-level isolation</li>
 *   <li>Testing against real HTTP proxies</li>
 *   <li>Integration testing with SSL termination proxies</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Forward proxy -- plain HTTP request forwarding with URI rewriting</li>
 *   <li>Reverse proxy -- path-based routing to backend servers</li>
 *   <li>Proxy filters -- request/response modification pipeline</li>
 *   <li>Cache proxy -- HTTP caching with Cache-Control, ETag</li>
 *   <li>CONNECT tunnel -- HTTPS tunneling via CONNECT method</li>
 *   <li>Proxy headers -- Via, X-Forwarded-For, X-Real-IP</li>
 *   <li>Error handling -- 502 Bad Gateway, 504 Gateway Timeout, 503 Service Unavailable</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoHttpProxyAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoHttpProxyAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house proxy (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Squid/Nginx
    // =========================================================================

    /** Set to {@code true} to connect to an external HTTP proxy. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external proxy. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external proxy. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 3128;

    private DemoHttpProxyAll() {}

    /**
     * Results from running the full proxy demo.
     *
     * @param forwardProxyBasic true if forward proxy GET forwarding worked
     * @param reverseProxyBasic true if reverse proxy path routing worked
     * @param proxyFilters      true if request/response filters applied correctly
     * @param cacheProxy        true if caching functionality worked (hits/misses/invalidation)
     * @param connectTunnel     true if CONNECT method tunneling worked
     * @param proxyHeaders      true if Via, X-Forwarded-For headers set correctly
     * @param errorHandling     true if 502/504/503 error responses produced correctly
     * @since 1.0.0
     */
    public record Results(
            boolean forwardProxyBasic,
            boolean reverseProxyBasic,
            boolean proxyFilters,
            boolean cacheProxy,
            boolean connectTunnel,
            boolean proxyHeaders,
            boolean errorHandling
    ) {}

    /**
     * Runs the comprehensive demo covering all proxy features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 1.0.0
     */
    public static Results runAll() throws Exception {
        boolean forward = demoForwardProxyBasic();
        boolean reverse = demoReverseProxyBasic();
        boolean filters = demoProxyFilters();
        boolean cache = demoCacheProxy();
        boolean tunnel = demoConnectTunnel();
        boolean headers = demoProxyHeaders();
        boolean errors = demoErrorHandling();

        return new Results(forward, reverse, filters, cache, tunnel, headers, errors);
    }

    // ======================== 1. FORWARD PROXY ==============================

    /**
     * Demonstrates forward proxy basic operation: accepting a client request
     * with an absolute URI, forwarding to the upstream, and returning the response.
     *
     * @return true if all forward proxy checks pass
     * @since 1.0.0
     */
    static boolean demoForwardProxyBasic() {
        LOG.info("=== 1. Forward Proxy Basic ===");
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-all-forward");

        var accessControl = ProxyAccessControl.allowHosts(
                Set.of("example.com", "api.example.com", "localhost"));

        var proxy = new ForwardProxy(config, accessControl) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK,
                        "Upstream response from " + target.host() + target.path());
            }
        };

        // GET through proxy
        var getReq = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var getResp = proxy.handleRequest(getReq);
        boolean getOk = getResp.getStatus() == HttpStatus.OK;
        LOG.info("GET http://example.com/page -> {}", getResp.getStatus());

        // POST through proxy
        var postReq = HttpRequest.of(HttpMethod.POST, "http://example.com/api/data");
        postReq.setBody(java.nio.ByteBuffer.wrap("{\"key\":\"value\"}".getBytes()));
        var postResp = proxy.handleRequest(postReq);
        boolean postOk = postResp.getStatus() == HttpStatus.OK;
        LOG.info("POST http://example.com/api/data -> {}", postResp.getStatus());

        // Blocked host
        var blockedReq = HttpRequest.of(HttpMethod.GET, "http://blocked.com/secret");
        var blockedResp = proxy.handleRequest(blockedReq);
        boolean blockedOk = blockedResp.getStatus() == HttpStatus.FORBIDDEN;
        LOG.info("GET http://blocked.com/secret -> {} (expected 403)", blockedResp.getStatus());

        // Request count
        boolean countOk = proxy.getRequestCount() == 3;
        LOG.info("Request count: {}", proxy.getRequestCount());

        proxy.close();
        return getOk && postOk && blockedOk && countOk;
    }

    // ======================== 2. REVERSE PROXY ==============================

    /**
     * Demonstrates reverse proxy with path-based routing to multiple backends
     * and load balancing.
     *
     * @return true if all reverse proxy checks pass
     * @since 1.0.0
     */
    static boolean demoReverseProxyBasic() {
        LOG.info("=== 2. Reverse Proxy Basic ===");
        var config = new ReverseProxyConfig();
        config.setProxyName("demo-all-reverse");

        var proxy = new ReverseProxy(config);

        // Set up routes
        var apiBackend1 = new BackendServer("api-1", 8081);
        var apiBackend2 = new BackendServer("api-2", 8082);
        proxy.addRoute(new ProxyRoute("/api",
                List.of(apiBackend1, apiBackend2),
                new RoundRobinBalancer(), true));

        var staticBackend = new BackendServer("static", 8083);
        proxy.addRoute(ProxyRoute.of("/static", staticBackend));

        // API request routed to backend
        var apiReq = HttpRequest.of(HttpMethod.GET, "/api/users");
        apiReq.getHeaders().set(HttpHeaders.HOST, "myapp.com");
        var apiResp = proxy.handleRequest(apiReq);
        boolean apiOk = apiResp.getStatus() == HttpStatus.OK;
        LOG.info("GET /api/users -> {}", apiResp.getStatus());

        // Static request routed to different backend
        var staticReq = HttpRequest.of(HttpMethod.GET, "/static/style.css");
        staticReq.getHeaders().set(HttpHeaders.HOST, "myapp.com");
        var staticResp = proxy.handleRequest(staticReq);
        boolean staticOk = staticResp.getStatus() == HttpStatus.OK;
        LOG.info("GET /static/style.css -> {}", staticResp.getStatus());

        // No route -> 404
        var notFoundReq = HttpRequest.of(HttpMethod.GET, "/unknown/path");
        var notFoundResp = proxy.handleRequest(notFoundReq);
        boolean notFoundOk = notFoundResp.getStatus() == HttpStatus.NOT_FOUND;
        LOG.info("GET /unknown/path -> {} (expected 404)", notFoundResp.getStatus());

        // Load balancing: multiple requests distribute across backends
        for (int i = 0; i < 4; i++) {
            var req = HttpRequest.of(HttpMethod.GET, "/api/resource");
            proxy.handleRequest(req);
        }
        boolean lbOk = apiBackend1.getTotalRequests() > 0 && apiBackend2.getTotalRequests() > 0;
        LOG.info("Load balancing: backend-1={}, backend-2={}",
                apiBackend1.getTotalRequests(), apiBackend2.getTotalRequests());

        proxy.close();
        return apiOk && staticOk && notFoundOk && lbOk;
    }

    // ======================== 3. PROXY FILTERS ==============================

    /**
     * Demonstrates request and response filters in the proxy pipeline.
     *
     * @return true if filters are applied correctly
     * @since 1.0.0
     */
    static boolean demoProxyFilters() {
        LOG.info("=== 3. Proxy Filters ===");
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-all-filters");

        var proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                var resp = HttpResponse.of(HttpStatus.OK, "filtered content");
                // Echo back if custom header was present on request
                String custom = request.getHeaders().get("x-custom-request");
                if (custom != null) {
                    resp.getHeaders().set("x-request-echo", custom);
                }
                return resp;
            }
        };

        // Add a request filter that adds a custom header
        proxy.addFilter(new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                request.getHeaders().set("x-custom-request", "filter-added");
                return request;
            }

            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                response.getHeaders().set("x-custom-response", "filter-modified");
                return response;
            }

            @Override
            public String getName() {
                return "demo-filter";
            }

            @Override
            public int getOrder() {
                return 10;
            }
        });

        // Add a second filter with higher priority (lower order)
        proxy.addFilter(new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                request.getHeaders().set("x-priority", "high");
                return request;
            }

            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                response.getHeaders().set("x-priority-response", "high");
                return response;
            }

            @Override
            public String getName() {
                return "priority-filter";
            }

            @Override
            public int getOrder() {
                return 1;
            }
        });

        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = proxy.handleRequest(request);

        boolean statusOk = response.getStatus() == HttpStatus.OK;
        boolean responseFilterApplied = "filter-modified".equals(
                response.getHeaders().get("x-custom-response"));
        boolean priorityFilterApplied = "high".equals(
                response.getHeaders().get("x-priority-response"));
        boolean requestFilterApplied = "filter-added".equals(
                response.getHeaders().get("x-request-echo"));
        boolean filterCount = proxy.getFilters().size() == 2;

        LOG.info("Response filter: {}, Priority filter: {}, Request filter echoed: {}",
                responseFilterApplied, priorityFilterApplied, requestFilterApplied);

        proxy.close();
        return statusOk && responseFilterApplied && priorityFilterApplied
                && requestFilterApplied && filterCount;
    }

    // ======================== 4. CACHE PROXY ================================

    /**
     * Demonstrates caching proxy: cache hits, misses, invalidation,
     * and conditional requests.
     *
     * @return true if caching works correctly
     * @since 1.0.0
     */
    static boolean demoCacheProxy() {
        LOG.info("=== 4. Cache Proxy ===");
        var config = new ReverseProxyConfig();
        config.setProxyName("demo-all-cache");

        var reverseProxy = new ReverseProxy(config);
        var backend = new BackendServer("cache-backend", 8080);
        reverseProxy.addRoute(ProxyRoute.of("/", backend));

        // Forwarder returns cacheable responses with ETag
        reverseProxy.setRequestForwarder((req, be) -> {
            var resp = HttpResponse.of(HttpStatus.OK, "Content for " + req.getUri());
            resp.getHeaders().set(HttpHeaders.CACHE_CONTROL, "max-age=300");
            resp.getHeaders().set(HttpHeaders.ETAG, "\"etag-" + req.getUri().hashCode() + "\"");
            return resp;
        });

        var cacheStore = new InMemoryProxyCacheStore(1000, 10 * 1024 * 1024);
        var cachingProxy = new CachingProxy(reverseProxy, cacheStore, new ProxyCacheConfig());

        // First request: cache miss
        var req1 = HttpRequest.of(HttpMethod.GET, "/data");
        var resp1 = cachingProxy.handleRequest(req1);
        boolean missOk = resp1.getStatus() == HttpStatus.OK && cachingProxy.getCacheMisses() == 1;
        LOG.info("1st GET /data -> {} (misses={})", resp1.getStatus(), cachingProxy.getCacheMisses());

        // Second request: cache hit
        var req2 = HttpRequest.of(HttpMethod.GET, "/data");
        var resp2 = cachingProxy.handleRequest(req2);
        boolean hitOk = resp2.getStatus() == HttpStatus.OK && cachingProxy.getCacheHits() == 1;
        String xCache = resp2.getHeaders().get("x-cache");
        boolean xCacheOk = "HIT".equals(xCache);
        LOG.info("2nd GET /data -> {} (hits={}, x-cache={})",
                resp2.getStatus(), cachingProxy.getCacheHits(), xCache);

        // POST invalidates cache
        var req3 = HttpRequest.of(HttpMethod.POST, "/data");
        cachingProxy.handleRequest(req3);

        // After invalidation: cache miss
        var req4 = HttpRequest.of(HttpMethod.GET, "/data");
        cachingProxy.handleRequest(req4);
        boolean invalidateOk = cachingProxy.getCacheMisses() == 2;
        LOG.info("After POST invalidation: misses={}", cachingProxy.getCacheMisses());

        cachingProxy.close();
        return missOk && hitOk && xCacheOk && invalidateOk;
    }

    // ======================== 5. CONNECT TUNNEL =============================

    /**
     * Demonstrates CONNECT method for HTTPS tunneling: the proxy returns
     * 200 Connection Established and a bidirectional tunnel is created.
     *
     * @return true if CONNECT tunnel works correctly
     * @since 1.0.0
     */
    static boolean demoConnectTunnel() throws Exception {
        LOG.info("=== 5. CONNECT Tunnel ===");
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-all-tunnel");

        var proxy = new ForwardProxy(config);

        // CONNECT request — proxy should return 200 Connection Established
        var connectReq = HttpRequest.of(HttpMethod.CONNECT, "secure.example.com:443");
        var connectResp = proxy.handleRequest(connectReq);
        boolean connectOk = connectResp.getStatus() == HttpStatus.OK;
        LOG.info("CONNECT secure.example.com:443 -> {}", connectResp.getStatus());

        // Tunnel count incremented
        boolean tunnelCountOk = proxy.getTunnelCount() == 1;
        LOG.info("Tunnel count: {}", proxy.getTunnelCount());

        // Verify ConnectTunnel lifecycle: create, start relay, close
        byte[] clientData = "Client hello".getBytes();
        byte[] serverData = "Server hello".getBytes();
        var tunnel = new ConnectTunnel("secure.example.com", 443,
                new ByteArrayInputStream(clientData), new ByteArrayOutputStream(),
                new ByteArrayInputStream(serverData), new ByteArrayOutputStream(),
                Duration.ofSeconds(5));

        tunnel.start();
        Thread.sleep(300); // Allow virtual threads to complete relay

        tunnel.close();
        boolean tunnelClosed = !tunnel.isActive();
        LOG.info("Tunnel closed: {}, relayed: {}->server, {}->client",
                tunnelClosed, tunnel.getBytesRelayedToServer(), tunnel.getBytesRelayedToClient());

        proxy.close();
        return connectOk && tunnelCountOk && tunnelClosed;
    }

    // ======================== 6. PROXY HEADERS ==============================

    /**
     * Demonstrates proxy header handling: Via, X-Forwarded-For, X-Forwarded-Proto,
     * X-Forwarded-Host, and X-Real-IP.
     *
     * @return true if all proxy headers are set correctly
     * @since 1.0.0
     */
    static boolean demoProxyHeaders() {
        LOG.info("=== 6. Proxy Headers ===");
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-all-headers");
        config.setAddViaHeader(true);
        config.setAddForwardedHeaders(true);

        var proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                // Echo back the proxy headers that were set on the forwarded request
                var resp = HttpResponse.of(HttpStatus.OK, "headers ok");
                String xff = request.getHeaders().get(ProxyHeaders.X_FORWARDED_FOR);
                if (xff != null) resp.getHeaders().set("x-echo-xff", xff);
                String host = request.getHeaders().get(HttpHeaders.HOST);
                if (host != null) resp.getHeaders().set("x-echo-host", host);
                return resp;
            }
        };

        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = proxy.handleRequest(request);

        // Via header added to response by proxy
        String via = ProxyHeaders.getVia(response.getHeaders());
        boolean viaOk = via != null && via.contains("demo-all-headers");
        LOG.info("Via: {}", via);

        // X-Forwarded-For was set on forwarded request
        String echoXff = response.getHeaders().get("x-echo-xff");
        boolean xffOk = echoXff != null && !echoXff.isEmpty();
        LOG.info("X-Forwarded-For (echoed): {}", echoXff);

        // Host header rewritten to target
        String echoHost = response.getHeaders().get("x-echo-host");
        boolean hostOk = echoHost != null && echoHost.contains("example.com");
        LOG.info("Host (echoed): {}", echoHost);

        // Utility method test: applyForwardHeaders
        var testHeaders = new HttpHeaders();
        ProxyHeaders.applyForwardHeaders(testHeaders,
                "192.168.1.1", "https", "original.example.com", "my-proxy");
        boolean utilXff = "192.168.1.1".equals(ProxyHeaders.getForwardedFor(testHeaders));
        boolean utilProto = "https".equals(ProxyHeaders.getForwardedProto(testHeaders));
        boolean utilHost = "original.example.com".equals(ProxyHeaders.getForwardedHost(testHeaders));
        boolean utilVia = ProxyHeaders.getVia(testHeaders).contains("my-proxy");
        boolean utilRealIp = "192.168.1.1".equals(ProxyHeaders.getRealIp(testHeaders));
        LOG.info("Utility headers: XFF={}, Proto={}, Host={}, Via={}, RealIP={}",
                utilXff, utilProto, utilHost, utilVia, utilRealIp);

        proxy.close();
        return viaOk && xffOk && hostOk && utilXff && utilProto && utilHost && utilVia && utilRealIp;
    }

    // ======================== 7. ERROR HANDLING ==============================

    /**
     * Demonstrates proxy error handling: 502 Bad Gateway, 504 Gateway Timeout,
     * 503 Service Unavailable, and exception-based error mapping.
     *
     * @return true if all error responses are produced correctly
     * @since 1.0.0
     */
    static boolean demoErrorHandling() {
        LOG.info("=== 7. Error Handling ===");
        var errorHandler = new ssg.legoflow.http.proxy.ProxyErrorHandler("demo-all-errors");

        // 502 Bad Gateway
        var badGateway = errorHandler.badGateway("upstream unreachable");
        boolean bgOk = badGateway.getStatus() == HttpStatus.BAD_GATEWAY;
        boolean bgBody = badGateway.getBodyAsString().contains("502");
        LOG.info("502 Bad Gateway: {}", badGateway.getStatus());

        // 504 Gateway Timeout
        var timeout = errorHandler.gatewayTimeout("read timed out");
        boolean toOk = timeout.getStatus() == HttpStatus.GATEWAY_TIMEOUT;
        boolean toBody = timeout.getBodyAsString().contains("504");
        LOG.info("504 Gateway Timeout: {}", timeout.getStatus());

        // 503 Service Unavailable
        var unavailable = errorHandler.serviceUnavailable("all backends down");
        boolean suOk = unavailable.getStatus() == HttpStatus.SERVICE_UNAVAILABLE;
        boolean suBody = unavailable.getBodyAsString().contains("503");
        LOG.info("503 Service Unavailable: {}", unavailable.getStatus());

        // Exception-based error mapping
        var connectError = errorHandler.handleError(new java.net.ConnectException("Connection refused"));
        boolean ceOk = connectError.getStatus() == HttpStatus.BAD_GATEWAY;
        LOG.info("ConnectException -> {}", connectError.getStatus());

        var timeoutError = errorHandler.handleError(new java.net.SocketTimeoutException("Read timed out"));
        boolean teOk = timeoutError.getStatus() == HttpStatus.GATEWAY_TIMEOUT;
        LOG.info("SocketTimeoutException -> {}", timeoutError.getStatus());

        // Forward proxy with unreachable upstream returns 502
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-all-error-proxy");
        var proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return errorHandler.badGateway("upstream connection failed");
            }
        };
        var req = HttpRequest.of(HttpMethod.GET, "http://example.com/unavailable");
        var resp = proxy.handleRequest(req);
        boolean proxyErrorOk = resp.getStatus() == HttpStatus.BAD_GATEWAY;
        LOG.info("Proxy upstream error -> {}", resp.getStatus());

        // Reverse proxy with no healthy backends returns 503
        var reverseConfig = new ReverseProxyConfig();
        var reverseProxy = new ReverseProxy(reverseConfig);
        var unhealthyBackend = new BackendServer("down", 9999);
        unhealthyBackend.setHealthy(false);
        reverseProxy.addRoute(new ProxyRoute("/api",
                List.of(unhealthyBackend), new RoundRobinBalancer(), true));
        var reverseReq = HttpRequest.of(HttpMethod.GET, "/api/test");
        var reverseResp = reverseProxy.handleRequest(reverseReq);
        boolean reverseErrorOk = reverseResp.getStatus() == HttpStatus.SERVICE_UNAVAILABLE;
        LOG.info("Reverse proxy no backends -> {}", reverseResp.getStatus());

        proxy.close();
        reverseProxy.close();
        return bgOk && bgBody && toOk && toBody && suOk && suBody
                && ceOk && teOk && proxyErrorOk && reverseErrorOk;
    }
}
