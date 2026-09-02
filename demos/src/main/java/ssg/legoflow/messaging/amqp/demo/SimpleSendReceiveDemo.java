package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

/**
 * Simple send/receive demo showing basic AMQP 1.0 messaging.
 *
 * <p>Starts a container (via AmqpContainerService), connects a producer and a consumer
 * (via AmqpClientService), sends messages through the container, and verifies they arrive.
 *
 * @since 0.1.0
 */
public final class SimpleSendReceiveDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleSendReceiveDemo.class);

    private SimpleSendReceiveDemo() {}

    /**
     * Runs the demo, returning the number of messages successfully received.
     *
     * @param messageCount the number of messages to send
     * @return the number of messages received
     * @throws Exception if an error occurs
     */
    public static int run(int messageCount) throws Exception {
        var containerService = AmqpContainerService.builder()
                .port(0)
                .containerId("demo-container")
                .build();
        containerService.connect(null);
        int port = containerService.port();
        LOG.info("Container started on port {}", port);

        Thread.sleep(100);

        // Producer
        try (var producerService = AmqpClientService.builder("localhost", port)
                .containerId("producer")
                .build()) {
            producerService.connect(null);
            var producer = producerService.getClient();
            AmqpSession session = producer.createSession();
            SenderLink sender = producer.createSender(session, "sender-1", "test-queue");

            Thread.sleep(200);

            // Consumer
            try (var consumerService = AmqpClientService.builder("localhost", port)
                    .containerId("consumer")
                    .build()) {
                consumerService.connect(null);
                var consumer = consumerService.getClient();
                AmqpSession consumerSession = consumer.createSession();
                ReceiverLink receiver = consumer.createReceiver(consumerSession, "receiver-1", "test-queue");

                Thread.sleep(200);

                // Send messages
                for (int i = 0; i < messageCount; i++) {
                    var message = AmqpMessage.of("Hello AMQP #" + i);
                    Delivery delivery = producer.send(sender, message, true);
                    LOG.info("Sent message {}", i);
                }

                // Receive messages
                int received = 0;
                for (int i = 0; i < messageCount; i++) {
                    Delivery delivery = receiver.receive(2, TimeUnit.SECONDS);
                    if (delivery != null) {
                        received++;
                        LOG.info("Received: {}", delivery.message().bodyAsString());
                    }
                }

                LOG.info("Sent {}, received {}", messageCount, received);
                producerService.disconnect(null);
                return received;
            }
        } finally {
            containerService.disconnect(null);
        }
    }
}
