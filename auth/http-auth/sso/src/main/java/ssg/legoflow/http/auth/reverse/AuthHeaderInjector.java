package ssg.legoflow.http.auth.reverse;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.core.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Injects authentication headers into HTTP requests for backend services.
 * Used by reverse proxy SSO to propagate authentication information.
 *
 * @since 0.1.0
 */
public class AuthHeaderInjector {

    private static final Logger LOG = LoggerFactory.getLogger(AuthHeaderInjector.class);

    private final ReverseProxySsoConfig config;

    /**
     * Creates an auth header injector.
     *
     * @param config the reverse proxy SSO configuration
     * @since 0.1.0
     */
    public AuthHeaderInjector(ReverseProxySsoConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Injects authentication headers into the request for the authenticated principal.
     *
     * @param request   the HTTP request to modify
     * @param principal the authenticated principal
     * @since 0.1.0
     */
    public void injectHeaders(HttpRequest request, AuthPrincipal principal) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(principal);

        request.getHeaders().set(config.getUserHeader(), principal.getName());

        if (!principal.getRoles().isEmpty()) {
            request.getHeaders().set(config.getRolesHeader(),
                    String.join(",", principal.getRoles()));
        }

        String email = principal.getAttribute("email");
        if (email != null) {
            request.getHeaders().set(config.getEmailHeader(), email);
        }

        String name = principal.getAttribute("display_name");
        if (name != null) {
            request.getHeaders().set(config.getNameHeader(), name);
        }

        LOG.debug("Injected auth headers for principal: {}", principal.getName());
    }

    /**
     * Strips authentication headers from a request to prevent spoofing.
     * Should be called on incoming requests before processing.
     *
     * @param request the HTTP request
     * @since 0.1.0
     */
    public void stripHeaders(HttpRequest request) {
        request.getHeaders().remove(config.getUserHeader());
        request.getHeaders().remove(config.getRolesHeader());
        request.getHeaders().remove(config.getEmailHeader());
        request.getHeaders().remove(config.getNameHeader());
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
