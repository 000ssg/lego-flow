package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Duration;
/**
 * Demonstrates NATS request/reply pattern.
 *
 * <p>A service subscribes to a subject, processes requests, and
 * replies to the requester's inbox. The requester uses the
 * built-in request/reply API with timeout handling.
 *
 * @since 0.1.0
 */
public final class RequestReplyDemo {

    private static final Logger LOG = LoggerFactory.getLogger(RequestReplyDemo.class);

    private RequestReplyDemo() {}

    /**
     * Runs the request/reply demo.
     *
     * @param port the server port (0 for ephemeral)
     * @return the reply data, or null if timeout
     * @throws IOException if connection fails
     * @throws InterruptedException if interrupted
     */
    public static String run(int port) throws IOException, InterruptedException {
        try (var server = new NatsServer(port)) {
            server.start(port);
            int actualPort = server.port();

            try (var service = new NatsClient("localhost", actualPort,
                    ConnectOptions.withDefaults("service"));
                 var requester = new NatsClient("localhost", actualPort,
                         ConnectOptions.withDefaults("requester"))) {

                service.connect();
                requester.connect();

                // Service listens and replies
                service.subscribe("math.add", msg -> {
                    LOG.info("Service received request: {}", msg.dataAsString());
                    String[] parts = msg.dataAsString().split("\\+");
                    int result = Integer.parseInt(parts[0].trim()) + Integer.parseInt(parts[1].trim());
                    try {
                        service.publish(msg.replyTo(), String.valueOf(result));
                    } catch (IOException e) {
                        LOG.error("Error sending reply", e);
                    }
                });

                Thread.sleep(50);

                // Request with timeout
                NatsMessage reply = requester.request("math.add", "10 + 20", Duration.ofSeconds(3));
                if (reply != null) {
                    LOG.info("Reply: {}", reply.dataAsString());
                    return reply.dataAsString();
                }
            }
        }
        return null;
    }
}
