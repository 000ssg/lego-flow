package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.messaging.nats.transport.InMemoryNatsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
/**
 * Demonstrates basic NATS publish/subscribe messaging.
 *
 * <p>Starts an embedded server, connects a publisher and subscriber,
 * then sends and receives messages on various subjects.
 *
 * @since 0.1.0
 */
public final class PubSubDemo {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubDemo.class);

    private PubSubDemo() {}

    /**
     * Runs the pub/sub demo.
     *
     * @param port the server port (ignored — the demo now runs over an in-memory transport)
     * @return the number of messages received
     * @throws IOException if connection fails
     * @throws InterruptedException if interrupted
     */
    public static int run(int port) throws IOException, InterruptedException {
        var received = new java.util.concurrent.atomic.AtomicInteger(0);

        try (var server = new NatsServer()) {
            server.start();

            // One in-memory transport pair per client (each pair is one connection)
            var subPair = InMemoryNatsTransport.createPair();
            server.handleConnection(subPair[0]);
            var pubPair = InMemoryNatsTransport.createPair();
            server.handleConnection(pubPair[0]);

            try (var subscriber = new NatsClient(subPair[1],
                    ConnectOptions.withDefaults("sub-client"));
                 var publisher = new NatsClient(pubPair[1],
                         ConnectOptions.withDefaults("pub-client"))) {

                subscriber.connect();
                publisher.connect();

                // Subscribe to various subjects
                subscriber.subscribe("events.>", msg -> {
                    LOG.info("Received on {}: {}", msg.subject(), msg.dataAsString());
                    received.incrementAndGet();
                });

                // Allow the subscription to propagate to the server's router
                // before publishing real messages (in-memory seam: SUB and PUB
                // travel over separate connection pairs, so a short settle wait
                // is required — a probe message can race the SUB registration).
                Thread.sleep(50);

                // Publish actual messages
                publisher.publish("events.user.login", "user=alice");
                publisher.publish("events.user.logout", "user=bob");
                publisher.publish("events.system.restart", "node=1");

                // Wait for the 3 messages with poll-based timeout
                long deadline = System.currentTimeMillis() + 5000;
                while (received.get() < 3 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }
            }
        }

        return received.get();
    }
}
