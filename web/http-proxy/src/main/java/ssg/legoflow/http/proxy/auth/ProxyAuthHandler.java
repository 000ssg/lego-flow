package ssg.legoflow.http.proxy.auth;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles 407 Proxy Authentication Required responses.
 *
 * <p>Coordinates authentication between the client and proxy using
 * the configured authenticator. When a 407 response is received,
 * this handler can supply the appropriate Proxy-Authorization header.</p>
 *
 * @since 0.1.0
 */
public class ProxyAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyAuthHandler.class);

    private final ProxyAuthenticator authenticator;
    private int maxRetries = 3;

    /**
     * Creates a new proxy auth handler.
     *
     * @param authenticator the authenticator to use
     * @since 0.1.0
     */
    public ProxyAuthHandler(ProxyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Checks whether a response is a 407 Proxy Authentication Required.
     *
     * @param response the response to check
     * @return true if 407
     * @since 0.1.0
     */
    public boolean isAuthRequired(HttpResponse response) {
        return response.getStatus() == HttpStatus.PROXY_AUTHENTICATION_REQUIRED;
    }

    /**
     * Attempts to authenticate the given request.
     *
     * @param request the request to authenticate
     * @return true if authentication succeeds
     * @since 0.1.0
     */
    public boolean handleAuth(HttpRequest request) {
        return authenticator.authenticate(request);
    }

    /**
     * Creates a challenge response.
     *
     * @return the 407 response with Proxy-Authenticate header
     * @since 0.1.0
     */
    public HttpResponse createChallenge() {
        return authenticator.createChallenge();
    }

    /**
     * Returns the authentication scheme.
     *
     * @return the scheme name
     * @since 0.1.0
     */
    public String getScheme() {
        return authenticator.getScheme();
    }

    /**
     * Returns the maximum number of authentication retries.
     *
     * @return the max retries
     * @since 0.1.0
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of authentication retries.
     *
     * @param maxRetries the max retries
     * @since 0.1.0
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Returns the underlying authenticator.
     *
     * @return the authenticator
     * @since 0.1.0
     */
    public ProxyAuthenticator getAuthenticator() {
        return authenticator;
    }
}
