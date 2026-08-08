package ssg.legoflow.messaging.stomp.demo;

import ssg.legoflow.messaging.stomp.core.StompBroker;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates basic STOMP publish/subscribe messaging.
 *
 * <p>Creates a broker with two clients: a publisher and a subscriber.
 * The publisher sends messages to a destination, and the subscriber
 * receives them via its subscription handler.
 *
 * @since 0.1.0
 */
public class SimplePubSubDemo {

    private final StompBroker broker;
    private final StompClient publisher;
    private final StompClient subscriber;
    private final List<StompFrame> receivedMessages = new CopyOnWriteArrayList<>();

    /**
     * Creates and connects the demo components.
     */
    public SimplePubSubDemo() {
        broker = new StompBroker();

        // Create in-memory transport pairs
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();

        // Connect transports to broker
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        // Create clients
        publisher = new StompClient(pubPair[0]);
        subscriber = new StompClient(subPair[0]);
    }

    /**
     * Runs the demo: connect, subscribe, publish, verify.
     *
     * @param destination the destination to use
     * @param messages    the messages to publish
     * @return the list of received messages
     * @throws InterruptedException if interrupted while waiting
     */
    public List<StompFrame> run(String destination, List<String> messages) throws InterruptedException {
        // Connect both clients
        publisher.connect("localhost");
        subscriber.connect("localhost");

        var latch = new CountDownLatch(messages.size());

        // Subscribe
        subscriber.subscribe(destination, msg -> {
            receivedMessages.add(msg);
            latch.countDown();
        });

        // Small delay to ensure subscription is registered
        Thread.sleep(50);

        // Publish messages
        for (String msg : messages) {
            publisher.send(destination, msg, "text/plain");
        }

        // Wait for all messages
        latch.await(5, TimeUnit.SECONDS);

        return receivedMessages;
    }

    /**
     * Returns the broker.
     *
     * @return the broker
     */
    public StompBroker getBroker() {
        return broker;
    }

    /**
     * Closes all resources.
     */
    public void close() {
        publisher.close();
        subscriber.close();
        broker.close();
    }
}
