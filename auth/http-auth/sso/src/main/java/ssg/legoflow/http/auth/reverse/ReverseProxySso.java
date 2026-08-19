package ssg.legoflow.http.auth.reverse;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.session.SessionManager;
import ssg.legoflow.http.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
/**
 * Reverse proxy SSO implementation. Validates authentication at the proxy level
 * and injects authenticated user headers for backend services.
 *
 * <p>Flow: Client -> Reverse Proxy (auth check) -> Backend (trusts proxy headers)</p>
 *
 * @since 0.1.0
 */
public class ReverseProxySso {

    private static final Logger LOG = LoggerFactory.getLogger(ReverseProxySso.class);

    private final ReverseProxySsoConfig config;
    private final AuthHeaderInjector headerInjector;
    private final SessionManager sessionManager;

    /**
     * Creates a reverse proxy SSO handler.
     *
     * @param config         the configuration
     * @param sessionManager the session manager for proxy-level sessions
     * @since 0.1.0
     */
    public ReverseProxySso(ReverseProxySsoConfig config, SessionManager sessionManager) {
        this.config = Objects.requireNonNull(config);
        this.headerInjector = new AuthHeaderInjector(config);
        this.sessionManager = sessionManager;
    }

    /**
     * Creates a reverse proxy SSO handler without session management.
     *
     * @param config the configuration
     * @since 0.1.0
     */
    public ReverseProxySso(ReverseProxySsoConfig config) {
        this(config, null);
    }

    /**
     * Extracts the authenticated principal from reverse proxy headers.
     *
     * @param request the incoming HTTP request
     * @return the principal, or empty if no auth headers present
     * @since 0.1.0
     */
    public Optional<AuthPrincipal> extractPrincipal(HttpRequest request) {
        String username = request.getHeaders().get(config.getUserHeader());
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Set<String> roles = Set.of();
        String rolesHeader = request.getHeaders().get(config.getRolesHeader());
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            roles = Set.of(rolesHeader.split(","));
        }

        Map<String, Object> attrs = new HashMap<>();
        String email = request.getHeaders().get(config.getEmailHeader());
        if (email != null) attrs.put("email", email);
        String name = request.getHeaders().get(config.getNameHeader());
        if (name != null) attrs.put("display_name", name);

        return Optional.of(new AuthPrincipal(username, roles, attrs));
    }

    /**
     * Prepares a request for forwarding to a backend by injecting auth headers.
     *
     * @param request   the request to forward
     * @param principal the authenticated principal
     * @since 0.1.0
     */
    public void prepareBackendRequest(HttpRequest request, AuthPrincipal principal) {
        // Strip any existing auth headers to prevent spoofing
        headerInjector.stripHeaders(request);
        // Inject authenticated headers
        headerInjector.injectHeaders(request, principal);
    }

    /**
     * Handles a proxy authentication check. Returns 401 if not authenticated,
     * or injects headers and signals to forward.
     *
     * @param request  the incoming request
     * @param response the response to populate if auth fails
     * @return the authenticated principal, or empty if not authenticated
     * @since 0.1.0
     */
    public Optional<AuthPrincipal> authenticate(HttpRequest request, HttpResponse response) {
        var principal = extractPrincipal(request);
        if (principal.isEmpty()) {
            LOG.debug("No authentication headers found in proxy request");
        }
        return principal;
    }

    /**
     * Returns the header injector.
     *
     * @return the injector
     * @since 0.1.0
     */
    public AuthHeaderInjector getHeaderInjector() {
        return headerInjector;
    }

    /**
     * Returns the configuration.
     *
     * @return the config
     * @since 0.1.0
     */
    public ReverseProxySsoConfig getConfig() {
        return config;
    }
}
