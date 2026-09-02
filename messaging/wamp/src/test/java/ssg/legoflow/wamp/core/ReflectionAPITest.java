package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.Broker;
import ssg.legoflow.wamp.core.router.Dealer;
import ssg.legoflow.wamp.core.transport.WampTransport;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for reflection/introspection methods on Broker and Dealer.
 */
class ReflectionAPITest {

    static class InMemTransport implements WampTransport {
        private final BlockingQueue<WampMessage> sendQ;
        private final BlockingQueue<WampMessage> receiveQ;
        private volatile boolean open = true;

        static InMemTransport[] createPair() {
            var q1 = new LinkedBlockingQueue<WampMessage>();
            var q2 = new LinkedBlockingQueue<WampMessage>();
            return new InMemTransport[]{
                new InMemTransport(q1, q2), new InMemTransport(q2, q1)
            };
        }

        InMemTransport(BlockingQueue<WampMessage> send, BlockingQueue<WampMessage> receive) {
            this.sendQ = send;
            this.receiveQ = receive;
        }

        @Override public void send(WampMessage msg) { if (!open) throw new IllegalStateException(); sendQ.offer(msg); }
        @Override public WampMessage receive() { return receiveQ.poll(); }
        @Override public void close() { open = false; }
        @Override public boolean isOpen() { return open; }
    }

    // ── Dealer reflection ──────────────────────────────────────────

    @Test
    void dealer_getRegisteredProcedures_empty() {
        var dealer = new Dealer();
        assertThat(dealer.getRegisteredProcedures()).isEmpty();
    }

    @Test
    void dealer_getRegisteredProcedures_afterRegistration() {
        var dealer = new Dealer();
        var pair = InMemTransport.createPair();
        dealer.handleRegister(new WampMessage.Register(1, Map.of(), "com.example.proc1"), pair[1]);
        dealer.handleRegister(new WampMessage.Register(2, Map.of(), "com.example.proc2"), pair[1]);

        var procedures = dealer.getRegisteredProcedures();
        assertThat(procedures).containsExactlyInAnyOrder("com.example.proc1", "com.example.proc2");
    }

    @Test
    void dealer_getRegisteredProcedures_afterUnregister() {
        var dealer = new Dealer();
        var pair = InMemTransport.createPair();
        var regMsg = dealer.handleRegister(
            new WampMessage.Register(1, Map.of(), "com.example.proc1"), pair[1]);
        var regId = ((WampMessage.Registered) regMsg).registrationId();

        assertThat(dealer.getRegisteredProcedures()).contains("com.example.proc1");

        dealer.handleUnregister(new WampMessage.Unregister(10, regId));
        assertThat(dealer.getRegisteredProcedures()).doesNotContain("com.example.proc1");
    }

    // ── Broker reflection ──────────────────────────────────────────

    @Test
    void broker_getSubscriptionTopics_empty() {
        var broker = new Broker();
        assertThat(broker.getSubscriptionTopics()).isEmpty();
    }

    @Test
    void broker_getSubscriptionTopics_afterSubscribe() {
        var broker = new Broker();
        var pair = InMemTransport.createPair();
        broker.handleSubscribe(new WampMessage.Subscribe(1, Map.of(), "com.example.topic1"), pair[1], 100L);
        broker.handleSubscribe(new WampMessage.Subscribe(2, Map.of(), "com.example.topic2"), pair[1], 100L);

        var topics = broker.getSubscriptionTopics();
        assertThat(topics).containsExactlyInAnyOrder("com.example.topic1", "com.example.topic2");
    }

    @Test
    void broker_getSubscriptionTopics_afterUnsubscribe() {
        var broker = new Broker();
        var pair = InMemTransport.createPair();
        var subMsg = broker.handleSubscribe(
            new WampMessage.Subscribe(1, Map.of(), "com.example.topic1"), pair[1], 100L);
        var subId = ((WampMessage.Subscribed) subMsg).subscriptionId();

        assertThat(broker.getSubscriptionTopics()).contains("com.example.topic1");

        broker.handleUnsubscribe(new WampMessage.Unsubscribe(10, subId));
        assertThat(broker.getSubscriptionTopics()).doesNotContain("com.example.topic1");
    }

    @Test
    void broker_getPrefixSubscriptionPatterns() {
        var broker = new Broker();
        var pair = InMemTransport.createPair();
        broker.handleSubscribe(
            new WampMessage.Subscribe(1, Map.of("match", "prefix"), "com.example."), pair[1], 100L);

        assertThat(broker.getPrefixSubscriptionPatterns()).contains("com.example.");
        assertThat(broker.getSubscriptionTopics()).isEmpty();
    }

    @Test
    void broker_getWildcardSubscriptionPatterns() {
        var broker = new Broker();
        var pair = InMemTransport.createPair();
        broker.handleSubscribe(
            new WampMessage.Subscribe(1, Map.of("match", "wildcard"), "com..service"), pair[1], 100L);

        assertThat(broker.getWildcardSubscriptionPatterns()).contains("com..service");
        assertThat(broker.getSubscriptionTopics()).isEmpty();
    }
}
