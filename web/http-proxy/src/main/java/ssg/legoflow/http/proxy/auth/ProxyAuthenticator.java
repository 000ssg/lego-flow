package ssg.legoflow.http.proxy.auth;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

/**
 * Interface for proxy authentication per RFC 7235 section 4.3.
 *
 * <p>Implementations validate the Proxy-Authorization header and produce
 * 407 Proxy Authentication Required challenges when authentication fails.</p>
 *
 * @since 1.0.0
 */
public interface ProxyAuthenticator {

    /**
     * Authenticates the given request by examining the Proxy-Authorization header.
     *
     * @param request the incoming request
     * @return true if the request is authenticated
     * @since 1.0.0
     */
    boolean authenticate(HttpRequest request);

    /**
     * Creates a 407 Proxy Authentication Required response with the appropriate
     * Proxy-Authenticate challenge header.
     *
     * @return the challenge response
     * @since 1.0.0
     */
    HttpResponse createChallenge();

    /**
     * Returns the authentication scheme name (e.g. "Basic", "Digest").
     *
     * @return the scheme name
     * @since 1.0.0
     */
    String getScheme();
}
