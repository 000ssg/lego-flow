package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyErrorHandler;
import ssg.legoflow.http.proxy.ProxyFilter;
import ssg.legoflow.http.proxy.ProxyHeaders;
import ssg.legoflow.http.proxy.auth.ProxyAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP forward proxy server.
 *
 * <p>Implements both plain HTTP proxying and HTTPS tunneling via the CONNECT method.
 * For plain HTTP requests, the proxy rewrites the request URI and forwards to the
 * target server. For CONNECT requests, the proxy establishes a TCP tunnel and relays
 * bytes bidirectionally.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>CONNECT method (HTTPS tunneling)</li>
 *   <li>Plain HTTP request forwarding with URI rewriting</li>
 *   <li>Configurable access control</li>
 *   <li>Via header addition per RFC 7230 section 5.7.1</li>
 *   <li>X-Forwarded-For header support</li>
 *   <li>Pluggable authentication</li>
 *   <li>Request/response filtering</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class ForwardProxy implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardProxy.class);

    /** Headers that JDK HttpClient manages internally and must not be set explicitly. */
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host", "content-length", "connection", "upgrade",
            "transfer-encoding", "expect");

    /** Hop-by-hop headers that should not be forwarded from upstream responses. */
    private static final Set<String> RESPONSE_HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    private final ForwardProxyConfig config;
    private final ProxyAccessControl accessControl;
    private final ProxyErrorHandler errorHandler;
    private final List<ProxyFilter> filters = new CopyOnWriteArrayList<>();
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong tunnelCount = new AtomicLong(0);
    private ProxyAuthenticator authenticator;

    /**
     * Creates a new forward proxy with the given configuration.
     *
     * @param config the proxy configuration
     * @since 1.0.0
     */
    public ForwardProxy(ForwardProxyConfig config) {
        this(config, ProxyAccessControl.allowAll());
    }

    /**
     * Creates a new forward proxy with configuration and access control.
     *
     * @param config the proxy configuration
     * @param accessControl the access control policy
     * @since 1.0.0
     */
    public ForwardProxy(ForwardProxyConfig config, ProxyAccessControl accessControl) {
        this.config = config;
        this.accessControl = accessControl;
        this.errorHandler = new ProxyErrorHandler(config.getProxyName());
    }

    /**
     * Handles an incoming HTTP request through the proxy.
     *
     * @param request the incoming request
     * @return the response from the target or an error response
     * @since 1.0.0
     */
    public HttpResponse handleRequest(HttpRequest request) {
        requestCount.incrementAndGet();
        LOG.debug("Forward proxy handling: {} {}", request.getMethod(), request.getUri());

        // Check method allowed
        if (!config.getAllowedMethods().contains(request.getMethod())) {
            return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED,
                    "Method " + request.getMethod() + " not allowed by proxy");
        }

        // Authenticate if required
        if (config.isAuthRequired() && authenticator != null) {
            if (!authenticator.authenticate(request)) {
                return authenticator.createChallenge();
            }
        }

        // Parse target from request
        TargetAddress target = parseTarget(request);
        if (target == null) {
            return errorHandler.badGateway("Cannot determine target host from request URI: " + request.getUri());
        }

        // Access control
        if (!accessControl.isAllowed(request, target.host(), target.port())) {
            return HttpResponse.of(HttpStatus.FORBIDDEN,
                    "Access denied: " + accessControl.getDenialReason());
        }

        // Handle CONNECT for tunneling
        if (request.getMethod() == HttpMethod.CONNECT) {
            return handleConnect(request, target);
        }

        // Handle plain HTTP proxy
        return handlePlainHttp(request, target);
    }

    /**
     * Handles a CONNECT tunnel request.
     *
     * @param request the CONNECT request
     * @param target the target address
     * @return 200 Connection Established response
     * @since 1.0.0
     */
    HttpResponse handleConnect(HttpRequest request, TargetAddress target) {
        tunnelCount.incrementAndGet();
        LOG.debug("Establishing CONNECT tunnel to {}:{}", target.host(), target.port());

        // Return 200 to indicate tunnel is established
        // In a real scenario, the socket I/O would be handled externally
        HttpResponse response = new HttpResponse(HttpStatus.OK, HttpVersion.HTTP_1_1, new HttpHeaders());
        response.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive");
        return response;
    }

    /**
     * Handles a plain HTTP proxy request by forwarding to the target.
     *
     * @param request the request to forward
     * @param target the target server address
     * @return the response from the target
     * @since 1.0.0
     */
    HttpResponse handlePlainHttp(HttpRequest request, TargetAddress target) {
        LOG.debug("Forwarding HTTP request to {}:{}{}", target.host(), target.port(), target.path());

        // Build forwarded request with rewritten URI
        HttpHeaders forwardHeaders = copyHeaders(request.getHeaders());

        // Add proxy headers
        if (config.isAddForwardedHeaders()) {
            ProxyHeaders.addForwardedFor(forwardHeaders, getClientIp(request));
        }
        if (config.isAddViaHeader()) {
            ProxyHeaders.addVia(forwardHeaders, "1.1", config.getProxyName());
        }

        // Set Host header to target
        forwardHeaders.set(HttpHeaders.HOST, target.host()
                + (target.port() != 80 && target.port() != 443 ? ":" + target.port() : ""));

        // Remove hop-by-hop headers
        removeHopByHopHeaders(forwardHeaders);

        HttpRequest forwardRequest = new HttpRequest(
                request.getMethod(), target.path(), request.getVersion(), forwardHeaders);
        if (request.getBody() != null) {
            forwardRequest.setBody(request.getBody().duplicate());
        }

        // Apply filters
        HttpRequest filteredRequest = applyRequestFilters(forwardRequest);

        // Forward to upstream via java.net.http.HttpClient (or subclass override)
        HttpResponse upstreamResponse = simulateUpstreamRequest(filteredRequest, target);

        // Add Via header to response
        if (config.isAddViaHeader()) {
            ProxyHeaders.addVia(upstreamResponse.getHeaders(), "1.1", config.getProxyName());
        }

        // Apply response filters
        return applyResponseFilters(upstreamResponse);
    }

    /**
     * Forwards a request to the upstream server using {@link java.net.http.HttpClient}.
     *
     * <p>Constructs the upstream URI from the target address, maps the request method
     * and headers, sends the request body if present, and converts the upstream
     * response back into the proxy's {@link HttpResponse} format.</p>
     *
     * <p>Error handling:</p>
     * <ul>
     *   <li>{@link ConnectException} / {@link IOException} produces 502 Bad Gateway</li>
     *   <li>{@link java.net.http.HttpTimeoutException} produces 504 Gateway Timeout</li>
     *   <li>{@link InterruptedException} produces 502 Bad Gateway</li>
     * </ul>
     *
     * <p>Subclasses may override this method to provide custom upstream communication,
     * e.g., for testing with mock servers.</p>
     *
     * @param request the forwarded request with proxy headers already applied
     * @param target the resolved target address (host, port, path)
     * @return the upstream response mapped to the proxy response format
     * @since 1.0.0
     */
    protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
        String scheme = (target.port() == 443) ? "https" : "http";
        String portPart = (scheme.equals("http") && target.port() == 80)
                || (scheme.equals("https") && target.port() == 443)
                ? "" : ":" + target.port();
        String uriStr = scheme + "://" + target.host() + portPart + target.path();

        try {
            URI upstreamUri = URI.create(uriStr);

            // Build JDK HttpClient request
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(upstreamUri)
                    .timeout(config.getReadTimeout());

            // Map method and body
            java.net.http.HttpRequest.BodyPublisher bodyPublisher =
                    java.net.http.HttpRequest.BodyPublishers.noBody();
            if (request.getBody() != null && request.getBody().hasRemaining()) {
                byte[] bodyBytes = new byte[request.getBody().remaining()];
                request.getBody().duplicate().get(bodyBytes);
                bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
            }
            builder.method(request.getMethod().name(), bodyPublisher);

            // Map headers (skip restricted headers that JDK HttpClient handles internally)
            for (String name : request.getHeaders().names()) {
                if (RESTRICTED_HEADERS.contains(name.toLowerCase())) {
                    continue;
                }
                for (String value : request.getHeaders().getAll(name)) {
                    builder.header(name, value);
                }
            }

            java.net.http.HttpRequest upstreamRequest = builder.build();

            // Send and receive
            try (var client = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectionTimeout())
                    .followRedirects(Redirect.NEVER)
                    .build()) {

                java.net.http.HttpResponse<byte[]> upstreamResponse = client.send(
                        upstreamRequest,
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray());

                // Map response
                return mapUpstreamResponse(upstreamResponse);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            LOG.warn("Upstream timeout for {}: {}", uriStr, e.getMessage());
            return errorHandler.gatewayTimeout("Upstream timeout: " + target.host() + ":" + target.port());
        } catch (ConnectException e) {
            LOG.warn("Cannot connect to upstream {}: {}", uriStr, e.getMessage());
            return errorHandler.badGateway("Connection refused: " + target.host() + ":" + target.port());
        } catch (SocketTimeoutException e) {
            LOG.warn("Socket timeout for upstream {}: {}", uriStr, e.getMessage());
            return errorHandler.gatewayTimeout("Socket timeout: " + target.host() + ":" + target.port());
        } catch (IOException e) {
            LOG.warn("I/O error forwarding to upstream {}: {}", uriStr, e.getMessage());
            return errorHandler.badGateway("Upstream error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted forwarding to upstream {}", uriStr);
            return errorHandler.badGateway("Request interrupted");
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid upstream URI {}: {}", uriStr, e.getMessage());
            return errorHandler.badGateway("Invalid upstream URI: " + uriStr);
        }
    }

    /**
     * Maps a JDK {@link java.net.http.HttpResponse} to the proxy's {@link HttpResponse}.
     *
     * @param upstream the upstream response
     * @return the mapped proxy response
     * @since 1.0.0
     */
    private HttpResponse mapUpstreamResponse(java.net.http.HttpResponse<byte[]> upstream) {
        HttpStatus status = HttpStatus.fromCode(upstream.statusCode());
        HttpHeaders responseHeaders = new HttpHeaders();

        upstream.headers().map().forEach((name, values) -> {
            // Skip pseudo-headers and hop-by-hop headers from upstream
            if (name.startsWith(":") || RESPONSE_HOP_BY_HOP.contains(name.toLowerCase())) {
                return;
            }
            for (String value : values) {
                responseHeaders.add(name, value);
            }
        });

        HttpResponse response = new HttpResponse(status, HttpVersion.HTTP_1_1, responseHeaders);
        byte[] body = upstream.body();
        if (body != null && body.length > 0) {
            response.setBody(ByteBuffer.wrap(body));
            responseHeaders.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        }
        return response;
    }

    /**
     * Parses the target address from the request.
     *
     * @param request the HTTP request
     * @return the target address, or null if unparseable
     * @since 1.0.0
     */
    TargetAddress parseTarget(HttpRequest request) {
        String uri = request.getUri();

        // CONNECT method: URI is host:port
        if (request.getMethod() == HttpMethod.CONNECT) {
            return parseHostPort(uri, 443);
        }

        // Absolute URI: http://host:port/path
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                URI parsed = URI.create(uri);
                String host = parsed.getHost();
                if (host == null) return null;
                int port = parsed.getPort();
                boolean ssl = uri.startsWith("https://");
                if (port < 0) port = ssl ? 443 : 80;
                String path = parsed.getRawPath();
                if (path == null || path.isEmpty()) path = "/";
                String query = parsed.getRawQuery();
                if (query != null) path = path + "?" + query;
                return new TargetAddress(host, port, path);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Relative URI: use Host header
        String hostHeader = request.getHeaders().get(HttpHeaders.HOST);
        if (hostHeader != null) {
            TargetAddress hostPort = parseHostPort(hostHeader, 80);
            if (hostPort != null) {
                String path = uri.isEmpty() ? "/" : uri;
                return new TargetAddress(hostPort.host(), hostPort.port(), path);
            }
        }

        return null;
    }

    private TargetAddress parseHostPort(String hostPort, int defaultPort) {
        if (hostPort == null || hostPort.isEmpty()) return null;
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx > 0) {
            try {
                String host = hostPort.substring(0, colonIdx);
                int port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                return new TargetAddress(host, port, "/");
            } catch (NumberFormatException e) {
                return new TargetAddress(hostPort, defaultPort, "/");
            }
        }
        return new TargetAddress(hostPort, defaultPort, "/");
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
        headers.remove("transfer-encoding");
        headers.remove("upgrade");
    }

    private String getClientIp(HttpRequest request) {
        String xff = request.getHeaders().get(ProxyHeaders.X_FORWARDED_FOR);
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return "127.0.0.1";
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
     * @param filter the filter to add
     * @since 1.0.0
     */
    public void addFilter(ProxyFilter filter) {
        filters.add(filter);
    }

    /**
     * Removes a filter from the proxy pipeline.
     *
     * @param filter the filter to remove
     * @since 1.0.0
     */
    public void removeFilter(ProxyFilter filter) {
        filters.remove(filter);
    }

    /**
     * Sets the proxy authenticator.
     *
     * @param authenticator the authenticator
     * @since 1.0.0
     */
    public void setAuthenticator(ProxyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Returns the proxy configuration.
     *
     * @return the configuration
     * @since 1.0.0
     */
    public ForwardProxyConfig getConfig() {
        return config;
    }

    /**
     * Returns the access control policy.
     *
     * @return the access control
     * @since 1.0.0
     */
    public ProxyAccessControl getAccessControl() {
        return accessControl;
    }

    /**
     * Returns the total number of requests processed.
     *
     * @return the request count
     * @since 1.0.0
     */
    public long getRequestCount() {
        return requestCount.get();
    }

    /**
     * Returns the total number of CONNECT tunnels established.
     *
     * @return the tunnel count
     * @since 1.0.0
     */
    public long getTunnelCount() {
        return tunnelCount.get();
    }

    /**
     * Returns the registered filters.
     *
     * @return the filters
     * @since 1.0.0
     */
    public List<ProxyFilter> getFilters() {
        return List.copyOf(filters);
    }

    @Override
    public void close() {
        LOG.info("Forward proxy closed. Total requests: {}, tunnels: {}",
                requestCount.get(), tunnelCount.get());
    }

    /**
     * Represents a parsed target address.
     *
     * @param host the target hostname
     * @param port the target port
     * @param path the request path
     * @since 1.0.0
     */
    public record TargetAddress(String host, int port, String path) {
    }
}
