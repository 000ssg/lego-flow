package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.util.Objects;
/**
 * HTTP request filter that runs authentication schemes against incoming requests.
 * Examines the Authorization header, finds the matching scheme in the registry,
 * and delegates authentication.
 *
 * @since 0.1.0
 */
public class AuthFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

    private final AuthSchemeRegistry registry;
    private final AuthContext context;
    private final String defaultScheme;

    /**
     * Creates an authentication filter.
     *
     * @param registry      the scheme registry
     * @param context       the authentication context
     * @param defaultScheme the default scheme name to use when no Authorization header is present
     * @since 0.1.0
     */
    public AuthFilter(AuthSchemeRegistry registry, AuthContext context, String defaultScheme) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.defaultScheme = defaultScheme;
    }

    /**
     * Creates an authentication filter without a default scheme.
     *
     * @param registry the scheme registry
     * @param context  the authentication context
     * @since 0.1.0
     */
    public AuthFilter(AuthSchemeRegistry registry, AuthContext context) {
        this(registry, context, null);
    }

    /**
     * Filters an HTTP request, performing authentication.
     *
     * @param request the HTTP request
     * @return the authentication result
     * @since 0.1.0
     */
    public AuthResult filter(HttpRequest request) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            if (defaultScheme != null) {
                LOG.debug("No Authorization header, issuing challenge for default scheme: {}", defaultScheme);
                return AuthResult.challenge(defaultScheme);
            }
            return AuthResult.failure("No Authorization header present");
        }

        // Parse scheme name from "Scheme credentials" format
        String schemeName = parseScheme(authHeader);
        var scheme = registry.get(schemeName);

        if (scheme.isEmpty()) {
            LOG.warn("Unknown authentication scheme: {}", schemeName);
            return AuthResult.failure("Unknown authentication scheme: " + schemeName);
        }

        AuthResult result = scheme.get().authenticate(request, context);

        if (result instanceof AuthResult.Success success) {
            context.setAuthenticatedPrincipal(success.principal());
            LOG.debug("Authentication successful for principal: {}", success.principal().getName());
        } else if (result instanceof AuthResult.Failure failure) {
            LOG.debug("Authentication failed: {}", failure.reason());
        }

        return result;
    }

    /**
     * Builds a 401 Unauthorized response with WWW-Authenticate challenges
     * from all registered schemes.
     *
     * @return the 401 response with challenge headers
     * @since 0.1.0
     */
    public HttpResponse buildChallengeResponse() {
        HttpResponse response = HttpResponse.of(HttpStatus.UNAUTHORIZED, "Unauthorized");
        for (AuthenticationScheme scheme : registry.schemes()) {
            scheme.challenge(response, context);
        }
        return response;
    }

    /**
     * Builds a 401 response with a challenge for a specific scheme.
     *
     * @param schemeName the scheme name
     * @return the 401 response
     * @since 0.1.0
     */
    public HttpResponse buildChallengeResponse(String schemeName) {
        HttpResponse response = HttpResponse.of(HttpStatus.UNAUTHORIZED, "Unauthorized");
        registry.get(schemeName).ifPresent(scheme -> scheme.challenge(response, context));
        return response;
    }

    private String parseScheme(String authHeader) {
        int space = authHeader.indexOf(' ');
        if (space > 0) {
            return authHeader.substring(0, space).toLowerCase(Locale.ROOT);
        }
        return authHeader.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the underlying authentication context.
     *
     * @return the auth context
     * @since 0.1.0
     */
    public AuthContext getContext() {
        return context;
    }

    /**
     * Returns the scheme registry.
     *
     * @return the registry
     * @since 0.1.0
     */
    public AuthSchemeRegistry getRegistry() {
        return registry;
    }
}
