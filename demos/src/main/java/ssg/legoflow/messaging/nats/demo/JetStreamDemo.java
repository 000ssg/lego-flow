package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.jetstream.*;
import ssg.legoflow.messaging.nats.server.NatsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates JetStream persistent streaming.
 *
 * <p>Creates a stream, publishes messages, creates a consumer,
 * and pulls messages with acknowledgement.
 *
 * @since 1.0.0
 */
public final class JetStreamDemo {

    private static final Logger LOG = LoggerFactory.getLogger(JetStreamDemo.class);

    private JetStreamDemo() {}

    /**
     * Runs the JetStream demo.
     *
     * @param port the server port (0 for ephemeral)
     * @return list of consumed messages
     * @throws IOException if an error occurs
     */
    public static List<NatsMessage> run(int port) throws IOException {
        var consumed = new ArrayList<NatsMessage>();

        try (var server = new NatsServer(port)) {
            server.start(port);
            var jsm = server.jetStreamManager();

            // Create a stream
            var streamConfig = StreamConfig.builder("ORDERS")
                    .subjects("orders.>")
                    .retention(StreamConfig.RetentionPolicy.LIMITS)
                    .maxMsgs(1000)
                    .build();
            jsm.createStream(streamConfig);

            // Publish messages directly to the stream store
            var stream = jsm.getStream("ORDERS");
            for (int i = 1; i <= 5; i++) {
                stream.store().store("orders.new", null,
                        ("order-" + i).getBytes());
                LOG.info("Published order-{}", i);
            }

            // Create a durable consumer
            var consumerConfig = ConsumerConfig.builder()
                    .durable("order-processor")
                    .deliverPolicy(ConsumerConfig.DeliverPolicy.ALL)
                    .ackPolicy(AckPolicy.EXPLICIT)
                    .build();
            jsm.createConsumer("ORDERS", consumerConfig);

            // Pull and process messages
            var pullSub = jsm.pullSubscribe("ORDERS", "order-processor");
            var messages = pullSub.fetch(10);

            for (var msg : messages) {
                LOG.info("Consumed: {} (seq={})", msg.dataAsString(),
                        msg.headers().getFirst("Nats-Sequence"));
                pullSub.ack(msg);
                consumed.add(msg);
            }
        }

        return consumed;
    }
}
