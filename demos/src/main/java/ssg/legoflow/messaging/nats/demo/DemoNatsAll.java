package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.jetstream.*;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.messaging.nats.server.auth.TokenAuthenticator;
import ssg.legoflow.messaging.nats.server.auth.UserPassAuthenticator;
import ssg.legoflow.messaging.nats.transport.InMemoryNatsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Comprehensive demo of all NATS module features.
 *
 * <h2>Transport</h2>
 * <p>All sections run against an in-house {@link NatsServer} over in-memory transport
 * pairs ({@link InMemoryNatsTransport#createPair()}) — no sockets, no ports, runs
 * anywhere. The server core is headless; each client connects over its own dedicated
 * in-memory pair wired into the server via {@link NatsServer#handleConnection}.
 * Supports all 12 protocol operations, subject-based routing with wildcards, queue
 * groups, request/reply, headers, authentication (token + user/pass), and JetStream
 * persistent streaming with durable consumers.
 * Ideal for development, testing, CI/CD, and learning the NATS protocol.</p>
 *
 * <p><b>Alternative: External NATS Server (nats-server)</b> — the in-memory design has
 * no external socket seam; {@link #USE_EXTERNAL}/{@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}
 * are retained as metadata only and are not used by the in-memory sections.</p>
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
 * @since 0.1.0
 */
public final class DemoNatsAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoNatsAll.class);

    // ============================= CONFIGURATION =============================
    // All sections run over the in-memory seam (headless NatsServer, no sockets).
    // The external-server fields below are retained as metadata only; the in-memory
    // design has no external socket seam, so they are not used by the sections.
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

        // Pub/sub, request/reply, queue groups (no auth) — each over the in-memory seam
        int pubSubMessages = demoPubSub();
        boolean requestReply = demoRequestReply();
        var queueResults = demoQueueGroups();
        int queueGroupTotal = queueResults[0];
        int queueGroupWorkers = queueResults[1];

        // JetStream needs the headless server reference
        int jetStreamConsumed;
        try (var server = new NatsServer()) {
            server.start();
            LOG.info("In-house NatsServer started (in-memory)");
            jetStreamConsumed = demoJetStream(server);
        }

        // Authentication demos require dedicated servers with auth configured
        boolean authToken = demoTokenAuth();
        boolean authUserPass = demoUserPassAuth();

        return new Results(pubSubMessages, requestReply, queueGroupTotal,
                queueGroupWorkers, jetStreamConsumed, authToken, authUserPass);
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        // Metadata-only: the in-memory seam has no external socket path.
        return new Results(0, false, 0, 0, 0, false, false);
    }

    // ======================== 1. PUB/SUB ====================================

    /**
     * Demonstrates basic publish/subscribe with wildcard subjects over the
     * in-memory seam.
     */
    static int demoPubSub() throws IOException, InterruptedException {
        LOG.info("=== 1. Pub/Sub ===");
        var received = new AtomicInteger(0);
        var latch = new CountDownLatch(3);

        try (var server = new NatsServer()) {
            server.start();

            var subPair = InMemoryNatsTransport.createPair();
            server.handleConnection(subPair[0]);
            var pubPair = InMemoryNatsTransport.createPair();
            server.handleConnection(pubPair[0]);

            try (var subscriber = new NatsClient(subPair[1],
                    ConnectOptions.withDefaults("demo-sub"));
                 var publisher = new NatsClient(pubPair[1],
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

                // Give subscriber's virtual thread time to process delivered messages
                Thread.sleep(100);
                latch.await(10, TimeUnit.SECONDS);
            }
        }

        LOG.info("Pub/sub received {} messages", received.get());
        return received.get();
    }

    // ======================== 2. REQUEST/REPLY ==============================

    /**
     * Demonstrates the request/reply pattern with automatic inbox management
     * over the in-memory seam.
     * <p>
     * The requester publishes a message with a unique reply-to inbox subject.
     * The responder processes the request and publishes the reply to that inbox.
     * CompletableFuture-based with configurable timeout.
     */
    static boolean demoRequestReply() throws IOException, InterruptedException {
        LOG.info("=== 2. Request/Reply ===");

        try (var server = new NatsServer()) {
            server.start();

            var servicePair = InMemoryNatsTransport.createPair();
            server.handleConnection(servicePair[0]);
            var requesterPair = InMemoryNatsTransport.createPair();
            server.handleConnection(requesterPair[0]);

            try (var service = new NatsClient(servicePair[1],
                    ConnectOptions.withDefaults("demo-service"));
                 var requester = new NatsClient(requesterPair[1],
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
        }

        return false;
    }

    // ======================== 3. QUEUE GROUPS ================================

    /**
     * Demonstrates queue group load balancing over the in-memory seam.
     * <p>
     * Multiple subscribers in the same queue group receive messages in a
     * round-robin fashion, enabling horizontal scaling. Non-queued
     * subscribers still receive all messages independently.
     */
    static int[] demoQueueGroups() throws IOException, InterruptedException {
        LOG.info("=== 3. Queue Groups ===");
        int numWorkers = 3;
        int numMessages = 12;
        var workerCounts = new ConcurrentHashMap<String, AtomicInteger>();
        var latch = new CountDownLatch(numMessages);

        try (var server = new NatsServer()) {
            server.start();

            var workers = new NatsClient[numWorkers];
            try {
                // Each worker client gets its OWN in-memory pair
                for (int i = 0; i < numWorkers; i++) {
                    String name = "worker-" + i;
                    var workerPair = InMemoryNatsTransport.createPair();
                    server.handleConnection(workerPair[0]);
                    workers[i] = new NatsClient(workerPair[1],
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

                // Publish tasks (publisher gets its own in-memory pair)
                var pubPair = InMemoryNatsTransport.createPair();
                server.handleConnection(pubPair[0]);
                var publisher = new NatsClient(pubPair[1],
                        ConnectOptions.withDefaults("demo-task-pub"));
                publisher.connect();
                try {
                    for (int i = 0; i < numMessages; i++) {
                        publisher.publish("tasks", "task-" + i);
                    }

                    // Give queue workers' virtual threads time to process messages
                    Thread.sleep(100);
                    // Wait for delivery before closing the publisher: closing the
                    // pair early would cut the server-side reader off mid-stream
                    // and drop in-flight messages.
                    latch.await(10, TimeUnit.SECONDS);
                } finally {
                    publisher.close();
                }

            } finally {
                for (var worker : workers) {
                    if (worker != null) worker.close();
                }
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

        try (var server = new NatsServer()) {
            server.setAuthenticator(new TokenAuthenticator("secret-token-123"));
            server.start();

            // Connect with valid token over the in-memory seam
            var clientPair = InMemoryNatsTransport.createPair();
            server.handleConnection(clientPair[0]);
            try (var client = new NatsClient(clientPair[1],
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

        try (var server = new NatsServer()) {
            var auth = new UserPassAuthenticator();
            auth.addUser("demo-user", "demo-password");
            server.setAuthenticator(auth);
            server.start();

            // Connect with valid credentials over the in-memory seam
            var clientPair = InMemoryNatsTransport.createPair();
            server.handleConnection(clientPair[0]);
            try (var client = new NatsClient(clientPair[1],
                    ConnectOptions.withDefaults("auth-client")
                            .withUserPass("demo-user", "demo-password"))) {
                client.connect();
                LOG.info("User/pass auth: connected successfully");
                return client.isConnected();
            }
        }
    }
}
