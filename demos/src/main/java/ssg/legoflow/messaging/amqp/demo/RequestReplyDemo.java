package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.Properties;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
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
        var containerService = AmqpContainerService.builder()
                .port(0)
                .containerId("rr-container")
                .build();
        containerService.connect(null);
        int port = containerService.port();
        LOG.info("Container started on port {}", port);

        Thread.sleep(100);

        // Server
        try (var serverService = AmqpClientService.builder("localhost", port)
                .containerId("server")
                .build()) {
            serverService.connect(null);
            var server = serverService.getClient();
            var serverSession = server.createSession();
            var requestReceiver = server.createReceiver(serverSession, "request-rcv", "request-queue");
            var replySender = server.createSender(serverSession, "reply-snd", "reply-queue");

            Thread.sleep(200);

            // Client
            try (var clientService = AmqpClientService.builder("localhost", port)
                    .containerId("client")
                    .build()) {
                clientService.connect(null);
                var client = clientService.getClient();
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
                                    .correlationId(reqCorrelationId)
                                    .build())
                            .bodyString("The answer is 42");
                    server.send(replySender, reply, true);
                    LOG.info("Server sent reply");
                }

                Thread.sleep(200);

                // Client receives reply
                Delivery replyDelivery = replyReceiver.receive(2, TimeUnit.SECONDS);
                if (replyDelivery != null) {
                    LOG.info("Client received reply: {}", replyDelivery.message().bodyAsString());
                    serverService.disconnect(null);
                    return correlationId.equals(replyDelivery.message().properties().correlationId());
                }
                serverService.disconnect(null);
                return false;
            }
        } finally {
            containerService.disconnect(null);
        }
    }
}
