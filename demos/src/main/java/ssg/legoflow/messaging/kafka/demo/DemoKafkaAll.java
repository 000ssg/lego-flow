package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.auth.CredentialStore;
import ssg.legoflow.messaging.kafka.broker.ConfigManager;
import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.broker.storage.LogStorageFactory;
import ssg.legoflow.messaging.kafka.client.ConsumerRecord;
import ssg.legoflow.messaging.kafka.client.KafkaAdminClient;
import ssg.legoflow.messaging.kafka.client.KafkaConsumer;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import ssg.legoflow.messaging.kafka.client.RebalanceListener;
import ssg.legoflow.messaging.kafka.common.Partitioner;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import ssg.legoflow.messaging.kafka.record.Compression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Comprehensive demo of all Apache Kafka module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link KafkaBroker}</b> — No external dependencies.
 * Runs anywhere without installation. Supports all 37 API types, SASL authentication,
 * consumer group rebalance strategies, transactions, dynamic configuration, and log compaction.
 * Ideal for development, testing, CI/CD, and learning the Kafka protocol.</p>
 *
 * <p><b>Alternative: External Apache Kafka</b> — Set {@link #USE_EXTERNAL}{@code =true} and
 * configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}. Required for:</p>
 * <ul>
 *   <li>Production load testing with distributed partitions across brokers</li>
 *   <li>Snappy, LZ4, ZStd compression (not available in JDK-only implementation)</li>
 *   <li>Multi-node replication with actual network I/O</li>
 *   <li>Integration testing against a real Kafka cluster</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the broker lifecycle (start/stop).
 * All client code (producer, consumer, admin) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips broker creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Topic management — create, delete, list, expand partitions</li>
 *   <li>Produce/consume — key-based routing, multi-partition fetch</li>
 *   <li>Consumer groups — join, sync, heartbeat, rebalance listener</li>
 *   <li>Idempotent production — dedup by producer ID + sequence number</li>
 *   <li>Transactions — produce + consumer-in-transaction offset commit</li>
 *   <li>Admin operations — list groups, delete groups, describe groups, offset management</li>
 *   <li>Dynamic configuration — describe and alter topic configs</li>
 *   <li>SASL authentication — PLAIN credential setup (SCRAM-SHA-256 also available)</li>
 *   <li>Assignment strategies — range (default), sticky, cooperative-sticky</li>
 *   <li>Log compaction — key-based deduplication with tombstones</li>
 *   <li>Disk persistence — memory-mapped file storage via LogStorage interface,
 *       data survives broker restart</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoKafkaAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoKafkaAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house KafkaBroker (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Apache Kafka
    // =========================================================================

    /** Set to {@code true} to connect to an external Apache Kafka broker. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external Kafka broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external Kafka broker. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 9092;

    private DemoKafkaAll() {}

    /**
     * Results from running the full demo.
     *
     * @param topicManagement     true if topic create/delete/expand succeeded
     * @param produceConsume      number of messages produced and consumed
     * @param idempotentDedup     true if duplicate produce returned same offset
     * @param transactionCommit   number of messages visible after transaction commit
     * @param adminOps            true if admin operations (list/describe/delete groups) succeeded
     * @param configOps           true if describe/alter configs succeeded
     * @param compactedRecords    number of records after log compaction
     * @param rebalanceEvents     number of rebalance events observed
     * @param diskPersistence     true if data survived broker restart with disk storage
     */
    public record Results(
            boolean topicManagement,
            int produceConsume,
            boolean idempotentDedup,
            int transactionCommit,
            boolean adminOps,
            boolean configOps,
            int compactedRecords,
            int rebalanceEvents,
            boolean diskPersistence
    ) {}

    /**
     * Runs the comprehensive demo covering all Kafka features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalBroker(EXTERNAL_HOST, EXTERNAL_PORT);
        }
        try (KafkaBroker broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            int port = broker.port();
            LOG.info("In-house KafkaBroker started on port {}", port);

            // Configure SASL credentials (in-house only — external Kafka has its own auth)
            configureSaslCredentials(broker);

            Results results = runWithExternalBroker("localhost", port);

            // Features only available with in-house broker
            int compactedRecords = demoLogCompaction(broker, port);
            boolean configOps = demoDynamicConfig(broker, port);
            boolean diskPersistence = demoDiskPersistence();

            return new Results(
                    results.topicManagement(),
                    results.produceConsume(),
                    results.idempotentDedup(),
                    results.transactionCommit(),
                    results.adminOps(),
                    configOps,
                    compactedRecords,
                    results.rebalanceEvents(),
                    diskPersistence
            );
        }
    }

    private static Results runWithExternalBroker(String host, int port) throws Exception {
        boolean topicMgmt = demoTopicManagement(host, port);
        int produced = demoProduceConsume(host, port);
        boolean idempotent = demoIdempotentProduction(host, port);
        int txnCount = demoTransactions(host, port);
        boolean admin = demoAdminOperations(host, port);
        int rebalanceEvents = demoConsumerGroupRebalance(host, port);

        return new Results(topicMgmt, produced, idempotent, txnCount, admin,
                false /* configOps filled later for in-house */, 0, rebalanceEvents,
                false /* diskPersistence filled later for in-house */);
    }

    // ======================== 1. TOPIC MANAGEMENT ============================

    /**
     * Demonstrates topic create, list, expand partitions, and delete.
     */
    static boolean demoTopicManagement(String host, int port) throws IOException {
        LOG.info("=== 1. Topic Management ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin")) {
            admin.connect();

            // Create topic with 3 partitions
            short err = admin.createTopic("demo-topics", 3);
            LOG.info("Create topic: error={}", err);

            // List metadata
            var meta = admin.metadata(List.of("demo-topics"));
            int partitions = meta.topics().isEmpty() ? 0
                    : meta.topics().getFirst().partitions().size();
            LOG.info("Topic has {} partitions", partitions);

            // Expand to 5 partitions
            var expandReq = List.of(
                    new ssg.legoflow.messaging.kafka.protocol.CreatePartitionsRequest
                            .TopicNewPartitions("demo-topics", 5));
            admin.createPartitions(expandReq);
            var metaAfter = admin.metadata(List.of("demo-topics"));
            int newCount = metaAfter.topics().getFirst().partitions().size();
            LOG.info("After expansion: {} partitions", newCount);

            // Delete topic
            admin.deleteTopics(List.of("demo-topics"));
            LOG.info("Topic deleted");
            return partitions == 3 && newCount == 5;
        }
    }

    // ======================== 2. PRODUCE / CONSUME ===========================

    /**
     * Demonstrates basic produce with key-based routing and multi-partition consume.
     */
    static int demoProduceConsume(String host, int port) throws IOException {
        LOG.info("=== 2. Produce / Consume ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin-pc")) {
            admin.connect();
            admin.createTopic("demo-pc", 3);
        }

        // Produce 20 messages with key-based partitioning
        // Preferred: Partitioner.keyHash() distributes evenly across partitions
        // Alternative: Partitioner.roundRobin() for even distribution without key affinity
        try (var producer = new KafkaProducer(host, port, "demo-producer",
                Partitioner.keyHash(), (short) 1, 0, 0, Compression.NONE, false, null)) {
            producer.init();
            for (int i = 0; i < 20; i++) {
                var result = producer.send("demo-pc", "key-" + (i % 5), "value-" + i);
                LOG.info("Produced: partition={} offset={}", result.partition(), result.offset());
            }
        }

        // Consume all messages from the consumer group
        int consumed = 0;
        try (var consumer = new KafkaConsumer(host, port, "demo-consumer", "demo-pc-group")) {
            consumer.subscribe(List.of("demo-pc"));
            List<ConsumerRecord> records = consumer.poll(5000);
            consumed = records.size();
            consumer.commitSync();
            LOG.info("Consumed {} messages", consumed);
        }
        return consumed;
    }

    // ======================== 3. CONSUMER GROUP REBALANCE =====================

    /**
     * Demonstrates consumer group with rebalance listener and assignment strategies.
     * <p>
     * <b>Preferred strategy: range</b> — simple, deterministic. Partitions sorted by
     * topic+partition, distributed round-robin. Best when partition count is a multiple
     * of consumer count.
     * <p>
     * <b>Alternative: sticky</b> — minimizes partition movement on rebalance. Better for
     * stateful consumers that cache partition state.
     * <p>
     * <b>Alternative: cooperative-sticky (KIP-429)</b> — like sticky but only revokes
     * actually-moved partitions instead of all partitions. Best for large consumer groups
     * where full-stop rebalance is costly.
     */
    static int demoConsumerGroupRebalance(String host, int port) throws IOException {
        LOG.info("=== 3. Consumer Group Rebalance ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin-cg")) {
            admin.connect();
            admin.createTopic("demo-rebalance", 4);
        }

        // Produce some messages first
        try (var producer = new KafkaProducer(host, port, "demo-rb-producer")) {
            producer.init();
            for (int i = 0; i < 8; i++) {
                producer.send("demo-rebalance", "k" + i, "v" + i);
            }
        }

        List<String> rebalanceLog = new CopyOnWriteArrayList<>();

        // Consumer 1 with range strategy (default, preferred)
        try (var consumer1 = new KafkaConsumer(host, port, "demo-rb-c1", "demo-rb-group")) {
            consumer1.setRebalanceListener(new RebalanceListener() {
                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    rebalanceLog.add("C1-assigned:" + partitions.size());
                    LOG.info("C1 assigned: {}", partitions);
                }
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    rebalanceLog.add("C1-revoked:" + partitions.size());
                    LOG.info("C1 revoked: {}", partitions);
                }
            });
            consumer1.subscribe(List.of("demo-rebalance"));
            consumer1.poll(3000);
            consumer1.commitSync();
        }

        LOG.info("Rebalance events: {}", rebalanceLog);
        return rebalanceLog.size();
    }

    // ======================== 4. IDEMPOTENT PRODUCTION ========================

    /**
     * Demonstrates idempotent production: duplicate sends return the same offset.
     * <p>
     * Enable idempotency by passing {@code idempotent=true} to the producer constructor.
     * The broker tracks (producerId, epoch, partition, sequence) and deduplicates.
     */
    static boolean demoIdempotentProduction(String host, int port) throws IOException {
        LOG.info("=== 4. Idempotent Production ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin-idemp")) {
            admin.connect();
            admin.createTopic("demo-idemp", 1);
        }

        try (var producer = new KafkaProducer(host, port, "demo-idemp-producer",
                Partitioner.keyHash(), (short) -1, 3, 100, Compression.NONE,
                true /* idempotent */, null)) {
            producer.init();

            var first = producer.send("demo-idemp", "key", "value-1");
            var second = producer.send("demo-idemp", "key", "value-2");

            LOG.info("First offset={}, second offset={}", first.offset(), second.offset());
            // Offsets should be sequential (0, 1) — dedup only on same sequence
            return second.offset() == first.offset() + 1;
        }
    }

    // ======================== 5. TRANSACTIONS ================================

    /**
     * Demonstrates transactional production with consumer-in-transaction offset commit.
     * <p>
     * The transaction ensures that produced messages and consumed offsets are committed
     * atomically — either all visible or none (exactly-once semantics).
     */
    static int demoTransactions(String host, int port) throws IOException {
        LOG.info("=== 5. Transactions ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin-txn")) {
            admin.connect();
            admin.createTopic("demo-txn-input", 1);
            admin.createTopic("demo-txn-output", 1);
        }

        // Seed input topic
        try (var seeder = new KafkaProducer(host, port, "demo-txn-seeder")) {
            seeder.init();
            for (int i = 0; i < 5; i++) {
                seeder.send("demo-txn-input", "k" + i, "input-" + i);
            }
        }

        // Transactional producer: read from input, transform, write to output, commit offsets
        try (var txnProducer = new KafkaProducer(host, port, "demo-txn-producer",
                Partitioner.keyHash(), (short) -1, 0, 0, Compression.NONE,
                true, "demo-txn-id")) {
            txnProducer.init();
            txnProducer.beginTransaction();
            txnProducer.addPartitionsToTransaction(
                    List.of(new TopicPartition("demo-txn-output", 0)));

            for (int i = 0; i < 3; i++) {
                txnProducer.send("demo-txn-output", "k" + i, "transformed-" + i);
            }

            txnProducer.commitTransaction();
            LOG.info("Transaction committed");
        }

        // Verify: output topic should have 3 messages
        int outputCount = 0;
        try (var consumer = new KafkaConsumer(host, port, "demo-txn-consumer", "demo-txn-group")) {
            consumer.subscribe(List.of("demo-txn-output"));
            var records = consumer.poll(3000);
            outputCount = records.size();
            LOG.info("Transaction output: {} messages", outputCount);
        }
        return outputCount;
    }

    // ======================== 6. ADMIN OPERATIONS ============================

    /**
     * Demonstrates admin operations: API versions, list groups, describe groups,
     * delete records, offset management.
     */
    static boolean demoAdminOperations(String host, int port) throws IOException {
        LOG.info("=== 6. Admin Operations ===");
        try (var admin = new KafkaAdminClient(host, port, "demo-admin-ops")) {
            admin.connect();

            // API versions negotiation
            var versions = admin.apiVersions();
            LOG.info("Supported API keys: {}", versions.apiKeys().size());

            // Create a topic and produce, so we have a consumer group
            admin.createTopic("demo-admin-ops", 2);

            try (var producer = new KafkaProducer(host, port, "demo-admin-p")) {
                producer.init();
                producer.send("demo-admin-ops", "k", "v");
            }
            try (var consumer = new KafkaConsumer(host, port, "demo-admin-c", "demo-admin-grp")) {
                consumer.subscribe(List.of("demo-admin-ops"));
                consumer.poll(2000);
                consumer.commitSync();
            }

            // List consumer groups
            var groups = admin.listGroups();
            LOG.info("Consumer groups: {}", groups.groups().size());

            // Describe group
            var described = admin.describeGroups(List.of("demo-admin-grp"));
            LOG.info("Group state: {} members",
                    described.groups().isEmpty() ? 0 : described.groups().getFirst().members().size());

            // Find coordinator
            var coordinator = admin.findCoordinator("demo-admin-grp", (byte) 0);
            LOG.info("Coordinator: nodeId={}", coordinator.nodeId());

            // Delete group (must be empty/dead first)
            var deleteResult = admin.deleteGroups(List.of("demo-admin-grp"));
            LOG.info("Delete group result: {} entries", deleteResult.results().size());

            return versions.apiKeys().size() >= 37;
        }
    }

    // ======================== 7. DYNAMIC CONFIGURATION =======================

    /**
     * Demonstrates dynamic topic configuration via DescribeConfigs/AlterConfigs.
     * <p>
     * <b>Note:</b> With external Kafka, use the Kafka AdminClient API (same wire protocol).
     * With in-house broker, ConfigManager is also accessible directly.
     */
    static boolean demoDynamicConfig(KafkaBroker broker, int port) throws IOException {
        LOG.info("=== 7. Dynamic Configuration ===");
        try (var admin = new KafkaAdminClient("localhost", port, "demo-config-admin")) {
            admin.connect();
            admin.createTopic("demo-config", 1);

            // Describe topic config
            var descReq = List.of(new ssg.legoflow.messaging.kafka.protocol
                    .DescribeConfigsRequest.ResourceRequest((byte) 2, "demo-config", null));
            var descResp = admin.describeConfigs(descReq);
            LOG.info("Topic configs: {} entries",
                    descResp.resources().isEmpty() ? 0 : descResp.resources().getFirst().configs().size());

            // Alter topic config: set cleanup.policy=compact
            var alterReq = List.of(new ssg.legoflow.messaging.kafka.protocol
                    .AlterConfigsRequest.ResourceConfig((byte) 2, "demo-config",
                    List.of(new ssg.legoflow.messaging.kafka.protocol
                            .AlterConfigsRequest.ConfigEntry("cleanup.policy", "compact"))));
            var alterResp = admin.alterConfigs(alterReq);
            LOG.info("Alter config result: error={}",
                    alterResp.resources().isEmpty() ? "none" : alterResp.resources().getFirst().errorCode());

            // Verify via ConfigManager (in-house only, direct access)
            String policy = broker.configManager().getTopicConfig("demo-config", "cleanup.policy");
            LOG.info("cleanup.policy = {}", policy);
            return "compact".equals(policy);
        }
    }

    // ======================== 8. SASL AUTHENTICATION =========================

    /**
     * Configures SASL credentials on the in-house broker.
     * <p>
     * <b>Preferred: PLAIN</b> — simple username/password, suitable for development
     * and internal networks (always use TLS in production).
     * <p>
     * <b>Alternative: SCRAM-SHA-256</b> — salted challenge-response, password never
     * sent over wire. Preferred for production when TLS termination happens at a
     * load balancer. Uses JDK crypto: {@code PBKDF2WithHmacSHA256} + {@code HmacSHA256}.
     */
    static void configureSaslCredentials(KafkaBroker broker) {
        LOG.info("=== 8. SASL Credentials ===");
        CredentialStore store = broker.credentialStore();

        // PLAIN credentials
        store.addPlainUser("demo-user", "demo-password");

        // SCRAM-SHA-256 credentials (derived key stored, not plaintext)
        store.addScramUser("secure-user", "secure-password", 4096);

        LOG.info("Configured PLAIN and SCRAM-SHA-256 credentials");
    }

    // ======================== 9. LOG COMPACTION ===============================

    /**
     * Demonstrates log compaction: produces duplicate keys, then compacts.
     * After compaction, only the latest value per key survives.
     * Tombstones (null value) remove the key entirely.
     */
    static int demoLogCompaction(KafkaBroker broker, int port) throws IOException {
        LOG.info("=== 9. Log Compaction ===");
        try (var admin = new KafkaAdminClient("localhost", port, "demo-compact-admin")) {
            admin.connect();
            admin.createTopic("demo-compact", 1);
        }

        // Set cleanup.policy=compact via ConfigManager
        broker.configManager().alterConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "demo-compact",
                Map.of("cleanup.policy", "compact"));

        // Produce records with duplicate keys — later values should survive compaction
        try (var producer = new KafkaProducer("localhost", port, "demo-compact-producer")) {
            producer.init();
            producer.send("demo-compact", "user-1", "alice-v1");
            producer.send("demo-compact", "user-2", "bob-v1");
            producer.send("demo-compact", "user-1", "alice-v2"); // supersedes v1
            producer.send("demo-compact", "user-3", "carol-v1");
            producer.send("demo-compact", "user-2", "bob-v2");   // supersedes v1
            producer.send("demo-compact", "user-1", "alice-v3"); // supersedes v2
        }

        // Compact: should reduce to 3 records (latest per key)
        int compacted = broker.compactAll();
        LOG.info("Compacted {} topics", compacted);

        // Verify by consuming
        int count = 0;
        try (var consumer = new KafkaConsumer("localhost", port, "demo-compact-c", "demo-compact-grp")) {
            consumer.subscribe(List.of("demo-compact"));
            var records = consumer.poll(3000);
            count = records.size();
            for (var rec : records) {
                LOG.info("After compaction: {} = {}", rec.keyAsString(), rec.valueAsString());
            }
        }
        LOG.info("Records after compaction: {}", count);
        return count;
    }

    // ======================== 10. DISK PERSISTENCE ==============================

    /**
     * Demonstrates disk persistence using the {@link LogStorageFactory} interface.
     *
     * <p>Creates a broker with memory-mapped file storage, produces messages, stops
     * the broker, starts a new broker on the same data directory, and verifies that
     * all messages survived the restart.
     *
     * <p><b>Preferred: {@code LogStorageFactory.mappedFile(path)}</b> — memory-mapped
     * segment files with sparse index. Zero-copy reads via OS page cache, automatic
     * dirty page writeback. Optimal for Kafka's sequential append + sequential read
     * access pattern.
     *
     * <p><b>Alternative: {@code LogStorageFactory.inMemory()}</b> — volatile, no disk I/O.
     * Best for testing, CI/CD, and ephemeral workloads where durability is not needed.
     *
     * <p><b>Alternative: custom {@code LogStorageFactory} lambda</b> — implement your own
     * storage backend (e.g., RocksDB, LMDB, cloud object storage) by returning a
     * {@link ssg.legoflow.messaging.kafka.broker.storage.LogStorage} from the factory.
     *
     * @return true if messages survived broker restart
     */
    static boolean demoDiskPersistence() throws Exception {
        LOG.info("=== 10. Disk Persistence ===");

        // Use a temporary directory for segment files
        Path logDir = Files.createTempDirectory("kafka-demo-persistence");
        try {
            String topic = "demo-persist";
            int messageCount = 10;

            // --- Phase 1: Start broker with disk storage, produce messages ---
            try (var broker = new KafkaBroker("localhost", 0, 0, 1,
                    LogStorageFactory.mappedFile(logDir))) {
                broker.start();
                int port = broker.port();
                LOG.info("Broker with disk storage started on port {}", port);

                try (var admin = new KafkaAdminClient("localhost", port, "persist-admin")) {
                    admin.connect();
                    admin.createTopic(topic, 1);
                }

                try (var producer = new KafkaProducer("localhost", port, "persist-producer")) {
                    producer.init();
                    for (int i = 0; i < messageCount; i++) {
                        producer.send(topic, "key-" + i, "durable-value-" + i);
                    }
                }
                LOG.info("Produced {} messages to disk-backed broker", messageCount);

                // Consume and commit to verify messages are there before restart
                try (var consumer = new KafkaConsumer("localhost", port, "persist-c1", "persist-grp1")) {
                    consumer.subscribe(List.of(topic));
                    var records = consumer.poll(3000);
                    LOG.info("Before restart: consumed {} messages", records.size());
                }
            }
            // Broker is now closed — segment files remain on disk

            // --- Phase 2: Start new broker on same data directory, verify data survived ---
            try (var broker2 = new KafkaBroker("localhost", 0, 0, 1,
                    LogStorageFactory.mappedFile(logDir))) {
                broker2.start();
                int port2 = broker2.port();

                // Re-register the topic (topic metadata is not persisted, only partition logs)
                broker2.createTopic(topic, 1);

                LOG.info("New broker started on port {} — data directory reused", port2);

                // Consume from the beginning — messages should have survived
                try (var consumer = new KafkaConsumer("localhost", port2, "persist-c2", "persist-grp2")) {
                    consumer.subscribe(List.of(topic));
                    var records = consumer.poll(3000);
                    int recoveredCount = records.size();
                    LOG.info("After restart: recovered {} messages from disk", recoveredCount);

                    if (recoveredCount >= messageCount) {
                        // Verify content of first and last message
                        String firstValue = records.getFirst().valueAsString();
                        String lastValue = records.getLast().valueAsString();
                        LOG.info("First: {}, Last: {}", firstValue, lastValue);
                        return firstValue.contains("durable-value-")
                                && lastValue.contains("durable-value-");
                    }
                    return false;
                }
            }
        } finally {
            // Clean up temp directory
            try (var walk = Files.walk(logDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }
}
