package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.session.AmqpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Simple send/receive demo showing basic AMQP 1.0 messaging.
 *
 * <p>Starts a container, connects a producer and a consumer, sends messages
 * through the container, and verifies they arrive at the consumer.
 *
 * @since 1.0.0
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
        var containerConfig = ContainerConfig.defaults();
        try (var container = new AmqpContainer(containerConfig)) {
            container.start();
            int port = container.port();
            LOG.info("Container started on port {}", port);

            // Wait for container to be ready
            Thread.sleep(100);

            // Producer
            var producerConfig = ClientConfig.builder().port(port).containerId("producer").build();
            try (var producer = new AmqpClient(producerConfig)) {
                producer.connect();
                AmqpSession session = producer.createSession();
                SenderLink sender = producer.createSender(session, "sender-1", "test-queue");

                // Wait for credit
                Thread.sleep(200);

                // Consumer
                var consumerConfig = ClientConfig.builder().port(port).containerId("consumer").build();
                try (var consumer = new AmqpClient(consumerConfig)) {
                    consumer.connect();
                    AmqpSession consumerSession = consumer.createSession();
                    ReceiverLink receiver = consumer.createReceiver(consumerSession, "receiver-1", "test-queue");

                    // Wait for links to be ready
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
                    return received;
                }
            }
        }
    }
}
