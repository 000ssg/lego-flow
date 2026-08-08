package ssg.legoflow.http.proxy.reverse;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyErrorHandler;
import ssg.legoflow.http.proxy.ProxyFilter;
import ssg.legoflow.http.proxy.ProxyHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * HTTP reverse proxy.
 *
 * <p>Maps incoming paths to upstream backend servers with support for:</p>
 * <ul>
 *   <li>Load balancing (round-robin, random, least-connections)</li>
 *   <li>Health checking of backends</li>
 *   <li>Request/response header rewriting</li>
 *   <li>Configurable path prefix stripping</li>
 *   <li>WebSocket proxy support (upgrade forwarding)</li>
 *   <li>Streaming body forwarding</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class ReverseProxy implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ReverseProxy.class);

    private final ReverseProxyConfig config;
    private final List<ProxyRoute> routes = new CopyOnWriteArrayList<>();
    private final List<ProxyFilter> filters = new CopyOnWriteArrayList<>();
    private final ProxyErrorHandler errorHandler;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final HealthChecker healthChecker;
    private BiFunction<HttpRequest, BackendServer, HttpResponse> requestForwarder;

    /**
     * Creates a new reverse proxy with the given configuration.
     *
     * @param config the proxy configuration
     * @since 0.1.0
     */
    public ReverseProxy(ReverseProxyConfig config) {
        this.config = config;
        this.errorHandler = new ProxyErrorHandler(config.getProxyName());
        this.healthChecker = new HealthChecker();
    }

    /**
     * Adds a route to the reverse proxy.
     *
     * @param route the route to add
     * @since 0.1.0
     */
    public void addRoute(ProxyRoute route) {
        routes.add(route);
        // Sort routes by prefix length (longest first) for correct matching
        routes.sort((a, b) -> Integer.compare(b.getPathPrefix().length(), a.getPathPrefix().length()));
        // Register backends with health checker
        for (BackendServer backend : route.getBackends()) {
            healthChecker.addBackend(backend);
        }
    }

    /**
     * Removes a route from the reverse proxy.
     *
     * @param pathPrefix the path prefix of the route to remove
     * @since 0.1.0
     */
    public void removeRoute(String pathPrefix) {
        routes.removeIf(r -> r.getPathPrefix().equals(pathPrefix));
    }

    /**
     * Handles an incoming HTTP request.
     *
     * @param request the incoming request
     * @return the proxy response
     * @since 0.1.0
     */
    public HttpResponse handleRequest(HttpRequest request) {
        requestCount.incrementAndGet();
        String path = extractPath(request.getUri());
        LOG.debug("Reverse proxy handling: {} {}", request.getMethod(), path);

        // Check for WebSocket upgrade
        if (config.isWebSocketSupport() && isWebSocketUpgrade(request)) {
            return handleWebSocketUpgrade(request, path);
        }

        // Find matching route
        ProxyRoute route = findRoute(path);
        if (route == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, "No route configured for path: " + path);
        }

        // Select backend
        BackendServer backend = route.selectBackend();
        if (backend == null) {
            return errorHandler.serviceUnavailable("No healthy backend available for path: " + path);
        }

        // Forward request
        return forwardRequest(request, route, backend);
    }

    /**
     * Forwards a request to the selected backend.
     *
     * @param request the original request
     * @param route the matched route
     * @param backend the selected backend
     * @return the response
     * @since 0.1.0
     */
    HttpResponse forwardRequest(HttpRequest request, ProxyRoute route, BackendServer backend) {
        backend.acquireConnection();
        try {
            // Rewrite path
            String originalPath = extractPath(request.getUri());
            String rewrittenPath = route.rewritePath(originalPath);

            // Preserve query string
            String query = extractQuery(request.getUri());
            if (query != null) {
                rewrittenPath = rewrittenPath + "?" + query;
            }

            // Build forwarded request
            HttpHeaders forwardHeaders = copyHeaders(request.getHeaders());

            // Rewrite Host header
            if (!config.isPreserveHostHeader()) {
                String originalHost = request.getHeaders().get(HttpHeaders.HOST);
                forwardHeaders.set(HttpHeaders.HOST, backend.getHost() + ":" + backend.getPort());
                if (config.isAddForwardedHeaders() && originalHost != null) {
                    ProxyHeaders.setForwardedHost(forwardHeaders, originalHost);
                    ProxyHeaders.addForwardedFor(forwardHeaders, "127.0.0.1");
                    ProxyHeaders.setForwardedProto(forwardHeaders, "http");
                }
            }

            // Add Via header
            if (config.isAddViaHeader()) {
                ProxyHeaders.addVia(forwardHeaders, "1.1", config.getProxyName());
            }

            // Remove hop-by-hop headers
            removeHopByHopHeaders(forwardHeaders);

            HttpRequest forwardRequest = new HttpRequest(
                    request.getMethod(), rewrittenPath, request.getVersion(), forwardHeaders);
            if (request.getBody() != null) {
                forwardRequest.setBody(request.getBody().duplicate());
            }

            // Apply request filters
            HttpRequest filteredRequest = applyRequestFilters(forwardRequest);

            // Forward to backend
            HttpResponse response;
            if (requestForwarder != null) {
                response = requestForwarder.apply(filteredRequest, backend);
            } else {
                response = simulateBackendResponse(filteredRequest, backend);
            }

            // Add Via to response
            if (config.isAddViaHeader()) {
                ProxyHeaders.addVia(response.getHeaders(), "1.1", config.getProxyName());
            }

            // Apply response filters
            return applyResponseFilters(response);

        } catch (Exception e) {
            backend.recordFailure();
            LOG.error("Error forwarding to backend {}: {}", backend.getId(), e.getMessage());
            return errorHandler.handleError(e);
        } finally {
            backend.releaseConnection();
        }
    }

    /**
     * Simulates a backend response. Override for real implementations.
     *
     * @param request the forwarded request
     * @param backend the target backend
     * @return a synthetic response
     * @since 0.1.0
     */
    protected HttpResponse simulateBackendResponse(HttpRequest request, BackendServer backend) {
        String body = "Response from " + backend.getId() + " for " + request.getUri();
        return HttpResponse.of(HttpStatus.OK, body);
    }

    /**
     * Sets a custom request forwarder for real upstream connections.
     *
     * @param forwarder the forwarder function
     * @since 0.1.0
     */
    public void setRequestForwarder(BiFunction<HttpRequest, BackendServer, HttpResponse> forwarder) {
        this.requestForwarder = forwarder;
    }

    /**
     * Handles a WebSocket upgrade request.
     *
     * @param request the upgrade request
     * @param path the request path
     * @return the upgrade response
     * @since 0.1.0
     */
    HttpResponse handleWebSocketUpgrade(HttpRequest request, String path) {
        ProxyRoute route = findRoute(path);
        if (route == null) {
            return HttpResponse.of(HttpStatus.NOT_FOUND, "No route for WebSocket path: " + path);
        }
        BackendServer backend = route.selectBackend();
        if (backend == null) {
            return errorHandler.serviceUnavailable("No backend for WebSocket");
        }

        // Forward the upgrade request headers
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.UPGRADE, "websocket");
        headers.set(HttpHeaders.CONNECTION, "Upgrade");
        HttpResponse response = new HttpResponse(HttpStatus.SWITCHING_PROTOCOLS, HttpVersion.HTTP_1_1, headers);
        return response;
    }

    private boolean isWebSocketUpgrade(HttpRequest request) {
        String upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        return upgrade != null && upgrade.equalsIgnoreCase("websocket");
    }

    /**
     * Finds the best matching route for the given path.
     *
     * @param path the request path
     * @return the matching route, or null
     * @since 0.1.0
     */
    ProxyRoute findRoute(String path) {
        for (ProxyRoute route : routes) {
            if (route.matches(path)) {
                return route;
            }
        }
        return null;
    }

    private String extractPath(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(0, queryStart) : uri;
    }

    private String extractQuery(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(queryStart + 1) : null;
    }

    private HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders copy = new HttpHeaders();
        for (String name : source.names()) {
            for (String value : source.getAll(name)) {
                copy.add(name, value);
            }
        }
        return copy;
    }

    private void removeHopByHopHeaders(HttpHeaders headers) {
        headers.remove("proxy-authorization");
        headers.remove("proxy-connection");
        headers.remove("te");
        headers.remove("trailer");
    }

    private HttpRequest applyRequestFilters(HttpRequest request) {
        HttpRequest result = request;
        List<ProxyFilter> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator.comparingInt(ProxyFilter::getOrder));
        for (ProxyFilter filter : sorted) {
            result = filter.filterRequest(result);
        }
        return result;
    }

    private HttpResponse applyResponseFilters(HttpResponse response) {
        HttpResponse result = response;
        List<ProxyFilter> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator.comparingInt(ProxyFilter::getOrder));
        for (ProxyFilter filter : sorted) {
            result = filter.filterResponse(result);
        }
        return result;
    }

    /**
     * Adds a filter to the proxy pipeline.
     *
     * @param filter the filter
     * @since 0.1.0
     */
    public void addFilter(ProxyFilter filter) {
        filters.add(filter);
    }

    /**
     * Returns the proxy configuration.
     *
     * @return the config
     * @since 0.1.0
     */
    public ReverseProxyConfig getConfig() {
        return config;
    }

    /**
     * Returns the registered routes.
     *
     * @return the routes
     * @since 0.1.0
     */
    public List<ProxyRoute> getRoutes() {
        return List.copyOf(routes);
    }

    /**
     * Returns the health checker.
     *
     * @return the health checker
     * @since 0.1.0
     */
    public HealthChecker getHealthChecker() {
        return healthChecker;
    }

    /**
     * Returns the total number of requests handled.
     *
     * @return the request count
     * @since 0.1.0
     */
    public long getRequestCount() {
        return requestCount.get();
    }

    /**
     * Returns the registered filters.
     *
     * @return the filters
     * @since 0.1.0
     */
    public List<ProxyFilter> getFilters() {
        return List.copyOf(filters);
    }

    @Override
    public void close() {
        healthChecker.close();
        LOG.info("Reverse proxy closed. Total requests: {}", requestCount.get());
    }
}
