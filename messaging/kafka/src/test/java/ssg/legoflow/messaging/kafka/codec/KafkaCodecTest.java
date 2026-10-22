package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Round-trip encode/decode tests for every API key request and response.
 */
class KafkaCodecTest {

    // ===== Request/Response frame =====

    @Test
    void testRequestFrame() {
        var header = new RequestHeader((short) 18, (short) 0, 42, "test-client");
        byte[] payload = new byte[]{1, 2, 3};
        ByteBuffer frame = KafkaCodec.encodeRequest(header, payload);

        // Read length
        int len = frame.getInt();
        assertThat(len).isGreaterThan(0);

        // Decode header
        RequestHeader decoded = KafkaCodec.decodeRequestHeader(frame);
        assertThat(decoded.apiKey()).isEqualTo((short) 18);
        assertThat(decoded.apiVersion()).isZero();
        assertThat(decoded.correlationId()).isEqualTo(42);
        assertThat(decoded.clientId()).isEqualTo("test-client");
    }

    @Test
    void testRequestFrameNullClientId() {
        var header = new RequestHeader((short) 0, (short) 0, 1, null);
        ByteBuffer frame = KafkaCodec.encodeRequest(header, new byte[0]);
        frame.getInt(); // skip length
        RequestHeader decoded = KafkaCodec.decodeRequestHeader(frame);
        assertThat(decoded.clientId()).isNull();
    }

    @Test
    void testResponseFrame() {
        var header = new ResponseHeader(42);
        byte[] payload = new byte[]{4, 5, 6};
        ByteBuffer frame = KafkaCodec.encodeResponse(header, payload);

        int len = frame.getInt();
        assertThat(len).isEqualTo(4 + 3);

        ResponseHeader decoded = KafkaCodec.decodeResponseHeader(frame);
        assertThat(decoded.correlationId()).isEqualTo(42);
    }

    // ===== ApiVersions (18) =====

    @Test
    void testApiVersionsRequest() {
        var req = new ApiVersionsRequest();
        byte[] encoded = KafkaCodec.encodeApiVersionsRequest((short) 0, req);
        ApiVersionsRequest decoded = KafkaCodec.decodeApiVersionsRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded).isNotNull();
    }

    @Test
    void testApiVersionsResponse() {
        var resp = new ApiVersionsResponse((short) 0, List.of(
                new ApiVersionsResponse.ApiVersion((short) 0, (short) 0, (short) 9),
                new ApiVersionsResponse.ApiVersion((short) 1, (short) 0, (short) 13)));
        byte[] encoded = KafkaCodec.encodeApiVersionsResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeApiVersionsResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.apiKeys()).hasSize(2);
        assertThat(decoded.apiKeys().getFirst().apiKey()).isZero();
        assertThat(decoded.apiKeys().getFirst().maxVersion()).isEqualTo((short) 9);
    }

    // ===== Metadata (3) =====
    // Phase 6a: moved to MetadataCodec + MetadataCodecTest (per-sub-category test mirror).
    // These facade-level round-trips pin v0 through the façade; byte-level conformance
    // lives in MetadataCodecTest.

    @Test
    void testMetadataRequestAllTopics() {
        var req = new MetadataRequest();
        byte[] encoded = KafkaCodec.encodeMetadataRequest((short) 0, req);
        var decoded = KafkaCodec.decodeMetadataRequest((short) 0, ByteBuffer.wrap(encoded));
        // v0: null topics encodes as the empty array (count 0 = all topics per spec),
        // which decodes back to the null (all-topics) marker
        assertThat(decoded.topics()).isNull();
    }

    @Test
    void testMetadataRequestSpecificTopics() {
        var req = new MetadataRequest(List.of("topic1", "topic2"));
        byte[] encoded = KafkaCodec.encodeMetadataRequest((short) 0, req);
        var decoded = KafkaCodec.decodeMetadataRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).containsExactly("topic1", "topic2");
    }

    @Test
    void testMetadataResponse() {
        var resp = new MetadataResponse(
                List.of(new MetadataResponse.BrokerMetadata(0, "localhost", 9092)),
                List.of(new MetadataResponse.TopicMetadata((short) 0, "test",
                        List.of(new MetadataResponse.PartitionMetadata((short) 0, 0, 0,
                                List.of(0), List.of(0))))));
        byte[] encoded = KafkaCodec.encodeMetadataResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeMetadataResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.brokers()).hasSize(1);
        assertThat(decoded.brokers().getFirst().host()).isEqualTo("localhost");
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().name()).isEqualTo("test");
        assertThat(decoded.topics().getFirst().partitions()).hasSize(1);
    }

    // ===== Produce (0) =====
    // Phase 6a: v0 moved to ProduceCodec + ProduceCodecTest (per-sub-category test mirror).
    // These facade-level round-trips pin v0 through the façade; byte-level conformance
    // lives in ProduceCodecTest.

    @Test
    void testProduceRequest() {
        var req = ProduceRequest.builder().transactionalId(null).acks((short) -1).timeoutMs(30000).topicData(List.of(new ProduceRequest.TopicData("topic", List.of(
                        new ProduceRequest.PartitionData(0, new byte[]{1, 2, 3}))))).build();
        byte[] encoded = KafkaCodec.encodeProduceRequest(ProduceCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeProduceRequest(ProduceCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.acks()).isEqualTo((short) -1);
        assertThat(decoded.timeoutMs()).isEqualTo(30000);
        assertThat(decoded.topicData()).hasSize(1);
        assertThat(decoded.topicData().getFirst().partitionData().getFirst().records())
                .isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void testProduceRequestV0NullRecords() {
        var req = ProduceRequest.builder().transactionalId(null).acks((short) 1).timeoutMs(5000).topicData(List.of(new ProduceRequest.TopicData("t", List.of(
                        new ProduceRequest.PartitionData(0, null))))).build();
        byte[] encoded = KafkaCodec.encodeProduceRequest(ProduceCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeProduceRequest(ProduceCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topicData().getFirst().partitionData().getFirst().records()).isNull();
    }

    @Test
    void testProduceResponse() {
        // Model carries throttleTimeMs (a v1+ field); at v0 it is discarded on the wire,
        // so a decode round-trip yields the v0 default of 0 regardless of the carried value.
        var resp = new ProduceResponse(List.of(
                new ProduceResponse.TopicResponse("topic", List.of(
                        new ProduceResponse.PartitionResponse(0, (short) 0, 42L, 1234567890L)))), 100);
        byte[] encoded = KafkaCodec.encodeProduceResponse(ProduceCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeProduceResponse(ProduceCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.responses()).hasSize(1);
        assertThat(decoded.responses().getFirst().partitionResponses().getFirst().baseOffset()).isEqualTo(42L);
        // v0 response has no LogAppendTimeMs — decoder defaults it to -1.
        assertThat(decoded.responses().getFirst().partitionResponses().getFirst().logAppendTimeMs()).isEqualTo(-1);
        // v0 response has no ThrottleTimeMs — decoder defaults it to 0 (carried value discarded).
        assertThat(decoded.throttleTimeMs()).isZero();
    }

    // ===== Fetch (1) =====

    @Test
    void testFetchRequest() {
        var req = new FetchRequest(500, 1, 1048576,
                List.of(new FetchRequest.TopicFetch("topic",
                        List.of(new FetchRequest.PartitionFetch(0, 10L, 65536)))));
        byte[] encoded = KafkaCodec.encodeFetchRequest(FetchCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeFetchRequest(FetchCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.maxWaitMs()).isEqualTo(500);
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions().getFirst().fetchOffset()).isEqualTo(10L);
    }

    @Test
    void testFetchResponse() {
        var resp = new FetchResponse(0, List.of(
                new FetchResponse.TopicResponse("topic", List.of(
                        new FetchResponse.PartitionResponse(0, (short) 0, 100L, new byte[]{1, 2})))));
        byte[] encoded = KafkaCodec.encodeFetchResponse(FetchCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeFetchResponse(FetchCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions().getFirst().highWatermark()).isEqualTo(100L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().records()).isEqualTo(new byte[]{1, 2});
    }

    @Test
    void testFetchResponseNullRecords() {
        var resp = new FetchResponse(0, List.of(
                new FetchResponse.TopicResponse("topic", List.of(
                        new FetchResponse.PartitionResponse(0, (short) 0, 0L, null)))));
        byte[] encoded = KafkaCodec.encodeFetchResponse(FetchCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeFetchResponse(FetchCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics().getFirst().partitions().getFirst().records()).isNull();
    }

    // ===== ListOffsets (2) =====

    @Test
    void testListOffsetsRequest() {
        var req = new ListOffsetsRequest(List.of(
                new ListOffsetsRequest.TopicOffsets("topic", List.of(
                        new ListOffsetsRequest.PartitionOffsets(0, -1L),
                        new ListOffsetsRequest.PartitionOffsets(1, -2L)))));
        byte[] encoded = KafkaCodec.encodeListOffsetsRequest(ListOffsetsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeListOffsetsRequest(ListOffsetsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().get(0).timestamp()).isEqualTo(-1L);
    }

    @Test
    void testListOffsetsResponse() {
        var resp = new ListOffsetsResponse(List.of(
                new ListOffsetsResponse.TopicResponse("topic", List.of(
                        new ListOffsetsResponse.PartitionResponse(0, (short) 0, 1000L, 42L)))));
        byte[] encoded = KafkaCodec.encodeListOffsetsResponse(ListOffsetsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeListOffsetsResponse(ListOffsetsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics().getFirst().partitions().getFirst().offset()).isEqualTo(42L);
    }

    // ===== FindCoordinator (10) =====

    @Test
    void testFindCoordinatorRequest() {
        var req = new FindCoordinatorRequest("my-group", FindCoordinatorRequest.KEY_TYPE_GROUP);
        byte[] encoded = KafkaCodec.encodeFindCoordinatorRequest(FindCoordinatorCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeFindCoordinatorRequest(FindCoordinatorCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.key()).isEqualTo("my-group");
        assertThat(decoded.keyType()).isZero();
    }

    @Test
    void testFindCoordinatorResponse() {
        var resp = new FindCoordinatorResponse((short) 0, 1, "broker1", 9092);
        byte[] encoded = KafkaCodec.encodeFindCoordinatorResponse(FindCoordinatorCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeFindCoordinatorResponse(FindCoordinatorCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.nodeId()).isEqualTo(1);
        assertThat(decoded.host()).isEqualTo("broker1");
        assertThat(decoded.port()).isEqualTo(9092);
    }

    // ===== JoinGroup (11) =====

    @Test
    void testJoinGroupRequest() {
        var req = new JoinGroupRequest("group1", 10000, 30000, "",
                "consumer", List.of(new JoinGroupRequest.Protocol("range", new byte[]{1, 2})));
        byte[] encoded = KafkaCodec.encodeJoinGroupRequest(JoinGroupCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeJoinGroupRequest(JoinGroupCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.sessionTimeoutMs()).isEqualTo(10000);
        assertThat(decoded.protocols()).hasSize(1);
        assertThat(decoded.protocols().getFirst().name()).isEqualTo("range");
    }

    @Test
    void testJoinGroupResponse() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "leader-1", "member-1",
                List.of(new JoinGroupResponse.Member("member-1", new byte[]{3, 4})));
        byte[] encoded = KafkaCodec.encodeJoinGroupResponse(JoinGroupCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeJoinGroupResponse(JoinGroupCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.generationId()).isEqualTo(1);
        assertThat(decoded.leader()).isEqualTo("leader-1");
        assertThat(decoded.memberId()).isEqualTo("member-1");
        assertThat(decoded.members()).hasSize(1);
    }

    // ===== SyncGroup (14) =====

    @Test
    void testSyncGroupRequest() {
        var req = new SyncGroupRequest("group1", 1, "member-1",
                List.of(new SyncGroupRequest.Assignment("member-1", new byte[]{1, 2, 3})));
        byte[] encoded = KafkaCodec.encodeSyncGroupRequest((short) 0, req);
        var decoded = KafkaCodec.decodeSyncGroupRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.assignments()).hasSize(1);
        assertThat(decoded.assignments().getFirst().assignment()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void testSyncGroupResponse() {
        var resp = new SyncGroupResponse((short) 0, new byte[]{4, 5, 6});
        byte[] encoded = KafkaCodec.encodeSyncGroupResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeSyncGroupResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.assignment()).isEqualTo(new byte[]{4, 5, 6});
    }

    // ===== Heartbeat (12) =====

    @Test
    void testHeartbeatRequest() {
        var req = new HeartbeatRequest("group1", 5, "member-1");
        byte[] encoded = KafkaCodec.encodeHeartbeatRequest((short) 0, req);
        var decoded = KafkaCodec.decodeHeartbeatRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.generationId()).isEqualTo(5);
        assertThat(decoded.memberId()).isEqualTo("member-1");
    }

    @Test
    void testHeartbeatResponse() {
        var resp = new HeartbeatResponse((short) 27); // REBALANCE_IN_PROGRESS
        byte[] encoded = KafkaCodec.encodeHeartbeatResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeHeartbeatResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isEqualTo((short) 27);
    }

    // ===== LeaveGroup (13) =====

    @Test
    void testLeaveGroupRequest() {
        var req = new LeaveGroupRequest("group1", "member-1");
        byte[] encoded = KafkaCodec.encodeLeaveGroupRequest((short) 0, req);
        var decoded = KafkaCodec.decodeLeaveGroupRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.memberId()).isEqualTo("member-1");
    }

    @Test
    void testLeaveGroupResponse() {
        var resp = new LeaveGroupResponse((short) 0);
        byte[] encoded = KafkaCodec.encodeLeaveGroupResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeLeaveGroupResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
    }

    // ===== OffsetCommit (8) =====

    @Test
    void testOffsetCommitRequest() {
        var req = new OffsetCommitRequest("group1", 1, "member-1",
                List.of(new OffsetCommitRequest.TopicOffsets("topic", List.of(
                        new OffsetCommitRequest.PartitionOffset(0, 42L, "meta")))));
        byte[] encoded = KafkaCodec.encodeOffsetCommitRequest(OffsetCommitCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeOffsetCommitRequest(OffsetCommitCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.topics().getFirst().partitions().getFirst().committedOffset()).isEqualTo(42L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().metadata()).isEqualTo("meta");
    }

    @Test
    void testOffsetCommitResponse() {
        var resp = new OffsetCommitResponse(List.of(
                new OffsetCommitResponse.TopicResponse("topic", List.of(
                        new OffsetCommitResponse.PartitionResponse(0, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeOffsetCommitResponse(OffsetCommitCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeOffsetCommitResponse(OffsetCommitCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
    }

    // ===== OffsetFetch (9) =====

    @Test
    void testOffsetFetchRequest() {
        var req = new OffsetFetchRequest("group1", List.of(
                new OffsetFetchRequest.TopicPartitions("topic", List.of(0, 1, 2))));
        byte[] encoded = KafkaCodec.encodeOffsetFetchRequest(OffsetFetchCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeOffsetFetchRequest(OffsetFetchCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.topics().getFirst().partitionIndexes()).containsExactly(0, 1, 2);
    }

    @Test
    void testOffsetFetchResponse() {
        var resp = new OffsetFetchResponse(List.of(
                new OffsetFetchResponse.TopicResponse("topic", List.of(
                        new OffsetFetchResponse.PartitionResponse(0, 42L, "meta", (short) 0)))));
        byte[] encoded = KafkaCodec.encodeOffsetFetchResponse(OffsetFetchCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeOffsetFetchResponse(OffsetFetchCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics().getFirst().partitions().getFirst().committedOffset()).isEqualTo(42L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().metadata()).isEqualTo("meta");
    }

    // ===== CreateTopics (19) =====

    @Test
    void testCreateTopicsRequest() {
        var req = new CreateTopicsRequest(List.of(
                new CreateTopicsRequest.TopicCreate("new-topic", 3, (short) 1,
                        Map.of("retention.ms", "86400000"))), 30000);
        byte[] encoded = KafkaCodec.encodeCreateTopicsRequest(CreateTopicsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeCreateTopicsRequest(CreateTopicsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().name()).isEqualTo("new-topic");
        assertThat(decoded.topics().getFirst().numPartitions()).isEqualTo(3);
        assertThat(decoded.topics().getFirst().configs()).containsEntry("retention.ms", "86400000");
    }

    @Test
    void testCreateTopicsResponse() {
        var resp = new CreateTopicsResponse(List.of(
                new CreateTopicsResponse.TopicResult("new-topic", (short) 0)));
        byte[] encoded = KafkaCodec.encodeCreateTopicsResponse(CreateTopicsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeCreateTopicsResponse(CreateTopicsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().errorCode()).isZero();
    }

    // ===== DeleteTopics (20) =====

    @Test
    void testDeleteTopicsRequest() {
        var req = new DeleteTopicsRequest(List.of("topic1", "topic2"), 5000);
        byte[] encoded = KafkaCodec.encodeDeleteTopicsRequest(DeleteTopicsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeDeleteTopicsRequest(DeleteTopicsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topicNames()).containsExactly("topic1", "topic2");
        assertThat(decoded.timeoutMs()).isEqualTo(5000);
    }

    @Test
    void testDeleteTopicsResponse() {
        var resp = new DeleteTopicsResponse(List.of(
                new DeleteTopicsResponse.TopicResult("topic1", (short) 0),
                new DeleteTopicsResponse.TopicResult("topic2", (short) 3)));
        byte[] encoded = KafkaCodec.encodeDeleteTopicsResponse(DeleteTopicsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeDeleteTopicsResponse(DeleteTopicsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.responses()).hasSize(2);
        assertThat(decoded.responses().get(1).errorCode()).isEqualTo((short) 3);
    }

    // ===== DescribeGroups (15) =====

    @Test
    void testDescribeGroupsRequest() {
        var req = new DescribeGroupsRequest(List.of("g1", "g2"));
        byte[] encoded = KafkaCodec.encodeDescribeGroupsRequest(DescribeGroupsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeDescribeGroupsRequest(DescribeGroupsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.groups()).containsExactly("g1", "g2");
    }

    @Test
    void testDescribeGroupsResponse() {
        var resp = new DescribeGroupsResponse(List.of(
                new DescribeGroupsResponse.GroupDescription((short) 0, "g1", "Stable",
                        "consumer", "range",
                        List.of(new DescribeGroupsResponse.MemberDescription(
                                "m1", "client-1", "/127.0.0.1",
                                new byte[]{1}, new byte[]{2})))));
        byte[] encoded = KafkaCodec.encodeDescribeGroupsResponse(DescribeGroupsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeDescribeGroupsResponse(DescribeGroupsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.groups()).hasSize(1);
        assertThat(decoded.groups().getFirst().state()).isEqualTo("Stable");
        assertThat(decoded.groups().getFirst().members()).hasSize(1);
    }

    // ===== InitProducerId (22) =====

    @Test
    void testInitProducerIdRequest() {
        var req = new InitProducerIdRequest("my-txn", 60000);
        byte[] encoded = KafkaCodec.encodeInitProducerIdRequest(InitProducerIdCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeInitProducerIdRequest(InitProducerIdCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isEqualTo("my-txn");
        assertThat(decoded.transactionTimeoutMs()).isEqualTo(60000);
    }

    @Test
    void testInitProducerIdRequestNullTxnId() {
        var req = new InitProducerIdRequest(null, 30000);
        byte[] encoded = KafkaCodec.encodeInitProducerIdRequest(InitProducerIdCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeInitProducerIdRequest(InitProducerIdCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isNull();
    }

    @Test
    void testInitProducerIdResponse() {
        var resp = new InitProducerIdResponse((short) 0, 1000L, (short) 0);
        byte[] encoded = KafkaCodec.encodeInitProducerIdResponse(InitProducerIdCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeInitProducerIdResponse(InitProducerIdCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.producerId()).isEqualTo(1000L);
        assertThat(decoded.producerEpoch()).isZero();
    }

    // ===== AddPartitionsToTxn (24) =====

    @Test
    void testAddPartitionsToTxnRequest() {
        var req = new AddPartitionsToTxnRequest("txn-1", 1000L, (short) 0,
                List.of(new AddPartitionsToTxnRequest.TopicPartitions("topic", List.of(0, 1))));
        byte[] encoded = KafkaCodec.encodeAddPartitionsToTxnRequest(AddPartitionsToTxnCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeAddPartitionsToTxnRequest(AddPartitionsToTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isEqualTo("txn-1");
        assertThat(decoded.producerId()).isEqualTo(1000L);
        assertThat(decoded.topics().getFirst().partitionIndexes()).containsExactly(0, 1);
    }

    @Test
    void testAddPartitionsToTxnResponse() {
        var resp = new AddPartitionsToTxnResponse(List.of(
                new AddPartitionsToTxnResponse.TopicResponse("topic", List.of(
                        new AddPartitionsToTxnResponse.PartitionResponse(0, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeAddPartitionsToTxnResponse(AddPartitionsToTxnCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeAddPartitionsToTxnResponse(AddPartitionsToTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
    }

    // ===== EndTxn (26) =====

    @Test
    void testEndTxnRequestCommit() {
        var req = new EndTxnRequest("txn-1", 1000L, (short) 0, true);
        byte[] encoded = KafkaCodec.encodeEndTxnRequest(EndTxnCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeEndTxnRequest(EndTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isEqualTo("txn-1");
        assertThat(decoded.committed()).isTrue();
    }

    @Test
    void testEndTxnRequestAbort() {
        var req = new EndTxnRequest("txn-1", 1000L, (short) 0, false);
        byte[] encoded = KafkaCodec.encodeEndTxnRequest(EndTxnCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeEndTxnRequest(EndTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.committed()).isFalse();
    }

    @Test
    void testEndTxnResponse() {
        var resp = new EndTxnResponse((short) 0);
        byte[] encoded = KafkaCodec.encodeEndTxnResponse(EndTxnCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeEndTxnResponse(EndTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
    }

    // ===== ListGroups (16) =====

    @Test
    void testListGroupsRequest() {
        var req = new ListGroupsRequest();
        byte[] encoded = KafkaCodec.encodeListGroupsRequest(ListGroupsCodec.MIN_VERSION, req);
        ListGroupsRequest decoded = KafkaCodec.decodeListGroupsRequest(ListGroupsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded).isNotNull();
    }

    @Test
    void testListGroupsResponse() {
        var resp = new ListGroupsResponse((short) 0, List.of(
                new ListGroupsResponse.GroupListing("group1", "consumer", "Stable"),
                new ListGroupsResponse.GroupListing("group2", "consumer", "Empty")));
        byte[] encoded = KafkaCodec.encodeListGroupsResponse(ListGroupsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeListGroupsResponse(ListGroupsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.groups()).hasSize(2);
        assertThat(decoded.groups().getFirst().groupId()).isEqualTo("group1");
        assertThat(decoded.groups().getFirst().protocolType()).isEqualTo("consumer");
        assertThat(decoded.groups().getFirst().state()).isNull(); // GroupState is v4+ only
        assertThat(decoded.groups().get(1).groupId()).isEqualTo("group2");
    }

    // ===== DeleteRecords (21) =====

    @Test
    void testDeleteRecordsRequest() {
        var req = new DeleteRecordsRequest(List.of(
                new DeleteRecordsRequest.TopicData("topic", List.of(
                        new DeleteRecordsRequest.PartitionData(0, 42L),
                        new DeleteRecordsRequest.PartitionData(1, 100L)))), 30000);
        byte[] encoded = KafkaCodec.encodeDeleteRecordsRequest(DeleteRecordsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeDeleteRecordsRequest(DeleteRecordsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().name()).isEqualTo("topic");
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().getFirst().offset()).isEqualTo(42L);
        assertThat(decoded.timeoutMs()).isEqualTo(30000);
    }

    @Test
    void testDeleteRecordsResponse() {
        var resp = new DeleteRecordsResponse(List.of(
                new DeleteRecordsResponse.TopicData("topic", List.of(
                        new DeleteRecordsResponse.PartitionData(0, 42L, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeDeleteRecordsResponse(DeleteRecordsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeDeleteRecordsResponse(DeleteRecordsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions().getFirst().lowWatermark()).isEqualTo(42L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().errorCode()).isZero();
    }

    // ===== CreatePartitions (37) =====

    @Test
    void testCreatePartitionsRequest() {
        var req = new CreatePartitionsRequest(List.of(
                new CreatePartitionsRequest.TopicNewPartitions("topic1", 5),
                new CreatePartitionsRequest.TopicNewPartitions("topic2", 10)), 30000);
        byte[] encoded = KafkaCodec.encodeCreatePartitionsRequest(CreatePartitionsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeCreatePartitionsRequest(CreatePartitionsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(2);
        assertThat(decoded.topics().getFirst().name()).isEqualTo("topic1");
        assertThat(decoded.topics().getFirst().newCount()).isEqualTo(5);
        assertThat(decoded.timeoutMs()).isEqualTo(30000);
    }

    @Test
    void testCreatePartitionsResponse() {
        var resp = new CreatePartitionsResponse(List.of(
                new CreatePartitionsResponse.TopicResult("topic1", (short) 0),
                new CreatePartitionsResponse.TopicResult("topic2", (short) 37)));
        byte[] encoded = KafkaCodec.encodeCreatePartitionsResponse(CreatePartitionsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeCreatePartitionsResponse(CreatePartitionsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.results()).hasSize(2);
        assertThat(decoded.results().getFirst().errorCode()).isZero();
        assertThat(decoded.results().get(1).errorCode()).isEqualTo((short) 37);
    }

    // ===== DeleteGroups (42) =====

    @Test
    void testDeleteGroupsRequest() {
        var req = new DeleteGroupsRequest(List.of("group1", "group2"));
        byte[] encoded = KafkaCodec.encodeDeleteGroupsRequest(DeleteGroupsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeDeleteGroupsRequest(DeleteGroupsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.groups()).containsExactly("group1", "group2");
    }

    @Test
    void testDeleteGroupsResponse() {
        var resp = new DeleteGroupsResponse(List.of(
                new DeleteGroupsResponse.GroupResult("group1", (short) 0),
                new DeleteGroupsResponse.GroupResult("group2", (short) 69)));
        byte[] encoded = KafkaCodec.encodeDeleteGroupsResponse(DeleteGroupsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeDeleteGroupsResponse(DeleteGroupsCodec.MIN_VERSION, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.results()).hasSize(2);
        assertThat(decoded.results().getFirst().errorCode()).isZero();
        assertThat(decoded.results().get(1).errorCode()).isEqualTo((short) 69);
    }

    // ===== OffsetDelete (47) =====

    @Test
    void testOffsetDeleteRequest() {
        var req = new OffsetDeleteRequest("group1", List.of(
                new OffsetDeleteRequest.TopicData("topic", List.of(
                        new OffsetDeleteRequest.PartitionData(0),
                        new OffsetDeleteRequest.PartitionData(1)))));
        byte[] encoded = KafkaCodec.encodeOffsetDeleteRequest(req);
        var decoded = KafkaCodec.decodeOffsetDeleteRequest(java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.groupId()).isEqualTo("group1");
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().getFirst().partitionIndex()).isZero();
    }

    @Test
    void testOffsetDeleteResponse() {
        var resp = new OffsetDeleteResponse((short) 0, List.of(
                new OffsetDeleteResponse.TopicData("topic", List.of(
                        new OffsetDeleteResponse.PartitionData(0, (short) 0),
                        new OffsetDeleteResponse.PartitionData(1, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeOffsetDeleteResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeOffsetDeleteResponse((short) 0, java.nio.ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
    }

    // ===== DescribeConfigs (32) =====

    @Test
    void testDescribeConfigsRequest() {
        var req = new DescribeConfigsRequest(List.of(
                new DescribeConfigsRequest.ResourceRequest((byte) 2, "my-topic",
                        List.of("retention.ms", "cleanup.policy")),
                new DescribeConfigsRequest.ResourceRequest((byte) 4, "0", null)));
        byte[] encoded = KafkaCodec.encodeDescribeConfigsRequest(DescribeConfigsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeDescribeConfigsRequest(DescribeConfigsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.resources()).hasSize(2);
        assertThat(decoded.resources().getFirst().resourceType()).isEqualTo((byte) 2);
        assertThat(decoded.resources().getFirst().resourceName()).isEqualTo("my-topic");
        assertThat(decoded.resources().getFirst().configNames()).containsExactly("retention.ms", "cleanup.policy");
        assertThat(decoded.resources().get(1).configNames()).isNull();
    }

    @Test
    void testDescribeConfigsResponse() {
        var resp = new DescribeConfigsResponse(List.of(
                new DescribeConfigsResponse.ResourceResponse((short) 0, "my-topic", List.of(
                        new DescribeConfigsResponse.ConfigEntry("retention.ms", "604800000", false, false),
                        new DescribeConfigsResponse.ConfigEntry("cleanup.policy", "delete", true, false)))));
        byte[] encoded = KafkaCodec.encodeDescribeConfigsResponse(DescribeConfigsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeDescribeConfigsResponse(DescribeConfigsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.resources()).hasSize(1);
        assertThat(decoded.resources().getFirst().errorCode()).isZero();
        assertThat(decoded.resources().getFirst().configs()).hasSize(2);
        assertThat(decoded.resources().getFirst().configs().getFirst().name()).isEqualTo("retention.ms");
        assertThat(decoded.resources().getFirst().configs().getFirst().readOnly()).isFalse();
        assertThat(decoded.resources().getFirst().configs().get(1).readOnly()).isTrue();
    }

    // ===== AlterConfigs (33) =====

    @Test
    void testAlterConfigsRequest() {
        var req = new AlterConfigsRequest(List.of(
                new AlterConfigsRequest.ResourceConfig((byte) 2, "my-topic", List.of(
                        new AlterConfigsRequest.ConfigEntry("retention.ms", "3600000"),
                        new AlterConfigsRequest.ConfigEntry("cleanup.policy", "compact")))),
                true);
        byte[] encoded = KafkaCodec.encodeAlterConfigsRequest(AlterConfigsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeAlterConfigsRequest(AlterConfigsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.resources()).hasSize(1);
        assertThat(decoded.resources().getFirst().resourceType()).isEqualTo((byte) 2);
        assertThat(decoded.resources().getFirst().configs()).hasSize(2);
        assertThat(decoded.resources().getFirst().configs().getFirst().name()).isEqualTo("retention.ms");
        assertThat(decoded.validateOnly()).isTrue();
    }

    @Test
    void testAlterConfigsResponse() {
        var resp = new AlterConfigsResponse(List.of(
                new AlterConfigsResponse.ResourceResponse((short) 0, "my-topic"),
                new AlterConfigsResponse.ResourceResponse((short) 40, "other-topic")));
        byte[] encoded = KafkaCodec.encodeAlterConfigsResponse(AlterConfigsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeAlterConfigsResponse(AlterConfigsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.resources()).hasSize(2);
        assertThat(decoded.resources().getFirst().errorCode()).isZero();
        assertThat(decoded.resources().get(1).errorCode()).isEqualTo((short) 40);
    }

    // ===== AddOffsetsToTxn (25) =====

    @Test
    void testAddOffsetsToTxnRequest() {
        var req = new AddOffsetsToTxnRequest("txn-1", 1000L, (short) 0, "my-group");
        byte[] encoded = KafkaCodec.encodeAddOffsetsToTxnRequest(AddOffsetsToTxnCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeAddOffsetsToTxnRequest(AddOffsetsToTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isEqualTo("txn-1");
        assertThat(decoded.producerId()).isEqualTo(1000L);
        assertThat(decoded.producerEpoch()).isZero();
        assertThat(decoded.groupId()).isEqualTo("my-group");
    }

    @Test
    void testAddOffsetsToTxnResponse() {
        var resp = new AddOffsetsToTxnResponse((short) 0);
        byte[] encoded = KafkaCodec.encodeAddOffsetsToTxnResponse(AddOffsetsToTxnCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeAddOffsetsToTxnResponse(AddOffsetsToTxnCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
    }

    // ===== TxnOffsetCommit (28) =====

    @Test
    void testTxnOffsetCommitRequest() {
        var req = new TxnOffsetCommitRequest("txn-1", "my-group", 1000L, (short) 0,
                List.of(new TxnOffsetCommitRequest.TopicData("topic", List.of(
                        new TxnOffsetCommitRequest.PartitionData(0, 42L, "meta"),
                        new TxnOffsetCommitRequest.PartitionData(1, 100L, null)))));
        byte[] encoded = KafkaCodec.encodeTxnOffsetCommitRequest(TxnOffsetCommitCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeTxnOffsetCommitRequest(TxnOffsetCommitCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.transactionalId()).isEqualTo("txn-1");
        assertThat(decoded.groupId()).isEqualTo("my-group");
        assertThat(decoded.producerId()).isEqualTo(1000L);
        assertThat(decoded.producerEpoch()).isZero();
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().getFirst().committedOffset()).isEqualTo(42L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().metadata()).isEqualTo("meta");
        assertThat(decoded.topics().getFirst().partitions().get(1).metadata()).isNull();
    }

    @Test
    void testTxnOffsetCommitResponse() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic", List.of(
                        new TxnOffsetCommitResponse.PartitionData(0, (short) 0),
                        new TxnOffsetCommitResponse.PartitionData(1, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeTxnOffsetCommitResponse(TxnOffsetCommitCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeTxnOffsetCommitResponse(TxnOffsetCommitCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().getFirst().errorCode()).isZero();
    }

    // ===== SaslHandshake (17) =====

    @Test
    void testSaslHandshakeRequest() {
        var req = new SaslHandshakeRequest("PLAIN");
        byte[] encoded = KafkaCodec.encodeSaslHandshakeRequest((short) 0, req);
        var decoded = KafkaCodec.decodeSaslHandshakeRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.mechanism()).isEqualTo("PLAIN");
    }

    @Test
    void testSaslHandshakeResponse() {
        var resp = new SaslHandshakeResponse((short) 0, List.of("PLAIN", "SCRAM-SHA-256"));
        byte[] encoded = KafkaCodec.encodeSaslHandshakeResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeSaslHandshakeResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.mechanisms()).containsExactly("PLAIN", "SCRAM-SHA-256");
    }

    // ===== SaslAuthenticate (36) =====

    @Test
    void testSaslAuthenticateRequest() {
        byte[] authBytes = "\0alice\0secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var req = new SaslAuthenticateRequest(authBytes);
        byte[] encoded = KafkaCodec.encodeSaslAuthenticateRequest((short) 0, req);
        var decoded = KafkaCodec.decodeSaslAuthenticateRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.authBytes()).isEqualTo(authBytes);
    }

    @Test
    void testSaslAuthenticateResponse() {
        byte[] authBytes = "v=serverSig".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // v0 layout (spec 3.6.1): errorCode, errorMessage, authBytes — NO sessionLifetimeMs
        var resp = SaslAuthenticateResponse.builder().errorCode((short) 0).errorMessage("").authBytes(authBytes).sessionLifetimeMs(0L).build();
        byte[] encoded = KafkaCodec.encodeSaslAuthenticateResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeSaslAuthenticateResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.errorMessage()).isEmpty();
        assertThat(decoded.authBytes()).isEqualTo(authBytes);
    }

    // ===== LeaderAndIsr (4) =====

    @Test
    void testLeaderAndIsrRequest() {
        var req = new LeaderAndIsrRequest(
                0, 1, LeaderAndIsrRequest.DEFAULT_BROKER_EPOCH,
                LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
                LeaderAndIsrRequest.DEFAULT_TYPE,
                List.of(
                        new LeaderAndIsrRequest.PartitionState("topic1", 0, 1, 0, 1,
                                List.of(0, 1, 2),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_PARTITION_EPOCH,
                                List.of(0, 1),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                                List.of(), List.of(),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE),
                        new LeaderAndIsrRequest.PartitionState("topic1", 1, 1, 1, 1,
                                List.of(0, 1),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_PARTITION_EPOCH,
                                List.of(0, 1),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                                List.of(), List.of(),
                                LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)),
                List.of(), List.of());
        byte[] encoded = KafkaCodec.encodeLeaderAndIsrRequest(
                LeaderAndIsrCodec.PINNED_VERSION, req);
        var decoded = KafkaCodec.decodeLeaderAndIsrRequest(
                LeaderAndIsrCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.controllerId()).isZero();
        assertThat(decoded.controllerEpoch()).isEqualTo(1);
        assertThat(decoded.partitionStates()).hasSize(2);
        assertThat(decoded.partitionStates().getFirst().topic()).isEqualTo("topic1");
        assertThat(decoded.partitionStates().getFirst().leader()).isZero();
        assertThat(decoded.partitionStates().getFirst().isr()).containsExactly(0, 1, 2);
    }

    @Test
    void testLeaderAndIsrResponse() {
        var resp = new LeaderAndIsrResponse((short) 0, List.of(
                new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0),
                new LeaderAndIsrResponse.PartitionError("topic1", 1, (short) 0)),
                List.of());
        byte[] encoded = KafkaCodec.encodeLeaderAndIsrResponse(
                LeaderAndIsrCodec.PINNED_VERSION, resp);
        var decoded = KafkaCodec.decodeLeaderAndIsrResponse(
                LeaderAndIsrCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.partitionErrors()).hasSize(2);
        assertThat(decoded.partitionErrors().getFirst().errorCode()).isZero();
    }

    // ===== StopReplica (5) =====

    @Test
    void testStopReplicaRequest() {
        var req = new StopReplicaRequest(
            0, 1, StopReplicaRequest.DEFAULT_BROKER_EPOCH,
            StopReplicaRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            true,
            List.of(
                new StopReplicaRequest.TopicPartitionData("topic1", 0),
                new StopReplicaRequest.TopicPartitionData("topic1", 1)),
            List.of(), List.of());
        byte[] encoded = KafkaCodec.encodeStopReplicaRequest(
            StopReplicaCodec.PINNED_VERSION, req);
        var decoded = KafkaCodec.decodeStopReplicaRequest(
            StopReplicaCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.controllerId()).isZero();
        assertThat(decoded.controllerEpoch()).isEqualTo(1);
        assertThat(decoded.deletePartitions()).isTrue();
        assertThat(decoded.partitions()).hasSize(2);
    }

    @Test
    void testStopReplicaResponse() {
        var resp = new StopReplicaResponse((short) 0, List.of(
                new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        byte[] encoded = KafkaCodec.encodeStopReplicaResponse(
            StopReplicaCodec.PINNED_VERSION, resp);
        var decoded = KafkaCodec.decodeStopReplicaResponse(
            StopReplicaCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.partitions()).hasSize(1);
    }

    // ===== UpdateMetadata (6) =====

    @Test
    void testUpdateMetadataRequest() {
        var req = new UpdateMetadataRequest(
            0, 1, UpdateMetadataRequest.DEFAULT_BROKER_EPOCH,
            UpdateMetadataRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            UpdateMetadataRequest.DEFAULT_TYPE,
            List.of(new UpdateMetadataRequest.PartitionState(
                "topic1", 0, 1, 0, 1,
                List.of(0, 1),
                UpdateMetadataRequest.PartitionState.DEFAULT_ZK_VERSION,
                List.of(0, 1), null)),
            List.of(),
            List.of(new UpdateMetadataRequest.BrokerState(0, "host1", 9092),
                    new UpdateMetadataRequest.BrokerState(1, "host2", 9093)));
        byte[] encoded = KafkaCodec.encodeUpdateMetadataRequest(
            UpdateMetadataCodec.PINNED_VERSION, req);
        var decoded = KafkaCodec.decodeUpdateMetadataRequest(
            UpdateMetadataCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.controllerId()).isZero();
        assertThat(decoded.liveBrokers()).hasSize(2);
        assertThat(decoded.liveBrokers().getFirst().host()).isEqualTo("host1");
        assertThat(decoded.partitionStates()).hasSize(1);
        assertThat(decoded.partitionStates().getFirst().replicas()).containsExactly(0, 1);
    }

    @Test
    void testUpdateMetadataResponse() {
        var resp = new UpdateMetadataResponse((short) 0);
        byte[] encoded = KafkaCodec.encodeUpdateMetadataResponse(
            UpdateMetadataCodec.PINNED_VERSION, resp);
        var decoded = KafkaCodec.decodeUpdateMetadataResponse(
            UpdateMetadataCodec.PINNED_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
    }

    // ===== ControlledShutdown (7) =====

    @Test
    void testControlledShutdownRequest() {
        var req = new ControlledShutdownRequest(2);
        byte[] encoded = KafkaCodec.encodeControlledShutdownRequest(req);
        var decoded = KafkaCodec.decodeControlledShutdownRequest(ByteBuffer.wrap(encoded));
        assertThat(decoded.brokerId()).isEqualTo(2);
    }

    @Test
    void testControlledShutdownResponse() {
        var resp = new ControlledShutdownResponse((short) 0, List.of(
                new ControlledShutdownResponse.TopicPartitionData("topic1", 0),
                new ControlledShutdownResponse.TopicPartitionData("topic2", 1)));
        byte[] encoded = KafkaCodec.encodeControlledShutdownResponse(resp);
        var decoded = KafkaCodec.decodeControlledShutdownResponse(ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.partitionsRemaining()).hasSize(2);
        assertThat(decoded.partitionsRemaining().getFirst().topic()).isEqualTo("topic1");
    }

    // ===== OffsetForLeaderEpoch (23) =====

    @Test
    void testOffsetForLeaderEpochRequest() {
        var req = new OffsetForLeaderEpochRequest(List.of(
                new OffsetForLeaderEpochRequest.TopicData("topic1", List.of(
                        new OffsetForLeaderEpochRequest.PartitionData(0, 5),
                        new OffsetForLeaderEpochRequest.PartitionData(1, 3)))));
        byte[] encoded = KafkaCodec.encodeOffsetForLeaderEpochRequest(req);
        var decoded = KafkaCodec.decodeOffsetForLeaderEpochRequest(ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).hasSize(2);
        assertThat(decoded.topics().getFirst().partitions().getFirst().leaderEpoch()).isEqualTo(5);
    }

    @Test
    void testOffsetForLeaderEpochResponse() {
        var resp = new OffsetForLeaderEpochResponse(List.of(
                new OffsetForLeaderEpochResponse.TopicData("topic1", List.of(
                        new OffsetForLeaderEpochResponse.PartitionData((short) 0, 0, 5, 1000L)))));
        byte[] encoded = KafkaCodec.encodeOffsetForLeaderEpochResponse(resp);
        var decoded = KafkaCodec.decodeOffsetForLeaderEpochResponse(ByteBuffer.wrap(encoded));
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions().getFirst().endOffset()).isEqualTo(1000L);
        assertThat(decoded.topics().getFirst().partitions().getFirst().leaderEpoch()).isEqualTo(5);
    }

    // ===== WriteTxnMarkers (27) =====

    @Test
    void testWriteTxnMarkersRequest() {
        var req = new WriteTxnMarkersRequest(List.of(
                new WriteTxnMarkersRequest.TxnMarker(100L, (short) 0, 1, true, List.of(
                        new WriteTxnMarkersRequest.TopicPartitionData("topic1", 0),
                        new WriteTxnMarkersRequest.TopicPartitionData("topic1", 1)))));
        byte[] encoded = KafkaCodec.encodeWriteTxnMarkersRequest((short) 0, req);
        var decoded = KafkaCodec.decodeWriteTxnMarkersRequest((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers()).hasSize(1);
        assertThat(decoded.markers().getFirst().producerId()).isEqualTo(100L);
        assertThat(decoded.markers().getFirst().committed()).isTrue();
        assertThat(decoded.markers().getFirst().partitions()).hasSize(2);
    }

    @Test
    void testWriteTxnMarkersResponse() {
        var resp = new WriteTxnMarkersResponse(List.of(
                new WriteTxnMarkersResponse.MarkerResult(100L, List.of(
                        new WriteTxnMarkersResponse.TopicResult("topic1", List.of(
                                new WriteTxnMarkersResponse.PartitionResult(0, (short) 0),
                                new WriteTxnMarkersResponse.PartitionResult(1, (short) 0)))))));
        byte[] encoded = KafkaCodec.encodeWriteTxnMarkersResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeWriteTxnMarkersResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers()).hasSize(1);
        assertThat(decoded.markers().getFirst().producerId()).isEqualTo(100L);
        assertThat(decoded.markers().getFirst().topics().getFirst().partitions()).hasSize(2);
    }

    // ===== AlterPartitionReassignments (45) =====

    @Test
    void testAlterPartitionReassignmentsRequest() {
        var req = new AlterPartitionReassignmentsRequest(30000, List.of(
                new AlterPartitionReassignmentsRequest.TopicReassignment("topic1", List.of(
                        new AlterPartitionReassignmentsRequest.PartitionReassignment(0, List.of(0, 1, 2)),
                        new AlterPartitionReassignmentsRequest.PartitionReassignment(1, null)))));
        byte[] encoded = KafkaCodec.encodeAlterPartitionReassignmentsRequest(AlterPartitionReassignmentsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeAlterPartitionReassignmentsRequest(AlterPartitionReassignmentsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.timeoutMs()).isEqualTo(30000);
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions().getFirst().replicas()).containsExactly(0, 1, 2);
        assertThat(decoded.topics().getFirst().partitions().get(1).replicas()).isNull();
    }

    @Test
    void testAlterPartitionReassignmentsResponse() {
        var resp = new AlterPartitionReassignmentsResponse((short) 0, List.of(
                new AlterPartitionReassignmentsResponse.TopicResult("topic1", List.of(
                        new AlterPartitionReassignmentsResponse.PartitionResult(0, (short) 0)))));
        byte[] encoded = KafkaCodec.encodeAlterPartitionReassignmentsResponse(AlterPartitionReassignmentsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeAlterPartitionReassignmentsResponse(AlterPartitionReassignmentsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.topics()).hasSize(1);
    }

    // ===== ListPartitionReassignments (46) =====

    @Test
    void testListPartitionReassignmentsRequest() {
        var req = new ListPartitionReassignmentsRequest(30000, List.of(
                new ListPartitionReassignmentsRequest.TopicData("topic1", List.of(0, 1))));
        byte[] encoded = KafkaCodec.encodeListPartitionReassignmentsRequest(ListPartitionReassignmentsCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeListPartitionReassignmentsRequest(ListPartitionReassignmentsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.timeoutMs()).isEqualTo(30000);
        assertThat(decoded.topics()).hasSize(1);
        assertThat(decoded.topics().getFirst().partitions()).containsExactly(0, 1);
    }

    @Test
    void testListPartitionReassignmentsResponse() {
        var resp = new ListPartitionReassignmentsResponse((short) 0, List.of(
                new ListPartitionReassignmentsResponse.TopicResult("topic1", List.of(
                        new ListPartitionReassignmentsResponse.PartitionResult(0,
                                List.of(0, 1, 2), List.of(2), List.of(1))))));
        byte[] encoded = KafkaCodec.encodeListPartitionReassignmentsResponse(ListPartitionReassignmentsCodec.MIN_VERSION, resp);
        var decoded = KafkaCodec.decodeListPartitionReassignmentsResponse(ListPartitionReassignmentsCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.errorCode()).isZero();
        assertThat(decoded.topics()).hasSize(1);
        var part = decoded.topics().getFirst().partitions().getFirst();
        assertThat(part.replicas()).containsExactly(0, 1, 2);
        assertThat(part.addingReplicas()).containsExactly(2);
        assertThat(part.removingReplicas()).containsExactly(1);
    }

    // ===== Edge cases =====

    @Test
    void testMetadataResponseMultipleBrokers() {
        var resp = new MetadataResponse(
                List.of(
                        new MetadataResponse.BrokerMetadata(0, "host1", 9092),
                        new MetadataResponse.BrokerMetadata(1, "host2", 9093),
                        new MetadataResponse.BrokerMetadata(2, "host3", 9094)),
                List.of());
        byte[] encoded = KafkaCodec.encodeMetadataResponse((short) 0, resp);
        var decoded = KafkaCodec.decodeMetadataResponse((short) 0, ByteBuffer.wrap(encoded));
        assertThat(decoded.brokers()).hasSize(3);
    }

    @Test
    void testProduceRequestMultipleTopicsAndPartitions() {
        var req = ProduceRequest.builder().transactionalId(null).acks((short) 1).timeoutMs(5000).topicData(List.of(
                        new ProduceRequest.TopicData("t1", List.of(
                                new ProduceRequest.PartitionData(0, new byte[]{1}),
                                new ProduceRequest.PartitionData(1, new byte[]{2}))),
                        new ProduceRequest.TopicData("t2", List.of(
                                new ProduceRequest.PartitionData(0, new byte[]{3}))))).build();
        byte[] encoded = KafkaCodec.encodeProduceRequest(ProduceCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeProduceRequest(ProduceCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topicData()).hasSize(2);
        assertThat(decoded.topicData().getFirst().partitionData()).hasSize(2);
    }

    @Test
    void testOffsetCommitNullMetadata() {
        var req = new OffsetCommitRequest("g", 1, "m",
                List.of(new OffsetCommitRequest.TopicOffsets("t", List.of(
                        new OffsetCommitRequest.PartitionOffset(0, 10L, null)))));
        byte[] encoded = KafkaCodec.encodeOffsetCommitRequest(OffsetCommitCodec.MIN_VERSION, req);
        var decoded = KafkaCodec.decodeOffsetCommitRequest(OffsetCommitCodec.MIN_VERSION, ByteBuffer.wrap(encoded));
        assertThat(decoded.topics().getFirst().partitions().getFirst().metadata()).isNull();
    }
}
