package ssg.legoflow.http.auth.session;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP session manager that creates, retrieves, and destroys sessions.
 * Uses cookie-based session tracking with configurable session cookies.
 * Implements {@link AutoCloseable} for proper resource cleanup.
 *
 * @since 1.0.0
 */
public class SessionManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);
    private static final String COOKIE_HEADER = "cookie";
    private static final String SET_COOKIE_HEADER = "set-cookie";

    private final SessionStore store;
    private final SessionCookie cookieConfig;
    private final long sessionTimeoutSeconds;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a session manager.
     *
     * @param store                the session store
     * @param cookieConfig         the session cookie configuration
     * @param sessionTimeoutSeconds the session timeout in seconds
     * @since 1.0.0
     */
    public SessionManager(SessionStore store, SessionCookie cookieConfig, long sessionTimeoutSeconds) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.cookieConfig = Objects.requireNonNull(cookieConfig, "cookieConfig must not be null");
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    /**
     * Creates a session manager with default cookie configuration and 30-minute timeout.
     *
     * @param store the session store
     * @since 1.0.0
     */
    public SessionManager(SessionStore store) {
        this(store, SessionCookie.defaults(), 1800);
    }

    /**
     * Creates a new session and adds the Set-Cookie header to the response.
     *
     * @param response the HTTP response
     * @return the created session
     * @since 1.0.0
     */
    public HttpSession createSession(HttpResponse response) {
        String sessionId = generateSessionId();
        var session = store.create(sessionId);
        response.getHeaders().add(SET_COOKIE_HEADER, cookieConfig.buildSetCookieHeader(sessionId));
        LOG.debug("Created new session: {}", sessionId);
        return session;
    }

    /**
     * Retrieves an existing session from the request cookie.
     *
     * @param request the HTTP request
     * @return the session, or empty if not found or expired
     * @since 1.0.0
     */
    public Optional<HttpSession> getSession(HttpRequest request) {
        String cookieHeader = request.getHeaders().get(COOKIE_HEADER);
        String sessionId = cookieConfig.extractSessionId(cookieHeader);
        if (sessionId == null) {
            return Optional.empty();
        }
        var session = store.get(sessionId);
        if (session.isPresent()) {
            var s = session.get();
            if (s.isExpired(sessionTimeoutSeconds)) {
                store.remove(sessionId);
                LOG.debug("Session expired: {}", sessionId);
                return Optional.empty();
            }
            s.touch();
        }
        return session;
    }

    /**
     * Retrieves or creates a session for the request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @return the existing or new session
     * @since 1.0.0
     */
    public HttpSession getOrCreateSession(HttpRequest request, HttpResponse response) {
        return getSession(request).orElseGet(() -> createSession(response));
    }

    /**
     * Destroys a session and adds the delete cookie header to the response.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @since 1.0.0
     */
    public void destroySession(HttpRequest request, HttpResponse response) {
        String cookieHeader = request.getHeaders().get(COOKIE_HEADER);
        String sessionId = cookieConfig.extractSessionId(cookieHeader);
        if (sessionId != null) {
            store.remove(sessionId);
            response.getHeaders().add(SET_COOKIE_HEADER, cookieConfig.buildDeleteCookieHeader());
            LOG.debug("Destroyed session: {}", sessionId);
        }
    }

    /**
     * Removes expired sessions from the store.
     *
     * @since 1.0.0
     */
    public void cleanExpiredSessions() {
        store.removeExpired(sessionTimeoutSeconds);
    }

    /**
     * Returns the number of active sessions.
     *
     * @return the session count
     * @since 1.0.0
     */
    public int getActiveSessionCount() {
        return store.size();
    }

    /**
     * Returns the session timeout in seconds.
     *
     * @return the timeout
     * @since 1.0.0
     */
    public long getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    /**
     * Returns the session cookie configuration.
     *
     * @return the cookie config
     * @since 1.0.0
     */
    public SessionCookie getCookieConfig() {
        return cookieConfig;
    }

    @Override
    public void close() {
        LOG.info("Closing session manager, {} active sessions", store.size());
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
