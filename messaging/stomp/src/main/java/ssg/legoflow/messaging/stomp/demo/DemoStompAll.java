package ssg.legoflow.messaging.stomp.demo;

import ssg.legoflow.messaging.stomp.core.StompBroker;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.StompHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo of all STOMP 1.2 module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link StompBroker}</b> -- No external dependencies.
 * Runs anywhere without installation. Supports all 16 STOMP commands, destination routing,
 * subscription management, transactions (BEGIN/COMMIT/ABORT), three acknowledgment modes
 * (auto, client, client-individual), receipts, heart-beat negotiation, and content-type headers.
 * Ideal for development, testing, CI/CD, and learning the STOMP protocol.</p>
 *
 * <p><b>Alternative: External ActiveMQ / RabbitMQ STOMP plugin</b> -- Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with persistent message stores</li>
 *   <li>Durable topic subscriptions across broker restarts</li>
 *   <li>STOMP-over-WebSocket with real HTTP upgrade negotiation</li>
 *   <li>Integration testing against a specific broker implementation</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the transport layer: in-house uses
 * {@link InMemoryStompTransport} (queue pairs, no network I/O); external uses
 * {@code TcpStompClient} (TCP sockets). All client protocol code (CONNECT, SEND,
 * SUBSCRIBE, ACK, transactions) uses the same {@link StompClient} API regardless.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>CONNECT/DISCONNECT -- session lifecycle with version negotiation</li>
 *   <li>SEND/SUBSCRIBE/UNSUBSCRIBE -- message routing and subscription management</li>
 *   <li>MESSAGE frames -- broker-delivered messages with headers</li>
 *   <li>Transactions -- BEGIN/COMMIT/ABORT with buffered SEND</li>
 *   <li>ACK/NACK -- client-individual acknowledgment mode</li>
 *   <li>Receipts -- server confirmation of processed frames</li>
 *   <li>Content-type headers -- text and binary message content</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoStompAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoStompAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house StompBroker with InMemoryStompTransport (no I/O)
    // Alternative: set USE_EXTERNAL=true and configure host/port for ActiveMQ/RabbitMQ
    // =========================================================================

    /** Set to {@code true} to connect to an external STOMP broker (ActiveMQ, RabbitMQ STOMP plugin). */
    public static boolean USE_EXTERNAL = false;

    /** Host for external STOMP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external STOMP broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 61613;

    private DemoStompAll() {}

    /**
     * Results from running the full demo.
     *
     * @param connectDisconnect  true if CONNECT/DISCONNECT lifecycle succeeded
     * @param sendSubscribe      number of messages received via SUBSCRIBE
     * @param unsubscribeOk      true if UNSUBSCRIBE stopped message delivery
     * @param messageHeaders     true if MESSAGE frames contained required headers
     * @param transactionCommit  number of messages received after COMMIT
     * @param transactionAbort   true if ABORT discarded all buffered messages
     * @param ackNack            true if ACK/NACK in client-individual mode worked
     * @param receiptReceived    true if a RECEIPT frame was received for a sent message
     * @param contentTypeOk      true if content-type header was preserved
     */
    public record Results(
            boolean connectDisconnect,
            int sendSubscribe,
            boolean unsubscribeOk,
            boolean messageHeaders,
            int transactionCommit,
            boolean transactionAbort,
            boolean ackNack,
            boolean receiptReceived,
            boolean contentTypeOk
    ) {}

    /**
     * Runs the comprehensive demo covering all STOMP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        // All demos use in-memory transport for the in-house broker
        boolean connect = demoConnectDisconnect();
        int sendSub = demoSendSubscribe();
        boolean unsub = demoUnsubscribe();
        boolean headers = demoMessageHeaders();
        int txnCommit = demoTransactionCommit();
        boolean txnAbort = demoTransactionAbort();
        boolean ackNack = demoAckNack();
        boolean receipt = demoReceipts();
        boolean contentType = demoContentType();

        return new Results(connect, sendSub, unsub, headers, txnCommit,
                txnAbort, ackNack, receipt, contentType);
    }

    // ======================== 1. CONNECT / DISCONNECT =======================

    /**
     * Demonstrates STOMP CONNECT and DISCONNECT lifecycle.
     * CONNECT negotiates protocol version; DISCONNECT performs graceful shutdown.
     */
    static boolean demoConnectDisconnect() throws Exception {
        LOG.info("=== 1. CONNECT / DISCONNECT ===");
        var broker = new StompBroker();
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);

        try {
            // CONNECT with version negotiation
            StompFrame connectedFrame = client.connect("localhost");
            boolean connected = client.isConnected();
            LOG.info("Connected: {}, version: {}", connected,
                    connectedFrame.header(StompHeaders.VERSION));

            // DISCONNECT gracefully
            client.disconnect();
            Thread.sleep(100);
            boolean disconnected = !client.isConnected();
            LOG.info("Disconnected: {}", disconnected);

            return connected && disconnected;
        } finally {
            client.close();
            broker.close();
        }
    }

    // ======================== 2. SEND / SUBSCRIBE ===========================

    /**
     * Demonstrates SEND and SUBSCRIBE: publisher sends messages to a destination,
     * subscriber receives them via its subscription handler.
     */
    static int demoSendSubscribe() throws Exception {
        LOG.info("=== 2. SEND / SUBSCRIBE ===");
        var broker = new StompBroker();
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        var publisher = new StompClient(pubPair[0]);
        var subscriber = new StompClient(subPair[0]);

        try {
            publisher.connect("localhost");
            subscriber.connect("localhost");

            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            subscriber.subscribe("/topic/demo", msg -> {
                received.add(msg.bodyAsText());
                latch.countDown();
            });
            Thread.sleep(50);

            // Send 3 messages
            publisher.send("/topic/demo", "msg-1", "text/plain");
            publisher.send("/topic/demo", "msg-2", "text/plain");
            publisher.send("/topic/demo", "msg-3", "text/plain");

            latch.await(5, TimeUnit.SECONDS);
            LOG.info("Send/Subscribe: received {} messages", received.size());
            return received.size();
        } finally {
            publisher.close();
            subscriber.close();
            broker.close();
        }
    }

    // ======================== 3. UNSUBSCRIBE ================================

    /**
     * Demonstrates UNSUBSCRIBE: after unsubscribing, subsequent messages
     * to the destination are not delivered to the client.
     */
    static boolean demoUnsubscribe() throws Exception {
        LOG.info("=== 3. UNSUBSCRIBE ===");
        var broker = new StompBroker();
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        var publisher = new StompClient(pubPair[0]);
        var subscriber = new StompClient(subPair[0]);

        try {
            publisher.connect("localhost");
            subscriber.connect("localhost");

            var received = new CopyOnWriteArrayList<String>();

            // Subscribe and receive one message
            String subId = subscriber.subscribe("/topic/unsub-demo", msg -> {
                received.add(msg.bodyAsText());
            });
            Thread.sleep(50);

            publisher.send("/topic/unsub-demo", "before-unsub", "text/plain");
            Thread.sleep(200);

            // Unsubscribe
            subscriber.unsubscribe(subId);
            Thread.sleep(50);

            // This message should NOT be delivered
            publisher.send("/topic/unsub-demo", "after-unsub", "text/plain");
            Thread.sleep(200);

            LOG.info("Unsubscribe: received {} (should be 1)", received.size());
            return received.size() == 1 && received.contains("before-unsub");
        } finally {
            publisher.close();
            subscriber.close();
            broker.close();
        }
    }

    // ======================== 4. MESSAGE FRAME HEADERS ======================

    /**
     * Demonstrates MESSAGE frame headers: the broker adds required headers
     * (destination, message-id, subscription) to delivered MESSAGE frames.
     */
    static boolean demoMessageHeaders() throws Exception {
        LOG.info("=== 4. MESSAGE Frame Headers ===");
        var broker = new StompBroker();
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        var publisher = new StompClient(pubPair[0]);
        var subscriber = new StompClient(subPair[0]);

        try {
            publisher.connect("localhost");
            subscriber.connect("localhost");

            var messageFrame = new CompletableFuture<StompFrame>();

            subscriber.subscribe("/queue/headers", msg -> {
                messageFrame.complete(msg);
            });
            Thread.sleep(50);

            publisher.send("/queue/headers", "check-headers", "text/plain");

            StompFrame msg = messageFrame.get(5, TimeUnit.SECONDS);
            boolean hasDestination = msg.header(StompHeaders.DESTINATION) != null;
            boolean hasMessageId = msg.header(StompHeaders.MESSAGE_ID) != null;
            boolean hasSubscription = msg.header(StompHeaders.SUBSCRIPTION) != null;

            LOG.info("Message headers: destination={}, message-id={}, subscription={}",
                    hasDestination, hasMessageId, hasSubscription);
            return hasDestination && hasMessageId && hasSubscription;
        } finally {
            publisher.close();
            subscriber.close();
            broker.close();
        }
    }

    // ======================== 5. TRANSACTION COMMIT =========================

    /**
     * Demonstrates BEGIN/SEND/COMMIT: messages sent within a committed transaction
     * are delivered atomically after COMMIT.
     */
    static int demoTransactionCommit() throws Exception {
        LOG.info("=== 5. Transaction COMMIT ===");
        var broker = new StompBroker();
        var sndPair = InMemoryStompTransport.createPair();
        var rcvPair = InMemoryStompTransport.createPair();
        broker.accept(sndPair[1]);
        broker.accept(rcvPair[1]);

        var sender = new StompClient(sndPair[0]);
        var receiver = new StompClient(rcvPair[0]);

        try {
            sender.connect("localhost");
            receiver.connect("localhost");

            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            receiver.subscribe("/topic/tx-commit", msg -> {
                received.add(msg.bodyAsText());
                latch.countDown();
            });
            Thread.sleep(50);

            // BEGIN transaction
            String txId = "tx-demo-commit";
            sender.begin(txId);

            // SEND within transaction (buffered, not delivered yet)
            sender.send("/topic/tx-commit", "tx-msg-1", "text/plain", txId);
            sender.send("/topic/tx-commit", "tx-msg-2", "text/plain", txId);
            sender.send("/topic/tx-commit", "tx-msg-3", "text/plain", txId);

            // Brief pause to verify messages are not delivered before commit
            Thread.sleep(100);
            int beforeCommit = received.size();

            // COMMIT -- all messages delivered atomically
            sender.commit(txId);

            latch.await(5, TimeUnit.SECONDS);
            LOG.info("Transaction commit: before={}, after={}", beforeCommit, received.size());
            return received.size();
        } finally {
            sender.close();
            receiver.close();
            broker.close();
        }
    }

    // ======================== 6. TRANSACTION ABORT ==========================

    /**
     * Demonstrates BEGIN/SEND/ABORT: messages sent within an aborted transaction
     * are discarded and never delivered.
     */
    static boolean demoTransactionAbort() throws Exception {
        LOG.info("=== 6. Transaction ABORT ===");
        var broker = new StompBroker();
        var sndPair = InMemoryStompTransport.createPair();
        var rcvPair = InMemoryStompTransport.createPair();
        broker.accept(sndPair[1]);
        broker.accept(rcvPair[1]);

        var sender = new StompClient(sndPair[0]);
        var receiver = new StompClient(rcvPair[0]);

        try {
            sender.connect("localhost");
            receiver.connect("localhost");

            var received = new CopyOnWriteArrayList<String>();

            receiver.subscribe("/topic/tx-abort", msg -> {
                received.add(msg.bodyAsText());
            });
            Thread.sleep(50);

            // BEGIN, SEND, ABORT
            String txId = "tx-demo-abort";
            sender.begin(txId);
            sender.send("/topic/tx-abort", "lost-msg-1", "text/plain", txId);
            sender.send("/topic/tx-abort", "lost-msg-2", "text/plain", txId);
            sender.abort(txId);

            // Wait to confirm no messages arrive
            Thread.sleep(300);
            LOG.info("Transaction abort: received {} (should be 0)", received.size());
            return received.isEmpty();
        } finally {
            sender.close();
            receiver.close();
            broker.close();
        }
    }

    // ======================== 7. ACK / NACK =================================

    /**
     * Demonstrates client-individual acknowledgment mode: each message must be
     * individually acknowledged with ACK. NACK indicates processing failure.
     */
    static boolean demoAckNack() throws Exception {
        LOG.info("=== 7. ACK / NACK ===");
        var broker = new StompBroker();
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        var publisher = new StompClient(pubPair[0]);
        var subscriber = new StompClient(subPair[0]);

        try {
            publisher.connect("localhost");
            subscriber.connect("localhost");

            var ackedMessages = new CopyOnWriteArrayList<String>();
            var nackedMessages = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            // Subscribe with client-individual ack mode
            subscriber.subscribe("/queue/ack-demo", "client-individual", msg -> {
                String body = msg.bodyAsText();
                String ackId = msg.header(StompHeaders.ACK);
                if (ackId == null) {
                    ackId = msg.header(StompHeaders.MESSAGE_ID);
                }
                if (ackId != null) {
                    if (body.contains("good")) {
                        subscriber.ack(ackId);
                        ackedMessages.add(body);
                    } else {
                        subscriber.nack(ackId);
                        nackedMessages.add(body);
                    }
                }
                latch.countDown();
            });
            Thread.sleep(50);

            publisher.send("/queue/ack-demo", "good-message", "text/plain");
            publisher.send("/queue/ack-demo", "bad-message", "text/plain");

            latch.await(5, TimeUnit.SECONDS);
            LOG.info("ACK/NACK: acked={}, nacked={}", ackedMessages.size(), nackedMessages.size());
            return ackedMessages.size() == 1 && nackedMessages.size() == 1;
        } finally {
            publisher.close();
            subscriber.close();
            broker.close();
        }
    }

    // ======================== 8. RECEIPTS ===================================

    /**
     * Demonstrates receipts: sendWithReceipt() adds a receipt header, and the
     * broker responds with a RECEIPT frame confirming the SEND was processed.
     */
    static boolean demoReceipts() throws Exception {
        LOG.info("=== 8. Receipts ===");
        var broker = new StompBroker();
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);

        try {
            client.connect("localhost");

            // Subscribe so the destination exists
            client.subscribe("/queue/receipt-demo", msg -> {});
            Thread.sleep(50);

            // Send with receipt -- returns a future that completes when RECEIPT arrives
            CompletableFuture<StompFrame> receiptFuture =
                    client.sendWithReceipt("/queue/receipt-demo", "receipt-test", "text/plain");

            StompFrame receipt = receiptFuture.get(5, TimeUnit.SECONDS);
            boolean hasReceiptId = receipt != null && receipt.header(StompHeaders.RECEIPT_ID) != null;
            LOG.info("Receipt received: hasReceiptId={}", hasReceiptId);
            return hasReceiptId;
        } finally {
            client.close();
            broker.close();
        }
    }

    // ======================== 9. CONTENT-TYPE HEADERS =======================

    /**
     * Demonstrates content-type header preservation: messages sent with a specific
     * content-type have that header available in the delivered MESSAGE frame.
     */
    static boolean demoContentType() throws Exception {
        LOG.info("=== 9. Content-Type Headers ===");
        var broker = new StompBroker();
        var pubPair = InMemoryStompTransport.createPair();
        var subPair = InMemoryStompTransport.createPair();
        broker.accept(pubPair[1]);
        broker.accept(subPair[1]);

        var publisher = new StompClient(pubPair[0]);
        var subscriber = new StompClient(subPair[0]);

        try {
            publisher.connect("localhost");
            subscriber.connect("localhost");

            var messageFrame = new CompletableFuture<StompFrame>();

            subscriber.subscribe("/queue/content-type", msg -> {
                messageFrame.complete(msg);
            });
            Thread.sleep(50);

            publisher.send("/queue/content-type", "{\"key\":\"value\"}", "application/json");

            StompFrame msg = messageFrame.get(5, TimeUnit.SECONDS);
            String contentType = msg.header(StompHeaders.CONTENT_TYPE);
            LOG.info("Content-type: {}", contentType);
            return "application/json".equals(contentType);
        } finally {
            publisher.close();
            subscriber.close();
            broker.close();
        }
    }
}
