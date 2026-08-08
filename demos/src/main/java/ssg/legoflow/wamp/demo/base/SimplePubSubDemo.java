package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import ssg.legoflow.wamp.core.router.Broker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple pub/sub demo: creates a Publisher and Subscriber, wires them through a Broker
 * via InMemoryTransport, subscribes to a topic, and publishes an event.
 *
 * @since 0.1.0
 */
public class SimplePubSubDemo {

    /**
     * Runs the pub/sub demo.
     *
     * @return the list of received event arguments
     */
    public List<Object> run() {
        // Create transport pairs: subscriber <-> broker, publisher <-> broker
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        var subscriberTransport = subPair[0];
        var subRouterTransport = subPair[1];
        var publisherTransport = pubPair[0];
        var pubRouterTransport = pubPair[1];

        var broker = new Broker();
        var subscriber = new Subscriber(subscriberTransport);
        var publisher = new Publisher(publisherTransport);

        // Collect received events
        var receivedEvents = new ArrayList<Object>();
        subscriber.onEvent(event -> receivedEvents.addAll(event.args()));

        // Subscribe to "events.test" topic
        subscriber.subscribe("events.test");

        // Router handles subscribe
        var subscribeMsg = (WampMessage.Subscribe) subRouterTransport.receive();
        var subscribed = broker.handleSubscribe(subscribeMsg, subRouterTransport);
        subRouterTransport.send(subscribed);

        // Subscriber receives confirmation
        var subscribedMsg = (WampMessage.Subscribed) subscriberTransport.receive();
        subscriber.handleSubscribed(subscribedMsg);

        // Publish an event
        publisher.publish("events.test", List.of("hello", 42));

        // Router handles publish and delivers to subscriber
        var publishMsg = (WampMessage.Publish) pubRouterTransport.receive();
        broker.handlePublish(publishMsg, pubRouterTransport);

        // Subscriber receives the event
        subscriber.handleEventMessage(subscriberTransport.receive());

        return receivedEvents;
    }
}
