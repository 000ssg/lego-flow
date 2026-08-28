package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.service.AmqpClientService;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.Properties;
import ssg.legoflow.messaging.amqp.sasl.PlainMechanism;
import ssg.legoflow.messaging.amqp.sasl.SaslAuthenticator;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.server.service.AmqpContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Comprehensive AMQP 1.0 demo covering all module features.
 *
 * @since 0.1.0
 */
public final class DemoAmqpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoAmqpAll.class);

    /** Set to {@code true} to connect to an external AMQP 1.0 broker. */
    public static boolean USE_EXTERNAL = false;
    /** Host for external AMQP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";
    /** Port for external AMQP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 5672;

    private DemoAmqpAll() {}

    public record Results(
            int sendReceive,
            boolean pubSubFanOut,
            boolean requestReply,
            boolean transactionState,
            boolean creditFlowControl,
            boolean saslAuth,
            int multipleSessions,
            int linkCount
    ) {}

    /**
     * Runs the comprehensive demo covering all AMQP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runAgainstBroker(EXTERNAL_HOST, EXTERNAL_PORT);
        }
        var containerService = AmqpContainerService.builder()
                .port(0)
                .containerId("demo-container")
                .build();
        containerService.connect(null);
        int port = containerService.port();
        LOG.info("In-house AmqpContainer started on port {}", port);

        Thread.sleep(100);

        Results baseResults = runAgainstBroker("localhost", port);
        boolean sasl = demoSaslAuthentication();

        containerService.disconnect(null);
        return new Results(
                baseResults.sendReceive(),
                baseResults.pubSubFanOut(),
                baseResults.requestReply(),
                baseResults.transactionState(),
                baseResults.creditFlowControl(),
                sasl,
                baseResults.multipleSessions(),
                baseResults.linkCount()
        );
    }

    private static Results runAgainstBroker(String host, int port) throws Exception {
        int sendReceive = demoSendReceive(host, port);
        boolean pubSub = demoPubSubFanOut(host, port);
        boolean requestReply = demoRequestReply(host, port);
        boolean txnState = demoTransactions();
        boolean creditFlow = demoCreditFlowControl(host, port);
        int sessions = demoMultipleSessions(host, port);
        int links = demoSenderReceiverLinks(host, port);

        return new Results(sendReceive, pubSub, requestReply, txnState,
                creditFlow, false, sessions, links);
    }

    // ======================== 1. SEND / RECEIVE =============================

    static int demoSendReceive(String host, int port) throws Exception {
        LOG.info("=== 1. Send / Receive ===");
        try (var producerService = AmqpClientService.builder(host, port)
                .containerId("sr-producer")
                .build()) {
            producerService.connect(null);
            var producer = producerService.getClient();
            AmqpSession session = producer.createSession();
            SenderLink sender = producer.createSender(session, "sr-sender", "demo-queue");

            Thread.sleep(200);

            try (var consumerService = AmqpClientService.builder(host, port)
                    .containerId("sr-consumer")
                    .build()) {
                consumerService.connect(null);
                var consumer = consumerService.getClient();
                AmqpSession consSession = consumer.createSession();
                ReceiverLink receiver = consumer.createReceiver(consSession, "sr-receiver", "demo-queue");

                Thread.sleep(200);

                for (int i = 0; i < 5; i++) {
                    producer.send(sender, AmqpMessage.of("Hello AMQP #" + i), true);
                }

                int received = 0;
                for (int i = 0; i < 5; i++) {
                    Delivery d = receiver.receive(2, TimeUnit.SECONDS);
                    if (d != null) {
                        received++;
                        LOG.info("Received: {}", d.message().bodyAsString());
                    }
                }
                LOG.info("Send/receive: sent 5, received {}", received);
                producerService.disconnect(null);
                return received;
            }
        }
    }

    // ======================== 2. PUB/SUB FAN-OUT ============================

    static boolean demoPubSubFanOut(String host, int port) throws Exception {
        LOG.info("=== 2. Pub/Sub Fan-Out ===");

        try (var sub1Service = AmqpClientService.builder(host, port)
                .containerId("fanout-sub1").build();
             var sub2Service = AmqpClientService.builder(host, port)
                .containerId("fanout-sub2").build()) {
            sub1Service.connect(null);
            sub2Service.connect(null);
            var sub1 = sub1Service.getClient();
            var sub2 = sub2Service.getClient();
            var r1 = sub1.createReceiver(sub1.createSession(), "fanout-rcv1", "topic/fanout");
            var r2 = sub2.createReceiver(sub2.createSession(), "fanout-rcv2", "topic/fanout");

            Thread.sleep(200);

            try (var pubService = AmqpClientService.builder(host, port)
                    .containerId("fanout-pub").build()) {
                pubService.connect(null);
                var pub = pubService.getClient();
                var sender = pub.createSender(pub.createSession(), "fanout-snd", "topic/fanout");
                Thread.sleep(200);

                pub.send(sender, AmqpMessage.of("Fan-out message"), true);
                Thread.sleep(300);
            }

            Delivery d1 = r1.receive(1, TimeUnit.SECONDS);
            LOG.info("Fanout sub1 received: {}", d1 != null);
            sub1Service.disconnect(null);
            sub2Service.disconnect(null);
            return d1 != null;
        }
    }

    // ======================== 3. REQUEST / REPLY ============================

    static boolean demoRequestReply(String host, int port) throws Exception {
        LOG.info("=== 3. Request / Reply ===");

        try (var serverService = AmqpClientService.builder(host, port)
                .containerId("rr-server").build()) {
            serverService.connect(null);
            var server = serverService.getClient();
            var serverSession = server.createSession();
            var requestRcv = server.createReceiver(serverSession, "rr-req-rcv", "rr-request-queue");
            var replySnd = server.createSender(serverSession, "rr-reply-snd", "rr-reply-queue");

            Thread.sleep(200);

            try (var clientService = AmqpClientService.builder(host, port)
                    .containerId("rr-client").build()) {
                clientService.connect(null);
                var client = clientService.getClient();
                var clientSession = client.createSession();
                var requestSnd = client.createSender(clientSession, "rr-req-snd", "rr-request-queue");
                var replyRcv = client.createReceiver(clientSession, "rr-reply-rcv", "rr-reply-queue");

                Thread.sleep(200);

                String correlationId = UUID.randomUUID().toString();
                var request = new AmqpMessage()
                        .properties(Properties.builder()
                                .messageId("req-1")
                                .correlationId(correlationId)
                                .replyTo("rr-reply-queue")
                                .build())
                        .bodyString("What is 6*7?");

                client.send(requestSnd, request, true);

                Delivery reqDelivery = requestRcv.receive(2, TimeUnit.SECONDS);
                if (reqDelivery != null) {
                    var reply = new AmqpMessage()
                            .properties(Properties.builder()
                                    .messageId("reply-1")
                                    .correlationId(reqDelivery.message().properties().correlationId())
                                    .build())
                            .bodyString("42");
                    Thread.sleep(100);
                    server.send(replySnd, reply, true);
                }

                Delivery replyDelivery = replyRcv.receive(2, TimeUnit.SECONDS);
                if (replyDelivery != null) {
                    LOG.info("Reply: {}", replyDelivery.message().bodyAsString());
                    serverService.disconnect(null);
                    return "42".equals(replyDelivery.message().bodyAsString());
                }
            }
        }
        return false;
    }

    // ======================== 4. TRANSACTIONS ===============================

    static boolean demoTransactions() {
        LOG.info("=== 4. Transactions ===");
        var commitState = new DeliveryState.TransactionalState(
                "txn-commit-1".getBytes(), new DeliveryState.Accepted());
        var rollbackState = new DeliveryState.TransactionalState(
                "txn-rollback-1".getBytes(), new DeliveryState.Released());
        LOG.info("Commit outcome={}, rollback outcome={}", commitState.outcome(), rollbackState.outcome());
        return commitState.outcome() instanceof DeliveryState.Accepted
                && rollbackState.outcome() instanceof DeliveryState.Released;
    }

    // ======================== 5. CREDIT-BASED FLOW CONTROL ==================

    static boolean demoCreditFlowControl(String host, int port) throws Exception {
        LOG.info("=== 5. Credit-Based Flow Control ===");

        try (var producerService = AmqpClientService.builder(host, port)
                .containerId("flow-producer").build()) {
            producerService.connect(null);
            var producer = producerService.getClient();
            SenderLink sender = producer.createSender(producer.createSession(), "flow-sender", "flow-queue");

            Thread.sleep(200);

            try (var consumerService = AmqpClientService.builder(host, port)
                    .containerId("flow-consumer").build()) {
                consumerService.connect(null);
                var consumer = consumerService.getClient();
                ReceiverLink receiver = consumer.createReceiver(consumer.createSession(), "flow-receiver", "flow-queue");

                Thread.sleep(200);

                int sent = 0;
                for (int i = 0; i < 10; i++) {
                    producer.send(sender, AmqpMessage.of("flow-msg-" + i), true);
                    sent++;
                }

                int received = 0;
                for (int i = 0; i < 10; i++) {
                    Delivery d = receiver.receive(1, TimeUnit.SECONDS);
                    if (d != null) received++;
                }

                LOG.info("Flow control: sent {}, received {}", sent, received);
                producerService.disconnect(null);
                return received > 0;
            }
        }
    }

    // ======================== 6. SASL AUTHENTICATION ========================

    static boolean demoSaslAuthentication() throws Exception {
        LOG.info("=== 6. SASL Authentication ===");

        var saslContainerService = AmqpContainerService.builder()
                .port(0)
                .containerId("sasl-container")
                .build();
        saslContainerService.connect(null);
        saslContainerService.setMessageHandler((ctx, msg) -> {});
        int port = saslContainerService.port();
        Thread.sleep(100);

        try (var clientService = AmqpClientService.builder("localhost", port)
                .containerId("sasl-client")
                .username("demo-user")
                .password("demo-password")
                .build()) {
            clientService.connect(null);
            var client = clientService.getClient();
            boolean connected = client.isConnected();
            LOG.info("SASL PLAIN auth: connected={}", connected);
            return connected;
        } finally {
            saslContainerService.disconnect(null);
        }
    }

    // ======================== 7. MULTIPLE SESSIONS ==========================

    static int demoMultipleSessions(String host, int port) throws Exception {
        LOG.info("=== 7. Multiple Sessions ===");

        try (var clientService = AmqpClientService.builder(host, port)
                .containerId("multi-session").build()) {
            clientService.connect(null);
            var client = clientService.getClient();

            AmqpSession session1 = client.createSession();
            AmqpSession session2 = client.createSession();
            AmqpSession session3 = client.createSession();

            int count = 0;
            if (session1 != null) count++;
            if (session2 != null) count++;
            if (session3 != null) count++;

            LOG.info("Created {} sessions on single connection", count);
            return count;
        }
    }

    // ======================== 8. SENDER / RECEIVER LINKS ====================

    static int demoSenderReceiverLinks(String host, int port) throws Exception {
        LOG.info("=== 8. Sender / Receiver Links ===");

        try (var clientService = AmqpClientService.builder(host, port)
                .containerId("links-client").build()) {
            clientService.connect(null);
            var client = clientService.getClient();
            AmqpSession session = client.createSession();

            SenderLink sender1 = client.createSender(session, "link-snd-1", "addr-1");
            SenderLink sender2 = client.createSender(session, "link-snd-2", "addr-2");
            ReceiverLink receiver1 = client.createReceiver(session, "link-rcv-1", "addr-1");
            ReceiverLink receiver2 = client.createReceiver(session, "link-rcv-2", "addr-2");

            int count = 0;
            if (sender1 != null) count++;
            if (sender2 != null) count++;
            if (receiver1 != null) count++;
            if (receiver2 != null) count++;

            LOG.info("Created {} links (2 senders + 2 receivers)", count);
            return count;
        }
    }
}
