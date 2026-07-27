package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import ssg.legoflow.wamp.core.router.Broker;
import ssg.legoflow.wamp.demo.base.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublisherSubscriberTest {

    @Test
    void testPublisherSendsPublishMessage() {
        var pair = InMemoryTransport.createPair();
        var publisher = new Publisher(pair[0]);

        publisher.publish("topic.test", List.of("data", 123));

        var msg = pair[1].receive();
        assertThat(msg).isInstanceOf(WampMessage.Publish.class);
        var publish = (WampMessage.Publish) msg;
        assertThat(publish.topic()).isEqualTo("topic.test");
        assertThat(publish.args()).containsExactly("data", 123);
    }

    @Test
    void testSubscriberSendsSubscribeMessage() {
        var pair = InMemoryTransport.createPair();
        var subscriber = new Subscriber(pair[0]);

        long requestId = subscriber.subscribe("topic.test");

        var msg = pair[1].receive();
        assertThat(msg).isInstanceOf(WampMessage.Subscribe.class);
        var subscribe = (WampMessage.Subscribe) msg;
        assertThat(subscribe.topic()).isEqualTo("topic.test");
        assertThat(subscribe.requestId()).isEqualTo(requestId);
    }

    @Test
    void testSubscriberTracksActiveSubscriptions() {
        var pair = InMemoryTransport.createPair();
        var subscriber = new Subscriber(pair[0]);

        subscriber.subscribe("topic.one");
        pair[1].receive(); // consume Subscribe
        pair[1].send(new WampMessage.Subscribed(1L, 100L));
        subscriber.handleSubscribed((WampMessage.Subscribed) pair[0].receive());

        assertThat(subscriber.getActiveSubscriptions()).containsEntry(100L, "topic.one");
    }

    @Test
    void testSubscriberReceivesEvents() {
        var pair = InMemoryTransport.createPair();
        var subscriber = new Subscriber(pair[0]);

        var received = new ArrayList<Object>();
        subscriber.onEvent(e -> received.addAll(e.args()));

        var event = new WampMessage.Publish(0, java.util.Map.of(), "topic.test", List.of("event-data"));
        subscriber.handleEvent(event);

        assertThat(received).containsExactly("event-data");
    }

    @Test
    void testSubscriberUnsubscribe() {
        var pair = InMemoryTransport.createPair();
        var subscriber = new Subscriber(pair[0]);
        var broker = new Broker();

        subscriber.subscribe("topic.unsub");
        var subMsg = (WampMessage.Subscribe) pair[1].receive();
        var subscribed = broker.handleSubscribe(subMsg, pair[1]);
        pair[1].send(subscribed);
        subscriber.handleSubscribed((WampMessage.Subscribed) pair[0].receive());

        assertThat(subscriber.getActiveSubscriptions()).hasSize(1);

        subscriber.unsubscribe(subscribed.subscriptionId());
        var unsubMsg = (WampMessage.Unsubscribe) pair[1].receive();
        broker.handleUnsubscribe(unsubMsg);

        assertThat(subscriber.getActiveSubscriptions()).isEmpty();
        assertThat(broker.getSubscriptionCount("topic.unsub")).isZero();
    }

    @Test
    void testMultipleSubscribersReceiveEvents() {
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

        sub1.subscribe("topic.shared");
        sub1Pair[1].send(broker.handleSubscribe(
                (WampMessage.Subscribe) sub1Pair[1].receive(), sub1Pair[1]));
        sub1.handleSubscribed((WampMessage.Subscribed) sub1Pair[0].receive());

        sub2.subscribe("topic.shared");
        sub2Pair[1].send(broker.handleSubscribe(
                (WampMessage.Subscribe) sub2Pair[1].receive(), sub2Pair[1]));
        sub2.handleSubscribed((WampMessage.Subscribed) sub2Pair[0].receive());

        pub.publish("topic.shared", List.of("shared-event"));
        broker.handlePublish((WampMessage.Publish) pubPair[1].receive(), pubPair[1]);

        sub1.handleEventMessage(sub1Pair[0].receive());
        sub2.handleEventMessage(sub2Pair[0].receive());

        assertThat(received1).containsExactly("shared-event");
        assertThat(received2).containsExactly("shared-event");
    }

    @Test
    void testNoEventHandlerIgnoresEvents() {
        var pair = InMemoryTransport.createPair();
        var subscriber = new Subscriber(pair[0]);

        // No onEvent handler registered — should not throw
        var event = new WampMessage.Publish(0, java.util.Map.of(), "topic.test", List.of("ignored"));
        subscriber.handleEvent(event);
    }
}
