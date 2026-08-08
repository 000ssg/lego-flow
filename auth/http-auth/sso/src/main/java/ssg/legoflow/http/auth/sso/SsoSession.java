package ssg.legoflow.http.auth.sso;

import ssg.legoflow.http.auth.AuthPrincipal;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Federated SSO session that spans multiple services. Tracks which services
 * the user has authenticated with and propagates login/logout across them.
 *
 * @since 0.1.0
 */
public class SsoSession {

    private final String id;
    private final AuthPrincipal principal;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private volatile boolean invalidated;
    private final Set<String> authenticatedServices = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * Creates a new SSO session.
     *
     * @param id        the session ID
     * @param principal the authenticated principal
     * @since 0.1.0
     */
    public SsoSession(String id, AuthPrincipal principal) {
        this.id = Objects.requireNonNull(id);
        this.principal = Objects.requireNonNull(principal);
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.invalidated = false;
    }

    /**
     * Records that the user has authenticated with a specific service.
     *
     * @param serviceUrl the service URL
     * @since 0.1.0
     */
    public void addAuthenticatedService(String serviceUrl) {
        authenticatedServices.add(serviceUrl);
        touch();
    }

    /**
     * Returns all services the user has authenticated with.
     *
     * @return unmodifiable set of service URLs
     * @since 0.1.0
     */
    public Set<String> getAuthenticatedServices() {
        return Collections.unmodifiableSet(authenticatedServices);
    }

    /**
     * Updates the last access time.
     *
     * @since 0.1.0
     */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Invalidates the session.
     *
     * @since 0.1.0
     */
    public void invalidate() {
        this.invalidated = true;
        attributes.clear();
    }

    /**
     * Checks if the session has expired.
     *
     * @param timeoutSeconds the timeout in seconds
     * @return true if expired
     * @since 0.1.0
     */
    public boolean isExpired(long timeoutSeconds) {
        if (invalidated) return true;
        return Instant.now().isAfter(lastAccessedAt.plusSeconds(timeoutSeconds));
    }

    /**
     * Sets a session attribute.
     *
     * @param name  the attribute name
     * @param value the attribute value
     * @since 0.1.0
     */
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * Gets a session attribute.
     *
     * @param name the attribute name
     * @param <T>  the expected type
     * @return the attribute value, or null
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    // Getters

    public String getId() { return id; }
    public AuthPrincipal getPrincipal() { return principal; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public boolean isInvalidated() { return invalidated; }
}
