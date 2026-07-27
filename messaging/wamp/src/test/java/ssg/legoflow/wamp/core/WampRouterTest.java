package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.WampRouter;
import ssg.legoflow.wamp.demo.base.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WampRouterTest {

    /**
     * InMemoryTransport.createPair() gives [client, server].
     * When we call router.route(msg, clientSide), the router calls clientSide.send(response).
     * clientSide.send() puts into sendQueue which is serverSide's receiveQueue.
     * So to read the router's response, we receive from serverSide (pair[1]).
     *
     * We pass pair[0] as the "client transport" to the router so that:
     * - router.route(msg, pair[0]) calls pair[0].send(response)
     * - pair[1].receive() or tryReceive() gets the response
     */

    @Test
    void testRouterRoutesSubscribe() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        var subscribe = new WampMessage.Subscribe(1L, Map.of(), "topic.test");
        router.route(subscribe, pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Subscribed.class);
    }

    @Test
    void testRouterRoutesPublish() {
        var router = new WampRouter();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        // Subscribe: router sends response via subPair[0].send() -> subPair[1].receive()
        router.route(new WampMessage.Subscribe(1L, Map.of(), "topic.test"), subPair[0]);
        var subscribed = subPair[1].receive();
        assertThat(subscribed).isInstanceOf(WampMessage.Subscribed.class);

        // Publish: events are delivered to the subscriber's transport (subPair[0])
        router.route(new WampMessage.Publish(2L, Map.of(), "topic.test", List.of("data")), pubPair[0]);

        // Publisher gets Published confirmation via pubPair[0].send() -> pubPair[1].receive()
        var published = pubPair[1].receive();
        assertThat(published).isInstanceOf(WampMessage.Published.class);

        // Subscriber receives event via subPair[0].send() -> subPair[1].receive()
        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
        assertThat(((WampMessage.Event) event).args()).containsExactly("data");
    }

    @Test
    void testRouterRoutesRegister() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        var register = new WampMessage.Register(1L, Map.of(), "com.test.proc");
        router.route(register, pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Registered.class);
        assertThat(router.getDealer().isRegistered("com.test.proc")).isTrue();
    }

    @Test
    void testRouterRoutesCallToCallee() {
        var router = new WampRouter();
        var calleePair = InMemoryTransport.createPair();
        var callerPair = InMemoryTransport.createPair();

        // Register procedure via calleePair[0]
        router.route(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);
        calleePair[1].receive(); // consume Registered

        // Call via callerPair[0]
        router.route(new WampMessage.Call(2L, Map.of(), "com.test.proc", List.of("arg1")), callerPair[0]);

        // Callee receives invocation: dealer sends invocation to calleePair[0].send() -> calleePair[1].receive()
        var invocation = calleePair[1].receive();
        assertThat(invocation).isInstanceOf(WampMessage.Invocation.class);
        assertThat(((WampMessage.Invocation) invocation).args()).containsExactly("arg1");
    }

    @Test
    void testRouterRoutesYieldBackToCaller() {
        var router = new WampRouter();
        var calleePair = InMemoryTransport.createPair();
        var callerPair = InMemoryTransport.createPair();

        router.route(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);
        calleePair[1].receive(); // consume Registered

        router.route(new WampMessage.Call(2L, Map.of(), "com.test.proc", List.of()), callerPair[0]);
        var invocation = (WampMessage.Invocation) calleePair[1].receive();

        // Yield via calleePair[0] — dealer sends result to callerPair[0].send() -> callerPair[1].receive()
        router.route(new WampMessage.Yield(invocation.requestId(), Map.of(), List.of("result")), calleePair[0]);

        var result = callerPair[1].receive();
        assertThat(result).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) result).args()).containsExactly("result");
    }

    @Test
    void testRouterCallToUnregisteredProcedureReturnsError() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.route(new WampMessage.Call(1L, Map.of(), "com.test.nonexistent", List.of()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Error.class);
        assertThat(((WampMessage.Error) response).error()).isEqualTo("wamp.error.no_such_procedure");
    }

    @Test
    void testRouterRoutesUnsubscribe() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.route(new WampMessage.Subscribe(1L, Map.of(), "topic.test"), pair[0]);
        var subscribed = (WampMessage.Subscribed) pair[1].receive();

        router.route(new WampMessage.Unsubscribe(2L, subscribed.subscriptionId()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Unsubscribed.class);
        assertThat(router.getBroker().getSubscriptionCount("topic.test")).isZero();
    }

    @Test
    void testRouterRoutesUnregister() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.route(new WampMessage.Register(1L, Map.of(), "com.test.proc"), pair[0]);
        var registered = (WampMessage.Registered) pair[1].receive();

        router.route(new WampMessage.Unregister(2L, registered.registrationId()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Unregistered.class);
        assertThat(router.getDealer().isRegistered("com.test.proc")).isFalse();
    }

    @Test
    void testBrokerAndDealerAreAccessible() {
        var router = new WampRouter();

        assertThat(router.getBroker()).isNotNull();
        assertThat(router.getDealer()).isNotNull();
    }

    @Test
    void testRouterIgnoresUnroutedMessageTypes() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.route(new WampMessage.Welcome(1L, Map.of()), pair[0]);
        router.route(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"), pair[0]);

        assertThat(pair[1].tryReceive()).isNull();
    }
}
