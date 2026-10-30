package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.messaging.kafka.client.InMemoryKafka;
import ssg.legoflow.messaging.kafka.client.KafkaAdminClient;
import ssg.legoflow.messaging.kafka.client.KafkaProducer;
import ssg.legoflow.messaging.kafka.client.KafkaProducer.ProduceResult;
import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.protocol.AlterConfigsRequest;
import ssg.legoflow.messaging.kafka.protocol.DescribeConfigsRequest;
import ssg.legoflow.messaging.kafka.record.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests: broker ↔ client round-trips via InMemoryKafka.
 *
 * <p>Tests cover: produce, admin operations, error handling, stress.
 */
class KafkaServiceIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaServiceIntegrationTest.class);

    private ssg.legoflow.messaging.kafka.broker.KafkaBroker broker;

    @BeforeEach
    void setUp(TestInfo info) {
        broker = InMemoryKafka.broker();
        LOG.info("=== {} ===", info.getDisplayName());
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    @Nested
    @DisplayName("Produce")
    class Produce {

        @Test
        @DisplayName("Single message")
        void testSingleMessage() throws Exception {
            broker.createTopic("single-topic", 1);

            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                var result = producer.send("single-topic", "key-0", "value-0");
                assertThat(result.topic()).isEqualTo("single-topic");
                assertThat(result.partition()).isGreaterThanOrEqualTo(0);
                assertThat(result.offset()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("Multiple messages")
        void testMultipleMessages() throws Exception {
            broker.createTopic("multi-topic", 3);

            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                for (int i = 0; i < 10; i++) {
                    var result = producer.send("multi-topic", "key-" + i, "value-" + i);
                    assertThat(result.offset()).isGreaterThanOrEqualTo(0);
                }
            }
        }

        @Test
        @DisplayName("Produce with null key")
        void testProduceWithNullKey() throws Exception {
            broker.createTopic("null-key-topic", 1);

            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                var result = producer.send("null-key-topic", (String) null, "value");
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Produce with headers")
        void testProduceWithHeaders() throws Exception {
            broker.createTopic("header-topic", 1);

            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                var result = producer.send("header-topic", "key".getBytes(), "value".getBytes(),
                        List.of(new Header("h1", "v1".getBytes())));
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Auto-create topic on produce")
        void testAutoCreateTopic() throws Exception {
            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                var result = producer.send("auto-topic", "key", "value");
                assertThat(result).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Admin Operations")
    class AdminOperations {

        @Test
        @DisplayName("Create topic")
        void testCreateTopic() throws Exception {
            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                admin.createTopic("admin-topic", 3);
                var metadata = admin.metadata(List.of("admin-topic"));
                assertThat(metadata.topics()).hasSize(1);
                assertThat(metadata.topics().getFirst().partitions()).hasSize(3);
            }
        }

        @Test
        @DisplayName("Metadata all topics")
        void testMetadataAllTopics() throws Exception {
            broker.createTopic("meta-topic", 2);

            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                var metadata = admin.metadata(null);
                assertThat(metadata.topics()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Metadata specific topic")
        void testMetadataSpecificTopic() throws Exception {
            broker.createTopic("meta-specific-topic", 2);

            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                var metadata = admin.metadata(List.of("meta-specific-topic"));
                assertThat(metadata.topics()).hasSize(1);
                assertThat(metadata.topics().getFirst().name()).isEqualTo("meta-specific-topic");
            }
        }

        @Test
        @DisplayName("Describe configs")
        void testDescribeConfigs() throws Exception {
            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                var configs = admin.describeConfigs(List.of(
                        new DescribeConfigsRequest.ResourceRequest((byte) 4, "default", null)));
                assertThat(configs.resources()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Alter and describe configs")
        void testAlterAndDescribeConfigs() throws Exception {
            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                admin.alterConfigs(List.of(
                        new AlterConfigsRequest.ResourceConfig(
                                (byte) 2, "default",
                                List.of(new AlterConfigsRequest.ConfigEntry("retention.ms", "86400000")))));
                var configs = admin.describeConfigs(List.of(
                        new DescribeConfigsRequest.ResourceRequest((byte) 2, "default", null)));
                assertThat(configs.resources()).isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Produce to unknown topic")
        void testProduceToUnknownTopic() throws Exception {
            try (var producer = InMemoryKafka.producer(broker, "test-producer")) {
                producer.init();
                var result = producer.send("unknown-topic", "key", "value");
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Metadata unknown topic")
        void testMetadataUnknownTopic() throws Exception {
            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                var metadata = admin.metadata(List.of("unknown-topic"));
                assertThat(metadata.topics()).hasSize(1);
                assertThat(metadata.topics().getFirst().errorCode())
                        .isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
            }
        }

        @Test
        @DisplayName("Delete nonexistent topic")
        void testDeleteNonexistentTopic() throws Exception {
            try (var admin = InMemoryKafka.admin(broker, "test-admin")) {
                admin.connect();
                admin.deleteTopics(List.of("nonexistent-topic"));
            }
        }
    }

    @Nested
    @DisplayName("Stress Test")
    class StressTest {

        @Test
        @DisplayName("50 messages")
        void test50Messages() throws Exception {
            broker.createTopic("stress-topic", 4);

            try (var producer = InMemoryKafka.producer(broker, "stress-producer")) {
                producer.init();
                for (int i = 0; i < 50; i++) {
                    var result = producer.send("stress-topic", "key-" + i, "value-" + i);
                    assertThat(result.offset()).isGreaterThanOrEqualTo(0);
                }
            }
        }

        @Test
        @DisplayName("100 messages mixed keys")
        void test100MessagesMixedKeys() throws Exception {
            broker.createTopic("mixed-topic", 4);

            try (var producer = InMemoryKafka.producer(broker, "mixed-producer")) {
                producer.init();
                for (int i = 0; i < 100; i++) {
                    var key = i % 10 == 0 ? null : "key-" + i;
                    var result = producer.send("mixed-topic", key, "value-" + i);
                    assertThat(result.offset()).isGreaterThanOrEqualTo(0);
                }
            }
        }
    }
}
