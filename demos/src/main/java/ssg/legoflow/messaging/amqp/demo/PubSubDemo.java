package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        var containerConfig = ContainerConfig.defaults();
        try (var container = new AmqpContainer(containerConfig)) {
            container.start();
            int port = container.port();

            Thread.sleep(100);

            AtomicInteger totalReceived = new AtomicInteger(0);
            var subscribers = new AmqpClient[subscriberCount];

            // Create subscribers
            for (int i = 0; i < subscriberCount; i++) {
                var config = ClientConfig.builder().port(port).containerId("sub-" + i).build();
                subscribers[i] = new AmqpClient(config);
                subscribers[i].connect();
                var session = subscribers[i].createSession();
                var receiver = subscribers[i].createReceiver(session, "sub-link-" + i, "topic/news");
            }

            Thread.sleep(200);

            // Create publisher
            var pubConfig = ClientConfig.builder().port(port).containerId("publisher").build();
            try (var publisher = new AmqpClient(pubConfig)) {
                publisher.connect();
                var session = publisher.createSession();
                var sender = publisher.createSender(session, "pub-link", "topic/news");

                Thread.sleep(200);

                // Publish messages
                for (int i = 0; i < messageCount; i++) {
                    publisher.send(sender, AmqpMessage.of("News item #" + i), true);
                }

                Thread.sleep(500);
            }

            // Close subscribers and count received
            for (var sub : subscribers) {
                sub.close();
            }

            return totalReceived.get();
        }
    }
}
