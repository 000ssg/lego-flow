package ssg.legoflow.wamp.core.router;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSession;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combined WAMP router providing both Broker (pub/sub) and Dealer (RPC) functionality.
 * Routes incoming messages from clients to the appropriate handler.
 * Supports session meta events and meta procedures.
 *
 * @since 1.0.0
 */
public class WampRouter {

    /** Session meta event: published when a session joins a realm. */
    public static final String META_ON_JOIN = "wamp.session.on_join";
    /** Session meta event: published when a session leaves a realm. */
    public static final String META_ON_LEAVE = "wamp.session.on_leave";
    /** Session meta procedure: returns the count of active sessions. */
    public static final String META_SESSION_COUNT = "wamp.session.count";
    /** Session meta procedure: returns the list of active session IDs. */
    public static final String META_SESSION_LIST = "wamp.session.list";
    /** Session meta procedure: returns details for a specific session. */
    public static final String META_SESSION_GET = "wamp.session.get";

    private final Broker broker;
    private final Dealer dealer;
    /** session ID -> session info for meta procedures */
    private final Map<Long, WampSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Creates a new router with its own Broker and Dealer.
     */
    public WampRouter() {
        this.broker = new Broker();
        this.dealer = new Dealer();
    }

    /**
     * Routes a WAMP message from a client to the appropriate handler.
     *
     * @param msg       the message to route
     * @param transport the client's transport
     */
    public void route(WampMessage msg, WampTransport transport) {
        route(msg, transport, 0);
    }

    /**
     * Routes a WAMP message with session tracking.
     *
     * @param msg       the message to route
     * @param transport the client's transport
     * @param sessionId the client's session ID
     */
    public void route(WampMessage msg, WampTransport transport, long sessionId) {
        switch (msg) {
            case WampMessage.Subscribe subscribe ->
                    transport.send(broker.handleSubscribe(subscribe, transport, sessionId));
            case WampMessage.Unsubscribe unsubscribe ->
                    transport.send(broker.handleUnsubscribe(unsubscribe));
            case WampMessage.Publish publish ->
                    transport.send(broker.handlePublish(publish, transport, sessionId));
            case WampMessage.Register register ->
                    transport.send(dealer.handleRegister(register, transport));
            case WampMessage.Unregister unregister ->
                    transport.send(dealer.handleUnregister(unregister));
            case WampMessage.Call call -> {
                // Handle meta procedures
                if (isMetaProcedure(call.procedure())) {
                    handleMetaCall(call, transport);
                } else {
                    dealer.handleCall(call, transport, sessionId);
                }
            }
            case WampMessage.Cancel cancel ->
                    dealer.handleCancel(cancel);
            case WampMessage.Yield yield ->
                    dealer.handleYield(yield);
            default -> { /* other message types not routed */ }
        }
    }

    /**
     * Notifies the router that a session has joined a realm.
     * Publishes a {@code wamp.session.on_join} meta event.
     *
     * @param session the session that joined
     */
    public void sessionJoined(WampSession session) {
        activeSessions.put(session.getSessionId(), session);
        publishMetaEvent(META_ON_JOIN, List.of(Map.of(
                "session", session.getSessionId(),
                "authid", session.getAuthId() != null ? session.getAuthId() : "",
                "authrole", session.getAuthRole() != null ? session.getAuthRole() : "",
                "authmethod", session.getAuthMethod() != null ? session.getAuthMethod() : ""
        )));
    }

    /**
     * Notifies the router that a session has left a realm.
     * Publishes a {@code wamp.session.on_leave} meta event.
     *
     * @param sessionId the ID of the session that left
     */
    public void sessionLeft(long sessionId) {
        var session = activeSessions.remove(sessionId);
        publishMetaEvent(META_ON_LEAVE, List.of(sessionId));
    }

    /**
     * Returns the number of active sessions.
     *
     * @return the active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Returns the list of active session IDs.
     *
     * @return list of session IDs
     */
    public List<Long> getActiveSessionIds() {
        return List.copyOf(activeSessions.keySet());
    }

    /**
     * Returns the Broker component.
     *
     * @return the broker
     */
    public Broker getBroker() {
        return broker;
    }

    /**
     * Returns the Dealer component.
     *
     * @return the dealer
     */
    public Dealer getDealer() {
        return dealer;
    }

    private boolean isMetaProcedure(String procedure) {
        return META_SESSION_COUNT.equals(procedure)
                || META_SESSION_LIST.equals(procedure)
                || META_SESSION_GET.equals(procedure);
    }

    private void handleMetaCall(WampMessage.Call call, WampTransport transport) {
        List<Object> result = switch (call.procedure()) {
            case META_SESSION_COUNT -> List.of(activeSessions.size());
            case META_SESSION_LIST -> List.of(List.copyOf(activeSessions.keySet()));
            case META_SESSION_GET -> {
                if (call.args() != null && !call.args().isEmpty()) {
                    long targetId = ((Number) call.args().getFirst()).longValue();
                    var session = activeSessions.get(targetId);
                    if (session != null) {
                        yield List.of(Map.of(
                                "session", session.getSessionId(),
                                "authid", session.getAuthId() != null ? session.getAuthId() : "",
                                "authrole", session.getAuthRole() != null ? session.getAuthRole() : "",
                                "authmethod", session.getAuthMethod() != null ? session.getAuthMethod() : ""
                        ));
                    }
                }
                yield List.of();
            }
            default -> List.of();
        };
        transport.send(new WampMessage.Result(call.requestId(), Map.of(), result));
    }

    private void publishMetaEvent(String topic, List<Object> args) {
        var publish = new WampMessage.Publish(0, Map.of("exclude_me", false), topic, args);
        broker.handlePublish(publish, null, 0);
    }
}
