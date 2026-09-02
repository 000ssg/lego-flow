package ssg.legoflow.wamp.demo.websocket;

import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.adapter.websocket.WebSocketWampTransport;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.realm.RealmManager;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Full WAMP server demo with concurrent RPC and Pub/Sub across multiple realms.
 * Demonstrates a realistic scenario where multiple WebSocket clients interact
 * through a realm-based WAMP router, performing both RPC calls and pub/sub messaging.
 *
 * @since 0.1.0
 */
public class FullWampServerDemo {

    private final RealmManager realmManager = new RealmManager();
    private final WampSerializer serializer = new WampSerializer();

    /**
     * Result of running the full demo.
     *
     * @param rpcResults    results from RPC calls per realm
     * @param pubSubEvents  events received per realm
     * @param realmCount    number of realms used
     */
    public record DemoResult(
            Map<String, List<Object>> rpcResults,
            Map<String, List<Object>> pubSubEvents,
            int realmCount
    ) {}

    /**
     * Runs the full WAMP server demo with two realms, concurrent RPC and Pub/Sub.
     *
     * @return the demo result
     */
    public DemoResult run() {
        var localRealmManager = new RealmManager();
        var realm1 = localRealmManager.createRealm("realm.math");
        var realm2 = localRealmManager.createRealm("realm.chat");

        var rpcResults = new ConcurrentHashMap<String, List<Object>>();
        var pubSubEvents = new ConcurrentHashMap<String, List<Object>>();

        // Realm 1: math RPC
        rpcResults.put("realm.math", runMathRpc(realm1));

        // Realm 2: chat pub/sub
        pubSubEvents.put("realm.chat", runChatPubSub(realm2));

        // Cross-realm: verify isolation by doing RPC in realm2
        rpcResults.put("realm.chat", runGreetRpc(realm2));

        return new DemoResult(
                Map.copyOf(rpcResults),
                Map.copyOf(pubSubEvents),
                localRealmManager.getRealmCount()
        );
    }

    private List<Object> runMathRpc(Realm realm) {
        var callerPair = createWiredPair("math-caller");
        var calleePair = createWiredPair("math-callee");

        var dealer = realm.getDealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register("com.math.multiply", args -> {
            int a = ((Number) args.get(0)).intValue();
            int b = ((Number) args.get(1)).intValue();
            return List.of(a * b);
        });

        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        calleePair[1].send(dealer.handleRegister(registerMsg, calleePair[1]));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var future = caller.call("com.math.multiply", List.of(7, 6));
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        return future.join().args();
    }

    private List<Object> runChatPubSub(Realm realm) {
        var subPair = createWiredPair("chat-sub");
        var pubPair = createWiredPair("chat-pub");

        var broker = realm.getBroker();
        var subscriber = new Subscriber(subPair[0]);
        var publisher = new Publisher(pubPair[0]);

        var events = new ArrayList<Object>();
        subscriber.onEvent(e -> events.addAll(e.args()));

        subscriber.subscribe("chat.messages");
        var subMsg = (WampMessage.Subscribe) subPair[1].receive();
        subPair[1].send(broker.handleSubscribe(subMsg, subPair[1]));
        subscriber.handleSubscribed((WampMessage.Subscribed) subPair[0].receive());

        publisher.publish("chat.messages", List.of("user1", "Hello everyone!"));
        var pubMsg = (WampMessage.Publish) pubPair[1].receive();
        broker.handlePublish(pubMsg, pubPair[1]);
        subscriber.handleEventMessage(subPair[0].receive());

        return events;
    }

    private List<Object> runGreetRpc(Realm realm) {
        var callerPair = createWiredPair("greet-caller");
        var calleePair = createWiredPair("greet-callee");

        var dealer = realm.getDealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register("com.chat.greet", args ->
                List.of("Welcome, " + args.getFirst() + "!"));

        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        calleePair[1].send(dealer.handleRegister(registerMsg, calleePair[1]));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var future = caller.call("com.chat.greet", List.of("Alice"));
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        return future.join().args();
    }

    private WebSocketWampTransport[] createWiredPair(String prefix) {
        var clientSession = new WebSocketSession(prefix + "-client");
        var routerSession = new WebSocketSession(prefix + "-router");
        var clientTransport = new WebSocketWampTransport(clientSession, serializer);
        var routerTransport = new WebSocketWampTransport(routerSession, serializer);
        clientTransport.onFrame(frame -> routerTransport.injectFrame(frame));
        routerTransport.onFrame(frame -> clientTransport.injectFrame(frame));
        return new WebSocketWampTransport[]{clientTransport, routerTransport};
    }
}
