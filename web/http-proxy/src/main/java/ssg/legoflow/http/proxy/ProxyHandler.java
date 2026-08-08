package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpRequestHandler;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.proxy.forward.ForwardProxy;
import ssg.legoflow.http.proxy.reverse.ReverseProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpRequestHandler integration for the http module's HttpRouter.
 *
 * <p>This handler bridges the proxy module with the HTTP server's routing
 * infrastructure. It can be registered on any path to delegate requests
 * to either a forward proxy or a reverse proxy.</p>
 *
 * @since 0.1.0
 */
public class ProxyHandler implements HttpRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final ForwardProxy forwardProxy;
    private final ReverseProxy reverseProxy;
    private final ProxyErrorHandler errorHandler;

    /**
     * Creates a proxy handler that delegates to a forward proxy.
     *
     * @param forwardProxy the forward proxy
     * @return a new handler
     * @since 0.1.0
     */
    public static ProxyHandler forForwardProxy(ForwardProxy forwardProxy) {
        return new ProxyHandler(forwardProxy, null);
    }

    /**
     * Creates a proxy handler that delegates to a reverse proxy.
     *
     * @param reverseProxy the reverse proxy
     * @return a new handler
     * @since 0.1.0
     */
    public static ProxyHandler forReverseProxy(ReverseProxy reverseProxy) {
        return new ProxyHandler(null, reverseProxy);
    }

    private ProxyHandler(ForwardProxy forwardProxy, ReverseProxy reverseProxy) {
        this.forwardProxy = forwardProxy;
        this.reverseProxy = reverseProxy;
        this.errorHandler = new ProxyErrorHandler("ProxyHandler");
    }

    /**
     * Handles an HTTP request by delegating to the configured proxy.
     *
     * @param ctx the HTTP context
     * @param request the incoming request
     * @return the proxy response
     * @since 0.1.0
     */
    @Override
    public HttpResponse handle(HttpContext ctx, HttpRequest request) {
        try {
            if (forwardProxy != null) {
                return forwardProxy.handleRequest(request);
            } else if (reverseProxy != null) {
                return reverseProxy.handleRequest(request);
            } else {
                return errorHandler.serviceUnavailable("No proxy configured");
            }
        } catch (Exception e) {
            LOG.error("Proxy error handling request {} {}", request.getMethod(), request.getUri(), e);
            return errorHandler.handleError(e);
        }
    }

    /**
     * Returns the forward proxy, or null if this is a reverse proxy handler.
     *
     * @return the forward proxy
     * @since 0.1.0
     */
    public ForwardProxy getForwardProxy() {
        return forwardProxy;
    }

    /**
     * Returns the reverse proxy, or null if this is a forward proxy handler.
     *
     * @return the reverse proxy
     * @since 0.1.0
     */
    public ReverseProxy getReverseProxy() {
        return reverseProxy;
    }
}
