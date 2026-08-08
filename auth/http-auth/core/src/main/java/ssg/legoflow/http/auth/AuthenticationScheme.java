package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

/**
 * Interface for HTTP authentication schemes (RFC 7235). Each scheme knows how to
 * extract credentials from a request, authenticate them, and issue challenges.
 *
 * <p>Implementations include Basic (RFC 7617), Digest (RFC 7616), Bearer (RFC 6750),
 * and custom schemes.</p>
 *
 * @since 0.1.0
 */
public interface AuthenticationScheme {

    /**
     * Returns the scheme name as it appears in the Authorization header
     * (e.g., "Basic", "Bearer", "Digest").
     *
     * @return the scheme name
     * @since 0.1.0
     */
    String schemeName();

    /**
     * Authenticates an HTTP request using this scheme.
     *
     * @param request the HTTP request
     * @param context the authentication context with realm, user store, etc.
     * @return the authentication result (success, failure, or challenge)
     * @since 0.1.0
     */
    AuthResult authenticate(HttpRequest request, AuthContext context);

    /**
     * Adds a WWW-Authenticate challenge header to the response for this scheme.
     *
     * @param response the HTTP response to add the challenge to
     * @param context  the authentication context (for realm, etc.)
     * @since 0.1.0
     */
    void challenge(HttpResponse response, AuthContext context);

    /**
     * Extracts credentials from the HTTP request for this scheme.
     *
     * @param request the HTTP request
     * @return the extracted credentials, or {@link AuthCredentials.None} if not present
     * @since 0.1.0
     */
    AuthCredentials extractCredentials(HttpRequest request);
}
