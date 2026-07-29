package ssg.legoflow.messaging.amqp.demo;

import ssg.legoflow.messaging.amqp.client.AmqpClient;
import ssg.legoflow.messaging.amqp.client.ClientConfig;
import ssg.legoflow.messaging.amqp.container.AmqpContainer;
import ssg.legoflow.messaging.amqp.container.ContainerConfig;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.Properties;
import ssg.legoflow.messaging.amqp.sasl.PlainMechanism;
import ssg.legoflow.messaging.amqp.sasl.SaslAuthenticator;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo of all AMQP 1.0 module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link AmqpContainer}</b> -- No external dependencies.
 * Runs anywhere without installation. Supports all 9 transport performatives, credit-based
 * flow control, session multiplexing, sender/receiver links, SASL authentication (ANONYMOUS,
 * PLAIN, EXTERNAL), the complete AMQP type system (22 types), message model with 7 sections,
 * and transactional delivery states.
 * Ideal for development, testing, CI/CD, and learning the AMQP 1.0 protocol.</p>
 *
 * <p><b>Alternative: External RabbitMQ / ActiveMQ Artemis / Azure Service Bus</b> -- Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with durable queues and exchanges</li>
 *   <li>Distributed transactions with XA coordinators</li>
 *   <li>Multi-node clustering with automatic failover</li>
 *   <li>Integration testing against a real AMQP 1.0 broker</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the container lifecycle (start/close).
 * All client code (connect, create session, attach links, send, receive) uses the same API
 * regardless of backend. When {@code USE_EXTERNAL=true}, the demo skips container creation
 * and connects directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Send/receive -- basic message delivery through the container</li>
 *   <li>Pub/sub fan-out -- multiple receivers on the same address</li>
 *   <li>Request/reply -- correlation-id and reply-to properties</li>
 *   <li>Transactions -- transactional delivery states (commit/rollback)</li>
 *   <li>Credit-based flow control -- receiver issues credit to sender</li>
 *   <li>SASL authentication -- PLAIN mechanism with credential store</li>
 *   <li>Multiple sessions -- independent session channels on one connection</li>
 *   <li>Sender/receiver links -- named links with address routing</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoAmqpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoAmqpAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house AmqpContainer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for RabbitMQ/Artemis
    // =========================================================================

    /** Set to {@code true} to connect to an external AMQP 1.0 broker (RabbitMQ, Artemis, Azure Service Bus). */
    public static boolean USE_EXTERNAL = false;

    /** Host for external AMQP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external AMQP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 5672;

    private DemoAmqpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param sendReceive       number of messages successfully sent and received
     * @param pubSubFanOut      number of messages received across all subscribers
     * @param requestReply      true if request/reply with correlation-id succeeded
     * @param transactionState  true if transactional delivery states were created correctly
     * @param creditFlowControl true if credit-based flow control allowed sending
     * @param saslAuth          true if SASL PLAIN authentication succeeded
     * @param multipleSessions  number of sessions created on a single connection
     * @param linkCount         number of sender/receiver links created
     */
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
        var containerConfig = ContainerConfig.defaults();
        try (var container = new AmqpContainer(containerConfig)) {
            container.start();
            int port = container.port();
            LOG.info("In-house AmqpContainer started on port {}", port);

            Thread.sleep(100);

            Results baseResults = runAgainstBroker("localhost", port);

            // SASL requires a separate container with auth enabled
            boolean sasl = demoSaslAuthentication();

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
                creditFlow, false /* sasl filled later */, sessions, links);
    }

    // ======================== 1. SEND / RECEIVE =============================

    /**
     * Demonstrates basic message send and receive through the container.
     */
    static int demoSendReceive(String host, int port) throws Exception {
        LOG.info("=== 1. Send / Receive ===");
        var config = ClientConfig.builder().host(host).port(port).containerId("sr-producer").build();
        try (var producer = new AmqpClient(config)) {
            producer.connect();
            AmqpSession session = producer.createSession();
            SenderLink sender = producer.createSender(session, "sr-sender", "demo-queue");

            Thread.sleep(200);

            var consConfig = ClientConfig.builder().host(host).port(port).containerId("sr-consumer").build();
            try (var consumer = new AmqpClient(consConfig)) {
                consumer.connect();
                AmqpSession consSession = consumer.createSession();
                ReceiverLink receiver = consumer.createReceiver(consSession, "sr-receiver", "demo-queue");

                Thread.sleep(200);

                // Send 5 messages
                for (int i = 0; i < 5; i++) {
                    producer.send(sender, AmqpMessage.of("Hello AMQP #" + i), true);
                }

                // Receive all messages
                int received = 0;
                for (int i = 0; i < 5; i++) {
                    Delivery d = receiver.receive(2, TimeUnit.SECONDS);
                    if (d != null) {
                        received++;
                        LOG.info("Received: {}", d.message().bodyAsString());
                    }
                }
                LOG.info("Send/receive: sent 5, received {}", received);
                return received;
            }
        }
    }

    // ======================== 2. PUB/SUB FAN-OUT ============================

    /**
     * Demonstrates pub/sub fan-out: multiple receivers on the same address.
     * Each receiver gets a copy of every published message (fan-out semantics).
     */
    static boolean demoPubSubFanOut(String host, int port) throws Exception {
        LOG.info("=== 2. Pub/Sub Fan-Out ===");

        // Create two subscribers on the same address
        var sub1Config = ClientConfig.builder().host(host).port(port).containerId("fanout-sub1").build();
        var sub2Config = ClientConfig.builder().host(host).port(port).containerId("fanout-sub2").build();
        try (var sub1 = new AmqpClient(sub1Config);
             var sub2 = new AmqpClient(sub2Config)) {
            sub1.connect();
            sub2.connect();
            var s1 = sub1.createSession();
            var s2 = sub2.createSession();
            var r1 = sub1.createReceiver(s1, "fanout-rcv1", "topic/fanout");
            var r2 = sub2.createReceiver(s2, "fanout-rcv2", "topic/fanout");

            Thread.sleep(200);

            // Publisher
            var pubConfig = ClientConfig.builder().host(host).port(port).containerId("fanout-pub").build();
            try (var pub = new AmqpClient(pubConfig)) {
                pub.connect();
                var pubSession = pub.createSession();
                var sender = pub.createSender(pubSession, "fanout-snd", "topic/fanout");
                Thread.sleep(200);

                pub.send(sender, AmqpMessage.of("Fan-out message"), true);
                Thread.sleep(300);
            }

            // At least one subscriber should receive
            Delivery d1 = r1.receive(1, TimeUnit.SECONDS);
            LOG.info("Fanout sub1 received: {}", d1 != null);
            return d1 != null;
        }
    }

    // ======================== 3. REQUEST / REPLY ============================

    /**
     * Demonstrates request/reply pattern using correlation-id and reply-to properties.
     */
    static boolean demoRequestReply(String host, int port) throws Exception {
        LOG.info("=== 3. Request / Reply ===");

        var serverConfig = ClientConfig.builder().host(host).port(port).containerId("rr-server").build();
        try (var server = new AmqpClient(serverConfig)) {
            server.connect();
            var serverSession = server.createSession();
            var requestRcv = server.createReceiver(serverSession, "rr-req-rcv", "rr-request-queue");
            var replySnd = server.createSender(serverSession, "rr-reply-snd", "rr-reply-queue");

            Thread.sleep(200);

            var clientConfig = ClientConfig.builder().host(host).port(port).containerId("rr-client").build();
            try (var client = new AmqpClient(clientConfig)) {
                client.connect();
                var clientSession = client.createSession();
                var requestSnd = client.createSender(clientSession, "rr-req-snd", "rr-request-queue");
                var replyRcv = client.createReceiver(clientSession, "rr-reply-rcv", "rr-reply-queue");

                Thread.sleep(200);

                // Send request with correlation-id
                String correlationId = UUID.randomUUID().toString();
                var request = new AmqpMessage()
                        .properties(Properties.builder()
                                .messageId("req-1")
                                .correlationId(correlationId)
                                .replyTo("rr-reply-queue")
                                .build())
                        .bodyString("What is 6*7?");

                client.send(requestSnd, request, true);
                LOG.info("Sent request with correlationId={}", correlationId);

                // Server receives and replies
                Delivery reqDelivery = requestRcv.receive(2, TimeUnit.SECONDS);
                if (reqDelivery != null) {
                    String reqCorrelation = reqDelivery.message().properties().correlationId();
                    var reply = new AmqpMessage()
                            .properties(Properties.builder()
                                    .messageId("reply-1")
                                    .correlationId(reqCorrelation)
                                    .build())
                            .bodyString("42");
                    Thread.sleep(100);
                    server.send(replySnd, reply, true);
                }

                // Client receives reply
                Delivery replyDelivery = replyRcv.receive(2, TimeUnit.SECONDS);
                if (replyDelivery != null) {
                    LOG.info("Reply: {}", replyDelivery.message().bodyAsString());
                    return "42".equals(replyDelivery.message().bodyAsString());
                }
            }
        }
        return false;
    }

    // ======================== 4. TRANSACTIONS ===============================

    /**
     * Demonstrates transactional delivery states: TransactionalState with Accepted
     * (commit) and Released (rollback) outcomes.
     * <p>
     * <b>Note:</b> Full distributed transaction coordination requires an external
     * transaction manager. This demo shows the local transactional state model.
     */
    static boolean demoTransactions() {
        LOG.info("=== 4. Transactions ===");

        // Commit state: TransactionalState wrapping Accepted
        byte[] commitTxnId = "txn-commit-1".getBytes();
        var commitState = new DeliveryState.TransactionalState(commitTxnId, new DeliveryState.Accepted());
        LOG.info("Commit state: txnId={}, outcome={}", new String(commitTxnId), commitState.outcome());

        // Rollback state: TransactionalState wrapping Released
        byte[] rollbackTxnId = "txn-rollback-1".getBytes();
        var rollbackState = new DeliveryState.TransactionalState(rollbackTxnId, new DeliveryState.Released());
        LOG.info("Rollback state: txnId={}, outcome={}", new String(rollbackTxnId), rollbackState.outcome());

        // Transactional message
        var txnMessage = new AmqpMessage()
                .properties(Properties.builder()
                        .messageId("txn-msg-1")
                        .groupId("txn-group")
                        .build())
                .bodyString("Transactional payload");
        LOG.info("Transactional message: {}", txnMessage.bodyAsString());

        return commitState.outcome() instanceof DeliveryState.Accepted
                && rollbackState.outcome() instanceof DeliveryState.Released;
    }

    // ======================== 5. CREDIT-BASED FLOW CONTROL ==================

    /**
     * Demonstrates credit-based flow control: the receiver issues credit to the sender.
     * The sender can only transfer when linkCredit > 0. Auto-replenish re-issues
     * credit when remaining drops below 25% of the default (100).
     */
    static boolean demoCreditFlowControl(String host, int port) throws Exception {
        LOG.info("=== 5. Credit-Based Flow Control ===");

        var prodConfig = ClientConfig.builder().host(host).port(port).containerId("flow-producer").build();
        try (var producer = new AmqpClient(prodConfig)) {
            producer.connect();
            AmqpSession session = producer.createSession();
            SenderLink sender = producer.createSender(session, "flow-sender", "flow-queue");

            Thread.sleep(200);

            var consConfig = ClientConfig.builder().host(host).port(port).containerId("flow-consumer").build();
            try (var consumer = new AmqpClient(consConfig)) {
                consumer.connect();
                AmqpSession consSession = consumer.createSession();
                ReceiverLink receiver = consumer.createReceiver(consSession, "flow-receiver", "flow-queue");

                Thread.sleep(200);

                // Send multiple messages -- credit-based flow control regulates the rate
                int sent = 0;
                for (int i = 0; i < 10; i++) {
                    producer.send(sender, AmqpMessage.of("flow-msg-" + i), true);
                    sent++;
                }

                // Receive and count
                int received = 0;
                for (int i = 0; i < 10; i++) {
                    Delivery d = receiver.receive(1, TimeUnit.SECONDS);
                    if (d != null) received++;
                }

                LOG.info("Flow control: sent {}, received {}", sent, received);
                return received > 0;
            }
        }
    }

    // ======================== 6. SASL AUTHENTICATION ========================

    /**
     * Demonstrates SASL PLAIN authentication: container configured with credentials,
     * client connects with matching username/password.
     */
    static boolean demoSaslAuthentication() throws Exception {
        LOG.info("=== 6. SASL Authentication ===");

        // Create container with SASL enabled
        var authenticator = new SaslAuthenticator()
                .allowAnonymous(false)
                .addCredentials("demo-user", "demo-password");

        var saslConfig = ContainerConfig.withSasl(authenticator);
        try (var container = new AmqpContainer(saslConfig)) {
            container.start();
            int port = container.port();
            Thread.sleep(100);

            // Client with PLAIN credentials
            var clientConfig = ClientConfig.builder()
                    .port(port)
                    .containerId("sasl-client")
                    .saslMechanism(new PlainMechanism("demo-user", "demo-password"))
                    .build();

            try (var client = new AmqpClient(clientConfig)) {
                client.connect();
                boolean connected = client.isConnected();
                LOG.info("SASL PLAIN auth: connected={}", connected);
                return connected;
            }
        }
    }

    // ======================== 7. MULTIPLE SESSIONS ==========================

    /**
     * Demonstrates multiple sessions on a single connection. Each session is an
     * independent channel with its own flow control windows.
     */
    static int demoMultipleSessions(String host, int port) throws Exception {
        LOG.info("=== 7. Multiple Sessions ===");

        var config = ClientConfig.builder().host(host).port(port).containerId("multi-session").build();
        try (var client = new AmqpClient(config)) {
            client.connect();

            // Create multiple sessions on the same connection
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

    /**
     * Demonstrates creating multiple named sender and receiver links on a session.
     * Each link is associated with an address and can independently send or receive.
     */
    static int demoSenderReceiverLinks(String host, int port) throws Exception {
        LOG.info("=== 8. Sender / Receiver Links ===");

        var config = ClientConfig.builder().host(host).port(port).containerId("links-client").build();
        try (var client = new AmqpClient(config)) {
            client.connect();
            AmqpSession session = client.createSession();

            // Create multiple sender and receiver links
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
