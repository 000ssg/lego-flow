package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
/**
 * Request/reply pattern demo using AMQP message properties.
 *
 * <p>Demonstrates using correlation-id and reply-to properties to
 * implement a request/reply communication pattern.
 *
 * @since 0.1.0
 */
public final class RequestReplyDemo {

    private static final Logger LOG = LoggerFactory.getLogger(RequestReplyDemo.class);

    private RequestReplyDemo() {}

    /**
     * Runs the request/reply demo.
     *
     * @return true if the reply was received successfully
     * @throws Exception if an error occurs
     */
    public static boolean run() throws Exception {
        var containerConfig = ContainerConfig.defaults();
        try (var container = new AmqpContainer(containerConfig)) {
            container.start();
            int port = container.port();

            Thread.sleep(100);

            // Server
            var serverConfig = ClientConfig.builder().port(port).containerId("server").build();
            try (var server = new AmqpClient(serverConfig)) {
                server.connect();
                var serverSession = server.createSession();
                var requestReceiver = server.createReceiver(serverSession, "request-rcv", "request-queue");
                var replySender = server.createSender(serverSession, "reply-snd", "reply-queue");

                Thread.sleep(200);

                // Client
                var clientConfig = ClientConfig.builder().port(port).containerId("client").build();
                try (var client = new AmqpClient(clientConfig)) {
                    client.connect();
                    var clientSession = client.createSession();
                    var requestSender = client.createSender(clientSession, "request-snd", "request-queue");
                    var replyReceiver = client.createReceiver(clientSession, "reply-rcv", "reply-queue");

                    Thread.sleep(200);

                    // Send request
                    String correlationId = UUID.randomUUID().toString();
                    var request = new AmqpMessage()
                            .properties(Properties.builder()
                                    .messageId("req-1")
                                    .correlationId(correlationId)
                                    .replyTo("reply-queue")
                                    .build())
                            .bodyString("What is the answer?");

                    client.send(requestSender, request, true);
                    LOG.info("Sent request with correlationId={}", correlationId);

                    // Server receives and replies
                    Delivery reqDelivery = requestReceiver.receive(2, TimeUnit.SECONDS);
                    if (reqDelivery != null) {
                        String reqCorrelationId = reqDelivery.message().properties().correlationId();
                        var reply = new AmqpMessage()
                                .properties(Properties.builder()
                                        .messageId("reply-1")
                                        .correlationId(reqCorrelationId)
                                        .build())
                                .bodyString("42");

                        Thread.sleep(100);
                        server.send(replySender, reply, true);
                        LOG.info("Server sent reply");
                    }

                    // Client receives reply
                    Delivery replyDelivery = replyReceiver.receive(2, TimeUnit.SECONDS);
                    if (replyDelivery != null) {
                        LOG.info("Received reply: {}", replyDelivery.message().bodyAsString());
                        return "42".equals(replyDelivery.message().bodyAsString());
                    }
                }
            }
        }
        return false;
    }
}
