package ssg.legoflow.http.auth.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP session with attributes, creation time, and last access tracking.
 * Thread-safe — attributes are stored in a ConcurrentHashMap.
 *
 * @since 1.0.0
 */
public class HttpSession {

    private final String id;
    private final Instant creationTime;
    private volatile Instant lastAccessTime;
    private volatile boolean invalidated;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * Creates a new HTTP session.
     *
     * @param id the session identifier
     * @since 1.0.0
     */
    public HttpSession(String id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.creationTime = Instant.now();
        this.lastAccessTime = this.creationTime;
        this.invalidated = false;
    }

    /**
     * Returns the session identifier.
     *
     * @return the session ID
     * @since 1.0.0
     */
    public String getId() {
        return id;
    }

    /**
     * Returns when this session was created.
     *
     * @return the creation time
     * @since 1.0.0
     */
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Returns when this session was last accessed.
     *
     * @return the last access time
     * @since 1.0.0
     */
    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Updates the last access time to now.
     *
     * @since 1.0.0
     */
    public void touch() {
        this.lastAccessTime = Instant.now();
    }

    /**
     * Sets a session attribute.
     *
     * @param name  the attribute name
     * @param value the attribute value
     * @since 1.0.0
     */
    public void setAttribute(String name, Object value) {
        checkValid();
        attributes.put(name, value);
    }

    /**
     * Returns a session attribute.
     *
     * @param name the attribute name
     * @param <T>  the expected value type
     * @return the attribute value, or null if not present
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        checkValid();
        return (T) attributes.get(name);
    }

    /**
     * Removes a session attribute.
     *
     * @param name the attribute name
     * @since 1.0.0
     */
    public void removeAttribute(String name) {
        checkValid();
        attributes.remove(name);
    }

    /**
     * Returns all attribute names.
     *
     * @return the attribute names
     * @since 1.0.0
     */
    public java.util.Set<String> getAttributeNames() {
        checkValid();
        return java.util.Collections.unmodifiableSet(attributes.keySet());
    }

    /**
     * Invalidates this session, clearing all attributes.
     *
     * @since 1.0.0
     */
    public void invalidate() {
        this.invalidated = true;
        attributes.clear();
    }

    /**
     * Returns whether this session has been invalidated.
     *
     * @return true if invalidated
     * @since 1.0.0
     */
    public boolean isInvalidated() {
        return invalidated;
    }

    /**
     * Checks if this session has expired based on the given timeout.
     *
     * @param timeoutSeconds the timeout in seconds
     * @return true if expired
     * @since 1.0.0
     */
    public boolean isExpired(long timeoutSeconds) {
        if (invalidated) return true;
        return Instant.now().isAfter(lastAccessTime.plusSeconds(timeoutSeconds));
    }

    private void checkValid() {
        if (invalidated) {
            throw new IllegalStateException("Session has been invalidated: " + id);
        }
    }

    @Override
    public String toString() {
        return "HttpSession{id='" + id + "', created=" + creationTime +
                ", lastAccess=" + lastAccessTime + ", invalidated=" + invalidated + "}";
    }
}
