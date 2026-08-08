package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.realm.RealmManager;
import ssg.legoflow.wamp.core.router.Broker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Comprehensive demo of all WAMP module features.
 *
 * <h2>Configuration</h2>
 * <p><b>Preferred (default): In-memory transport</b> -- No external dependencies.
 * Runs anywhere without a WebSocket server. Uses {@link InMemoryTransport} for
 * deterministic, fast demos covering all WAMP core features.</p>
 *
 * <p><b>Alternative: WebSocket transport</b> -- Set {@link #USE_EXTERNAL}{@code =true}
 * to exercise the WebSocket adapter layer. Required for end-to-end browser integration,
 * TLS testing, and cross-implementation interoperability.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>RPC -- register a procedure, call it, receive the result</li>
 *   <li>Pub/Sub -- subscribe to a topic, publish events, receive delivery</li>
 *   <li>Multi-realm isolation -- procedures and topics in separate realms do not leak</li>
 *   <li>Session lifecycle -- HELLO/WELCOME handshake, session tracking, GOODBYE teardown</li>
 *   <li>Calculator service -- multiple RPC procedures through a shared Dealer</li>
 *   <li>Chat room -- multi-subscriber fan-out with join/leave system notifications</li>
 *   <li>Serialization -- JSON round-trip for all message types</li>
 *   <li>Pattern subscriptions -- prefix-based topic matching</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoWampAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoWampAll.class);

    /** Set to {@code true} to use WebSocket transport instead of in-memory. */
    public static boolean USE_EXTERNAL = false;

    private DemoWampAll() {}

    /**
     * Results from running the full demo.
     *
     * @param rpcResultCorrect     true if RPC call returned the expected result
     * @param pubSubEventsReceived number of events received by the subscriber
     * @param realmIsolated        true if procedures in one realm are invisible to another
     * @param sessionEstablished   true if HELLO/WELCOME handshake succeeded
     * @param calculatorSum        the result of calling the calculator add procedure
     * @param chatMessageCount     number of chat messages received by a participant
     * @param serializationOk      true if JSON serialize/deserialize round-trip succeeded
     * @param prefixMatchCount     number of events matched by prefix subscription
     */
    public record Results(
            boolean rpcResultCorrect,
            int pubSubEventsReceived,
            boolean realmIsolated,
            boolean sessionEstablished,
            double calculatorSum,
            int chatMessageCount,
            boolean serializationOk,
            int prefixMatchCount
    ) {}

    /**
     * Runs the comprehensive demo covering all WAMP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        boolean rpc = demoRpc();
        int pubSub = demoPubSub();
        boolean isolation = demoRealmIsolation();
        boolean session = demoSessionLifecycle();
        double calcSum = demoCalculatorService();
        int chatMsgs = demoChatRoom();
        boolean serial = demoSerialization();
        int prefixMatch = demoPrefixSubscription();

        return new Results(rpc, pubSub, isolation, session, calcSum, chatMsgs, serial, prefixMatch);
    }

    // ======================== 1. RPC =======================================

    /**
     * Demonstrates basic RPC: register, call, receive result.
     */
    static boolean demoRpc() {
        LOG.info("=== 1. RPC (Remote Procedure Call) ===");
        var demo = new SimpleRpcDemo();
        var result = demo.run();
        LOG.info("RPC result: {}", result);
        // add(3, 5) should return [8]
        return result.size() == 1 && ((Number) result.getFirst()).intValue() == 8;
    }

    // ======================== 2. PUB/SUB ===================================

    /**
     * Demonstrates pub/sub: subscribe to topic, publish event, receive delivery.
     */
    static int demoPubSub() {
        LOG.info("=== 2. Pub/Sub ===");
        var demo = new SimplePubSubDemo();
        var events = demo.run();
        LOG.info("Pub/Sub events received: {}", events);
        return events.size();
    }

    // ======================== 3. REALM ISOLATION ============================

    /**
     * Demonstrates that procedures registered in one realm are invisible to another.
     */
    static boolean demoRealmIsolation() {
        LOG.info("=== 3. Realm Isolation ===");
        var demo = new MultiRealmDemo();
        boolean isolated = demo.verifyRealmIsolation();
        LOG.info("Realm isolation verified: {}", isolated);
        return isolated;
    }

    // ======================== 4. SESSION LIFECYCLE ==========================

    /**
     * Demonstrates session establishment with HELLO/WELCOME handshake.
     */
    static boolean demoSessionLifecycle() {
        LOG.info("=== 4. Session Lifecycle ===");
        var realmManager = new RealmManager();
        var realm = realmManager.createRealm("demo.realm");

        var pair = InMemoryTransport.createPair();
        var clientTransport = pair[0];
        var routerTransport = pair[1];

        // Client sends HELLO
        var hello = new WampMessage.Hello("demo.realm", Map.of("roles", Map.of(
                "caller", Map.of(), "subscriber", Map.of())));
        clientTransport.send(hello);

        // Router side: receive HELLO, use Realm to establish session
        var receivedHello = (WampMessage.Hello) routerTransport.receive();
        var welcome = realm.addSession(routerTransport);
        routerTransport.send(welcome);

        // Client receives WELCOME
        var receivedWelcome = (WampMessage.Welcome) clientTransport.receive();
        boolean established = receivedWelcome.sessionId() > 0;

        // GOODBYE
        var goodbye = new WampMessage.Goodbye(Map.of(), "wamp.close.normal");
        clientTransport.send(goodbye);

        LOG.info("Session established: id={}", receivedWelcome.sessionId());
        return established;
    }

    // ======================== 5. CALCULATOR SERVICE =========================

    /**
     * Demonstrates multiple RPC procedures through a shared Dealer.
     */
    static double demoCalculatorService() {
        LOG.info("=== 5. Calculator Service ===");
        var calc = new CalculatorServiceDemo();
        calc.setup();
        double sum = calc.add(17.5, 24.5);
        double product = calc.multiply(6, 7);
        LOG.info("Calculator: 17.5+24.5={}, 6*7={}", sum, product);
        return sum;
    }

    // ======================== 6. CHAT ROOM =================================

    /**
     * Demonstrates multi-subscriber chat with join/leave notifications.
     */
    static int demoChatRoom() {
        LOG.info("=== 6. Chat Room ===");
        var realmManager = new RealmManager();
        var realm = realmManager.createRealm("chat.realm");
        var demo = new ChatRoomDemo(realm);

        demo.join("Alice");
        demo.join("Bob");

        demo.sendMessage("Alice", "Hello everyone!");
        demo.sendMessage("Bob", "Hi Alice!");

        var aliceMessages = demo.getMessages("Alice");
        var bobMessages = demo.getMessages("Bob");
        int totalMessages = aliceMessages.size() + bobMessages.size();
        LOG.info("Chat: Alice received {}, Bob received {}", aliceMessages.size(), bobMessages.size());
        return totalMessages;
    }

    // ======================== 7. SERIALIZATION =============================

    /**
     * Demonstrates JSON serialization round-trip for WAMP messages.
     */
    static boolean demoSerialization() {
        LOG.info("=== 7. Serialization ===");
        var serializer = new WampSerializer();

        // Round-trip a HELLO message
        var hello = new WampMessage.Hello("test.realm", Map.of("roles", Map.of("caller", Map.of())));
        var json = serializer.serialize(hello);
        var deserialized = serializer.deserialize(json);

        boolean ok = deserialized instanceof WampMessage.Hello h
                && "test.realm".equals(h.realm());
        LOG.info("Serialization round-trip: {} -> {} -> ok={}", hello.type(), json, ok);
        return ok;
    }

    // ======================== 8. PREFIX SUBSCRIPTION ========================

    /**
     * Demonstrates prefix-based topic matching in the Broker.
     */
    static int demoPrefixSubscription() {
        LOG.info("=== 8. Prefix Subscription ===");
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        var events = new CopyOnWriteArrayList<Object>();

        // Send Subscribe with prefix match option directly (Subscriber.subscribe() hardcodes empty options)
        long requestId = 1;
        subPair[0].send(new WampMessage.Subscribe(requestId, Map.of("match", "prefix"), "com.app."));
        var subMsg = (WampMessage.Subscribe) subPair[1].receive();
        var subscribed = broker.handleSubscribe(subMsg, subPair[1]);
        subPair[1].send(subscribed);
        var subscribedMsg = (WampMessage.Subscribed) subPair[0].receive();
        LOG.info("Prefix subscription established: subscriptionId={}", subscribedMsg.subscriptionId());

        // Publish to topics matching the prefix
        publishEvent(pubPair, broker, "com.app.user.login", List.of("user1"));
        publishEvent(pubPair, broker, "com.app.user.logout", List.of("user2"));
        publishEvent(pubPair, broker, "com.app.order.created", List.of("order1"));

        // Collect delivered events from subscriber's client-side transport
        WampMessage event;
        while ((event = subPair[0].tryReceive()) != null) {
            if (event instanceof WampMessage.Event e) {
                events.addAll(e.args());
            }
        }

        LOG.info("Prefix subscription received {} events", events.size());
        return events.size();
    }

    private static void publishEvent(InMemoryTransport[] pubPair,
                                     Broker broker, String topic, List<Object> args) {
        pubPair[0].send(new WampMessage.Publish(1, Map.of(), topic, args));
        var pubMsg = (WampMessage.Publish) pubPair[1].receive();
        broker.handlePublish(pubMsg, pubPair[1]);
    }
}
