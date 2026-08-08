package ssg.legoflow.http.auth.session;

import java.util.Collection;
import java.util.Optional;

/**
 * Storage interface for HTTP sessions. Implementations can store sessions in memory,
 * in a database, or in a distributed cache.
 *
 * @since 0.1.0
 */
public interface SessionStore {

    /**
     * Creates a new session with the given ID.
     *
     * @param id the session ID
     * @return the created session
     * @since 0.1.0
     */
    HttpSession create(String id);

    /**
     * Retrieves a session by ID.
     *
     * @param id the session ID
     * @return the session, or empty if not found or expired
     * @since 0.1.0
     */
    Optional<HttpSession> get(String id);

    /**
     * Removes a session by ID.
     *
     * @param id the session ID
     * @since 0.1.0
     */
    void remove(String id);

    /**
     * Removes all expired sessions.
     *
     * @param timeoutSeconds the session timeout in seconds
     * @since 0.1.0
     */
    void removeExpired(long timeoutSeconds);

    /**
     * Returns the number of active sessions.
     *
     * @return the session count
     * @since 0.1.0
     */
    int size();

    /**
     * Returns all active sessions.
     *
     * @return unmodifiable collection of sessions
     * @since 0.1.0
     */
    Collection<HttpSession> all();
}
