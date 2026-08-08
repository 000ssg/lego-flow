package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.common.ApiKey;
import ssg.legoflow.messaging.kafka.protocol.*;

import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Binary codec for the Kafka wire protocol.
 *
 * <p>Handles encoding and decoding of all supported Kafka API request/response types.
 * The wire format uses: 4-byte length prefix, 2-byte API key, 2-byte API version,
 * 4-byte correlation ID, then the request/response body.
 *
 * @since 0.1.0
 */
public final class KafkaCodec {

    private KafkaCodec() {
    }

    // ===== Frame-level encoding =====

    /**
     * Encodes a request with its header into a length-prefixed frame.
     *
     * @param header  the request header
     * @param payload the encoded request body
     * @return the complete frame ready for transmission
     */
    public static ByteBuffer encodeRequest(RequestHeader header, byte[] payload) {
        byte[] clientIdBytes = header.clientId() != null
                ? header.clientId().getBytes(StandardCharsets.UTF_8) : null;
        int clientIdLen = clientIdBytes != null ? clientIdBytes.length : 0;

        // Header: apiKey(2) + apiVersion(2) + correlationId(4) + clientId(2+len)
        int headerSize = 2 + 2 + 4 + 2 + clientIdLen;
        int totalSize = headerSize + payload.length;

        ByteBuffer buf = BufferPool.getBuffer(4 + totalSize);
        buf.putInt(totalSize);
        buf.putShort(header.apiKey());
        buf.putShort(header.apiVersion());
        buf.putInt(header.correlationId());
        if (clientIdBytes != null) {
            buf.putShort((short) clientIdBytes.length);
            buf.put(clientIdBytes);
        } else {
            buf.putShort((short) -1);
        }
        buf.put(payload);
        buf.flip();
        return buf;
    }

    /**
     * Encodes a response with its header into a length-prefixed frame.
     *
     * @param header  the response header
     * @param payload the encoded response body
     * @return the complete frame ready for transmission
     */
    public static ByteBuffer encodeResponse(ResponseHeader header, byte[] payload) {
        int totalSize = 4 + payload.length; // correlationId(4) + payload
        ByteBuffer buf = BufferPool.getBuffer(4 + totalSize);
        buf.putInt(totalSize);
        buf.putInt(header.correlationId());
        buf.put(payload);
        buf.flip();
        return buf;
    }

    /**
     * Decodes a request header from a buffer (after the length prefix has been read).
     *
     * @param buf the buffer positioned after the 4-byte length prefix
     * @return the decoded request header
     */
    public static RequestHeader decodeRequestHeader(ByteBuffer buf) {
        short apiKey = buf.getShort();
        short apiVersion = buf.getShort();
        int correlationId = buf.getInt();
        String clientId = readNullableString(buf);
        return new RequestHeader(apiKey, apiVersion, correlationId, clientId);
    }

    /**
     * Decodes a response header from a buffer (after the length prefix has been read).
     *
     * @param buf the buffer positioned after the 4-byte length prefix
     * @return the decoded response header
     */
    public static ResponseHeader decodeResponseHeader(ByteBuffer buf) {
        int correlationId = buf.getInt();
        return new ResponseHeader(correlationId);
    }

    // ===== ApiVersions (18) =====

    /**
     * Encodes an ApiVersions request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeApiVersionsRequest(ApiVersionsRequest req) {
        // Simple version: no body needed for v0
        return new byte[0];
    }

    /**
     * Decodes an ApiVersions request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ApiVersionsRequest decodeApiVersionsRequest(ByteBuffer buf) {
        return new ApiVersionsRequest();
    }

    /**
     * Encodes an ApiVersions response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeApiVersionsResponse(ApiVersionsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + resp.apiKeys().size() * 6);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.apiKeys().size());
        for (var ak : resp.apiKeys()) {
            buf.putShort(ak.apiKey());
            buf.putShort(ak.minVersion());
            buf.putShort(ak.maxVersion());
        }
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes an ApiVersions response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ApiVersionsResponse decodeApiVersionsResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ApiVersionsResponse.ApiVersion> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(new ApiVersionsResponse.ApiVersion(buf.getShort(), buf.getShort(), buf.getShort()));
        }
        return new ApiVersionsResponse(errorCode, keys);
    }

    // ===== Metadata (3) =====

    public static byte[] encodeMetadataRequest(MetadataRequest req) {
        if (req.topics() == null) {
            ByteBuffer buf = BufferPool.getBuffer(4);
            buf.putInt(-1); // null array = all topics
            buf.flip();
            return toBytes(buf);
        }
        int size = 4;
        List<byte[]> topicBytes = new ArrayList<>();
        for (String topic : req.topics()) {
            byte[] tb = topic.getBytes(StandardCharsets.UTF_8);
            topicBytes.add(tb);
            size += 2 + tb.length;
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.topics().size());
        for (byte[] tb : topicBytes) {
            buf.putShort((short) tb.length);
            buf.put(tb);
        }
        buf.flip();
        return toBytes(buf);
    }

    public static MetadataRequest decodeMetadataRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        if (topicCount < 0) return new MetadataRequest(null, false);
        List<String> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            topics.add(readString(buf));
        }
        return new MetadataRequest(topics, false);
    }

    public static byte[] encodeMetadataResponse(MetadataResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        // Brokers
        buf.putInt(resp.brokers().size());
        for (var b : resp.brokers()) {
            buf.putInt(b.nodeId());
            writeString(buf, b.host());
            buf.putInt(b.port());
        }
        // Topics
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            buf.putShort(t.errorCode());
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putShort(p.errorCode());
                buf.putInt(p.partitionIndex());
                buf.putInt(p.leaderId());
                // Replicas
                buf.putInt(p.replicaIds().size());
                for (int r : p.replicaIds()) buf.putInt(r);
                // ISR
                buf.putInt(p.isrIds().size());
                for (int r : p.isrIds()) buf.putInt(r);
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static MetadataResponse decodeMetadataResponse(ByteBuffer buf) {
        int brokerCount = buf.getInt();
        List<MetadataResponse.BrokerMetadata> brokers = new ArrayList<>(brokerCount);
        for (int i = 0; i < brokerCount; i++) {
            int nodeId = buf.getInt();
            String host = readString(buf);
            int port = buf.getInt();
            brokers.add(new MetadataResponse.BrokerMetadata(nodeId, host, port));
        }
        int topicCount = buf.getInt();
        List<MetadataResponse.TopicMetadata> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            short errorCode = buf.getShort();
            String name = readString(buf);
            int partCount = buf.getInt();
            List<MetadataResponse.PartitionMetadata> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                short pError = buf.getShort();
                int pIdx = buf.getInt();
                int leader = buf.getInt();
                int repCount = buf.getInt();
                List<Integer> replicas = new ArrayList<>(repCount);
                for (int k = 0; k < repCount; k++) replicas.add(buf.getInt());
                int isrCount = buf.getInt();
                List<Integer> isrs = new ArrayList<>(isrCount);
                for (int k = 0; k < isrCount; k++) isrs.add(buf.getInt());
                partitions.add(new MetadataResponse.PartitionMetadata(pError, pIdx, leader, replicas, isrs));
            }
            topics.add(new MetadataResponse.TopicMetadata(errorCode, name, partitions));
        }
        return new MetadataResponse(brokers, topics);
    }

    // ===== Produce (0) =====

    public static byte[] encodeProduceRequest(ProduceRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(65536);
        writeNullableString(buf, req.transactionalId());
        buf.putShort(req.acks());
        buf.putInt(req.timeoutMs());
        buf.putInt(req.topicData().size());
        for (var td : req.topicData()) {
            writeString(buf, td.name());
            buf.putInt(td.partitionData().size());
            for (var pd : td.partitionData()) {
                buf.putInt(pd.index());
                buf.putInt(pd.records() != null ? pd.records().length : -1);
                if (pd.records() != null) buf.put(pd.records());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static ProduceRequest decodeProduceRequest(ByteBuffer buf) {
        String txnId = readNullableString(buf);
        short acks = buf.getShort();
        int timeout = buf.getInt();
        int topicCount = buf.getInt();
        List<ProduceRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<ProduceRequest.PartitionData> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int idx = buf.getInt();
                int recLen = buf.getInt();
                byte[] records = null;
                if (recLen >= 0) {
                    records = new byte[recLen];
                    buf.get(records);
                }
                partitions.add(new ProduceRequest.PartitionData(idx, records));
            }
            topics.add(new ProduceRequest.TopicData(name, partitions));
        }
        return new ProduceRequest(txnId, acks, timeout, topics);
    }

    public static byte[] encodeProduceResponse(ProduceResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        buf.putInt(resp.responses().size());
        for (var tr : resp.responses()) {
            writeString(buf, tr.name());
            buf.putInt(tr.partitionResponses().size());
            for (var pr : tr.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                buf.putLong(pr.logAppendTimeMs());
            }
        }
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return toBytes(buf);
    }

    public static ProduceResponse decodeProduceResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new ProduceResponse.PartitionResponse(
                        buf.getInt(), buf.getShort(), buf.getLong(), buf.getLong()));
            }
            topics.add(new ProduceResponse.TopicResponse(name, parts));
        }
        int throttle = buf.getInt();
        return new ProduceResponse(topics, throttle);
    }

    // ===== Fetch (1) =====

    public static byte[] encodeFetchRequest(FetchRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(65536);
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes());
        buf.putInt(req.topics().size());
        for (var tf : req.topics()) {
            writeString(buf, tf.name());
            buf.putInt(tf.partitions().size());
            for (var pf : tf.partitions()) {
                buf.putInt(pf.partition());
                buf.putLong(pf.fetchOffset());
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static FetchRequest decodeFetchRequest(ByteBuffer buf) {
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt();
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new FetchRequest.PartitionFetch(buf.getInt(), buf.getLong(), buf.getInt()));
            }
            topics.add(new FetchRequest.TopicFetch(name, parts));
        }
        return new FetchRequest(maxWait, minBytes, maxBytes, topics);
    }

    public static byte[] encodeFetchResponse(FetchResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(65536);
        buf.putInt(resp.throttleTimeMs());
        buf.putInt(resp.topics().size());
        for (var tr : resp.topics()) {
            writeString(buf, tr.name());
            buf.putInt(tr.partitions().size());
            for (var pr : tr.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                buf.putInt(pr.records() != null ? pr.records().length : -1);
                if (pr.records() != null) buf.put(pr.records());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static FetchResponse decodeFetchResponse(ByteBuffer buf) {
        int throttle = buf.getInt();
        int topicCount = buf.getInt();
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<FetchResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int pIdx = buf.getInt();
                short err = buf.getShort();
                long hw = buf.getLong();
                int recLen = buf.getInt();
                byte[] records = null;
                if (recLen >= 0) {
                    records = new byte[recLen];
                    buf.get(records);
                }
                parts.add(new FetchResponse.PartitionResponse(pIdx, err, hw, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, parts));
        }
        return new FetchResponse(throttle, topics);
    }

    // ===== ListOffsets (2) =====

    public static byte[] encodeListOffsetsRequest(ListOffsetsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.timestamp());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static ListOffsetsRequest decodeListOffsetsRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ListOffsetsRequest.TopicOffsets> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<ListOffsetsRequest.PartitionOffsets> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new ListOffsetsRequest.PartitionOffsets(buf.getInt(), buf.getLong()));
            }
            topics.add(new ListOffsetsRequest.TopicOffsets(name, parts));
        }
        return new ListOffsetsRequest(topics);
    }

    public static byte[] encodeListOffsetsResponse(ListOffsetsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
                buf.putLong(p.timestamp());
                buf.putLong(p.offset());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static ListOffsetsResponse decodeListOffsetsResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ListOffsetsResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<ListOffsetsResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new ListOffsetsResponse.PartitionResponse(
                        buf.getInt(), buf.getShort(), buf.getLong(), buf.getLong()));
            }
            topics.add(new ListOffsetsResponse.TopicResponse(name, parts));
        }
        return new ListOffsetsResponse(topics);
    }

    // ===== FindCoordinator (10) =====

    public static byte[] encodeFindCoordinatorRequest(FindCoordinatorRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.key());
        buf.put(req.keyType());
        buf.flip();
        return toBytes(buf);
    }

    public static FindCoordinatorRequest decodeFindCoordinatorRequest(ByteBuffer buf) {
        String key = readString(buf);
        byte keyType = buf.hasRemaining() ? buf.get() : FindCoordinatorRequest.KEY_TYPE_GROUP;
        return new FindCoordinatorRequest(key, keyType);
    }

    public static byte[] encodeFindCoordinatorResponse(FindCoordinatorResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.nodeId());
        writeString(buf, resp.host());
        buf.putInt(resp.port());
        buf.flip();
        return toBytes(buf);
    }

    public static FindCoordinatorResponse decodeFindCoordinatorResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int nodeId = buf.getInt();
        String host = readString(buf);
        int port = buf.getInt();
        return new FindCoordinatorResponse(errorCode, nodeId, host, port);
    }

    // ===== JoinGroup (11) =====

    public static byte[] encodeJoinGroupRequest(JoinGroupRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.groupId());
        buf.putInt(req.sessionTimeoutMs());
        buf.putInt(req.rebalanceTimeoutMs());
        writeString(buf, req.memberId());
        writeString(buf, req.protocolType());
        buf.putInt(req.protocols().size());
        for (var p : req.protocols()) {
            writeString(buf, p.name());
            buf.putInt(p.metadata().length);
            buf.put(p.metadata());
        }
        buf.flip();
        return toBytes(buf);
    }

    public static JoinGroupRequest decodeJoinGroupRequest(ByteBuffer buf) {
        String groupId = readString(buf);
        int sessionTimeout = buf.getInt();
        int rebalanceTimeout = buf.getInt();
        String memberId = readString(buf);
        String protocolType = readString(buf);
        int protocolCount = buf.getInt();
        List<JoinGroupRequest.Protocol> protocols = new ArrayList<>(protocolCount);
        for (int i = 0; i < protocolCount; i++) {
            String name = readString(buf);
            int metaLen = buf.getInt();
            byte[] metadata = new byte[metaLen];
            buf.get(metadata);
            protocols.add(new JoinGroupRequest.Protocol(name, metadata));
        }
        return new JoinGroupRequest(groupId, sessionTimeout, rebalanceTimeout, memberId,
                protocolType, protocols);
    }

    public static byte[] encodeJoinGroupResponse(JoinGroupResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.generationId());
        writeString(buf, resp.protocolName());
        writeString(buf, resp.leader());
        writeString(buf, resp.memberId());
        buf.putInt(resp.members().size());
        for (var m : resp.members()) {
            writeString(buf, m.memberId());
            buf.putInt(m.metadata().length);
            buf.put(m.metadata());
        }
        buf.flip();
        return toBytes(buf);
    }

    public static JoinGroupResponse decodeJoinGroupResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int generationId = buf.getInt();
        String protocolName = readString(buf);
        String leader = readString(buf);
        String memberId = readString(buf);
        int memberCount = buf.getInt();
        List<JoinGroupResponse.Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            String mId = readString(buf);
            int metaLen = buf.getInt();
            byte[] metadata = new byte[metaLen];
            buf.get(metadata);
            members.add(new JoinGroupResponse.Member(mId, metadata));
        }
        return new JoinGroupResponse(errorCode, generationId, protocolName, leader, memberId, members);
    }

    // ===== SyncGroup (14) =====

    public static byte[] encodeSyncGroupRequest(SyncGroupRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        writeString(buf, req.groupId());
        buf.putInt(req.generationId());
        writeString(buf, req.memberId());
        buf.putInt(req.assignments().size());
        for (var a : req.assignments()) {
            writeString(buf, a.memberId());
            buf.putInt(a.assignment().length);
            buf.put(a.assignment());
        }
        buf.flip();
        return toBytes(buf);
    }

    public static SyncGroupRequest decodeSyncGroupRequest(ByteBuffer buf) {
        String groupId = readString(buf);
        int generationId = buf.getInt();
        String memberId = readString(buf);
        int assignCount = buf.getInt();
        List<SyncGroupRequest.Assignment> assignments = new ArrayList<>(assignCount);
        for (int i = 0; i < assignCount; i++) {
            String mId = readString(buf);
            int aLen = buf.getInt();
            byte[] assignment = new byte[aLen];
            buf.get(assignment);
            assignments.add(new SyncGroupRequest.Assignment(mId, assignment));
        }
        return new SyncGroupRequest(groupId, generationId, memberId, assignments);
    }

    public static byte[] encodeSyncGroupResponse(SyncGroupResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.assignment() != null ? resp.assignment().length : 0);
        if (resp.assignment() != null) buf.put(resp.assignment());
        buf.flip();
        return toBytes(buf);
    }

    public static SyncGroupResponse decodeSyncGroupResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int aLen = buf.getInt();
        byte[] assignment = new byte[aLen];
        if (aLen > 0) buf.get(assignment);
        return new SyncGroupResponse(errorCode, assignment);
    }

    // ===== Heartbeat (12) =====

    public static byte[] encodeHeartbeatRequest(HeartbeatRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.groupId());
        buf.putInt(req.generationId());
        writeString(buf, req.memberId());
        buf.flip();
        return toBytes(buf);
    }

    public static HeartbeatRequest decodeHeartbeatRequest(ByteBuffer buf) {
        return new HeartbeatRequest(readString(buf), buf.getInt(), readString(buf));
    }

    public static byte[] encodeHeartbeatResponse(HeartbeatResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2);
        buf.putShort(resp.errorCode());
        buf.flip();
        return toBytes(buf);
    }

    public static HeartbeatResponse decodeHeartbeatResponse(ByteBuffer buf) {
        return new HeartbeatResponse(buf.getShort());
    }

    // ===== LeaveGroup (13) =====

    public static byte[] encodeLeaveGroupRequest(LeaveGroupRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.groupId());
        writeString(buf, req.memberId());
        buf.flip();
        return toBytes(buf);
    }

    public static LeaveGroupRequest decodeLeaveGroupRequest(ByteBuffer buf) {
        return new LeaveGroupRequest(readString(buf), readString(buf));
    }

    public static byte[] encodeLeaveGroupResponse(LeaveGroupResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2);
        buf.putShort(resp.errorCode());
        buf.flip();
        return toBytes(buf);
    }

    public static LeaveGroupResponse decodeLeaveGroupResponse(ByteBuffer buf) {
        return new LeaveGroupResponse(buf.getShort());
    }

    // ===== OffsetCommit (8) =====

    public static byte[] encodeOffsetCommitRequest(OffsetCommitRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.groupId());
        buf.putInt(req.generationId());
        writeString(buf, req.memberId());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.committedOffset());
                writeNullableString(buf, p.metadata());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static OffsetCommitRequest decodeOffsetCommitRequest(ByteBuffer buf) {
        String groupId = readString(buf);
        int generationId = buf.getInt();
        String memberId = readString(buf);
        int topicCount = buf.getInt();
        List<OffsetCommitRequest.TopicOffsets> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<OffsetCommitRequest.PartitionOffset> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetCommitRequest.PartitionOffset(
                        buf.getInt(), buf.getLong(), readNullableString(buf)));
            }
            topics.add(new OffsetCommitRequest.TopicOffsets(name, parts));
        }
        return new OffsetCommitRequest(groupId, generationId, memberId, topics);
    }

    public static byte[] encodeOffsetCommitResponse(OffsetCommitResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static OffsetCommitResponse decodeOffsetCommitResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<OffsetCommitResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<OffsetCommitResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetCommitResponse.PartitionResponse(buf.getInt(), buf.getShort()));
            }
            topics.add(new OffsetCommitResponse.TopicResponse(name, parts));
        }
        return new OffsetCommitResponse(topics);
    }

    // ===== OffsetFetch (9) =====

    public static byte[] encodeOffsetFetchRequest(OffsetFetchRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.groupId());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitionIndexes().size());
            for (int idx : t.partitionIndexes()) {
                buf.putInt(idx);
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static OffsetFetchRequest decodeOffsetFetchRequest(ByteBuffer buf) {
        String groupId = readString(buf);
        int topicCount = buf.getInt();
        List<OffsetFetchRequest.TopicPartitions> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<Integer> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) parts.add(buf.getInt());
            topics.add(new OffsetFetchRequest.TopicPartitions(name, parts));
        }
        return new OffsetFetchRequest(groupId, topics);
    }

    public static byte[] encodeOffsetFetchResponse(OffsetFetchResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.committedOffset());
                writeNullableString(buf, p.metadata());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static OffsetFetchResponse decodeOffsetFetchResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<OffsetFetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<OffsetFetchResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetFetchResponse.PartitionResponse(
                        buf.getInt(), buf.getLong(), readNullableString(buf), buf.getShort()));
            }
            topics.add(new OffsetFetchResponse.TopicResponse(name, parts));
        }
        return new OffsetFetchResponse(topics);
    }

    // ===== CreateTopics (19) =====

    public static byte[] encodeCreateTopicsRequest(CreateTopicsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.numPartitions());
            buf.putShort(t.replicationFactor());
            // Configs
            buf.putInt(t.configs() != null ? t.configs().size() : 0);
            if (t.configs() != null) {
                for (var e : t.configs().entrySet()) {
                    writeString(buf, e.getKey());
                    writeNullableString(buf, e.getValue());
                }
            }
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return toBytes(buf);
    }

    public static CreateTopicsRequest decodeCreateTopicsRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<CreateTopicsRequest.TopicCreate> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int numParts = buf.getInt();
            short repFactor = buf.getShort();
            int configCount = buf.getInt();
            Map<String, String> configs = new LinkedHashMap<>();
            for (int j = 0; j < configCount; j++) {
                configs.put(readString(buf), readNullableString(buf));
            }
            topics.add(new CreateTopicsRequest.TopicCreate(name, numParts, repFactor, configs));
        }
        int timeout = buf.getInt();
        return new CreateTopicsRequest(topics, timeout);
    }

    public static byte[] encodeCreateTopicsResponse(CreateTopicsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putShort(t.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    public static CreateTopicsResponse decodeCreateTopicsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<CreateTopicsResponse.TopicResult> topics = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            topics.add(new CreateTopicsResponse.TopicResult(readString(buf), buf.getShort()));
        }
        return new CreateTopicsResponse(topics);
    }

    // ===== DeleteTopics (20) =====

    public static byte[] encodeDeleteTopicsRequest(DeleteTopicsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topicNames().size());
        for (String name : req.topicNames()) {
            writeString(buf, name);
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return toBytes(buf);
    }

    public static DeleteTopicsRequest decodeDeleteTopicsRequest(ByteBuffer buf) {
        int count = buf.getInt();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) names.add(readString(buf));
        int timeout = buf.getInt();
        return new DeleteTopicsRequest(names, timeout);
    }

    public static byte[] encodeDeleteTopicsResponse(DeleteTopicsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.responses().size());
        for (var t : resp.responses()) {
            writeString(buf, t.name());
            buf.putShort(t.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    public static DeleteTopicsResponse decodeDeleteTopicsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<DeleteTopicsResponse.TopicResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new DeleteTopicsResponse.TopicResult(readString(buf), buf.getShort()));
        }
        return new DeleteTopicsResponse(results);
    }

    // ===== DescribeGroups (15) =====

    public static byte[] encodeDescribeGroupsRequest(DescribeGroupsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.groups().size());
        for (String g : req.groups()) writeString(buf, g);
        buf.flip();
        return toBytes(buf);
    }

    public static DescribeGroupsRequest decodeDescribeGroupsRequest(ByteBuffer buf) {
        int count = buf.getInt();
        List<String> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) groups.add(readString(buf));
        return new DescribeGroupsRequest(groups);
    }

    public static byte[] encodeDescribeGroupsResponse(DescribeGroupsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        buf.putInt(resp.groups().size());
        for (var g : resp.groups()) {
            buf.putShort(g.errorCode());
            writeString(buf, g.groupId());
            writeString(buf, g.state());
            writeString(buf, g.protocolType());
            writeString(buf, g.protocol());
            buf.putInt(g.members().size());
            for (var m : g.members()) {
                writeString(buf, m.memberId());
                writeString(buf, m.clientId());
                writeString(buf, m.clientHost());
                buf.putInt(m.memberMetadata() != null ? m.memberMetadata().length : 0);
                if (m.memberMetadata() != null) buf.put(m.memberMetadata());
                buf.putInt(m.memberAssignment() != null ? m.memberAssignment().length : 0);
                if (m.memberAssignment() != null) buf.put(m.memberAssignment());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static DescribeGroupsResponse decodeDescribeGroupsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<DescribeGroupsResponse.GroupDescription> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            short errorCode = buf.getShort();
            String groupId = readString(buf);
            String state = readString(buf);
            String protocolType = readString(buf);
            String protocol = readString(buf);
            int memberCount = buf.getInt();
            List<DescribeGroupsResponse.MemberDescription> members = new ArrayList<>(memberCount);
            for (int j = 0; j < memberCount; j++) {
                String mId = readString(buf);
                String cId = readString(buf);
                String cHost = readString(buf);
                int metaLen = buf.getInt();
                byte[] metadata = new byte[metaLen];
                if (metaLen > 0) buf.get(metadata);
                int assignLen = buf.getInt();
                byte[] assignment = new byte[assignLen];
                if (assignLen > 0) buf.get(assignment);
                members.add(new DescribeGroupsResponse.MemberDescription(mId, cId, cHost, metadata, assignment));
            }
            groups.add(new DescribeGroupsResponse.GroupDescription(errorCode, groupId, state,
                    protocolType, protocol, members));
        }
        return new DescribeGroupsResponse(groups);
    }

    // ===== InitProducerId (22) =====

    public static byte[] encodeInitProducerIdRequest(InitProducerIdRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeNullableString(buf, req.transactionalId());
        buf.putInt(req.transactionTimeoutMs());
        buf.flip();
        return toBytes(buf);
    }

    public static InitProducerIdRequest decodeInitProducerIdRequest(ByteBuffer buf) {
        return new InitProducerIdRequest(readNullableString(buf), buf.getInt());
    }

    public static byte[] encodeInitProducerIdResponse(InitProducerIdResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(12);
        buf.putShort(resp.errorCode());
        buf.putLong(resp.producerId());
        buf.putShort(resp.producerEpoch());
        buf.flip();
        return toBytes(buf);
    }

    public static InitProducerIdResponse decodeInitProducerIdResponse(ByteBuffer buf) {
        return new InitProducerIdResponse(buf.getShort(), buf.getLong(), buf.getShort());
    }

    // ===== AddPartitionsToTxn (24) =====

    public static byte[] encodeAddPartitionsToTxnRequest(AddPartitionsToTxnRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.transactionalId());
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitionIndexes().size());
            for (int idx : t.partitionIndexes()) buf.putInt(idx);
        }
        buf.flip();
        return toBytes(buf);
    }

    public static AddPartitionsToTxnRequest decodeAddPartitionsToTxnRequest(ByteBuffer buf) {
        String txnId = readString(buf);
        long producerId = buf.getLong();
        short epoch = buf.getShort();
        int topicCount = buf.getInt();
        List<AddPartitionsToTxnRequest.TopicPartitions> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<Integer> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) parts.add(buf.getInt());
            topics.add(new AddPartitionsToTxnRequest.TopicPartitions(name, parts));
        }
        return new AddPartitionsToTxnRequest(txnId, producerId, epoch, topics);
    }

    public static byte[] encodeAddPartitionsToTxnResponse(AddPartitionsToTxnResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    public static AddPartitionsToTxnResponse decodeAddPartitionsToTxnResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<AddPartitionsToTxnResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<AddPartitionsToTxnResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new AddPartitionsToTxnResponse.PartitionResponse(buf.getInt(), buf.getShort()));
            }
            topics.add(new AddPartitionsToTxnResponse.TopicResponse(name, parts));
        }
        return new AddPartitionsToTxnResponse(topics);
    }

    // ===== ListGroups (16) =====

    /**
     * Encodes a ListGroups request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeListGroupsRequest(ListGroupsRequest req) {
        return new byte[0];
    }

    /**
     * Decodes a ListGroups request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ListGroupsRequest decodeListGroupsRequest(ByteBuffer buf) {
        return new ListGroupsRequest();
    }

    /**
     * Encodes a ListGroups response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeListGroupsResponse(ListGroupsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.groups().size());
        for (var g : resp.groups()) {
            writeString(buf, g.groupId());
            writeString(buf, g.protocolType());
            writeString(buf, g.state());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ListGroups response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ListGroupsResponse decodeListGroupsResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ListGroupsResponse.GroupListing> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            groups.add(new ListGroupsResponse.GroupListing(readString(buf), readString(buf), readString(buf)));
        }
        return new ListGroupsResponse(errorCode, groups);
    }

    // ===== DeleteRecords (21) =====

    /**
     * Encodes a DeleteRecords request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteRecordsRequest(DeleteRecordsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.offset());
            }
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DeleteRecords request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DeleteRecordsRequest decodeDeleteRecordsRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<DeleteRecordsRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<DeleteRecordsRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsRequest.PartitionData(buf.getInt(), buf.getLong()));
            }
            topics.add(new DeleteRecordsRequest.TopicData(name, parts));
        }
        int timeout = buf.getInt();
        return new DeleteRecordsRequest(topics, timeout);
    }

    /**
     * Encodes a DeleteRecords response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteRecordsResponse(DeleteRecordsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.lowWatermark());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DeleteRecords response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DeleteRecordsResponse decodeDeleteRecordsResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<DeleteRecordsResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<DeleteRecordsResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsResponse.PartitionData(buf.getInt(), buf.getLong(), buf.getShort()));
            }
            topics.add(new DeleteRecordsResponse.TopicData(name, parts));
        }
        return new DeleteRecordsResponse(topics);
    }

    // ===== CreatePartitions (37) =====

    /**
     * Encodes a CreatePartitions request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeCreatePartitionsRequest(CreatePartitionsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.newCount());
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a CreatePartitions request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static CreatePartitionsRequest decodeCreatePartitionsRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<CreatePartitionsRequest.TopicNewPartitions> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            topics.add(new CreatePartitionsRequest.TopicNewPartitions(readString(buf), buf.getInt()));
        }
        int timeout = buf.getInt();
        return new CreatePartitionsRequest(topics, timeout);
    }

    /**
     * Encodes a CreatePartitions response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeCreatePartitionsResponse(CreatePartitionsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.results().size());
        for (var t : resp.results()) {
            writeString(buf, t.name());
            buf.putShort(t.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a CreatePartitions response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static CreatePartitionsResponse decodeCreatePartitionsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<CreatePartitionsResponse.TopicResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new CreatePartitionsResponse.TopicResult(readString(buf), buf.getShort()));
        }
        return new CreatePartitionsResponse(results);
    }

    // ===== DeleteGroups (42) =====

    /**
     * Encodes a DeleteGroups request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteGroupsRequest(DeleteGroupsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.groups().size());
        for (String g : req.groups()) writeString(buf, g);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DeleteGroups request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DeleteGroupsRequest decodeDeleteGroupsRequest(ByteBuffer buf) {
        int count = buf.getInt();
        List<String> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) groups.add(readString(buf));
        return new DeleteGroupsRequest(groups);
    }

    /**
     * Encodes a DeleteGroups response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteGroupsResponse(DeleteGroupsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.results().size());
        for (var r : resp.results()) {
            writeString(buf, r.groupId());
            buf.putShort(r.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DeleteGroups response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DeleteGroupsResponse decodeDeleteGroupsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<DeleteGroupsResponse.GroupResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new DeleteGroupsResponse.GroupResult(readString(buf), buf.getShort()));
        }
        return new DeleteGroupsResponse(results);
    }

    // ===== OffsetDelete (47) =====

    /**
     * Encodes an OffsetDelete request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetDeleteRequest(OffsetDeleteRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.groupId());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetDelete request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static OffsetDeleteRequest decodeOffsetDeleteRequest(ByteBuffer buf) {
        String groupId = readString(buf);
        int topicCount = buf.getInt();
        List<OffsetDeleteRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<OffsetDeleteRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetDeleteRequest.PartitionData(buf.getInt()));
            }
            topics.add(new OffsetDeleteRequest.TopicData(name, parts));
        }
        return new OffsetDeleteRequest(groupId, topics);
    }

    /**
     * Encodes an OffsetDelete response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetDeleteResponse(OffsetDeleteResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetDelete response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static OffsetDeleteResponse decodeOffsetDeleteResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int topicCount = buf.getInt();
        List<OffsetDeleteResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<OffsetDeleteResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetDeleteResponse.PartitionData(buf.getInt(), buf.getShort()));
            }
            topics.add(new OffsetDeleteResponse.TopicData(name, parts));
        }
        return new OffsetDeleteResponse(errorCode, topics);
    }

    // ===== DescribeConfigs (32) =====

    /**
     * Encodes a DescribeConfigs request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeConfigsRequest(DescribeConfigsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putInt(req.resources().size());
        for (var r : req.resources()) {
            buf.put(r.resourceType());
            writeString(buf, r.resourceName());
            if (r.configNames() == null) {
                buf.putInt(-1);
            } else {
                buf.putInt(r.configNames().size());
                for (String name : r.configNames()) {
                    writeString(buf, name);
                }
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DescribeConfigs request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DescribeConfigsRequest decodeDescribeConfigsRequest(ByteBuffer buf) {
        int count = buf.getInt();
        List<DescribeConfigsRequest.ResourceRequest> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte resourceType = buf.get();
            String resourceName = readString(buf);
            int nameCount = buf.getInt();
            List<String> configNames = null;
            if (nameCount >= 0) {
                configNames = new ArrayList<>(nameCount);
                for (int j = 0; j < nameCount; j++) {
                    configNames.add(readString(buf));
                }
            }
            resources.add(new DescribeConfigsRequest.ResourceRequest(resourceType, resourceName, configNames));
        }
        return new DescribeConfigsRequest(resources);
    }

    /**
     * Encodes a DescribeConfigs response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeConfigsResponse(DescribeConfigsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        buf.putInt(resp.resources().size());
        for (var r : resp.resources()) {
            buf.putShort(r.errorCode());
            writeString(buf, r.resourceName());
            buf.putInt(r.configs().size());
            for (var c : r.configs()) {
                writeString(buf, c.name());
                writeNullableString(buf, c.value());
                buf.put((byte) (c.readOnly() ? 1 : 0));
                buf.put((byte) (c.isSensitive() ? 1 : 0));
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a DescribeConfigs response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DescribeConfigsResponse decodeDescribeConfigsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<DescribeConfigsResponse.ResourceResponse> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            short errorCode = buf.getShort();
            String resourceName = readString(buf);
            int configCount = buf.getInt();
            List<DescribeConfigsResponse.ConfigEntry> configs = new ArrayList<>(configCount);
            for (int j = 0; j < configCount; j++) {
                String name = readString(buf);
                String value = readNullableString(buf);
                boolean readOnly = buf.get() == 1;
                boolean isSensitive = buf.get() == 1;
                configs.add(new DescribeConfigsResponse.ConfigEntry(name, value, readOnly, isSensitive));
            }
            resources.add(new DescribeConfigsResponse.ResourceResponse(errorCode, resourceName, configs));
        }
        return new DescribeConfigsResponse(resources);
    }

    // ===== AlterConfigs (33) =====

    /**
     * Encodes an AlterConfigs request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAlterConfigsRequest(AlterConfigsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putInt(req.resources().size());
        for (var r : req.resources()) {
            buf.put(r.resourceType());
            writeString(buf, r.resourceName());
            buf.putInt(r.configs().size());
            for (var c : r.configs()) {
                writeString(buf, c.name());
                writeNullableString(buf, c.value());
            }
        }
        buf.put((byte) (req.validateOnly() ? 1 : 0));
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AlterConfigs request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AlterConfigsRequest decodeAlterConfigsRequest(ByteBuffer buf) {
        int count = buf.getInt();
        List<AlterConfigsRequest.ResourceConfig> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte resourceType = buf.get();
            String resourceName = readString(buf);
            int configCount = buf.getInt();
            List<AlterConfigsRequest.ConfigEntry> configs = new ArrayList<>(configCount);
            for (int j = 0; j < configCount; j++) {
                configs.add(new AlterConfigsRequest.ConfigEntry(readString(buf), readNullableString(buf)));
            }
            resources.add(new AlterConfigsRequest.ResourceConfig(resourceType, resourceName, configs));
        }
        boolean validateOnly = buf.get() == 1;
        return new AlterConfigsRequest(resources, validateOnly);
    }

    /**
     * Encodes an AlterConfigs response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAlterConfigsResponse(AlterConfigsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.resources().size());
        for (var r : resp.resources()) {
            buf.putShort(r.errorCode());
            writeString(buf, r.resourceName());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AlterConfigs response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AlterConfigsResponse decodeAlterConfigsResponse(ByteBuffer buf) {
        int count = buf.getInt();
        List<AlterConfigsResponse.ResourceResponse> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            resources.add(new AlterConfigsResponse.ResourceResponse(buf.getShort(), readString(buf)));
        }
        return new AlterConfigsResponse(resources);
    }

    // ===== AddOffsetsToTxn (25) =====

    /**
     * Encodes an AddOffsetsToTxn request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAddOffsetsToTxnRequest(AddOffsetsToTxnRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.transactionalId());
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        writeString(buf, req.groupId());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AddOffsetsToTxn request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AddOffsetsToTxnRequest decodeAddOffsetsToTxnRequest(ByteBuffer buf) {
        String txnId = readString(buf);
        long producerId = buf.getLong();
        short epoch = buf.getShort();
        String groupId = readString(buf);
        return new AddOffsetsToTxnRequest(txnId, producerId, epoch, groupId);
    }

    /**
     * Encodes an AddOffsetsToTxn response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAddOffsetsToTxnResponse(AddOffsetsToTxnResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2);
        buf.putShort(resp.errorCode());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AddOffsetsToTxn response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AddOffsetsToTxnResponse decodeAddOffsetsToTxnResponse(ByteBuffer buf) {
        return new AddOffsetsToTxnResponse(buf.getShort());
    }

    // ===== TxnOffsetCommit (28) =====

    /**
     * Encodes a TxnOffsetCommit request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeTxnOffsetCommitRequest(TxnOffsetCommitRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        writeString(buf, req.transactionalId());
        writeString(buf, req.groupId());
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.committedOffset());
                writeNullableString(buf, p.metadata());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a TxnOffsetCommit request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static TxnOffsetCommitRequest decodeTxnOffsetCommitRequest(ByteBuffer buf) {
        String txnId = readString(buf);
        String groupId = readString(buf);
        long producerId = buf.getLong();
        short epoch = buf.getShort();
        int topicCount = buf.getInt();
        List<TxnOffsetCommitRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<TxnOffsetCommitRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new TxnOffsetCommitRequest.PartitionData(
                        buf.getInt(), buf.getLong(), readNullableString(buf)));
            }
            topics.add(new TxnOffsetCommitRequest.TopicData(name, parts));
        }
        return new TxnOffsetCommitRequest(txnId, groupId, producerId, epoch, topics);
    }

    /**
     * Encodes a TxnOffsetCommit response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeTxnOffsetCommitResponse(TxnOffsetCommitResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a TxnOffsetCommit response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static TxnOffsetCommitResponse decodeTxnOffsetCommitResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<TxnOffsetCommitResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = readString(buf);
            int partCount = buf.getInt();
            List<TxnOffsetCommitResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new TxnOffsetCommitResponse.PartitionData(buf.getInt(), buf.getShort()));
            }
            topics.add(new TxnOffsetCommitResponse.TopicData(name, parts));
        }
        return new TxnOffsetCommitResponse(topics);
    }

    // ===== EndTxn (26) =====

    public static byte[] encodeEndTxnRequest(EndTxnRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.transactionalId());
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        buf.put((byte) (req.committed() ? 1 : 0));
        buf.flip();
        return toBytes(buf);
    }

    public static EndTxnRequest decodeEndTxnRequest(ByteBuffer buf) {
        return new EndTxnRequest(readString(buf), buf.getLong(), buf.getShort(), buf.get() == 1);
    }

    public static byte[] encodeEndTxnResponse(EndTxnResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2);
        buf.putShort(resp.errorCode());
        buf.flip();
        return toBytes(buf);
    }

    public static EndTxnResponse decodeEndTxnResponse(ByteBuffer buf) {
        return new EndTxnResponse(buf.getShort());
    }

    // ===== SaslHandshake (17) =====

    /**
     * Encodes a SaslHandshake request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeSaslHandshakeRequest(SaslHandshakeRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        writeString(buf, req.mechanism());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a SaslHandshake request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static SaslHandshakeRequest decodeSaslHandshakeRequest(ByteBuffer buf) {
        return new SaslHandshakeRequest(readString(buf));
    }

    /**
     * Encodes a SaslHandshake response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeSaslHandshakeResponse(SaslHandshakeResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.mechanisms().size());
        for (String m : resp.mechanisms()) {
            writeString(buf, m);
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a SaslHandshake response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static SaslHandshakeResponse decodeSaslHandshakeResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<String> mechanisms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            mechanisms.add(readString(buf));
        }
        return new SaslHandshakeResponse(errorCode, mechanisms);
    }

    // ===== SaslAuthenticate (36) =====

    /**
     * Encodes a SaslAuthenticate request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeSaslAuthenticateRequest(SaslAuthenticateRequest req) {
        byte[] authBytes = req.authBytes() != null ? req.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(4 + authBytes.length);
        buf.putInt(authBytes.length);
        buf.put(authBytes);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a SaslAuthenticate request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static SaslAuthenticateRequest decodeSaslAuthenticateRequest(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] authBytes = new byte[len];
        if (len > 0) buf.get(authBytes);
        return new SaslAuthenticateRequest(authBytes);
    }

    /**
     * Encodes a SaslAuthenticate response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeSaslAuthenticateResponse(SaslAuthenticateResponse resp) {
        byte[] authBytes = resp.authBytes() != null ? resp.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + authBytes.length + 8);
        buf.putShort(resp.errorCode());
        buf.putInt(authBytes.length);
        buf.put(authBytes);
        buf.putLong(resp.sessionLifetimeMs());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a SaslAuthenticate response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static SaslAuthenticateResponse decodeSaslAuthenticateResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int len = buf.getInt();
        byte[] authBytes = new byte[len];
        if (len > 0) buf.get(authBytes);
        long sessionLifetimeMs = buf.getLong();
        return new SaslAuthenticateResponse(errorCode, authBytes, sessionLifetimeMs);
    }

    // ===== LeaderAndIsr (4) =====

    /**
     * Encodes a LeaderAndIsr request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeLeaderAndIsrRequest(LeaderAndIsrRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        buf.putInt(req.controllerId());
        buf.putInt(req.controllerEpoch());
        buf.putInt(req.partitionStates().size());
        for (var ps : req.partitionStates()) {
            writeString(buf, ps.topic());
            buf.putInt(ps.partition());
            buf.putInt(ps.leader());
            buf.putInt(ps.leaderEpoch());
            buf.putInt(ps.isr().size());
            for (int r : ps.isr()) buf.putInt(r);
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a LeaderAndIsr request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static LeaderAndIsrRequest decodeLeaderAndIsrRequest(ByteBuffer buf) {
        int controllerId = buf.getInt();
        int controllerEpoch = buf.getInt();
        int count = buf.getInt();
        List<LeaderAndIsrRequest.PartitionState> states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String topic = readString(buf);
            int partition = buf.getInt();
            int leader = buf.getInt();
            int leaderEpoch = buf.getInt();
            int isrCount = buf.getInt();
            List<Integer> isr = new ArrayList<>(isrCount);
            for (int j = 0; j < isrCount; j++) isr.add(buf.getInt());
            states.add(new LeaderAndIsrRequest.PartitionState(topic, partition, leader, leaderEpoch, isr));
        }
        return new LeaderAndIsrRequest(controllerId, controllerEpoch, states);
    }

    /**
     * Encodes a LeaderAndIsr response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeLeaderAndIsrResponse(LeaderAndIsrResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.partitions().size());
        for (var pr : resp.partitions()) {
            writeString(buf, pr.topic());
            buf.putInt(pr.partition());
            buf.putShort(pr.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a LeaderAndIsr response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static LeaderAndIsrResponse decodeLeaderAndIsrResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<LeaderAndIsrResponse.PartitionResult> partitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            partitions.add(new LeaderAndIsrResponse.PartitionResult(readString(buf), buf.getInt(), buf.getShort()));
        }
        return new LeaderAndIsrResponse(errorCode, partitions);
    }

    // ===== StopReplica (5) =====

    /**
     * Encodes a StopReplica request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeStopReplicaRequest(StopReplicaRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.controllerId());
        buf.putInt(req.controllerEpoch());
        buf.put((byte) (req.deletePartitions() ? 1 : 0));
        buf.putInt(req.partitions().size());
        for (var p : req.partitions()) {
            writeString(buf, p.topic());
            buf.putInt(p.partition());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a StopReplica request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static StopReplicaRequest decodeStopReplicaRequest(ByteBuffer buf) {
        int controllerId = buf.getInt();
        int controllerEpoch = buf.getInt();
        boolean deletePartitions = buf.get() == 1;
        int count = buf.getInt();
        List<StopReplicaRequest.TopicPartitionData> partitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            partitions.add(new StopReplicaRequest.TopicPartitionData(readString(buf), buf.getInt()));
        }
        return new StopReplicaRequest(controllerId, controllerEpoch, deletePartitions, partitions);
    }

    /**
     * Encodes a StopReplica response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeStopReplicaResponse(StopReplicaResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.partitions().size());
        for (var pr : resp.partitions()) {
            writeString(buf, pr.topic());
            buf.putInt(pr.partition());
            buf.putShort(pr.errorCode());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a StopReplica response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static StopReplicaResponse decodeStopReplicaResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<StopReplicaResponse.PartitionResult> partitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            partitions.add(new StopReplicaResponse.PartitionResult(readString(buf), buf.getInt(), buf.getShort()));
        }
        return new StopReplicaResponse(errorCode, partitions);
    }

    // ===== UpdateMetadata (6) =====

    /**
     * Encodes an UpdateMetadata request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeUpdateMetadataRequest(UpdateMetadataRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(16384);
        buf.putInt(req.controllerId());
        buf.putInt(req.controllerEpoch());
        buf.putInt(req.liveBrokers().size());
        for (var b : req.liveBrokers()) {
            buf.putInt(b.brokerId());
            writeString(buf, b.host());
            buf.putInt(b.port());
        }
        buf.putInt(req.partitionStates().size());
        for (var ps : req.partitionStates()) {
            writeString(buf, ps.topic());
            buf.putInt(ps.partition());
            buf.putInt(ps.leader());
            buf.putInt(ps.leaderEpoch());
            buf.putInt(ps.isr().size());
            for (int r : ps.isr()) buf.putInt(r);
            buf.putInt(ps.replicas().size());
            for (int r : ps.replicas()) buf.putInt(r);
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an UpdateMetadata request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static UpdateMetadataRequest decodeUpdateMetadataRequest(ByteBuffer buf) {
        int controllerId = buf.getInt();
        int controllerEpoch = buf.getInt();
        int brokerCount = buf.getInt();
        List<UpdateMetadataRequest.BrokerState> brokers = new ArrayList<>(brokerCount);
        for (int i = 0; i < brokerCount; i++) {
            brokers.add(new UpdateMetadataRequest.BrokerState(buf.getInt(), readString(buf), buf.getInt()));
        }
        int partCount = buf.getInt();
        List<UpdateMetadataRequest.PartitionState> partitions = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) {
            String topic = readString(buf);
            int partition = buf.getInt();
            int leader = buf.getInt();
            int leaderEpoch = buf.getInt();
            int isrCount = buf.getInt();
            List<Integer> isr = new ArrayList<>(isrCount);
            for (int j = 0; j < isrCount; j++) isr.add(buf.getInt());
            int repCount = buf.getInt();
            List<Integer> replicas = new ArrayList<>(repCount);
            for (int j = 0; j < repCount; j++) replicas.add(buf.getInt());
            partitions.add(new UpdateMetadataRequest.PartitionState(topic, partition, leader, leaderEpoch, isr, replicas));
        }
        return new UpdateMetadataRequest(controllerId, controllerEpoch, brokers, partitions);
    }

    /**
     * Encodes an UpdateMetadata response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeUpdateMetadataResponse(UpdateMetadataResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2);
        buf.putShort(resp.errorCode());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an UpdateMetadata response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static UpdateMetadataResponse decodeUpdateMetadataResponse(ByteBuffer buf) {
        return new UpdateMetadataResponse(buf.getShort());
    }

    // ===== ControlledShutdown (7) =====

    /**
     * Encodes a ControlledShutdown request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeControlledShutdownRequest(ControlledShutdownRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4);
        buf.putInt(req.brokerId());
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ControlledShutdown request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ControlledShutdownRequest decodeControlledShutdownRequest(ByteBuffer buf) {
        return new ControlledShutdownRequest(buf.getInt());
    }

    /**
     * Encodes a ControlledShutdown response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeControlledShutdownResponse(ControlledShutdownResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.partitionsRemaining().size());
        for (var p : resp.partitionsRemaining()) {
            writeString(buf, p.topic());
            buf.putInt(p.partition());
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ControlledShutdown response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ControlledShutdownResponse decodeControlledShutdownResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ControlledShutdownResponse.TopicPartitionData> partitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            partitions.add(new ControlledShutdownResponse.TopicPartitionData(readString(buf), buf.getInt()));
        }
        return new ControlledShutdownResponse(errorCode, partitions);
    }

    // ===== OffsetForLeaderEpoch (23) =====

    /**
     * Encodes an OffsetForLeaderEpoch request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetForLeaderEpochRequest(OffsetForLeaderEpochRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.topic());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.leaderEpoch());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetForLeaderEpoch request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static OffsetForLeaderEpochRequest decodeOffsetForLeaderEpochRequest(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<OffsetForLeaderEpochRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<OffsetForLeaderEpochRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetForLeaderEpochRequest.PartitionData(buf.getInt(), buf.getInt()));
            }
            topics.add(new OffsetForLeaderEpochRequest.TopicData(topic, parts));
        }
        return new OffsetForLeaderEpochRequest(topics);
    }

    /**
     * Encodes an OffsetForLeaderEpoch response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetForLeaderEpochResponse(OffsetForLeaderEpochResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.topic());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putShort(p.errorCode());
                buf.putInt(p.partition());
                buf.putInt(p.leaderEpoch());
                buf.putLong(p.endOffset());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetForLeaderEpoch response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static OffsetForLeaderEpochResponse decodeOffsetForLeaderEpochResponse(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<OffsetForLeaderEpochResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<OffsetForLeaderEpochResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetForLeaderEpochResponse.PartitionData(
                        buf.getShort(), buf.getInt(), buf.getInt(), buf.getLong()));
            }
            topics.add(new OffsetForLeaderEpochResponse.TopicData(topic, parts));
        }
        return new OffsetForLeaderEpochResponse(topics);
    }

    // ===== WriteTxnMarkers (27) =====

    /**
     * Encodes a WriteTxnMarkers request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeWriteTxnMarkersRequest(WriteTxnMarkersRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putInt(req.markers().size());
        for (var m : req.markers()) {
            buf.putLong(m.producerId());
            buf.putShort(m.producerEpoch());
            buf.putInt(m.coordinatorEpoch());
            buf.put((byte) (m.committed() ? 1 : 0));
            buf.putInt(m.partitions().size());
            for (var p : m.partitions()) {
                writeString(buf, p.topic());
                buf.putInt(p.partition());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a WriteTxnMarkers request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static WriteTxnMarkersRequest decodeWriteTxnMarkersRequest(ByteBuffer buf) {
        int markerCount = buf.getInt();
        List<WriteTxnMarkersRequest.TxnMarker> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            long producerId = buf.getLong();
            short producerEpoch = buf.getShort();
            int coordinatorEpoch = buf.getInt();
            boolean committed = buf.get() == 1;
            int partCount = buf.getInt();
            List<WriteTxnMarkersRequest.TopicPartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new WriteTxnMarkersRequest.TopicPartitionData(readString(buf), buf.getInt()));
            }
            markers.add(new WriteTxnMarkersRequest.TxnMarker(producerId, producerEpoch, coordinatorEpoch, committed, parts));
        }
        return new WriteTxnMarkersRequest(markers);
    }

    /**
     * Encodes a WriteTxnMarkers response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeWriteTxnMarkersResponse(WriteTxnMarkersResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putInt(resp.markers().size());
        for (var m : resp.markers()) {
            buf.putLong(m.producerId());
            buf.putInt(m.topics().size());
            for (var t : m.topics()) {
                writeString(buf, t.topic());
                buf.putInt(t.partitions().size());
                for (var p : t.partitions()) {
                    buf.putInt(p.partition());
                    buf.putShort(p.errorCode());
                }
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a WriteTxnMarkers response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static WriteTxnMarkersResponse decodeWriteTxnMarkersResponse(ByteBuffer buf) {
        int markerCount = buf.getInt();
        List<WriteTxnMarkersResponse.MarkerResult> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            long producerId = buf.getLong();
            int topicCount = buf.getInt();
            List<WriteTxnMarkersResponse.TopicResult> topics = new ArrayList<>(topicCount);
            for (int j = 0; j < topicCount; j++) {
                String topic = readString(buf);
                int partCount = buf.getInt();
                List<WriteTxnMarkersResponse.PartitionResult> parts = new ArrayList<>(partCount);
                for (int k = 0; k < partCount; k++) {
                    parts.add(new WriteTxnMarkersResponse.PartitionResult(buf.getInt(), buf.getShort()));
                }
                topics.add(new WriteTxnMarkersResponse.TopicResult(topic, parts));
            }
            markers.add(new WriteTxnMarkersResponse.MarkerResult(producerId, topics));
        }
        return new WriteTxnMarkersResponse(markers);
    }

    // ===== AlterPartitionReassignments (45) =====

    /**
     * Encodes an AlterPartitionReassignments request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAlterPartitionReassignmentsRequest(AlterPartitionReassignmentsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putInt(req.timeoutMs());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            writeString(buf, t.topic());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partition());
                if (p.replicas() == null) {
                    buf.putInt(-1);
                } else {
                    buf.putInt(p.replicas().size());
                    for (int r : p.replicas()) buf.putInt(r);
                }
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AlterPartitionReassignments request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AlterPartitionReassignmentsRequest decodeAlterPartitionReassignmentsRequest(ByteBuffer buf) {
        int timeoutMs = buf.getInt();
        int topicCount = buf.getInt();
        List<AlterPartitionReassignmentsRequest.TopicReassignment> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<AlterPartitionReassignmentsRequest.PartitionReassignment> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int repCount = buf.getInt();
                List<Integer> replicas = null;
                if (repCount >= 0) {
                    replicas = new ArrayList<>(repCount);
                    for (int k = 0; k < repCount; k++) replicas.add(buf.getInt());
                }
                parts.add(new AlterPartitionReassignmentsRequest.PartitionReassignment(partition, replicas));
            }
            topics.add(new AlterPartitionReassignmentsRequest.TopicReassignment(topic, parts));
        }
        return new AlterPartitionReassignmentsRequest(timeoutMs, topics);
    }

    /**
     * Encodes an AlterPartitionReassignments response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAlterPartitionReassignmentsResponse(AlterPartitionReassignmentsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.topic());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partition());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an AlterPartitionReassignments response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AlterPartitionReassignmentsResponse decodeAlterPartitionReassignmentsResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int topicCount = buf.getInt();
        List<AlterPartitionReassignmentsResponse.TopicResult> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<AlterPartitionReassignmentsResponse.PartitionResult> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new AlterPartitionReassignmentsResponse.PartitionResult(buf.getInt(), buf.getShort()));
            }
            topics.add(new AlterPartitionReassignmentsResponse.TopicResult(topic, parts));
        }
        return new AlterPartitionReassignmentsResponse(errorCode, topics);
    }

    // ===== ListPartitionReassignments (46) =====

    /**
     * Encodes a ListPartitionReassignments request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeListPartitionReassignmentsRequest(ListPartitionReassignmentsRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putInt(req.timeoutMs());
        if (req.topics() == null) {
            buf.putInt(-1);
        } else {
            buf.putInt(req.topics().size());
            for (var t : req.topics()) {
                writeString(buf, t.topic());
                buf.putInt(t.partitions().size());
                for (int p : t.partitions()) buf.putInt(p);
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ListPartitionReassignments request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ListPartitionReassignmentsRequest decodeListPartitionReassignmentsRequest(ByteBuffer buf) {
        int timeoutMs = buf.getInt();
        int topicCount = buf.getInt();
        if (topicCount < 0) return new ListPartitionReassignmentsRequest(timeoutMs, null);
        List<ListPartitionReassignmentsRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<Integer> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) parts.add(buf.getInt());
            topics.add(new ListPartitionReassignmentsRequest.TopicData(topic, parts));
        }
        return new ListPartitionReassignmentsRequest(timeoutMs, topics);
    }

    /**
     * Encodes a ListPartitionReassignments response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeListPartitionReassignmentsResponse(ListPartitionReassignmentsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(8192);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            writeString(buf, t.topic());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.replicas().size());
                for (int r : p.replicas()) buf.putInt(r);
                buf.putInt(p.addingReplicas().size());
                for (int r : p.addingReplicas()) buf.putInt(r);
                buf.putInt(p.removingReplicas().size());
                for (int r : p.removingReplicas()) buf.putInt(r);
            }
        }
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ListPartitionReassignments response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ListPartitionReassignmentsResponse decodeListPartitionReassignmentsResponse(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int topicCount = buf.getInt();
        List<ListPartitionReassignmentsResponse.TopicResult> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String topic = readString(buf);
            int partCount = buf.getInt();
            List<ListPartitionReassignmentsResponse.PartitionResult> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int repCount = buf.getInt();
                List<Integer> replicas = new ArrayList<>(repCount);
                for (int k = 0; k < repCount; k++) replicas.add(buf.getInt());
                int addCount = buf.getInt();
                List<Integer> adding = new ArrayList<>(addCount);
                for (int k = 0; k < addCount; k++) adding.add(buf.getInt());
                int remCount = buf.getInt();
                List<Integer> removing = new ArrayList<>(remCount);
                for (int k = 0; k < remCount; k++) removing.add(buf.getInt());
                parts.add(new ListPartitionReassignmentsResponse.PartitionResult(partition, replicas, adding, removing));
            }
            topics.add(new ListPartitionReassignmentsResponse.TopicResult(topic, parts));
        }
        return new ListPartitionReassignmentsResponse(errorCode, topics);
    }

    // ===== Helper methods =====

    private static void writeString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    private static void writeNullableString(ByteBuffer buf, String s) {
        if (s == null) {
            buf.putShort((short) -1);
        } else {
            writeString(buf, s);
        }
    }

    private static String readString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len < 0) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String readNullableString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len < 0) return null;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] toBytes(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
