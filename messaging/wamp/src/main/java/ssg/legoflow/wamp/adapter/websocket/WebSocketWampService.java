package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.realm.RealmManager;
import ssg.legoflow.wamp.core.router.WampRouter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that wires WAMP protocol handling over WebSocket connections.
 * Each incoming WebSocket session is assigned a {@link WebSocketWampTransport} and
 * messages are routed through the appropriate realm's {@link WampRouter}.
 *
 * <p>The service manages the full HELLO/WELCOME/GOODBYE lifecycle per session
 * and delegates SUBSCRIBE/PUBLISH/REGISTER/CALL/YIELD to the realm router.</p>
 *
 * @since 0.1.0
 */
public class WebSocketWampService {

    private final RealmManager realmManager;
    private final WampSerializer serializer;
    private final Map<String, SessionContext> activeSessions = new ConcurrentHashMap<>();

    /**
     * Creates a new WAMP WebSocket service.
     *
     * @param realmManager the realm manager for session routing
     * @param serializer   the serializer for WAMP messages
     */
    public WebSocketWampService(RealmManager realmManager, WampSerializer serializer) {
        this.realmManager = realmManager;
        this.serializer = serializer;
    }

    /**
     * Creates a new WAMP WebSocket service with a default serializer.
     *
     * @param realmManager the realm manager for session routing
     */
    public WebSocketWampService(RealmManager realmManager) {
        this(realmManager, new WampSerializer());
    }

    /**
     * Handles a new WebSocket connection by creating a transport and starting
     * a virtual thread to process incoming WAMP messages.
     *
     * @param wsSession the WebSocket session
     * @return the created WAMP transport
     */
    public WebSocketWampTransport handleConnection(WebSocketSession wsSession) {
        var transport = new WebSocketWampTransport(wsSession, serializer);
        Thread.startVirtualThread(() -> processMessages(transport, wsSession.getId()));
        return transport;
    }

    /**
     * Processes incoming WAMP messages for a single connection.
     * Runs on a virtual thread until the transport closes.
     */
    private void processMessages(WebSocketWampTransport transport, String sessionId) {
        try {
            while (transport.isOpen()) {
                var msg = transport.receive();
                handleMessage(msg, transport, sessionId);
            }
        } catch (RuntimeException e) {
            if (transport.isOpen()) {
                transport.close();
            }
        } finally {
            var ctx = activeSessions.remove(sessionId);
            if (ctx != null) {
                ctx.realm().removeSession(ctx.wampSessionId());
            }
        }
    }

    private void handleMessage(WampMessage msg, WebSocketWampTransport transport, String wsSessionId) {
        switch (msg) {
            case WampMessage.Hello hello -> handleHello(hello, transport, wsSessionId);
            case WampMessage.Goodbye goodbye -> handleGoodbye(goodbye, transport, wsSessionId);
            default -> {
                var ctx = activeSessions.get(wsSessionId);
                if (ctx != null) {
                    var router = new WampRouter();
                    // Use the realm's own broker and dealer
                    routeThroughRealm(msg, transport, ctx.realm());
                }
            }
        }
    }

    private void handleHello(WampMessage.Hello hello, WebSocketWampTransport transport, String wsSessionId) {
        var realmOpt = realmManager.getRealm(hello.realm());
        if (realmOpt.isEmpty()) {
            transport.send(new WampMessage.Abort(Map.of(), "wamp.error.no_such_realm"));
            transport.close();
            return;
        }
        var realm = realmOpt.get();
        var welcome = realm.addSession(transport);
        activeSessions.put(wsSessionId, new SessionContext(realm, welcome.sessionId()));
        transport.send(welcome);
    }

    private void handleGoodbye(WampMessage.Goodbye goodbye, WebSocketWampTransport transport, String wsSessionId) {
        var ctx = activeSessions.remove(wsSessionId);
        if (ctx != null) {
            ctx.realm().removeSession(ctx.wampSessionId());
        }
        transport.send(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"));
        transport.close();
    }

    private void routeThroughRealm(WampMessage msg, WebSocketWampTransport transport, Realm realm) {
        switch (msg) {
            case WampMessage.Subscribe subscribe ->
                    transport.send(realm.getBroker().handleSubscribe(subscribe, transport));
            case WampMessage.Unsubscribe unsubscribe ->
                    transport.send(realm.getBroker().handleUnsubscribe(unsubscribe));
            case WampMessage.Publish publish ->
                    transport.send(realm.getBroker().handlePublish(publish, transport));
            case WampMessage.Register register ->
                    transport.send(realm.getDealer().handleRegister(register, transport));
            case WampMessage.Unregister unregister ->
                    transport.send(realm.getDealer().handleUnregister(unregister));
            case WampMessage.Call call ->
                    realm.getDealer().handleCall(call, transport);
            case WampMessage.Yield yield ->
                    realm.getDealer().handleYield(yield);
            default -> { }
        }
    }

    /**
     * Returns the number of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Returns the realm manager.
     *
     * @return the realm manager
     */
    public RealmManager getRealmManager() {
        return realmManager;
    }

    private record SessionContext(Realm realm, long wampSessionId) {}
}
