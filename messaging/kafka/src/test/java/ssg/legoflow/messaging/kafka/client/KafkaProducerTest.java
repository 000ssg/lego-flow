package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.common.Partitioner;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import ssg.legoflow.messaging.kafka.record.Compression;
import ssg.legoflow.messaging.kafka.record.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class KafkaProducerTest {

    private KafkaBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        broker = new KafkaBroker("localhost", 0);
        broker.start();
        broker.createTopic("test", 3);
        port = broker.port();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    @Test
    void testSimpleProduce() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.send("test", "key", "value");
            assertThat(result.topic()).isEqualTo("test");
            assertThat(result.offset()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void testProduceMultipleMessages() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            for (int i = 0; i < 10; i++) {
                var result = producer.send("test", "key-" + i, "value-" + i);
                assertThat(result.offset()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    void testProduceWithByteArrays() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.send("test", new byte[]{1, 2}, new byte[]{3, 4});
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testProduceNullKey() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.send("test", (String) null, "value");
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testProduceWithHeaders() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.send("test", "key".getBytes(), "value".getBytes(),
                    List.of(Header.of("h1", "v1")));
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testProduceAutoCreateTopic() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.send("new-topic", "key", "value");
            assertThat(result).isNotNull();
            assertThat(broker.topicNames()).contains("new-topic");
        }
    }

    @Test
    void testProduceWithRoundRobinPartitioner() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer",
                Partitioner.roundRobin(), (short) 1, 0, 100, Compression.NONE, false, null)) {
            producer.init();
            java.util.Set<Integer> usedPartitions = new java.util.HashSet<>();
            for (int i = 0; i < 6; i++) {
                var result = producer.send("test", (byte[]) null, ("v" + i).getBytes());
                usedPartitions.add(result.partition());
            }
            assertThat(usedPartitions.size()).isGreaterThan(1);
        }
    }

    @Test
    void testProduceWithGzipCompression() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer",
                null, (short) 1, 0, 100, Compression.GZIP, false, null)) {
            producer.init();
            var result = producer.send("test", "key", "compressed-value");
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testIdempotentProducer() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer",
                null, (short) -1, 0, 100, Compression.NONE, true, null)) {
            producer.init();
            var r1 = producer.send("test", "key", "value-1");
            var r2 = producer.send("test", "key", "value-2");
            assertThat(r1).isNotNull();
            assertThat(r2).isNotNull();
        }
    }

    @Test
    void testTransactionalProducer() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer",
                null, (short) -1, 0, 100, Compression.NONE, true, "my-txn")) {
            producer.init();
            producer.beginTransaction();
            producer.addPartitionsToTransaction(List.of(new TopicPartition("test", 0)));
            producer.send("test", "key", "value");
            producer.commitTransaction();
        }
    }

    @Test
    void testTransactionalProducerAbort() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer",
                null, (short) -1, 0, 100, Compression.NONE, true, "my-txn")) {
            producer.init();
            producer.beginTransaction();
            producer.addPartitionsToTransaction(List.of(new TopicPartition("test", 0)));
            producer.send("test", "key", "value");
            producer.abortTransaction();
        }
    }

    @Test
    void testBeginTransactionWithoutTxnIdThrows() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            assertThatThrownBy(producer::beginTransaction)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testSendToPartition() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            var result = producer.sendToPartition("test", 1, "key".getBytes(), "value".getBytes(), List.of());
            assertThat(result.partition()).isEqualTo(1);
        }
    }

    @Test
    void testRefreshMetadata() throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            producer.refreshMetadata("test");
            // Should not throw
        }
    }
}
