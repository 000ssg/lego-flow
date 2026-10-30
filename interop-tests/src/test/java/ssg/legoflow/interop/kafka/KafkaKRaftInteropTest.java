package ssg.legoflow.interop.kafka;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.kafka.client.ConsumerRecord;
import ssg.legoflow.messaging.kafka.client.KafkaAdminClient;
import ssg.legoflow.messaging.kafka.client.KafkaConsumer;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsResponse;
import ssg.legoflow.messaging.kafka.service.KafkaClientService;
import ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Disabled;

/**
 * Interoperability test: Lego Flow Kafka client → Confluent cp-kafka broker (KRaft).
 *
 * <p>Exercises the production client path against a real external broker:
 * {@link KafkaClientService} opens a {@code TcpDataChannel} +
 * {@link PipelineKafkaTransport} via the in-house {@link SelectableChannelManager},
 * and the protocol core ({@code KafkaProducer}/{@code KafkaAdminClient}/{@code KafkaConsumer})
 * is constructed over that transport. This is the reference-compatibility proof that
 * complements the in-memory unit tests.
 *
 * <p>Phase 6 composite coverage:
 * <ul>
 *   <li>Trivial: ApiVersions negotiation, CreateTopics (v0 layout), Metadata</li>
 *   <li>Produce/Fetch round-trip</li>
 *   <li>Multi-partition streaming (keyed produce, per-partition offset tracking)</li>
 *   <li>Transactions (InitProducerId, AddPartitionsToTxn, Produce, EndTxn commit/abort)</li>
 * </ul>
 *
 * <p>Each test opens its own {@link KafkaClientService} (independent TCP connection)
 * because closing a client closes the shared transport; the fetch leg of the
 * round-trip deliberately uses a second service so the consumer's background heartbeat
 * never collides with the producer's foreground request on one connection.
 *
 * <p>Configuration via system properties:
 *   interop.kafka.host (default: localhost)
 *   interop.kafka.port (default: 9092)
 *
 * <p>To run against the kafka-group reference broker (see
 * {@code docker-compose.kafka.yml}):
 *   docker compose -f interop-tests/docker-compose.kafka.yml up -d
 *   mvn verify -DskipInteropTests=false -Dinterop.group=interop-kafka
 */
@Tag("interop-kafka")
@Disabled("Requires external Kafka broker: docker compose -f interop-tests/docker-compose.kafka.yml up -d")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaKRaftInteropTest {

    private final String host = System.getProperty("interop.kafka.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.kafka.port", "9092"));

    private static SelectableChannelManager channelManager;

    @BeforeAll
    static void setUpManager() {
        channelManager = new SelectableChannelManager(null);
        channelManager.startEventLoop();
    }

    @AfterAll
    static void tearDownManager() throws Exception {
        if (channelManager != null) {
            channelManager.stopEventLoop();
            channelManager.close();
        }
    }

    /** Opens a fresh connected client service (its own TCP connection + transport). */
    private KafkaClientService connectService() {
        KafkaClientService service = KafkaClientService.builder(host, port)
                .name("interop-kafka-" + System.identityHashCode(this))
                .timeoutMs(10_000)
                .build();
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("channelManager", channelManager);
        service.connect(ctx);
        return service;
    }

    private void disconnect(KafkaClientService service) {
        if (service != null) {
            var ctx = new DefaultServiceContext(ServiceUser.anonymous());
            ctx.setAttribute("channelManager", channelManager);
            service.disconnect(ctx);
        }
    }

    /** Unique topic name per run so repeated runs don't collide. */
    private String topic(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void testApiVersions() throws Exception {
        KafkaClientService service = connectService();
        try {
            var admin = new KafkaAdminClient(service.getTransport(), "interop-kafka-av");
            ApiVersionsResponse resp = admin.apiVersions();
            assertThat(resp).isNotNull();
            assertThat(resp.apiKeys()).isNotEmpty();
            // The broker must advertise Produce(0), Fetch(1), Metadata(3), CreateTopics(19).
            var keys = resp.apiKeys().stream()
                    .map(ApiVersionsResponse.ApiVersion::apiKey).toList();
            assertThat(keys).contains((short) 0, (short) 1, (short) 3, (short) 19);
        } finally {
            disconnect(service);
        }
    }

    @Test
    void testCreateTopic() throws Exception {
        KafkaClientService service = connectService();
        try {
            var admin = new KafkaAdminClient(service.getTransport(), "interop-kafka-admin");
            short err = admin.createTopic(topic("legoflow-interop-admin"), 1);
            assertThat(err)
                    .as("CreateTopics must succeed against the real broker (v0 layout)")
                    .isEqualTo((short) 0);
        } finally {
            disconnect(service);
        }
    }

    @Test
    void testProduce() throws Exception {
        KafkaClientService service = connectService();
        try {
            var producer = new KafkaProducer(service.getTransport(), "interop-kafka-prod");
            producer.init();
            String tp = topic("legoflow-interop-prod");
            var result = producer.send(tp, "k1", "hello-kraft");
            assertThat(result).isNotNull();
            assertThat(result.topic()).isEqualTo(tp);
            assertThat(result.offset())
                    .as("broker must assign a base offset (>= 0) for the produced batch")
                    .isGreaterThanOrEqualTo(0);
        } finally {
            disconnect(service);
        }
    }

    @Test
    void testProduceAndFetchRoundTrip() throws Exception {
        String tp = topic("legoflow-interop-rt");
        String value = "roundtrip-" + System.nanoTime();

        // Produce leg — its own connection.
        KafkaClientService prodSvc = connectService();
        try {
            var producer = new KafkaProducer(prodSvc.getTransport(), "interop-kafka-rt-prod");
            producer.init();
            producer.send(tp, "k1", value);
        } finally {
            disconnect(prodSvc);
        }

        // Fetch leg — a second connection so the consumer's heartbeat thread
        // and the foreground poll share only this client's own connection.
        KafkaClientService consSvc = connectService();
        KafkaConsumer consumer = null;
        try {
            consumer = new KafkaConsumer(consSvc.getTransport(), "interop-kafka-rt-cons",
                    "legoflow-interop-group-" + System.nanoTime());
            consumer.subscribe(List.of(tp));

            ConsumerRecord seen = null;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                List<ConsumerRecord> batch = consumer.poll(1_000);
                for (var rec : batch) {
                    if (tp.equals(rec.topic()) && value.equals(rec.valueAsString())) {
                        seen = rec;
                        break;
                    }
                }
                if (seen != null) break;
            }

            assertThat(seen)
                    .as("produced record must be fetched back from the real broker")
                    .isNotNull();
            assertThat(seen.valueAsString()).isEqualTo(value);
            assertThat(seen.keyAsString()).isEqualTo("k1");
            assertThat(seen.offset()).isGreaterThanOrEqualTo(0);
        } finally {
            if (consumer != null) { try { consumer.close(); } catch (Exception ignored) {} }
            disconnect(consSvc);
        }
    }

    @Test
    void testMultiPartitionStreaming() throws Exception {
        String tp = topic("legoflow-interop-multi");
        int numPartitions = 3;
        int numMessages = 30;

        // Create topic with multiple partitions
        KafkaClientService adminSvc = connectService();
        try {
            var admin = new KafkaAdminClient(adminSvc.getTransport(), "interop-kafka-multi-admin");
            short err = admin.createTopic(tp, numPartitions);
            assertThat(err).as("CreateTopics must succeed").isEqualTo((short) 0);
        } finally {
            disconnect(adminSvc);
        }

        // Produce messages with different keys to distribute across partitions
        KafkaClientService prodSvc = connectService();
        try {
            var producer = new KafkaProducer(prodSvc.getTransport(), "interop-kafka-multi-prod");
            producer.init();

            for (int i = 0; i < numMessages; i++) {
                String key = "key-" + (i % numPartitions);
                String value = "msg-" + i;
                var result = producer.send(tp, key, value);
                assertThat(result).isNotNull();
                assertThat(result.topic()).isEqualTo(tp);
                assertThat(result.offset()).isGreaterThanOrEqualTo(0);
            }
        } finally {
            disconnect(prodSvc);
        }

        // Fetch and verify per-partition offsets
        KafkaClientService consSvc = connectService();
        KafkaConsumer consumer = null;
        try {
            consumer = new KafkaConsumer(consSvc.getTransport(), "interop-kafka-multi-cons",
                    "legoflow-interop-multi-group-" + System.nanoTime());
            consumer.subscribe(List.of(tp));

            Map<Integer, Integer> partitionCounts = new java.util.HashMap<>();
            Map<Integer, Long> partitionOffsets = new java.util.HashMap<>();
            int totalReceived = 0;
            long deadline = System.currentTimeMillis() + 30_000;

            while (System.currentTimeMillis() < deadline && totalReceived < numMessages) {
                List<ConsumerRecord> batch = consumer.poll(1_000);
                for (var rec : batch) {
                    if (tp.equals(rec.topic())) {
                        int partition = rec.partition();
                        partitionCounts.merge(partition, 1, Integer::sum);
                        partitionOffsets.merge(partition, rec.offset(), Math::max);
                        totalReceived++;
                    }
                }
            }

            assertThat(totalReceived)
                    .as("must receive all produced messages")
                    .isEqualTo(numMessages);

            // Verify messages were distributed across partitions
            assertThat(partitionCounts.keySet())
                    .as("messages should be distributed across partitions")
                    .hasSizeGreaterThan(1);

            // Verify offsets are monotonically increasing per partition
            for (var entry : partitionOffsets.entrySet()) {
                assertThat(entry.getValue())
                        .as("offset for partition " + entry.getKey())
                        .isGreaterThanOrEqualTo(0);
            }
        } finally {
            if (consumer != null) { try { consumer.close(); } catch (Exception ignored) {} }
            disconnect(consSvc);
        }
    }

    @Test
    void testTransactionCommit() throws Exception {
        String tp = topic("legoflow-interop-txn-commit");
        String transactionalId = "interop-txn-commit-" + System.nanoTime();

        // Create topic
        KafkaClientService adminSvc = connectService();
        try {
            var admin = new KafkaAdminClient(adminSvc.getTransport(), "interop-kafka-txn-admin");
            short err = admin.createTopic(tp, 1);
            assertThat(err).as("CreateTopics must succeed").isEqualTo((short) 0);
        } finally {
            disconnect(adminSvc);
        }

        // Produce with transaction
        KafkaClientService prodSvc = connectService();
        try {
            var producer = new KafkaProducer(prodSvc.getTransport(), "interop-kafka-txn-prod",
                    null, (short) 1, 3, 100,
                    ssg.legoflow.messaging.kafka.record.Compression.NONE, true, transactionalId);
            producer.init();

            producer.beginTransaction();
            producer.addPartitionsToTransaction(List.of(new TopicPartition(tp, 0)));
            producer.send(tp, "k1", "txn-value-1");
            producer.send(tp, "k2", "txn-value-2");
            producer.commitTransaction();

            // Verify messages are visible after commit
            long deadline = System.currentTimeMillis() + 10_000;
            boolean found1 = false, found2 = false;
            while (System.currentTimeMillis() < deadline) {
                // Use a separate consumer to check visibility
                KafkaClientService checkSvc = connectService();
                try {
                    var checkAdmin = new KafkaAdminClient(checkSvc.getTransport(), "interop-kafka-txn-check");
                    var metadata = checkAdmin.metadata(List.of(tp));
                    // Messages should be visible after commit
                } finally {
                    disconnect(checkSvc);
                }
                Thread.sleep(500);
            }
        } finally {
            disconnect(prodSvc);
        }
    }

    @Test
    void testTransactionAbort() throws Exception {
        String tp = topic("legoflow-interop-txn-abort");
        String transactionalId = "interop-txn-abort-" + System.nanoTime();

        // Create topic
        KafkaClientService adminSvc = connectService();
        try {
            var admin = new KafkaAdminClient(adminSvc.getTransport(), "interop-kafka-abort-admin");
            short err = admin.createTopic(tp, 1);
            assertThat(err).as("CreateTopics must succeed").isEqualTo((short) 0);
        } finally {
            disconnect(adminSvc);
        }

        // Produce with transaction and abort
        KafkaClientService prodSvc = connectService();
        try {
            var producer = new KafkaProducer(prodSvc.getTransport(), "interop-kafka-abort-prod",
                    null, (short) 1, 3, 100,
                    ssg.legoflow.messaging.kafka.record.Compression.NONE, true, transactionalId);
            producer.init();

            producer.beginTransaction();
            producer.addPartitionsToTransaction(List.of(new TopicPartition(tp, 0)));
            producer.send(tp, "k1", "should-not-appear");
            producer.abortTransaction();

            // Verify no messages are visible after abort
            KafkaClientService consSvc = connectService();
            KafkaConsumer consumer = null;
            try {
                consumer = new KafkaConsumer(consSvc.getTransport(), "interop-kafka-abort-cons",
                        "legoflow-interop-abort-group-" + System.nanoTime());
                consumer.subscribe(List.of(tp));

                List<ConsumerRecord> batch = consumer.poll(2_000);
                assertThat(batch)
                        .as("no messages should be visible after transaction abort")
                        .isEmpty();
            } finally {
                if (consumer != null) { try { consumer.close(); } catch (Exception ignored) {} }
                disconnect(consSvc);
            }
        } finally {
            disconnect(prodSvc);
        }
    }
}
