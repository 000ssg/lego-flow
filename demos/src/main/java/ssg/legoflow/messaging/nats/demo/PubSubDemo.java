package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * @param port the server port (0 for ephemeral)
     * @return the number of messages received
     * @throws IOException if connection fails
     * @throws InterruptedException if interrupted
     */
    public static int run(int port) throws IOException, InterruptedException {
        var received = new java.util.concurrent.atomic.AtomicInteger(0);

        try (var server = new NatsServer(port)) {
            server.start(port);
            int actualPort = server.port();

            try (var subscriber = new NatsClient("localhost", actualPort,
                    ConnectOptions.withDefaults("sub-client"));
                 var publisher = new NatsClient("localhost", actualPort,
                         ConnectOptions.withDefaults("pub-client"))) {

                subscriber.connect();
                publisher.connect();

                // Separate latch to confirm subscription propagation (does not count toward received)
                var readyLatch = new CountDownLatch(1);

                // Subscribe to various subjects
                subscriber.subscribe("events.>", msg -> {
                    LOG.info("Received on {}: {}", msg.subject(), msg.dataAsString());
                    readyLatch.countDown();
                    if (!msg.subject().equals("events.__ready")) {
                        received.incrementAndGet();
                    }
                });

                // Probe to confirm subscription has propagated before publishing real messages
                publisher.publish("events.__ready", "probe");
                readyLatch.await(5, TimeUnit.SECONDS);

                // Publish actual messages
                publisher.publish("events.user.login", "user=alice");
                publisher.publish("events.user.logout", "user=bob");
                publisher.publish("events.system.restart", "node=1");

                // Wait for remaining 3 messages with poll-based timeout
                long deadline = System.currentTimeMillis() + 5000;
                while (received.get() < 3 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }
            }
        }

        return received.get();
    }
}
