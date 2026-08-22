package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class KafkaAdminClientTest {

    private KafkaBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        broker = new KafkaBroker("localhost", 0);
        broker.start();
        port = broker.port();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    @Test
    void testApiVersions() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.apiVersions();
            assertThat(response.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.apiKeys()).isNotEmpty();
        }
    }

    @Test
    void testMetadataAllTopics() throws IOException {
        broker.createTopic("topic1", 2);
        broker.createTopic("topic2", 3);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.metadata(null);
            assertThat(response.brokers()).hasSize(1);
            assertThat(response.topics()).hasSize(2);
        }
    }

    @Test
    void testMetadataSpecificTopics() throws IOException {
        broker.createTopic("topic1", 2);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.metadata(List.of("topic1"));
            assertThat(response.topics()).hasSize(1);
            assertThat(response.topics().getFirst().name()).isEqualTo("topic1");
            assertThat(response.topics().getFirst().partitions()).hasSize(2);
        }
    }

    @Test
    void testMetadataUnknownTopic() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.metadata(List.of("nonexistent"));
            assertThat(response.topics().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
        }
    }

    @Test
    void testCreateTopic() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            short error = admin.createTopic("new-topic", 5);
            assertThat(error).isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.partitionCount("new-topic")).isEqualTo(5);
        }
    }

    @Test
    void testCreateTopicAlreadyExists() throws IOException {
        broker.createTopic("existing", 1);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            short error = admin.createTopic("existing", 1);
            assertThat(error).isEqualTo(KafkaErrors.TOPIC_ALREADY_EXISTS.code());
        }
    }

    @Test
    void testCreateMultipleTopics() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.createTopics(List.of(
                    new CreateTopicsRequest.TopicCreate("t1", 1, (short) 1, Map.of()),
                    new CreateTopicsRequest.TopicCreate("t2", 2, (short) 1, Map.of())));
            assertThat(response.topics()).hasSize(2);
            for (var t : response.topics()) {
                assertThat(t.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            }
        }
    }

    @Test
    void testDeleteTopics() throws IOException {
        broker.createTopic("to-delete", 1);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteTopics(List.of("to-delete"));
            assertThat(response.responses().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.topicNames()).doesNotContain("to-delete");
        }
    }

    @Test
    void testDeleteNonexistentTopic() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteTopics(List.of("nonexistent"));
            assertThat(response.responses().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
        }
    }

    @Test
    void testDescribeGroups() throws IOException {
        // Join a group first
        try (var consumer = new KafkaConsumer("localhost", port, "consumer-1", "test-group")) {
            broker.createTopic("test", 1);
            consumer.subscribe(List.of("test"));

            try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
                admin.connect();
                var response = admin.describeGroups(List.of("test-group"));
                assertThat(response.groups()).hasSize(1);
                assertThat(response.groups().getFirst().groupId()).isEqualTo("test-group");
                assertThat(response.groups().getFirst().members()).isNotEmpty();
            }
        }
    }

    @Test
    void testDescribeGroupNotFound() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.describeGroups(List.of("nonexistent"));
            assertThat(response.groups()).hasSize(1);
            assertThat(response.groups().getFirst().state()).isEqualTo("Empty");
        }
    }

    @Test
    void testListOffsets() throws IOException {
        broker.createTopic("test", 1);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.listOffsets(new ListOffsetsRequest(List.of(
                    new ListOffsetsRequest.TopicOffsets("test", List.of(
                            new ListOffsetsRequest.PartitionOffsets(0, ListOffsetsRequest.LATEST_TIMESTAMP))))));
            assertThat(response.topics()).hasSize(1);
            assertThat(response.topics().getFirst().partitions().getFirst().offset()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void testListGroups() throws IOException {
        // Create a group by joining
        broker.groupCoordinator().joinGroup("test-group", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.listGroups();
            assertThat(response.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.groups()).isNotEmpty();
            assertThat(response.groups().stream().map(ListGroupsResponse.GroupListing::groupId).toList())
                    .contains("test-group");
        }
    }

    @Test
    void testListGroupsEmpty() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.listGroups();
            assertThat(response.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.groups()).isEmpty();
        }
    }

    @Test
    void testDeleteGroups() throws IOException {
        // Create and empty a group
        var join = broker.groupCoordinator().joinGroup("delete-me", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");
        broker.groupCoordinator().leaveGroup("delete-me", join.memberId());

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteGroups(List.of("delete-me"));
            assertThat(response.results()).hasSize(1);
            assertThat(response.results().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());
        }
    }

    @Test
    void testDeleteGroupsNonEmpty() throws IOException {
        broker.groupCoordinator().joinGroup("active-group", "", "consumer", 10000,
                List.of(Map.entry("range", new byte[0])), "client-1");

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteGroups(List.of("active-group"));
            assertThat(response.results().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.NON_EMPTY_GROUP.code());
        }
    }

    @Test
    void testDeleteGroupsNotFound() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteGroups(List.of("nonexistent"));
            assertThat(response.results().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.GROUP_ID_NOT_FOUND.code());
        }
    }

    @Test
    void testCreatePartitions() throws IOException {
        broker.createTopic("test-cp", 2);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.createPartitions(List.of(
                    new CreatePartitionsRequest.TopicNewPartitions("test-cp", 5)));
            assertThat(response.results()).hasSize(1);
            assertThat(response.results().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.partitionCount("test-cp")).isEqualTo(5);
        }
    }

    @Test
    void testCreatePartitionsDecrease() throws IOException {
        broker.createTopic("test-cp2", 5);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.createPartitions(List.of(
                    new CreatePartitionsRequest.TopicNewPartitions("test-cp2", 3)));
            assertThat(response.results().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.INVALID_PARTITIONS.code());
        }
    }

    @Test
    void testCreatePartitionsUnknownTopic() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.createPartitions(List.of(
                    new CreatePartitionsRequest.TopicNewPartitions("nonexistent", 5)));
            assertThat(response.results().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
        }
    }

    @Test
    void testDeleteRecords() throws IOException {
        broker.createTopic("test-dr", 1);
        // Append some records
        var log = broker.getPartitionLog(new ssg.legoflow.messaging.kafka.common.TopicPartition("test-dr", 0));
        var records = new java.util.ArrayList<ssg.legoflow.messaging.kafka.record.Record>();
        for (int i = 0; i < 5; i++) {
            records.add(new ssg.legoflow.messaging.kafka.record.Record(i, 0L,
                    ("k" + i).getBytes(), ("v" + i).getBytes(), List.of()));
        }
        byte[] batch = new ssg.legoflow.messaging.kafka.record.RecordBatch()
                .baseOffset(0).lastOffsetDelta(4)
                .baseTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records).encode();
        log.append(batch);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteRecords(List.of(
                    new DeleteRecordsRequest.TopicData("test-dr", List.of(
                            new DeleteRecordsRequest.PartitionData(0, 3L)))));
            assertThat(response.topics()).hasSize(1);
            assertThat(response.topics().getFirst().partitions().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.NONE.code());
        }
    }

    @Test
    void testDeleteRecordsUnknownPartition() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.deleteRecords(List.of(
                    new DeleteRecordsRequest.TopicData("nonexistent", List.of(
                            new DeleteRecordsRequest.PartitionData(0, 0L)))));
            assertThat(response.topics().getFirst().partitions().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
        }
    }

    @Test
    void testOffsetDelete() throws IOException {
        // Commit offsets then delete them
        broker.groupCoordinator().commitOffsets("od-group",
                Map.of(new ssg.legoflow.messaging.kafka.common.TopicPartition("test", 0), 42L));

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.offsetDelete("od-group", List.of(
                    new OffsetDeleteRequest.TopicData("test", List.of(
                            new OffsetDeleteRequest.PartitionData(0)))));
            assertThat(response.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.topics()).hasSize(1);
            assertThat(response.topics().getFirst().partitions().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.NONE.code());
        }

        // Verify offset is gone
        var fetched = broker.groupCoordinator().fetchOffsets("od-group",
                List.of(new ssg.legoflow.messaging.kafka.common.TopicPartition("test", 0)));
        assertThat(fetched.values().iterator().next()).isEqualTo(-1L);
    }

    @Test
    void testFindCoordinator() throws IOException {
        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.findCoordinator("my-group", FindCoordinatorRequest.KEY_TYPE_GROUP);
            assertThat(response.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.port()).isEqualTo(port);
        }
    }

    @Test
    void testDescribeConfigs() throws IOException {
        broker.createTopic("config-topic", 2);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.describeConfigs(List.of(
                    new DescribeConfigsRequest.ResourceRequest(
                            (byte) 2, "config-topic", null)));
            assertThat(response.resources()).hasSize(1);
            assertThat(response.resources().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(response.resources().getFirst().resourceName()).isEqualTo("config-topic");
            assertThat(response.resources().getFirst().configs()).isNotEmpty();
            assertThat(response.resources().getFirst().configs().stream()
                    .map(DescribeConfigsResponse.ConfigEntry::name).toList())
                    .contains("retention.ms", "cleanup.policy");
        }
    }

    @Test
    void testAlterConfigs() throws IOException {
        broker.createTopic("alter-topic", 1);

        try (var admin = new KafkaAdminClient("localhost", port, "admin")) {
            admin.connect();
            var response = admin.alterConfigs(List.of(
                    new AlterConfigsRequest.ResourceConfig(
                            (byte) 2, "alter-topic", List.of(
                                    new AlterConfigsRequest.ConfigEntry("retention.ms", "3600000")))));
            assertThat(response.resources()).hasSize(1);
            assertThat(response.resources().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());

            // Verify the change took effect
            var descResp = admin.describeConfigs(List.of(
                    new DescribeConfigsRequest.ResourceRequest(
                            (byte) 2, "alter-topic", null)));
            var retentionMs = descResp.resources().getFirst().configs().stream()
                    .filter(c -> c.name().equals("retention.ms")).findFirst().orElseThrow();
            assertThat(retentionMs.value()).isEqualTo("3600000");
        }
    }
}
