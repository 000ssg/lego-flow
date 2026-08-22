package ssg.legoflow.wamp.core.realm;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSession;
import ssg.legoflow.wamp.core.router.Broker;
import ssg.legoflow.wamp.core.router.Dealer;
import ssg.legoflow.wamp.core.transport.WampTransport;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/**
 * A WAMP realm provides isolation for sessions, subscriptions, and registrations.
 * Each realm has its own Broker and Dealer.
 *
 * @since 0.1.0
 */
public class Realm {

    private final String name;
    private final Broker broker;
    private final Dealer dealer;
    private final AtomicLong sessionIdCounter = new AtomicLong(1);
    private final Map<Long, WampSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a new realm with the given name.
     *
     * @param name the realm name
     */
    public Realm(String name) {
        this.name = name;
        this.broker = new Broker();
        this.dealer = new Dealer();
    }

    /**
     * Returns the realm name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the Broker for this realm.
     *
     * @return the broker
     */
    public Broker getBroker() {
        return broker;
    }

    /**
     * Returns the Dealer for this realm.
     *
     * @return the dealer
     */
    public Dealer getDealer() {
        return dealer;
    }

    /**
     * Returns a snapshot of all active sessions in this realm.
     * <p>
     * The returned map is unmodifiable and reflects the session set at the
     * time of the call; it is not live-linked to the internal registry.
     *
     * @return unmodifiable map of session ID to session
     * @since 0.2.0
     */
    public Map<Long, WampSession> getActiveSessions() {
        return Map.copyOf(sessions);
    }

    /**
     * Adds a new session to this realm, establishing it with a unique session ID.
     *
     * @param transport the client's transport
     * @return the Welcome message to send back to the client
     */
    public WampMessage.Welcome addSession(WampTransport transport) {
        long sessionId = sessionIdCounter.getAndIncrement();
        var session = new WampSession();
        session.establish(sessionId, name);
        sessions.put(sessionId, session);
        return new WampMessage.Welcome(sessionId, Map.of("roles", Map.of(
                "broker", Map.of(),
                "dealer", Map.of()
        )));
    }
    /**
     * Adds a virtual session with the given authentication context.
     * <p>
     * Virtual sessions have no transport — they exist purely for identity mapping
     * (e.g., HTTP-authenticated users calling WAMP procedures via REST bridge).
     * Unlike {@link #addSession(WampTransport)}, this method does not require
     * a transport and does not send a Welcome message.
     *
     * @param authId    the authentication identity (may be null)
     * @param authRole  the authentication role (may be null)
     * @param authMethod the auth method used (may be null)
     * @return the assigned session ID
     * @since 0.2.0
     */
    public long addVirtualSession(String authId, String authRole, String authMethod) {
        long sessionId = sessionIdCounter.getAndIncrement();
        var session = new WampSession();
        session.establish(sessionId, name);
        if (authId != null) session.setAuthId(authId);
        if (authRole != null) session.setAuthRole(authRole);
        if (authMethod != null) session.setAuthMethod(authMethod);
        sessions.put(sessionId, session);
        return sessionId;
    }

    /**
     * Removes a session from this realm.
     *
     * @param sessionId the session to remove
     */
    public void removeSession(long sessionId) {
        var session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    /**
     * Returns the number of active sessions in this realm.
     *
     * @return the session count
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Returns the session with the given ID, or {@code null} if not found.
     *
     * @param sessionId the session ID
     * @return the session, or null
     */
    public WampSession getSession(long sessionId) {
        return sessions.get(sessionId);
    }
}
