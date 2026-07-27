package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import ssg.legoflow.wamp.core.router.Broker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePubSubDemoTest {

    @Test
    void testSimplePubSubDemo() {
        var demo = new SimplePubSubDemo();
        var received = demo.run();

        assertThat(received).containsExactly("hello", 42);
    }

    @Test
    void testMultipleSubscribers() {
        var sub1Pair = InMemoryTransport.createPair();
        var sub2Pair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();
        var broker = new Broker();

        var sub1 = new Subscriber(sub1Pair[0]);
        var sub2 = new Subscriber(sub2Pair[0]);
        var pub = new Publisher(pubPair[0]);

        var received1 = new ArrayList<Object>();
        var received2 = new ArrayList<Object>();
        sub1.onEvent(e -> received1.addAll(e.args()));
        sub2.onEvent(e -> received2.addAll(e.args()));

        // Subscribe both
        sub1.subscribe("topic.multi");
        var subMsg1 = (WampMessage.Subscribe) sub1Pair[1].receive();
        sub1Pair[1].send(broker.handleSubscribe(subMsg1, sub1Pair[1]));
        sub1.handleSubscribed((WampMessage.Subscribed) sub1Pair[0].receive());

        sub2.subscribe("topic.multi");
        var subMsg2 = (WampMessage.Subscribe) sub2Pair[1].receive();
        sub2Pair[1].send(broker.handleSubscribe(subMsg2, sub2Pair[1]));
        sub2.handleSubscribed((WampMessage.Subscribed) sub2Pair[0].receive());

        // Publish
        pub.publish("topic.multi", List.of("broadcast"));
        var pubMsg = (WampMessage.Publish) pubPair[1].receive();
        broker.handlePublish(pubMsg, pubPair[1]);

        // Both subscribers receive the event
        sub1.handleEventMessage(sub1Pair[0].receive());
        sub2.handleEventMessage(sub2Pair[0].receive());

        assertThat(received1).containsExactly("broadcast");
        assertThat(received2).containsExactly("broadcast");
    }

    @Test
    void testUnsubscribeStopsEvents() {
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();
        var broker = new Broker();

        var subscriber = new Subscriber(subPair[0]);
        var publisher = new Publisher(pubPair[0]);

        var received = new ArrayList<Object>();
        subscriber.onEvent(e -> received.addAll(e.args()));

        // Subscribe
        subscriber.subscribe("topic.unsub");
        var subMsg = (WampMessage.Subscribe) subPair[1].receive();
        var subscribed = broker.handleSubscribe(subMsg, subPair[1]);
        subPair[1].send(subscribed);
        subscriber.handleSubscribed((WampMessage.Subscribed) subPair[0].receive());

        assertThat(broker.getSubscriptionCount("topic.unsub")).isEqualTo(1);

        // Unsubscribe
        subscriber.unsubscribe(subscribed.subscriptionId());
        var unsubMsg = (WampMessage.Unsubscribe) subPair[1].receive();
        broker.handleUnsubscribe(unsubMsg);

        assertThat(broker.getSubscriptionCount("topic.unsub")).isZero();
    }
}
