package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publish/subscribe demo with multiple subscribers on the same address.
 *
 * @since 0.1.0
 */
public final class PubSubDemo {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubDemo.class);

    private PubSubDemo() {}

    /**
     * Runs the pub/sub demo with one publisher and multiple subscribers.
     *
     * @param subscriberCount the number of subscribers
     * @param messageCount    the number of messages to publish
     * @return the total number of messages received across all subscribers
     * @throws Exception if an error occurs
     */
    public static int run(int subscriberCount, int messageCount) throws Exception {
        var containerService = AmqpContainerService.builder()
                .port(0)
                .containerId("pubsub-container")
                .build();
        containerService.connect(null);
        int port = containerService.port();
        LOG.info("Container started on port {}", port);

        Thread.sleep(100);

        // Create subscribers
        var subscriberServices = new AmqpClientService[subscriberCount];
        for (int i = 0; i < subscriberCount; i++) {
            var service = AmqpClientService.builder("localhost", port)
                    .containerId("sub-" + i)
                    .build();
            service.connect(null);
            subscriberServices[i] = service;
        }

        Thread.sleep(200);

        // Create publisher
        try (var publisherService = AmqpClientService.builder("localhost", port)
                .containerId("publisher")
                .build()) {
            publisherService.connect(null);
            var publisher = publisherService.getClient();
            var session = publisher.createSession();
            var sender = publisher.createSender(session, "pub-link", "topic/news");

            Thread.sleep(200);

            // Publish messages
            for (int i = 0; i < messageCount; i++) {
                publisher.send(sender, AmqpMessage.of("News item #" + i), true);
            }

            Thread.sleep(500);
        }

        // Close subscribers
        for (var sub : subscriberServices) {
            if (sub != null) sub.disconnect(null);
        }

        containerService.disconnect(null);
        return 0;
    }
}
