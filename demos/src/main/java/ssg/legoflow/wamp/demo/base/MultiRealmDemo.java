package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
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
 * Multi-realm demo: creates multiple WAMP realms and demonstrates that
 * procedures and topics in one realm are isolated from another.
 *
 * @since 0.1.0
 */
public class MultiRealmDemo {

    /**
     * Result of the multi-realm demo.
     *
     * @param rpcResultsByRealm  RPC results keyed by realm name
     * @param pubSubEventsByRealm pub/sub events keyed by realm name
     * @param realmCount         total number of realms created
     */
    public record DemoResult(
            Map<String, List<Object>> rpcResultsByRealm,
            Map<String, List<Object>> pubSubEventsByRealm,
            int realmCount
    ) {}

    /**
     * Runs the multi-realm demo with two isolated realms.
     * Each realm gets its own procedure registrations and topic subscriptions.
     *
     * @return the demo result with per-realm RPC results and pub/sub events
     */
    public DemoResult run() {
        var realmManager = new RealmManager();
        var realmA = realmManager.createRealm("realm.alpha");
        var realmB = realmManager.createRealm("realm.beta");

        var rpcResults = new ConcurrentHashMap<String, List<Object>>();
        var pubSubEvents = new ConcurrentHashMap<String, List<Object>>();

        rpcResults.put("realm.alpha", runRpc(realmA, "com.alpha.double", args -> {
            int val = ((Number) args.get(0)).intValue();
            return List.of(val * 2);
        }, List.of(10)));

        rpcResults.put("realm.beta", runRpc(realmB, "com.beta.negate", args -> {
            int val = ((Number) args.get(0)).intValue();
            return List.of(-val);
        }, List.of(7)));

        pubSubEvents.put("realm.alpha", runPubSub(realmA, "alpha.events", List.of("alpha-msg")));
        pubSubEvents.put("realm.beta", runPubSub(realmB, "beta.events", List.of("beta-msg")));

        return new DemoResult(
                Map.copyOf(rpcResults),
                Map.copyOf(pubSubEvents),
                realmManager.getRealmCount()
        );
    }

    /**
     * Checks whether a procedure registered in one realm is visible in another.
     *
     * @return {@code true} if realms are properly isolated
     */
    public boolean verifyRealmIsolation() {
        var realmManager = new RealmManager();
        var realmA = realmManager.createRealm("realm.iso.a");
        var realmB = realmManager.createRealm("realm.iso.b");

        var calleePair = InMemoryTransport.createPair();
        var callee = new Callee(calleePair[0]);
        callee.register("com.iso.proc", args -> List.of("ok"));
        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        realmA.getDealer().handleRegister(registerMsg, calleePair[1]);

        return realmA.getDealer().isRegistered("com.iso.proc")
                && !realmB.getDealer().isRegistered("com.iso.proc");
    }

    private List<Object> runRpc(Realm realm, String procedure,
                                java.util.function.Function<List<Object>, List<Object>> handler,
                                List<Object> args) {
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();
        var dealer = realm.getDealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register(procedure, handler);
        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        calleePair[1].send(dealer.handleRegister(registerMsg, calleePair[1]));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var future = caller.call(procedure, args);
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        return future.join().args();
    }

    private List<Object> runPubSub(Realm realm, String topic, List<Object> payload) {
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();
        var broker = realm.getBroker();
        var subscriber = new Subscriber(subPair[0]);
        var publisher = new Publisher(pubPair[0]);

        var events = new ArrayList<Object>();
        subscriber.onEvent(e -> events.addAll(e.args()));

        subscriber.subscribe(topic);
        var subMsg = (WampMessage.Subscribe) subPair[1].receive();
        subPair[1].send(broker.handleSubscribe(subMsg, subPair[1]));
        subscriber.handleSubscribed((WampMessage.Subscribed) subPair[0].receive());

        publisher.publish(topic, payload);
        var pubMsg = (WampMessage.Publish) pubPair[1].receive();
        broker.handlePublish(pubMsg, pubPair[1]);
        subscriber.handleEventMessage(subPair[0].receive());

        return events;
    }
}
