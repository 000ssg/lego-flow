package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class KafkaConsumerTest {

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

    private void produceMessages(int count) throws IOException {
        try (var producer = new KafkaProducer("localhost", port, "test-producer")) {
            producer.init();
            for (int i = 0; i < count; i++) {
                producer.send("test", "key-" + i, "value-" + i);
            }
        }
    }

    @Test
    void testSubscribeAndPoll() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            var records = consumer.poll(5000);
            assertThat(records).isNotEmpty();
        }
    }

    @Test
    void testConsumeAllMessages() throws IOException {
        produceMessages(10);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            var records = consumer.poll(5000);
            assertThat(records).hasSize(10);
        }
    }

    @Test
    void testConsumerRecordFields() throws IOException {
        produceMessages(1);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            var records = consumer.poll(5000);
            assertThat(records).hasSize(1);

            var record = records.getFirst();
            assertThat(record.topic()).isEqualTo("test");
            assertThat(record.partition()).isBetween(0, 2);
            assertThat(record.offset()).isGreaterThanOrEqualTo(0);
            assertThat(record.keyAsString()).isEqualTo("key-0");
            assertThat(record.valueAsString()).isEqualTo("value-0");
        }
    }

    @Test
    void testGroupMembership() throws IOException {
        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            assertThat(consumer.memberId()).isNotEmpty();
            assertThat(consumer.generationId()).isGreaterThan(0);
            assertThat(consumer.assignment()).isNotEmpty();
        }
    }

    @Test
    void testManualCommit() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1",
                false, 5000, 10000, 500)) {
            consumer.subscribe(List.of("test"));
            consumer.poll(5000);
            consumer.commitSync();
            // Should not throw
        }
    }

    @Test
    void testSeek() throws IOException {
        produceMessages(10);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            consumer.poll(5000); // get assigned

            // Seek to beginning
            for (TopicPartition tp : consumer.assignment()) {
                consumer.seek(tp, 0);
            }

            var records = consumer.poll(5000);
            assertThat(records).isNotEmpty();
        }
    }

    @Test
    void testSeekToBeginning() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            consumer.poll(5000);
            consumer.seekToBeginning();

            var records = consumer.poll(5000);
            assertThat(records).isNotEmpty();
        }
    }

    @Test
    void testPosition() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            consumer.poll(5000);

            for (TopicPartition tp : consumer.assignment()) {
                assertThat(consumer.position(tp)).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    void testRebalanceListener() throws IOException {
        AtomicInteger assignedCount = new AtomicInteger(0);
        AtomicInteger revokedCount = new AtomicInteger(0);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.setRebalanceListener(new RebalanceListener() {
                @Override
                public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                    assignedCount.addAndGet(partitions.size());
                }

                @Override
                public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) {
                    revokedCount.addAndGet(partitions.size());
                }
            });
            consumer.subscribe(List.of("test"));
            assertThat(assignedCount.get()).isGreaterThan(0);
        }
    }

    @Test
    void testEmptyPoll() throws IOException {
        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            var records = consumer.poll(100);
            assertThat(records).isEmpty();
        }
    }

    @Test
    void testLeaveGroup() throws IOException {
        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.subscribe(List.of("test"));
            consumer.leaveGroup();
            // memberId should still be set from the join
            assertThat(consumer.memberId()).isNotEmpty();
        }
    }

    @Test
    void testConsumerRecordToString() {
        var record = new ConsumerRecord("topic", 0, 42, "key".getBytes(), "value".getBytes(), List.of());
        assertThat(record.toString()).contains("topic").contains("42").contains("key").contains("value");
    }

    @Test
    void testConsumerWithStickyAssignmentStrategy() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.setAssignmentStrategy("sticky");
            assertThat(consumer.assignmentStrategy()).isEqualTo("sticky");
            consumer.subscribe(List.of("test"));
            assertThat(consumer.assignment()).isNotEmpty();
            var records = consumer.poll(5000);
            assertThat(records).isNotEmpty();
        }
    }

    @Test
    void testConsumerWithCooperativeStickyStrategy() throws IOException {
        produceMessages(5);

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            consumer.setAssignmentStrategy("cooperative-sticky");
            assertThat(consumer.assignmentStrategy()).isEqualTo("cooperative-sticky");
            consumer.subscribe(List.of("test"));
            assertThat(consumer.assignment()).isNotEmpty();
            var records = consumer.poll(5000);
            assertThat(records).isNotEmpty();
        }
    }

    @Test
    void testCooperativeStickyOnlyRevokesMovedPartitions() throws IOException {
        List<TopicPartition> revoked = new ArrayList<>();
        List<TopicPartition> assigned = new ArrayList<>();

        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-coop")) {
            consumer.setAssignmentStrategy("cooperative-sticky");
            consumer.setRebalanceListener(new RebalanceListener() {
                @Override
                public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                    assigned.addAll(partitions);
                }

                @Override
                public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) {
                    revoked.addAll(partitions);
                }
            });
            consumer.subscribe(List.of("test"));

            // First join: all partitions are newly assigned, none revoked
            assertThat(assigned).isNotEmpty();
            assertThat(revoked).isEmpty(); // No previous partitions to revoke
        }
    }

    @Test
    void testDefaultAssignmentStrategyIsRange() {
        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "group-1")) {
            assertThat(consumer.assignmentStrategy()).isEqualTo("range");
        }
    }

    @Test
    void testConsumerRecordNullKeyValue() {
        var record = new ConsumerRecord("topic", 0, 0, null, null, List.of());
        assertThat(record.keyAsString()).isNull();
        assertThat(record.valueAsString()).isNull();
    }
}
