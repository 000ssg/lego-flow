package ssg.legoflow.http.auth.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory session store. Thread-safe using ConcurrentHashMap.
 * Suitable for single-server deployments and testing.
 *
 * @since 0.1.0
 */
public class InMemorySessionStore implements SessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySessionStore.class);

    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

    @Override
    public HttpSession create(String id) {
        var session = new HttpSession(id);
        sessions.put(id, session);
        LOG.debug("Created session: {}", id);
        return session;
    }

    @Override
    public Optional<HttpSession> get(String id) {
        if (id == null) return Optional.empty();
        var session = sessions.get(id);
        if (session != null && session.isInvalidated()) {
            sessions.remove(id);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    @Override
    public void remove(String id) {
        var session = sessions.remove(id);
        if (session != null) {
            session.invalidate();
            LOG.debug("Removed session: {}", id);
        }
    }

    @Override
    public void removeExpired(long timeoutSeconds) {
        var expired = new ArrayList<String>();
        sessions.forEach((id, session) -> {
            if (session.isExpired(timeoutSeconds)) {
                expired.add(id);
            }
        });
        for (String id : expired) {
            sessions.remove(id);
            LOG.debug("Expired session removed: {}", id);
        }
        if (!expired.isEmpty()) {
            LOG.info("Removed {} expired sessions", expired.size());
        }
    }

    @Override
    public int size() {
        return sessions.size();
    }

    @Override
    public Collection<HttpSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}
