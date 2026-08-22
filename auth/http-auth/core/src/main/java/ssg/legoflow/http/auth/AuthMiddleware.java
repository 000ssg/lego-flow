package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.Set;
/**
 * HTTP request handler wrapper that enforces authentication before delegating
 * to the wrapped handler. Supports path exclusions (e.g., for health checks)
 * and role-based access control.
 *
 * @since 0.1.0
 */
public class AuthMiddleware implements HttpRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthMiddleware.class);

    private final HttpRequestHandler delegate;
    private final AuthFilter authFilter;
    private final Set<String> excludedPaths;
    private final Set<String> requiredRoles;

    /**
     * Creates an authentication middleware.
     *
     * @param delegate      the handler to delegate to after successful auth
     * @param authFilter    the auth filter to use
     * @param excludedPaths paths that bypass authentication
     * @param requiredRoles roles required for access (empty means any authenticated user)
     * @since 0.1.0
     */
    public AuthMiddleware(HttpRequestHandler delegate, AuthFilter authFilter,
                          Set<String> excludedPaths, Set<String> requiredRoles) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.authFilter = Objects.requireNonNull(authFilter, "authFilter must not be null");
        this.excludedPaths = excludedPaths != null ? Set.copyOf(excludedPaths) : Set.of();
        this.requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
    }

    /**
     * Creates an authentication middleware with no exclusions or role requirements.
     *
     * @param delegate   the handler to delegate to
     * @param authFilter the auth filter
     * @since 0.1.0
     */
    public AuthMiddleware(HttpRequestHandler delegate, AuthFilter authFilter) {
        this(delegate, authFilter, Set.of(), Set.of());
    }

    @Override
    public HttpResponse handle(HttpContext ctx, HttpRequest request) {
        String path = extractPath(request.getUri());

        // Skip auth for excluded paths
        if (excludedPaths.contains(path)) {
            LOG.debug("Path {} is excluded from authentication", path);
            return delegate.handle(ctx, request);
        }

        // Run authentication
        AuthResult result = authFilter.filter(request);

        return switch (result) {
            case AuthResult.Success success -> {
                // Check role requirements
                if (!requiredRoles.isEmpty()) {
                    boolean hasRole = requiredRoles.stream()
                            .anyMatch(success.principal()::hasRole);
                    if (!hasRole) {
                        LOG.debug("Principal {} lacks required roles: {}",
                                success.principal().getName(), requiredRoles);
                        yield HttpResponse.of(HttpStatus.FORBIDDEN, "Forbidden");
                    }
                }
                yield delegate.handle(ctx, request);
            }
            case AuthResult.Challenge challenge ->
                    authFilter.buildChallengeResponse(challenge.schemeName());
            case AuthResult.Failure failure -> {
                LOG.debug("Authentication failed: {}", failure.reason());
                yield authFilter.buildChallengeResponse();
            }
        };
    }

    private String extractPath(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(0, queryStart) : uri;
    }

    /**
     * Returns the delegate handler.
     *
     * @return the delegate
     * @since 0.1.0
     */
    public HttpRequestHandler getDelegate() {
        return delegate;
    }

    /**
     * Returns the excluded paths.
     *
     * @return the excluded paths
     * @since 0.1.0
     */
    public Set<String> getExcludedPaths() {
        return excludedPaths;
    }

    /**
     * Returns the required roles.
     *
     * @return the required roles
     * @since 0.1.0
     */
    public Set<String> getRequiredRoles() {
        return requiredRoles;
    }
}
