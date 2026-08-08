package ssg.legoflow.http.auth.sso;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single Sign-On orchestrator managing federated sessions across services.
 * Uses JWT tokens for session federation and cookie-based session tracking.
 *
 * @since 0.1.0
 */
public class SsoManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SsoManager.class);
    private static final String COOKIE_HEADER = "cookie";
    private static final String SET_COOKIE_HEADER = "set-cookie";

    private final SsoConfig config;
    private final JwtTokenProvider tokenProvider;
    private final Map<String, SsoSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates an SSO manager.
     *
     * @param config        the SSO configuration
     * @param tokenProvider the JWT token provider for SSO tokens
     * @since 0.1.0
     */
    public SsoManager(SsoConfig config, JwtTokenProvider tokenProvider) {
        this.config = Objects.requireNonNull(config);
        this.tokenProvider = Objects.requireNonNull(tokenProvider);
    }

    /**
     * Creates a new SSO session and sets the SSO cookie.
     *
     * @param principal the authenticated principal
     * @param response  the HTTP response to add the cookie to
     * @return the SSO session
     * @since 0.1.0
     */
    public SsoSession login(AuthPrincipal principal, HttpResponse response) {
        String sessionId = generateSessionId();
        var session = new SsoSession(sessionId, principal);
        sessions.put(sessionId, session);

        // Generate JWT SSO token
        String token = tokenProvider.generateToken(principal.getName(),
                Map.of("session_id", sessionId, "roles", String.join(",", principal.getRoles())));

        // Set SSO cookie
        String cookie = config.getCookieName() + "=" + token
                + "; Path=/; Domain=" + config.getDomain()
                + "; HttpOnly; SameSite=Lax"
                + (config.isSecureCookies() ? "; Secure" : "");
        response.getHeaders().add(SET_COOKIE_HEADER, cookie);

        LOG.info("SSO login for principal: {}, session: {}", principal.getName(), sessionId);
        return session;
    }

    /**
     * Validates an SSO session from the request cookie.
     *
     * @param request the HTTP request
     * @return the SSO session, or empty if not authenticated
     * @since 0.1.0
     */
    public Optional<SsoSession> validateSession(HttpRequest request) {
        String cookieHeader = request.getHeaders().get(COOKIE_HEADER);
        if (cookieHeader == null) return Optional.empty();

        String token = extractCookieValue(cookieHeader, config.getCookieName());
        if (token == null) return Optional.empty();

        // Validate JWT token
        var claims = tokenProvider.validateToken(token);
        if (claims.isEmpty()) {
            LOG.debug("Invalid SSO token");
            return Optional.empty();
        }

        String sessionId = (String) claims.get().get("session_id");
        if (sessionId == null) return Optional.empty();

        var session = sessions.get(sessionId);
        if (session == null || session.isInvalidated()) return Optional.empty();

        long timeoutSeconds = config.getSessionTimeout().toSeconds();
        if (session.isExpired(timeoutSeconds)) {
            sessions.remove(sessionId);
            LOG.debug("SSO session expired: {}", sessionId);
            return Optional.empty();
        }

        session.touch();
        return Optional.of(session);
    }

    /**
     * Logs out from SSO, invalidating the session and clearing the cookie.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return the set of service URLs that need logout propagation
     * @since 0.1.0
     */
    public Set<String> logout(HttpRequest request, HttpResponse response) {
        String cookieHeader = request.getHeaders().get(COOKIE_HEADER);
        if (cookieHeader == null) return Set.of();

        String token = extractCookieValue(cookieHeader, config.getCookieName());
        if (token == null) return Set.of();

        var claims = tokenProvider.validateToken(token);
        Set<String> services = Set.of();
        if (claims.isPresent()) {
            String sessionId = (String) claims.get().get("session_id");
            if (sessionId != null) {
                var session = sessions.remove(sessionId);
                if (session != null) {
                    services = session.getAuthenticatedServices();
                    session.invalidate();
                    LOG.info("SSO logout for session: {}", sessionId);
                }
            }
        }

        // Clear SSO cookie
        String deleteCookie = config.getCookieName() + "=; Path=/; Domain=" + config.getDomain()
                + "; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT";
        response.getHeaders().add(SET_COOKIE_HEADER, deleteCookie);

        return services;
    }

    /**
     * Returns the number of active SSO sessions.
     *
     * @return the session count
     * @since 0.1.0
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Cleans up expired sessions.
     *
     * @return the number of sessions removed
     * @since 0.1.0
     */
    public int cleanExpiredSessions() {
        long timeoutSeconds = config.getSessionTimeout().toSeconds();
        var expired = sessions.entrySet().stream()
                .filter(e -> e.getValue().isExpired(timeoutSeconds))
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(id -> {
            var s = sessions.remove(id);
            if (s != null) s.invalidate();
        });
        return expired.size();
    }

    /**
     * Returns the SSO configuration.
     *
     * @return the config
     * @since 0.1.0
     */
    public SsoConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        LOG.info("Closing SSO manager, {} active sessions", sessions.size());
        sessions.values().forEach(SsoSession::invalidate);
        sessions.clear();
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractCookieValue(String cookieHeader, String name) {
        String prefix = name + "=";
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }
}
