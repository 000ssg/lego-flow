package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.jetstream.*;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.messaging.nats.server.auth.TokenAuthenticator;
import ssg.legoflow.messaging.nats.server.auth.UserPassAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive demo of all NATS module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link NatsServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports all 12 protocol operations, subject-based
 * routing with wildcards, queue groups, request/reply, headers, authentication
 * (token + user/pass), and JetStream persistent streaming with durable consumers.
 * Ideal for development, testing, CI/CD, and learning the NATS protocol.</p>
 *
 * <p><b>Alternative: External NATS Server (nats-server)</b> — Set {@link #USE_EXTERNAL}{@code =true}
 * and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}. Required for:</p>
 * <ul>
 *   <li>Production load testing with clustering and route mesh</li>
 *   <li>TLS encryption and NKEY/JWT authentication</li>
 *   <li>Multi-node JetStream replication (R3+ clusters)</li>
 *   <li>Integration testing against a real NATS infrastructure</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (pub/sub, request/reply, JetStream) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Pub/Sub — publish and subscribe with wildcard subjects</li>
 *   <li>Request/Reply — synchronous request with inbox-based reply</li>
 *   <li>Queue Groups — load-balanced message distribution across workers</li>
 *   <li>JetStream Streams — create stream, publish, durable consumer, pull subscribe</li>
 *   <li>Authentication — token-based and user/password authentication</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoNatsAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoNatsAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house NatsServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for nats-server
    // =========================================================================

    /** Set to {@code true} to connect to an external NATS server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external NATS server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external NATS server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 4222;

    private DemoNatsAll() {}

    /**
     * Results from running the full demo.
     *
     * @param pubSubMessages     number of messages received via pub/sub
     * @param requestReply       true if request/reply returned expected result
     * @param queueGroupTotal    total messages processed across all queue group workers
     * @param queueGroupWorkers  number of workers that processed at least one message
     * @param jetStreamConsumed  number of messages consumed from JetStream
     * @param authToken          true if token authentication succeeded
     * @param authUserPass       true if user/password authentication succeeded
     */
    public record Results(
            int pubSubMessages,
            boolean requestReply,
            int queueGroupTotal,
            int queueGroupWorkers,
            int jetStreamConsumed,
            boolean authToken,
            boolean authUserPass
    ) {}

    /**
     * Runs the comprehensive demo covering all NATS features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }

        // Pub/sub, request/reply, queue groups (no auth)
        int pubSubMessages;
        boolean requestReply;
        int queueGroupTotal;
        int queueGroupWorkers;
        int jetStreamConsumed;

        try (var server = new NatsServer(0)) {
            server.start(0);
            int port = server.port();
            LOG.info("In-house NatsServer started on port {}", port);

            pubSubMessages = demoPubSub(port);
            requestReply = demoRequestReply(port);
            var queueResults = demoQueueGroups(port);
            queueGroupTotal = queueResults[0];
            queueGroupWorkers = queueResults[1];
            jetStreamConsumed = demoJetStream(server);
        }

        // Authentication demos require dedicated servers with auth configured
        boolean authToken = demoTokenAuth();
        boolean authUserPass = demoUserPassAuth();

        return new Results(pubSubMessages, requestReply, queueGroupTotal,
                queueGroupWorkers, jetStreamConsumed, authToken, authUserPass);
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        int pubSubMessages = demoPubSub(host, port);
        boolean requestReply = demoRequestReply(host, port);
        var queueResults = demoQueueGroups(host, port);

        // JetStream and auth not available without in-house server reference
        return new Results(pubSubMessages, requestReply, queueResults[0],
                queueResults[1], 0, false, false);
    }

    // ======================== 1. PUB/SUB ====================================

    /**
     * Demonstrates basic publish/subscribe with wildcard subjects.
     */
    static int demoPubSub(int port) throws IOException, InterruptedException {
        return demoPubSub("localhost", port);
    }

    /**
     * Demonstrates basic publish/subscribe with wildcard subjects.
     */
    static int demoPubSub(String host, int port) throws IOException, InterruptedException {
        LOG.info("=== 1. Pub/Sub ===");
        var received = new AtomicInteger(0);
        var latch = new CountDownLatch(3);

        try (var subscriber = new NatsClient(host, port,
                ConnectOptions.withDefaults("demo-sub"));
             var publisher = new NatsClient(host, port,
                     ConnectOptions.withDefaults("demo-pub"))) {

            subscriber.connect();
            publisher.connect();

            // Subscribe with wildcard '>' (matches one or more trailing tokens)
            subscriber.subscribe("demo.>", msg -> {
                LOG.info("Received on {}: {}", msg.subject(), msg.dataAsString());
                received.incrementAndGet();
                latch.countDown();
            });

            Thread.sleep(50); // Allow subscription to propagate

            // Publish to different subjects matching the wildcard
            publisher.publish("demo.user.login", "user=alice");
            publisher.publish("demo.user.logout", "user=bob");
            publisher.publish("demo.system.restart", "node=1");

            latch.await(5, TimeUnit.SECONDS);
        }

        LOG.info("Pub/sub received {} messages", received.get());
        return received.get();
    }

    // ======================== 2. REQUEST/REPLY ==============================

    /**
     * Demonstrates the request/reply pattern with automatic inbox management.
     * <p>
     * The requester publishes a message with a unique reply-to inbox subject.
     * The responder processes the request and publishes the reply to that inbox.
     * CompletableFuture-based with configurable timeout.
     */
    static boolean demoRequestReply(int port) throws IOException, InterruptedException {
        return demoRequestReply("localhost", port);
    }

    /**
     * Demonstrates the request/reply pattern with automatic inbox management.
     */
    static boolean demoRequestReply(String host, int port) throws IOException, InterruptedException {
        LOG.info("=== 2. Request/Reply ===");

        try (var service = new NatsClient(host, port,
                ConnectOptions.withDefaults("demo-service"));
             var requester = new NatsClient(host, port,
                     ConnectOptions.withDefaults("demo-requester"))) {

            service.connect();
            requester.connect();

            // Service subscribes and replies
            service.subscribe("math.add", msg -> {
                String[] parts = msg.dataAsString().split("\\+");
                int result = Integer.parseInt(parts[0].trim()) + Integer.parseInt(parts[1].trim());
                try {
                    service.publish(msg.replyTo(), String.valueOf(result)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LOG.error("Error sending reply", e);
                }
            });

            Thread.sleep(50);

            // Send request with 3-second timeout
            NatsMessage reply = requester.request("math.add", "15 + 25",
                    Duration.ofSeconds(3));

            if (reply != null) {
                String result = reply.dataAsString();
                LOG.info("Request/reply result: 15 + 25 = {}", result);
                return "40".equals(result);
            }
        }

        return false;
    }

    // ======================== 3. QUEUE GROUPS ================================

    /**
     * Demonstrates queue group load balancing.
     * <p>
     * Multiple subscribers in the same queue group receive messages in a
     * round-robin fashion, enabling horizontal scaling. Non-queued
     * subscribers still receive all messages independently.
     */
    static int[] demoQueueGroups(int port) throws IOException, InterruptedException {
        return demoQueueGroups("localhost", port);
    }

    /**
     * Demonstrates queue group load balancing.
     */
    static int[] demoQueueGroups(String host, int port) throws IOException, InterruptedException {
        LOG.info("=== 3. Queue Groups ===");
        int numWorkers = 3;
        int numMessages = 12;
        var workerCounts = new ConcurrentHashMap<String, AtomicInteger>();
        var latch = new CountDownLatch(numMessages);

        var workers = new NatsClient[numWorkers];
        try {
            for (int i = 0; i < numWorkers; i++) {
                String name = "worker-" + i;
                workers[i] = new NatsClient(host, port,
                        ConnectOptions.withDefaults(name));
                workers[i].connect();
                workerCounts.put(name, new AtomicInteger(0));

                final String workerName = name;
                workers[i].subscribe("tasks", "worker-group", msg -> {
                    workerCounts.get(workerName).incrementAndGet();
                    latch.countDown();
                });
            }

            Thread.sleep(50);

            // Publish tasks
            try (var publisher = new NatsClient(host, port,
                    ConnectOptions.withDefaults("demo-task-pub"))) {
                publisher.connect();
                for (int i = 0; i < numMessages; i++) {
                    publisher.publish("tasks", "task-" + i);
                }
            }

            latch.await(5, TimeUnit.SECONDS);

        } finally {
            for (var worker : workers) {
                if (worker != null) worker.close();
            }
        }

        int total = workerCounts.values().stream().mapToInt(AtomicInteger::get).sum();
        int activeWorkers = (int) workerCounts.values().stream()
                .filter(c -> c.get() > 0).count();

        LOG.info("Queue group: {} messages across {} active workers", total, activeWorkers);
        return new int[]{total, activeWorkers};
    }

    // ======================== 4. JETSTREAM ==================================

    /**
     * Demonstrates JetStream persistent streaming: create stream, publish
     * messages, create durable consumer, pull and acknowledge messages.
     * <p>
     * JetStream adds persistence to NATS. Streams capture messages published
     * to matching subjects. Consumers track delivery position with
     * configurable ack policies (none/all/explicit) and deliver policies
     * (ALL, LAST, NEW, BY_START_SEQ).
     */
    static int demoJetStream(NatsServer server) throws IOException {
        LOG.info("=== 4. JetStream ===");
        var jsm = server.jetStreamManager();

        // Create a stream capturing orders.> subjects
        var streamConfig = StreamConfig.builder("DEMO-ORDERS")
                .subjects("orders.>")
                .retention(StreamConfig.RetentionPolicy.LIMITS)
                .maxMsgs(1000)
                .build();
        jsm.createStream(streamConfig);

        // Publish messages directly to the stream store
        var stream = jsm.getStream("DEMO-ORDERS");
        for (int i = 1; i <= 5; i++) {
            stream.store().store("orders.new", null,
                    ("order-" + i).getBytes());
            LOG.info("Published order-{} to JetStream", i);
        }

        // Create a durable consumer with explicit ack policy
        var consumerConfig = ConsumerConfig.builder()
                .durable("demo-processor")
                .deliverPolicy(ConsumerConfig.DeliverPolicy.ALL)
                .ackPolicy(AckPolicy.EXPLICIT)
                .build();
        jsm.createConsumer("DEMO-ORDERS", consumerConfig);

        // Pull and acknowledge messages
        var pullSub = jsm.pullSubscribe("DEMO-ORDERS", "demo-processor");
        var messages = pullSub.fetch(10);

        int consumed = 0;
        for (var msg : messages) {
            LOG.info("JetStream consumed: {} (seq={})", msg.dataAsString(),
                    msg.headers() != null ? msg.headers().getFirst("Nats-Sequence") : "?");
            pullSub.ack(msg);
            consumed++;
        }

        LOG.info("JetStream consumed {} messages", consumed);
        return consumed;
    }

    // ======================== 5. TOKEN AUTHENTICATION ========================

    /**
     * Demonstrates token-based authentication.
     * <p>
     * The server is configured with a {@link TokenAuthenticator}. Clients
     * must provide the correct token in their CONNECT options. Connections
     * without a valid token are rejected.
     */
    static boolean demoTokenAuth() throws IOException, InterruptedException {
        LOG.info("=== 5. Token Authentication ===");

        try (var server = new NatsServer(0)) {
            server.setAuthenticator(new TokenAuthenticator("secret-token-123"));
            server.start(0);
            int port = server.port();

            // Connect with valid token
            try (var client = new NatsClient("localhost", port,
                    ConnectOptions.withDefaults("auth-client").withToken("secret-token-123"))) {
                client.connect();
                LOG.info("Token auth: connected successfully");
                return client.isConnected();
            }
        }
    }

    // ======================== 6. USER/PASS AUTHENTICATION ====================

    /**
     * Demonstrates username/password authentication.
     * <p>
     * The server is configured with a {@link UserPassAuthenticator}. Clients
     * must provide valid credentials in their CONNECT options.
     */
    static boolean demoUserPassAuth() throws IOException, InterruptedException {
        LOG.info("=== 6. User/Pass Authentication ===");

        try (var server = new NatsServer(0)) {
            var auth = new UserPassAuthenticator();
            auth.addUser("demo-user", "demo-password");
            server.setAuthenticator(auth);
            server.start(0);
            int port = server.port();

            // Connect with valid credentials
            try (var client = new NatsClient("localhost", port,
                    ConnectOptions.withDefaults("auth-client")
                            .withUserPass("demo-user", "demo-password"))) {
                client.connect();
                LOG.info("User/pass auth: connected successfully");
                return client.isConnected();
            }
        }
    }
}
