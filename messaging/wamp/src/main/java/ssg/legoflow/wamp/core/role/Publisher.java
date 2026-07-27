package ssg.legoflow.wamp.core.role;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WAMP Publisher role — publishes events to topics.
 *
 * @since 1.0.0
 */
public class Publisher {

    private final WampTransport transport;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    /**
     * Creates a new Publisher that communicates via the given transport.
     *
     * @param transport the transport to use
     */
    public Publisher(WampTransport transport) {
        this.transport = transport;
    }

    /**
     * Publishes an event to a topic with the given arguments.
     *
     * @param topic the topic URI to publish to
     * @param args  positional event arguments
     */
    public void publish(String topic, List<Object> args) {
        long requestId = requestIdCounter.getAndIncrement();
        transport.send(new WampMessage.Publish(requestId, Map.of(), topic, args));
    }
}
