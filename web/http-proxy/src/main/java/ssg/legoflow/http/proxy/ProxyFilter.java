package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

/**
 * Filter interface for request/response modification in the proxy pipeline.
 *
 * <p>Proxy filters can modify requests before forwarding to the upstream
 * and modify responses before returning to the client. Filters are applied
 * in order of registration.</p>
 *
 * @since 0.1.0
 */
public interface ProxyFilter {

    /**
     * Filters a request before it is forwarded to the upstream server.
     * Implementations may modify headers, URI, or body.
     *
     * @param request the outgoing request
     * @return the filtered request (may be the same instance or a new one)
     * @since 0.1.0
     */
    HttpRequest filterRequest(HttpRequest request);

    /**
     * Filters a response before it is returned to the client.
     * Implementations may modify headers, status, or body.
     *
     * @param response the incoming response from upstream
     * @return the filtered response (may be the same instance or a new one)
     * @since 0.1.0
     */
    HttpResponse filterResponse(HttpResponse response);

    /**
     * Returns the name of this filter for logging and identification.
     *
     * @return the filter name
     * @since 0.1.0
     */
    String getName();

    /**
     * Returns the order of this filter. Lower values execute first.
     * Default is 0.
     *
     * @return the filter order
     * @since 0.1.0
     */
    default int getOrder() {
        return 0;
    }
}
