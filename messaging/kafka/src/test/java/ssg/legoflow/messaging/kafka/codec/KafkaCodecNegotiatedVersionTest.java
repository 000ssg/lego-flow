package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.client.InMemoryKafka;
import ssg.legoflow.messaging.kafka.common.ApiKey;
import ssg.legoflow.messaging.kafka.protocol.RequestHeader;
import ssg.legoflow.messaging.kafka.protocol.ResponseHeader;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.messaging.kafka.transport.InMemoryKafkaTransport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Unpin phase: each of the 25 versioned APIs round-trips at its MIN / MID /
 * MAX negotiated version through the *versioned* facade, and a request at a
 * negotiated (non-pinned) version survives the full broker round-trip over
 * the in-memory transport (design decisions 2-4).
 *
 * <p>Round-trip = encode at V, decode at V, re-encode at V and compare
 * bytes (encoding is deterministic). The E2E leg uses the legacy request
 * header layout (the in-house client's layout: the version rides in the
 * header) and verifies the correlation id echo plus a versioned response
 * decode at the same V.
 *
 * @author legoflow
 */
@DisplayName("KafkaCodec negotiated-version round-trip")
final class KafkaCodecNegotiatedVersionTest {

    // no helper methods needed — nested record constructors are used inline
    // (the per-API codec tests used overloaded part()/topic() helpers;
    // inlining them here avoids name collisions across the 25 APIs).

    private static Map<String, String> configs(String... kv) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }


    @Test
    @DisplayName("AddOffsetsToTxn round-trips at negotiated versions")
    void addOffsetsToTxnRoundTrip() {
        AddOffsetsToTxnRequest req = new AddOffsetsToTxnRequest("txn-1", 1000L, (short) 3, "my-group");
        // v0
        byte[] body0 = KafkaCodec.encodeAddOffsetsToTxnRequest((short) 0, req);
        AddOffsetsToTxnRequest dec0 = KafkaCodec.decodeAddOffsetsToTxnRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeAddOffsetsToTxnRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeAddOffsetsToTxnRequest((short) 1, req);
        AddOffsetsToTxnRequest dec1 = KafkaCodec.decodeAddOffsetsToTxnRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeAddOffsetsToTxnRequest((short) 1, dec1), "v1 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeAddOffsetsToTxnRequest((short) 3, req);
        AddOffsetsToTxnRequest dec3 = KafkaCodec.decodeAddOffsetsToTxnRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeAddOffsetsToTxnRequest((short) 3, dec3), "v3 re-encode");
    }

    @Test
    @DisplayName("AddPartitionsToTxn round-trips at negotiated versions")
    void addPartitionsToTxnRoundTrip() {
        AddPartitionsToTxnRequest req = new AddPartitionsToTxnRequest(
                                "txn-1", 123L, (short) 7,
                                List.of(new AddPartitionsToTxnRequest.TopicPartitions("topicA", List.of(3, 5))));
        // v0
        byte[] body0 = KafkaCodec.encodeAddPartitionsToTxnRequest((short) 0, req);
        AddPartitionsToTxnRequest dec0 = KafkaCodec.decodeAddPartitionsToTxnRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeAddPartitionsToTxnRequest((short) 0, dec0), "v0 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeAddPartitionsToTxnRequest((short) 2, req);
        AddPartitionsToTxnRequest dec2 = KafkaCodec.decodeAddPartitionsToTxnRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeAddPartitionsToTxnRequest((short) 2, dec2), "v2 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeAddPartitionsToTxnRequest((short) 4, req);
        AddPartitionsToTxnRequest dec4 = KafkaCodec.decodeAddPartitionsToTxnRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeAddPartitionsToTxnRequest((short) 4, dec4), "v4 re-encode");
    }

    @Test
    @DisplayName("AlterConfigs round-trips at negotiated versions")
    void alterConfigsRoundTrip() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "a",
                                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1"),
                                                new AlterConfigsRequest.ConfigEntry("n2", "v2")))),
                                true);
        // v0
        byte[] body0 = KafkaCodec.encodeAlterConfigsRequest((short) 0, req);
        AlterConfigsRequest dec0 = KafkaCodec.decodeAlterConfigsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeAlterConfigsRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeAlterConfigsRequest((short) 1, req);
        AlterConfigsRequest dec1 = KafkaCodec.decodeAlterConfigsRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeAlterConfigsRequest((short) 1, dec1), "v1 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeAlterConfigsRequest((short) 2, req);
        AlterConfigsRequest dec2 = KafkaCodec.decodeAlterConfigsRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeAlterConfigsRequest((short) 2, dec2), "v2 re-encode");
    }

    @Test
    @DisplayName("AlterPartitionReassignments round-trips at negotiated versions")
    void alterPartitionReassignmentsRoundTrip() {
        AlterPartitionReassignmentsRequest req = new AlterPartitionReassignmentsRequest(30_000, List.of(
                                new AlterPartitionReassignmentsRequest.TopicReassignment("orders", List.of(
                                        new AlterPartitionReassignmentsRequest.PartitionReassignment(0, List.of(1, 2, 3)),
                                        new AlterPartitionReassignmentsRequest.PartitionReassignment(1, null)))));
        // v0
        byte[] body0 = KafkaCodec.encodeAlterPartitionReassignmentsRequest((short) 0, req);
        AlterPartitionReassignmentsRequest dec0 = KafkaCodec.decodeAlterPartitionReassignmentsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeAlterPartitionReassignmentsRequest((short) 0, dec0), "v0 re-encode");
    }

    @Test
    @DisplayName("ApiVersions round-trips at negotiated versions")
    void apiVersionsRoundTrip() {
        ApiVersionsRequest req = new ApiVersionsRequest();
        // v0
        byte[] body0 = KafkaCodec.encodeApiVersionsRequest((short) 0, req);
        ApiVersionsRequest dec0 = KafkaCodec.decodeApiVersionsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeApiVersionsRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeApiVersionsRequest((short) 1, req);
        ApiVersionsRequest dec1 = KafkaCodec.decodeApiVersionsRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeApiVersionsRequest((short) 1, dec1), "v1 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeApiVersionsRequest((short) 3, req);
        ApiVersionsRequest dec3 = KafkaCodec.decodeApiVersionsRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeApiVersionsRequest((short) 3, dec3), "v3 re-encode");
    }

    @Test
    @DisplayName("CreatePartitions round-trips at negotiated versions")
    void createPartitionsRoundTrip() {
        CreatePartitionsRequest req = new CreatePartitionsRequest(
                                List.of(new CreatePartitionsRequest.TopicNewPartitions("orders", 5)), 30_000);
        // v0
        byte[] body0 = KafkaCodec.encodeCreatePartitionsRequest((short) 0, req);
        CreatePartitionsRequest dec0 = KafkaCodec.decodeCreatePartitionsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeCreatePartitionsRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeCreatePartitionsRequest((short) 1, req);
        CreatePartitionsRequest dec1 = KafkaCodec.decodeCreatePartitionsRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeCreatePartitionsRequest((short) 1, dec1), "v1 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeCreatePartitionsRequest((short) 3, req);
        CreatePartitionsRequest dec3 = KafkaCodec.decodeCreatePartitionsRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeCreatePartitionsRequest((short) 3, dec3), "v3 re-encode");
    }

    @Test
    @DisplayName("CreateTopics round-trips at negotiated versions")
    void createTopicsRoundTrip() {
        CreateTopicsRequest req = new CreateTopicsRequest(List.of(new CreateTopicsRequest.TopicCreate(
                                "orders", 3, (short) 2,
                                List.of(new CreateTopicsRequest.TopicCreate.Assignment(0, List.of(1, 2))),
                                configs("retention.ms", "604800000", "cleanup.policy", "delete"))),
                                30_000);
        // v0
        byte[] body0 = KafkaCodec.encodeCreateTopicsRequest((short) 0, req);
        CreateTopicsRequest dec0 = KafkaCodec.decodeCreateTopicsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeCreateTopicsRequest((short) 0, dec0), "v0 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeCreateTopicsRequest((short) 3, req);
        CreateTopicsRequest dec3 = KafkaCodec.decodeCreateTopicsRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeCreateTopicsRequest((short) 3, dec3), "v3 re-encode");
        // v7
        byte[] body7 = KafkaCodec.encodeCreateTopicsRequest((short) 7, req);
        CreateTopicsRequest dec7 = KafkaCodec.decodeCreateTopicsRequest((short) 7, ByteBuffer.wrap(body7));
        assertArrayEquals(body7, KafkaCodec.encodeCreateTopicsRequest((short) 7, dec7), "v7 re-encode");
    }

    @Test
    @DisplayName("DeleteGroups round-trips at negotiated versions")
    void deleteGroupsRoundTrip() {
        DeleteGroupsRequest req = new DeleteGroupsRequest(List.of("orders", "events"));
        // v0
        byte[] body0 = KafkaCodec.encodeDeleteGroupsRequest((short) 0, req);
        DeleteGroupsRequest dec0 = KafkaCodec.decodeDeleteGroupsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeDeleteGroupsRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeDeleteGroupsRequest((short) 1, req);
        DeleteGroupsRequest dec1 = KafkaCodec.decodeDeleteGroupsRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeDeleteGroupsRequest((short) 1, dec1), "v1 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeDeleteGroupsRequest((short) 2, req);
        DeleteGroupsRequest dec2 = KafkaCodec.decodeDeleteGroupsRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeDeleteGroupsRequest((short) 2, dec2), "v2 re-encode");
    }

    @Test
    @DisplayName("DeleteRecords round-trips at negotiated versions")
    void deleteRecordsRoundTrip() {
        DeleteRecordsRequest req = new DeleteRecordsRequest(List.of(new DeleteRecordsRequest.TopicData(
                                "orders", List.of(
                                        new DeleteRecordsRequest.PartitionData(0, 1000L),
                                        new DeleteRecordsRequest.PartitionData(1, 2000L)))), 30_000);
        // v0
        byte[] body0 = KafkaCodec.encodeDeleteRecordsRequest((short) 0, req);
        DeleteRecordsRequest dec0 = KafkaCodec.decodeDeleteRecordsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeDeleteRecordsRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeDeleteRecordsRequest((short) 1, req);
        DeleteRecordsRequest dec1 = KafkaCodec.decodeDeleteRecordsRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeDeleteRecordsRequest((short) 1, dec1), "v1 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeDeleteRecordsRequest((short) 2, req);
        DeleteRecordsRequest dec2 = KafkaCodec.decodeDeleteRecordsRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeDeleteRecordsRequest((short) 2, dec2), "v2 re-encode");
    }

    @Test
    @DisplayName("DeleteTopics round-trips at negotiated versions")
    void deleteTopicsRoundTrip() {
        DeleteTopicsRequest req = new DeleteTopicsRequest(List.of("orders", "events"), 30_000);
        // v0
        byte[] body0 = KafkaCodec.encodeDeleteTopicsRequest((short) 0, req);
        DeleteTopicsRequest dec0 = KafkaCodec.decodeDeleteTopicsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeDeleteTopicsRequest((short) 0, dec0), "v0 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeDeleteTopicsRequest((short) 3, req);
        DeleteTopicsRequest dec3 = KafkaCodec.decodeDeleteTopicsRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeDeleteTopicsRequest((short) 3, dec3), "v3 re-encode");
        // v6
        byte[] body6 = KafkaCodec.encodeDeleteTopicsRequest((short) 6, req);
        DeleteTopicsRequest dec6 = KafkaCodec.decodeDeleteTopicsRequest((short) 6, ByteBuffer.wrap(body6));
        assertArrayEquals(body6, KafkaCodec.encodeDeleteTopicsRequest((short) 6, dec6), "v6 re-encode");
    }

    @Test
    @DisplayName("DescribeConfigs round-trips at negotiated versions")
    void describeConfigsRoundTrip() {
        DescribeConfigsRequest req = new DescribeConfigsRequest(List.of(
                                new DescribeConfigsRequest.ResourceRequest((byte) 2, "orders", List.of("a", "b")),
                                new DescribeConfigsRequest.ResourceRequest((byte) 4, "broker", null)));
        // v0
        byte[] body0 = KafkaCodec.encodeDescribeConfigsRequest((short) 0, req);
        DescribeConfigsRequest dec0 = KafkaCodec.decodeDescribeConfigsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeDescribeConfigsRequest((short) 0, dec0), "v0 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeDescribeConfigsRequest((short) 2, req);
        DescribeConfigsRequest dec2 = KafkaCodec.decodeDescribeConfigsRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeDescribeConfigsRequest((short) 2, dec2), "v2 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeDescribeConfigsRequest((short) 4, req);
        DescribeConfigsRequest dec4 = KafkaCodec.decodeDescribeConfigsRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeDescribeConfigsRequest((short) 4, dec4), "v4 re-encode");
    }

    @Test
    @DisplayName("EndTxn round-trips at negotiated versions")
    void endTxnRoundTrip() {
        EndTxnRequest req = new EndTxnRequest("txn-1", 1000L, (short) 3, true);
        // v0
        byte[] body0 = KafkaCodec.encodeEndTxnRequest((short) 0, req);
        EndTxnRequest dec0 = KafkaCodec.decodeEndTxnRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeEndTxnRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeEndTxnRequest((short) 1, req);
        EndTxnRequest dec1 = KafkaCodec.decodeEndTxnRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeEndTxnRequest((short) 1, dec1), "v1 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeEndTxnRequest((short) 3, req);
        EndTxnRequest dec3 = KafkaCodec.decodeEndTxnRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeEndTxnRequest((short) 3, dec3), "v3 re-encode");
    }

    @Test
    @DisplayName("Fetch round-trips at negotiated versions")
    void fetchRoundTrip() {
        FetchRequest req = new FetchRequest(-1, 500, 1, 1048576,
                                    List.of(new FetchRequest.TopicFetch("topic", List.of(
                                            new FetchRequest.PartitionFetch(0, 10L, 65536))),
                                            new FetchRequest.TopicFetch("other", List.of())));
        // v0
        byte[] body0 = KafkaCodec.encodeFetchRequest((short) 0, req);
        FetchRequest dec0 = KafkaCodec.decodeFetchRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeFetchRequest((short) 0, dec0), "v0 re-encode");
        // v7
        byte[] body7 = KafkaCodec.encodeFetchRequest((short) 7, req);
        FetchRequest dec7 = KafkaCodec.decodeFetchRequest((short) 7, ByteBuffer.wrap(body7));
        assertArrayEquals(body7, KafkaCodec.encodeFetchRequest((short) 7, dec7), "v7 re-encode");
        // v15
        byte[] body15 = KafkaCodec.encodeFetchRequest((short) 15, req);
        FetchRequest dec15 = KafkaCodec.decodeFetchRequest((short) 15, ByteBuffer.wrap(body15));
        assertArrayEquals(body15, KafkaCodec.encodeFetchRequest((short) 15, dec15), "v15 re-encode");
    }

    @Test
    @DisplayName("FindCoordinator round-trips at negotiated versions")
    void findCoordinatorRoundTrip() {
        FindCoordinatorRequest req = new FindCoordinatorRequest("group-1", FindCoordinatorRequest.KEY_TYPE_GROUP);
        // v0
        byte[] body0 = KafkaCodec.encodeFindCoordinatorRequest((short) 0, req);
        FindCoordinatorRequest dec0 = KafkaCodec.decodeFindCoordinatorRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeFindCoordinatorRequest((short) 0, dec0), "v0 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeFindCoordinatorRequest((short) 2, req);
        FindCoordinatorRequest dec2 = KafkaCodec.decodeFindCoordinatorRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeFindCoordinatorRequest((short) 2, dec2), "v2 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeFindCoordinatorRequest((short) 4, req);
        FindCoordinatorRequest dec4 = KafkaCodec.decodeFindCoordinatorRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeFindCoordinatorRequest((short) 4, dec4), "v4 re-encode");
    }

    @Test
    @DisplayName("InitProducerId round-trips at negotiated versions")
    void initProducerIdRoundTrip() {
        InitProducerIdRequest req = new InitProducerIdRequest("prod-txn", 60_000);
        // v0
        byte[] body0 = KafkaCodec.encodeInitProducerIdRequest((short) 0, req);
        InitProducerIdRequest dec0 = KafkaCodec.decodeInitProducerIdRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeInitProducerIdRequest((short) 0, dec0), "v0 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeInitProducerIdRequest((short) 2, req);
        InitProducerIdRequest dec2 = KafkaCodec.decodeInitProducerIdRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeInitProducerIdRequest((short) 2, dec2), "v2 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeInitProducerIdRequest((short) 4, req);
        InitProducerIdRequest dec4 = KafkaCodec.decodeInitProducerIdRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeInitProducerIdRequest((short) 4, dec4), "v4 re-encode");
    }

    @Test
    @DisplayName("JoinGroup round-trips at negotiated versions")
    void joinGroupRoundTrip() {
        JoinGroupRequest req = new JoinGroupRequest("grp1", 10000, 30000, "", "consumer",
                                List.of(new JoinGroupRequest.Protocol("range", new byte[]{1, 2})));
        // v0
        byte[] body0 = KafkaCodec.encodeJoinGroupRequest((short) 0, req);
        JoinGroupRequest dec0 = KafkaCodec.decodeJoinGroupRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeJoinGroupRequest((short) 0, dec0), "v0 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeJoinGroupRequest((short) 4, req);
        JoinGroupRequest dec4 = KafkaCodec.decodeJoinGroupRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeJoinGroupRequest((short) 4, dec4), "v4 re-encode");
        // v9
        byte[] body9 = KafkaCodec.encodeJoinGroupRequest((short) 9, req);
        JoinGroupRequest dec9 = KafkaCodec.decodeJoinGroupRequest((short) 9, ByteBuffer.wrap(body9));
        assertArrayEquals(body9, KafkaCodec.encodeJoinGroupRequest((short) 9, dec9), "v9 re-encode");
    }

    @Test
    @DisplayName("ListOffsets round-trips at negotiated versions")
    void listOffsetsRoundTrip() {
        ListOffsetsRequest req = new ListOffsetsRequest(List.of(
                                new ListOffsetsRequest.TopicOffsets("topic", List.of(
                                        new ListOffsetsRequest.PartitionOffsets(0, 10L)))));
        // v0
        byte[] body0 = KafkaCodec.encodeListOffsetsRequest((short) 0, req);
        ListOffsetsRequest dec0 = KafkaCodec.decodeListOffsetsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeListOffsetsRequest((short) 0, dec0), "v0 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeListOffsetsRequest((short) 4, req);
        ListOffsetsRequest dec4 = KafkaCodec.decodeListOffsetsRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeListOffsetsRequest((short) 4, dec4), "v4 re-encode");
        // v8
        byte[] body8 = KafkaCodec.encodeListOffsetsRequest((short) 8, req);
        ListOffsetsRequest dec8 = KafkaCodec.decodeListOffsetsRequest((short) 8, ByteBuffer.wrap(body8));
        assertArrayEquals(body8, KafkaCodec.encodeListOffsetsRequest((short) 8, dec8), "v8 re-encode");
    }

    @Test
    @DisplayName("ListPartitionReassignments round-trips at negotiated versions")
    void listPartitionReassignmentsRoundTrip() {
        ListPartitionReassignmentsRequest req = new ListPartitionReassignmentsRequest(30_000, List.of(
                                new ListPartitionReassignmentsRequest.TopicData("orders", List.of(0, 1, 2))));
        // v0
        byte[] body0 = KafkaCodec.encodeListPartitionReassignmentsRequest((short) 0, req);
        ListPartitionReassignmentsRequest dec0 = KafkaCodec.decodeListPartitionReassignmentsRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeListPartitionReassignmentsRequest((short) 0, dec0), "v0 re-encode");
    }

    @Test
    @DisplayName("OffsetCommit round-trips at negotiated versions")
    void offsetCommitRoundTrip() {
        OffsetCommitRequest req = new OffsetCommitRequest("grp-1", 5, "member-1",
                                List.of(new OffsetCommitRequest.TopicOffsets("topic-a",
                                        List.of(new OffsetCommitRequest.PartitionOffset(0, 42L, "meta")))));
        // v0
        byte[] body0 = KafkaCodec.encodeOffsetCommitRequest((short) 0, req);
        OffsetCommitRequest dec0 = KafkaCodec.decodeOffsetCommitRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeOffsetCommitRequest((short) 0, dec0), "v0 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeOffsetCommitRequest((short) 4, req);
        OffsetCommitRequest dec4 = KafkaCodec.decodeOffsetCommitRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeOffsetCommitRequest((short) 4, dec4), "v4 re-encode");
        // v9
        byte[] body9 = KafkaCodec.encodeOffsetCommitRequest((short) 9, req);
        OffsetCommitRequest dec9 = KafkaCodec.decodeOffsetCommitRequest((short) 9, ByteBuffer.wrap(body9));
        assertArrayEquals(body9, KafkaCodec.encodeOffsetCommitRequest((short) 9, dec9), "v9 re-encode");
    }

    @Test
    @DisplayName("OffsetFetch round-trips at negotiated versions")
    void offsetFetchRoundTrip() {
        OffsetFetchRequest req = new OffsetFetchRequest("grp-1", List.of(new OffsetFetchRequest.TopicPartitions(
                                "topic-a", List.of(0, 1, 2))));
        // v0
        byte[] body0 = KafkaCodec.encodeOffsetFetchRequest((short) 0, req);
        OffsetFetchRequest dec0 = KafkaCodec.decodeOffsetFetchRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeOffsetFetchRequest((short) 0, dec0), "v0 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeOffsetFetchRequest((short) 4, req);
        OffsetFetchRequest dec4 = KafkaCodec.decodeOffsetFetchRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeOffsetFetchRequest((short) 4, dec4), "v4 re-encode");
        // v8
        byte[] body8 = KafkaCodec.encodeOffsetFetchRequest((short) 8, req);
        OffsetFetchRequest dec8 = KafkaCodec.decodeOffsetFetchRequest((short) 8, ByteBuffer.wrap(body8));
        assertArrayEquals(body8, KafkaCodec.encodeOffsetFetchRequest((short) 8, dec8), "v8 re-encode");
    }

    @Test
    @DisplayName("Produce round-trips at negotiated versions")
    void produceRoundTrip() {
        ProduceRequest req = ProduceRequest.builder().transactionalId(null).acks((short) -1).timeoutMs(30_000)
                                .topicData(List.of(new ProduceRequest.TopicData("topic", List.of(
                                        new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3})))))
                                .build();
        // v0
        byte[] body0 = KafkaCodec.encodeProduceRequest((short) 0, req);
        ProduceRequest dec0 = KafkaCodec.decodeProduceRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeProduceRequest((short) 0, dec0), "v0 re-encode");
        // v4
        byte[] body4 = KafkaCodec.encodeProduceRequest((short) 4, req);
        ProduceRequest dec4 = KafkaCodec.decodeProduceRequest((short) 4, ByteBuffer.wrap(body4));
        assertArrayEquals(body4, KafkaCodec.encodeProduceRequest((short) 4, dec4), "v4 re-encode");
        // v9
        byte[] body9 = KafkaCodec.encodeProduceRequest((short) 9, req);
        ProduceRequest dec9 = KafkaCodec.decodeProduceRequest((short) 9, ByteBuffer.wrap(body9));
        assertArrayEquals(body9, KafkaCodec.encodeProduceRequest((short) 9, dec9), "v9 re-encode");
    }

    @Test
    @DisplayName("SaslAuthenticate round-trips at negotiated versions")
    void saslAuthenticateRoundTrip() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest("user:secret".getBytes());
        // v0
        byte[] body0 = KafkaCodec.encodeSaslAuthenticateRequest((short) 0, req);
        SaslAuthenticateRequest dec0 = KafkaCodec.decodeSaslAuthenticateRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeSaslAuthenticateRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeSaslAuthenticateRequest((short) 1, req);
        SaslAuthenticateRequest dec1 = KafkaCodec.decodeSaslAuthenticateRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeSaslAuthenticateRequest((short) 1, dec1), "v1 re-encode");
        // v2
        byte[] body2 = KafkaCodec.encodeSaslAuthenticateRequest((short) 2, req);
        SaslAuthenticateRequest dec2 = KafkaCodec.decodeSaslAuthenticateRequest((short) 2, ByteBuffer.wrap(body2));
        assertArrayEquals(body2, KafkaCodec.encodeSaslAuthenticateRequest((short) 2, dec2), "v2 re-encode");
    }

    @Test
    @DisplayName("SaslHandshake round-trips at negotiated versions")
    void saslHandshakeRoundTrip() {
        SaslHandshakeRequest req = new SaslHandshakeRequest("GSSAPI");
        // v0
        byte[] body0 = KafkaCodec.encodeSaslHandshakeRequest((short) 0, req);
        SaslHandshakeRequest dec0 = KafkaCodec.decodeSaslHandshakeRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeSaslHandshakeRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeSaslHandshakeRequest((short) 1, req);
        SaslHandshakeRequest dec1 = KafkaCodec.decodeSaslHandshakeRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeSaslHandshakeRequest((short) 1, dec1), "v1 re-encode");
    }

    @Test
    @DisplayName("TxnOffsetCommit round-trips at negotiated versions")
    void txnOffsetCommitRoundTrip() {
        TxnOffsetCommitRequest req = new TxnOffsetCommitRequest("txn-1", "grp-1", 123456L, (short) 7,
                                List.of(new TxnOffsetCommitRequest.TopicData("topic-a",
                                        List.of(new TxnOffsetCommitRequest.PartitionData(0, 42L, "meta")))));
        // v0
        byte[] body0 = KafkaCodec.encodeTxnOffsetCommitRequest((short) 0, req);
        TxnOffsetCommitRequest dec0 = KafkaCodec.decodeTxnOffsetCommitRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeTxnOffsetCommitRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeTxnOffsetCommitRequest((short) 1, req);
        TxnOffsetCommitRequest dec1 = KafkaCodec.decodeTxnOffsetCommitRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeTxnOffsetCommitRequest((short) 1, dec1), "v1 re-encode");
        // v3
        byte[] body3 = KafkaCodec.encodeTxnOffsetCommitRequest((short) 3, req);
        TxnOffsetCommitRequest dec3 = KafkaCodec.decodeTxnOffsetCommitRequest((short) 3, ByteBuffer.wrap(body3));
        assertArrayEquals(body3, KafkaCodec.encodeTxnOffsetCommitRequest((short) 3, dec3), "v3 re-encode");
    }

    @Test
    @DisplayName("WriteTxnMarkers round-trips at negotiated versions")
    void writeTxnMarkersRoundTrip() {
        WriteTxnMarkersRequest req = new WriteTxnMarkersRequest(List.of(
                            new WriteTxnMarkersRequest.TxnMarker(100L, (short) 0, 1, true, List.of(
                                new WriteTxnMarkersRequest.TopicPartitionData("topic1", 0),
                                new WriteTxnMarkersRequest.TopicPartitionData("topic1", 1)))));
        // v0
        byte[] body0 = KafkaCodec.encodeWriteTxnMarkersRequest((short) 0, req);
        WriteTxnMarkersRequest dec0 = KafkaCodec.decodeWriteTxnMarkersRequest((short) 0, ByteBuffer.wrap(body0));
        assertArrayEquals(body0, KafkaCodec.encodeWriteTxnMarkersRequest((short) 0, dec0), "v0 re-encode");
        // v1
        byte[] body1 = KafkaCodec.encodeWriteTxnMarkersRequest((short) 1, req);
        WriteTxnMarkersRequest dec1 = KafkaCodec.decodeWriteTxnMarkersRequest((short) 1, ByteBuffer.wrap(body1));
        assertArrayEquals(body1, KafkaCodec.encodeWriteTxnMarkersRequest((short) 1, dec1), "v1 re-encode");
    }

    // ---- end-to-end over the in-memory transport ----

    @Test
    @DisplayName("FindCoordinator at negotiated v3 (a non-pinned version) survives the broker")
    void findCoordinatorNegotiatedE2E() throws Exception {
        var broker = InMemoryKafka.broker();
        try {
            var client = InMemoryKafka.pair(broker)[1];

            // v3 is FindCoordinator's flex version; the in-house client sends
            // the legacy header layout with the version in the header.
            short version = 3;
            byte[] body = KafkaCodec.encodeFindCoordinatorRequest(version,
                    new FindCoordinatorRequest("group-1", FindCoordinatorRequest.KEY_TYPE_GROUP));
            var header = new RequestHeader(ApiKey.FIND_COORDINATOR.key(), version, 42, "neg-test");
            client.send(KafkaCodec.encodeRequest(header, body));

            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            readFully(client, lenBuf);
            lenBuf.flip();
            int len = lenBuf.getInt();
            ByteBuffer respBuf = ByteBuffer.allocate(len);
            readFully(client, respBuf);
            respBuf.flip();

            ResponseHeader respHeader = KafkaCodec.decodeResponseHeader(respBuf);
            assertEquals(42, respHeader.correlationId(), "correlation id echoed");
            var resp = KafkaCodec.decodeFindCoordinatorResponse(version, respBuf);
            assertEquals(0, resp.errorCode(), "no error at negotiated v3");
        } finally {
            broker.close();
        }
    }

    private static void readFully(InMemoryKafkaTransport in, ByteBuffer out) throws Exception {
        while (out.hasRemaining()) {
            int n = in.receiveWithTimeout(out, 5, java.util.concurrent.TimeUnit.SECONDS);
            if (n < 0) {
                break;
            }
        }
    }
}
