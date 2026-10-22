package ssg.legoflow.messaging.kafka.codec;

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
        return encodeRequest(header, payload, false);
    }

    /**
     * Encodes a request with its header into a length-prefixed frame.
     *
     * <p>When {@code flexible} is true the header uses the flexible (Kafka 3.0+) layout:
     * apiKey has bit 15 set, correlationId is a signed varint, and clientId is a
     * nullable compact (varint-length) string. The request body is already encoded
     * by the caller at the matching version.
     *
     * @param header   the request header
     * @param payload  the encoded request body
     * @param flexible true for flexible versions (header layout)
     * @return the complete frame ready for transmission
     */
    public static ByteBuffer encodeRequest(RequestHeader header, byte[] payload, boolean flexible) {
        if (flexible) {
            // Flexible header: apiKey|0x8000 (2) + apiVersion (2) + correlationId varint (1–5)
            // + clientId compact string (1 + len, or 1 if null).
            int corrVarintLen = KafkaCodecPrimitives.varintSize((header.correlationId() << 1) ^ (header.correlationId() >> 31));
            int clientIdLen = header.clientId() == null ? 0
                    : header.clientId().getBytes(StandardCharsets.UTF_8).length;
            int headerSize = 2 + 2 + corrVarintLen + 1 + clientIdLen;
            int totalSize = headerSize + payload.length;
            ByteBuffer buf = BufferPool.getBuffer(4 + totalSize);
            buf.putInt(totalSize);
            buf.putShort((short) (header.apiKey() | 0x8000));
            buf.putShort(header.apiVersion());
            KafkaCodecPrimitives.writeVarintSigned(buf, header.correlationId());
            KafkaCodecPrimitives.writeCompactString(buf, header.clientId());
            buf.put(payload);
            buf.flip();
            return buf;
        }

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
     * Decodes a request header in flexible (Kafka 3.0+) layout: apiKey with bit 15
     * (the flexible bit, stripped), apiVersion, correlationId as a signed varint,
     * clientId as a nullable compact string.
     *
     * @param buf the buffer positioned after the 4-byte length prefix
     * @return the decoded request header (apiKey without the flexible bit)
     */
    public static RequestHeader decodeRequestHeaderFlexible(ByteBuffer buf) {
        short apiKey = (short) (buf.getShort() & 0x7FFF);
        short apiVersion = buf.getShort();
        int correlationId = KafkaCodecPrimitives.readVarintSigned(buf);
        String clientId = KafkaCodecPrimitives.readCompactString(buf);
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
    public static byte[] encodeApiVersionsRequest(short version, ApiVersionsRequest req) {
        return ApiVersionsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an ApiVersions request body (v0 — the negotiation entry point uses v0).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ApiVersionsRequest decodeApiVersionsRequest(short version, ByteBuffer buf) {
        return ApiVersionsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an ApiVersions response body (v0 — the broker advertises v0; the response version
     * is chosen by the request version, which is always v0 on the wire today).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeApiVersionsResponse(short version, ApiVersionsResponse resp) {
        return ApiVersionsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an ApiVersions response body (v0).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ApiVersionsResponse decodeApiVersionsResponse(short version, ByteBuffer buf) {
        return ApiVersionsCodec.decodeResponse(version, buf);
    }

    // ===== Metadata (3) =====

    /**
     * Encodes a Metadata request body at the given API version.
     *
     * @param version the API version (0–12)
     * @param req     the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeMetadataRequest(short version, MetadataRequest req) {
        return MetadataCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a Metadata request body at the given API version.
     *
     * @param version the API version (0–12)
     * @param buf     the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static MetadataRequest decodeMetadataRequest(short version, ByteBuffer buf) {
        return MetadataCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a Metadata response body at the given API version.
     *
     * @param version the API version (0–12)
     * @param resp    the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeMetadataResponse(short version, MetadataResponse resp) {
        return MetadataCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a Metadata response body at the given API version.
     *
     * @param version the API version (0–12)
     * @param buf     the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static MetadataResponse decodeMetadataResponse(short version, ByteBuffer buf) {
        return MetadataCodec.decodeResponse(version, buf);
    }

    // ===== Produce (0) =====

    /**
     * Encodes a Produce request body.
     *
     * <p>Phase 6a: delegates to {@link ProduceCodec} at the in-house pinned version (v0).
     * The old inline layout unconditionally wrote a leading nullable TransactionalId
     * (a v3+ field), which produced a v3-shaped body under a v0 frame that a real broker
     * misparses; the v0 layout (Acks + TimeoutMs + TopicData) is spec-correct now.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeProduceRequest(short version, ProduceRequest req) {
        return ProduceCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a Produce request body (v0).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ProduceRequest decodeProduceRequest(short version, ByteBuffer buf) {
        return ProduceCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a Produce response body (v0: TopicData + PartitionResponse).
     *
     * <p>Phase 6a: delegates to {@link ProduceCodec} at v0. The old inline layout wrote
     * a per-partition LogAppendTimeMs (v2+) and a trailing ThrottleTimeMs (v1+); v0 writes
     * neither, so both carried values are discarded at this version.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeProduceResponse(short version, ProduceResponse resp) {
        return ProduceCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a Produce response body (v0).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ProduceResponse decodeProduceResponse(short version, ByteBuffer buf) {
        return ProduceCodec.decodeResponse(version, buf);
    }

    // ===== Fetch (1) =====

    /**
     * Encodes a Fetch request body (v0: ReplicaId + MaxWaitMs + MinBytes +
     * Topics[Topic + Partitions[Partition + FetchOffset + PartitionMaxBytes]]).
     *
     * <p>Phase 6a: delegates to {@link FetchCodec} at v0. The old inline layout wrote
     * MaxBytes (a v3+ field) while omitting ReplicaId (a v0+ field) — a v3-shaped body
     * under a v0 frame; v0 is spec-correct here (MaxBytes is a v3+ value, carried in the
     * model but absent from v0–v2 bodies).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeFetchRequest(short version, FetchRequest req) {
        return FetchCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a Fetch request body (v0).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static FetchRequest decodeFetchRequest(short version, ByteBuffer buf) {
        return FetchCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a Fetch response body (v0: Responses[Topic + Partitions[PartitionIndex +
     * ErrorCode + HighWatermark + Records]]).
     *
     * <p>Phase 6a: delegates to {@link FetchCodec} at v0. The old inline layout wrote a
     * leading ThrottleTimeMs (a v1+ field); v0 writes none, so the carried value is
     * discarded at this version.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeFetchResponse(short version, FetchResponse resp) {
        return FetchCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a Fetch response body (v0).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static FetchResponse decodeFetchResponse(short version, ByteBuffer buf) {
        return FetchCodec.decodeResponse(version, buf);
    }

    // ===== ListOffsets (2) =====

    /**
     * Encodes a ListOffsets request body (v1: ReplicaId + Topics[Name +
     * Partitions[PartitionIndex + Timestamp]]).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeListOffsetsRequest(short version, ListOffsetsRequest req) {
        return ListOffsetsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a ListOffsets request body (v1).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ListOffsetsRequest decodeListOffsetsRequest(short version, ByteBuffer buf) {
        return ListOffsetsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a ListOffsets response body (v1: Topics[Name +
     * Partitions[PartitionIndex + ErrorCode + Timestamp + Offset]]).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeListOffsetsResponse(short version, ListOffsetsResponse resp) {
        return ListOffsetsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a ListOffsets response body (v1).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ListOffsetsResponse decodeListOffsetsResponse(short version, ByteBuffer buf) {
        return ListOffsetsCodec.decodeResponse(version, buf);
    }

    // ===== FindCoordinator (10) =====

    /**
     * Encodes a FindCoordinator request body (pinned version).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeFindCoordinatorRequest(short version, FindCoordinatorRequest req) {
        return FindCoordinatorCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a FindCoordinator request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static FindCoordinatorRequest decodeFindCoordinatorRequest(short version, ByteBuffer buf) {
        return FindCoordinatorCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a FindCoordinator response body (pinned version).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeFindCoordinatorResponse(short version, FindCoordinatorResponse resp) {
        return FindCoordinatorCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a FindCoordinator response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static FindCoordinatorResponse decodeFindCoordinatorResponse(short version, ByteBuffer buf) {
        return FindCoordinatorCodec.decodeResponse(version, buf);
    }

    // ===== JoinGroup (11) =====

    /**
     * Encodes a JoinGroup request body at the pinned version (v0).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeJoinGroupRequest(short version, JoinGroupRequest req) {
        return JoinGroupCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a JoinGroup request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static JoinGroupRequest decodeJoinGroupRequest(short version, ByteBuffer buf) {
        return JoinGroupCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a JoinGroup response body at the pinned version (v0).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeJoinGroupResponse(short version, JoinGroupResponse resp) {
        return JoinGroupCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a JoinGroup response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static JoinGroupResponse decodeJoinGroupResponse(short version, ByteBuffer buf) {
        return JoinGroupCodec.decodeResponse(version, buf);
    }

    // ===== SyncGroup (14) =====

    /**
     * Encodes a SyncGroup request body at the given API version.
     *
     * @param version the API version (0–5)
     * @param req     the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeSyncGroupRequest(short version, SyncGroupRequest req) {
        return SyncGroupCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a SyncGroup request body at the given API version.
     *
     * @param version the API version (0–5)
     * @param buf     the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static SyncGroupRequest decodeSyncGroupRequest(short version, ByteBuffer buf) {
        return SyncGroupCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a SyncGroup response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param resp    the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeSyncGroupResponse(short version, SyncGroupResponse resp) {
        return SyncGroupCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a SyncGroup response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param buf     the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static SyncGroupResponse decodeSyncGroupResponse(short version, ByteBuffer buf) {
        return SyncGroupCodec.decodeResponse(version, buf);
    }

    // ===== Heartbeat (12) =====

    /**
     * Encodes a Heartbeat request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeHeartbeatRequest(short version, HeartbeatRequest req) {
        return HeartbeatCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a Heartbeat request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static HeartbeatRequest decodeHeartbeatRequest(short version, ByteBuffer buf) {
        return HeartbeatCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a Heartbeat response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeHeartbeatResponse(short version, HeartbeatResponse resp) {
        return HeartbeatCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a Heartbeat response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static HeartbeatResponse decodeHeartbeatResponse(short version, ByteBuffer buf) {
        return HeartbeatCodec.decodeResponse(version, buf);
    }

    // ===== LeaveGroup (13) =====

    /**
     * Encodes a LeaveGroup request body at the given API version.
     *
     * @param version the API version (0–5)
     * @param req     the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeLeaveGroupRequest(short version, LeaveGroupRequest req) {
        return LeaveGroupCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a LeaveGroup request body at the given API version.
     *
     * @param version the API version (0–5)
     * @param buf     the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static LeaveGroupRequest decodeLeaveGroupRequest(short version, ByteBuffer buf) {
        return LeaveGroupCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a LeaveGroup response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param resp    the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeLeaveGroupResponse(short version, LeaveGroupResponse resp) {
        return LeaveGroupCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a LeaveGroup response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param buf     the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static LeaveGroupResponse decodeLeaveGroupResponse(short version, ByteBuffer buf) {
        return LeaveGroupCodec.decodeResponse(version, buf);
    }

    // ===== OffsetCommit (8) =====

    /**
     * Encodes an OffsetCommit request body (pinned version).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeOffsetCommitRequest(short version, OffsetCommitRequest req) {
        return OffsetCommitCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an OffsetCommit request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static OffsetCommitRequest decodeOffsetCommitRequest(short version, ByteBuffer buf) {
        return OffsetCommitCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an OffsetCommit response body (pinned version).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeOffsetCommitResponse(short version, OffsetCommitResponse resp) {
        return OffsetCommitCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an OffsetCommit response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static OffsetCommitResponse decodeOffsetCommitResponse(short version, ByteBuffer buf) {
        return OffsetCommitCodec.decodeResponse(version, buf);
    }

    // ===== OffsetFetch (9) =====

    /**
     * Encodes an OffsetFetch request body (pinned version).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeOffsetFetchRequest(short version, OffsetFetchRequest req) {
        return OffsetFetchCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an OffsetFetch request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static OffsetFetchRequest decodeOffsetFetchRequest(short version, ByteBuffer buf) {
        return OffsetFetchCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an OffsetFetch response body (pinned version).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeOffsetFetchResponse(short version, OffsetFetchResponse resp) {
        return OffsetFetchCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an OffsetFetch response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static OffsetFetchResponse decodeOffsetFetchResponse(short version, ByteBuffer buf) {
        return OffsetFetchCodec.decodeResponse(version, buf);
    }

    // ===== CreateTopics (19) =====

    /**
     * Encodes a CreateTopics request body (pinned version; Assignments array v0+,
     * validateOnly v1+; see {@link CreateTopicsCodec} for version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeCreateTopicsRequest(short version, CreateTopicsRequest req) {
        return CreateTopicsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a CreateTopics request body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static CreateTopicsRequest decodeCreateTopicsRequest(short version, ByteBuffer buf) {
        return CreateTopicsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a CreateTopics response body (pinned version; ThrottleTimeMs v2+,
     * ErrorMessage v1+; see {@link CreateTopicsCodec} for version-gated layout).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeCreateTopicsResponse(short version, CreateTopicsResponse resp) {
        return CreateTopicsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a CreateTopics response body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static CreateTopicsResponse decodeCreateTopicsResponse(short version, ByteBuffer buf) {
        return CreateTopicsCodec.decodeResponse(version, buf);
    }

    // ===== DeleteTopics (20) =====

    /**
     * Encodes a DeleteTopics request body (pinned version; TopicNames v0–v5,
     * Topics[]DeleteTopicState v6; see {@link DeleteTopicsCodec} for version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteTopicsRequest(short version, DeleteTopicsRequest req) {
        return DeleteTopicsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a DeleteTopics request body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DeleteTopicsRequest decodeDeleteTopicsRequest(short version, ByteBuffer buf) {
        return DeleteTopicsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a DeleteTopics response body (pinned version; ThrottleTimeMs v1+,
     * ErrorMessage v5+, TopicId v6+; see {@link DeleteTopicsCodec} for version-gated layout).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteTopicsResponse(short version, DeleteTopicsResponse resp) {
        return DeleteTopicsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a DeleteTopics response body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DeleteTopicsResponse decodeDeleteTopicsResponse(short version, ByteBuffer buf) {
        return DeleteTopicsCodec.decodeResponse(version, buf);
    }

    // ===== DescribeGroups (15) =====

    /**
     * Encodes a DescribeGroups request body (pinned version; Groups[]string v0+,
     * IncludeAuthorizedOperations v3+; see {@link DescribeGroupsCodec} for version-gated layout).
     *
     * @param version the API version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeGroupsRequest(short version, DescribeGroupsRequest req) {
        return DescribeGroupsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a DescribeGroups request body (pinned version).
     *
     * @param version the API version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static DescribeGroupsRequest decodeDescribeGroupsRequest(short version, ByteBuffer buf) {
        return DescribeGroupsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a DescribeGroups response body (pinned version; ThrottleTimeMs v1+,
     * AuthorizedOperations v3+, GroupInstanceId v4+; see {@link DescribeGroupsCodec}
     * for version-gated layout).
     *
     * @param version the API version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeGroupsResponse(short version, DescribeGroupsResponse resp) {
        return DescribeGroupsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a DescribeGroups response body (pinned version).
     *
     * @param version the API version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static DescribeGroupsResponse decodeDescribeGroupsResponse(short version, ByteBuffer buf) {
        return DescribeGroupsCodec.decodeResponse(version, buf);
    }

    // ===== InitProducerId (22) =====

    public static byte[] encodeInitProducerIdRequest(short version, InitProducerIdRequest req) {
        return InitProducerIdCodec.encodeRequest(version, req);
    }

    public static InitProducerIdRequest decodeInitProducerIdRequest(short version, ByteBuffer buf) {
        return InitProducerIdCodec.decodeRequest(version, buf);
    }

    public static byte[] encodeInitProducerIdResponse(short version, InitProducerIdResponse resp) {
        return InitProducerIdCodec.encodeResponse(version, resp);
    }

    public static InitProducerIdResponse decodeInitProducerIdResponse(short version, ByteBuffer buf) {
        return InitProducerIdCodec.decodeResponse(version, buf);
    }

    // ===== AddPartitionsToTxn (24) =====

    public static byte[] encodeAddPartitionsToTxnRequest(short version, AddPartitionsToTxnRequest req) {
        return AddPartitionsToTxnCodec.encodeRequest(version, req);
    }

    public static AddPartitionsToTxnRequest decodeAddPartitionsToTxnRequest(short version, ByteBuffer buf) {
        return AddPartitionsToTxnCodec.decodeRequest(version, buf);
    }

    public static byte[] encodeAddPartitionsToTxnResponse(short version, AddPartitionsToTxnResponse resp) {
        return AddPartitionsToTxnCodec.encodeResponse(version, resp);
    }

    public static AddPartitionsToTxnResponse decodeAddPartitionsToTxnResponse(short version, ByteBuffer buf) {
        return AddPartitionsToTxnCodec.decodeResponse(version, buf);
    }

    // ===== ListGroups (16) =====

    /**
     * Encodes a ListGroups request body (pinned version; empty body v0–v2, flexible
     * v3+, StatesFilter v4+; see {@link ListGroupsCodec} for version-gated layout).
     *
     * @param version the API version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeListGroupsRequest(short version, ListGroupsRequest req) {
        return ListGroupsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a ListGroups request body (pinned version).
     *
     * @param version the API version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static ListGroupsRequest decodeListGroupsRequest(short version, ByteBuffer buf) {
        return ListGroupsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a ListGroups response body (pinned version; ThrottleTimeMs v1+,
     * flexible v3+, GroupState v4+; see {@link ListGroupsCodec} for version-gated
     * layout).
     *
     * @param version the API version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeListGroupsResponse(short version, ListGroupsResponse resp) {
        return ListGroupsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a ListGroups response body (pinned version).
     *
     * @param version the API version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static ListGroupsResponse decodeListGroupsResponse(short version, ByteBuffer buf) {
        return ListGroupsCodec.decodeResponse(version, buf);
    }

    // ===== DeleteRecords (21) =====

    /**
     * Encodes a DeleteRecords request body (pinned version; v1 wire-identical to v0,
     * v2 flexible; see {@link DeleteRecordsCodec} for version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteRecordsRequest(short version, DeleteRecordsRequest req) {
        return DeleteRecordsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a DeleteRecords request body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DeleteRecordsRequest decodeDeleteRecordsRequest(short version, ByteBuffer buf) {
        return DeleteRecordsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a DeleteRecords response body (pinned version; leading ThrottleTimeMs,
     * v2 flexible; see {@link DeleteRecordsCodec} for version-gated layout).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteRecordsResponse(short version, DeleteRecordsResponse resp) {
        return DeleteRecordsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a DeleteRecords response body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DeleteRecordsResponse decodeDeleteRecordsResponse(short version, ByteBuffer buf) {
        return DeleteRecordsCodec.decodeResponse(version, buf);
    }

    // ===== CreatePartitions (37) =====

    /**
     * Encodes a CreatePartitions request body (pinned version; v1 wire-identical to v0,
     * v2–v3 flexible; see {@link CreatePartitionsCodec} for version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeCreatePartitionsRequest(short version, CreatePartitionsRequest req) {
        return CreatePartitionsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a CreatePartitions request body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static CreatePartitionsRequest decodeCreatePartitionsRequest(short version, ByteBuffer buf) {
        return CreatePartitionsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a CreatePartitions response body (pinned version; leading ThrottleTimeMs,
     * v2–v3 flexible; see {@link CreatePartitionsCodec} for version-gated layout).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeCreatePartitionsResponse(short version, CreatePartitionsResponse resp) {
        return CreatePartitionsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a CreatePartitions response body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static CreatePartitionsResponse decodeCreatePartitionsResponse(short version, ByteBuffer buf) {
        return CreatePartitionsCodec.decodeResponse(version, buf);
    }

    // ===== DeleteGroups (42) =====

    /**
     * Encodes a DeleteGroups request body (pinned version; v1 wire-identical to v0,
     * v2 flexible; see {@link DeleteGroupsCodec} for version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteGroupsRequest(short version, DeleteGroupsRequest req) {
        return DeleteGroupsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a DeleteGroups request body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DeleteGroupsRequest decodeDeleteGroupsRequest(short version, ByteBuffer buf) {
        return DeleteGroupsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a DeleteGroups response body (pinned version; leading ThrottleTimeMs,
     * v2 flexible; see {@link DeleteGroupsCodec} for version-gated layout).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDeleteGroupsResponse(short version, DeleteGroupsResponse resp) {
        return DeleteGroupsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a DeleteGroups response body (pinned version).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DeleteGroupsResponse decodeDeleteGroupsResponse(short version, ByteBuffer buf) {
        return DeleteGroupsCodec.decodeResponse(version, buf);
    }

    // ===== OffsetDelete (47) =====

    /**
     * Encodes an OffsetDelete request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetDeleteRequest(OffsetDeleteRequest req) {
        return OffsetDeleteCodec.encodeRequest(OffsetDeleteCodec.MIN_VERSION, req);
    }

    /**
     * Decodes an OffsetDelete request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the buffer
     * @return the decoded request
     */
    public static OffsetDeleteRequest decodeOffsetDeleteRequest(short version, ByteBuffer buf) {
        return OffsetDeleteCodec.decodeRequest(version, buf);
    }

    /**
     * Decodes an OffsetDelete request body (v0 baseline).
     *
     * @param buf the buffer
     * @return the decoded request
     * @deprecated use {@link #decodeOffsetDeleteRequest(short, ByteBuffer)} with an explicit
     *             version instead
     */
    @Deprecated
    public static OffsetDeleteRequest decodeOffsetDeleteRequest(ByteBuffer buf) {
        return OffsetDeleteCodec.decodeRequest(OffsetDeleteCodec.MIN_VERSION, buf);
    }

    /**
     * Encodes an OffsetDelete response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeOffsetDeleteResponse(short version, OffsetDeleteResponse resp) {
        return OffsetDeleteCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an OffsetDelete response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the buffer
     * @return the decoded response
     */
    public static OffsetDeleteResponse decodeOffsetDeleteResponse(short version, ByteBuffer buf) {
        return OffsetDeleteCodec.decodeResponse(version, buf);
    }

    /**
     * Decodes an OffsetDelete response body (v0 baseline).
     *
     * @param buf the buffer
     * @return the decoded response
     * @deprecated use {@link #decodeOffsetDeleteResponse(short, ByteBuffer)} with an explicit
     *             version instead
     */
    @Deprecated
    public static OffsetDeleteResponse decodeOffsetDeleteResponse(ByteBuffer buf) {
        return OffsetDeleteCodec.decodeResponse(OffsetDeleteCodec.MIN_VERSION, buf);
    }

    // ===== DescribeConfigs (32) =====

    /**
     * Encodes a DescribeConfigs request body (pinned version; v1 adds IncludeSynonyms, v3
     * adds IncludeDocumentation, v4 flexible; see {@link DescribeConfigsCodec} for the
     * version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeConfigsRequest(short version, DescribeConfigsRequest req) {
        return DescribeConfigsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a DescribeConfigs request body at the pinned version.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static DescribeConfigsRequest decodeDescribeConfigsRequest(short version, ByteBuffer buf) {
        return DescribeConfigsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a DescribeConfigs response body (pinned version; v1 adds Synonyms, v3 adds
     * ConfigType + Documentation, v4 flexible; leading ThrottleTimeMs written with the
     * spec default per {@link DescribeConfigsCodec}).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeDescribeConfigsResponse(short version, DescribeConfigsResponse resp) {
        return DescribeConfigsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a DescribeConfigs response body at the pinned version.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static DescribeConfigsResponse decodeDescribeConfigsResponse(short version, ByteBuffer buf) {
        return DescribeConfigsCodec.decodeResponse(version, buf);
    }

    // ===== AlterConfigs (33) =====

    /**
     * Encodes an AlterConfigs request body (pinned version; v1 byte-identical, v2 flexible;
     * see {@link AlterConfigsCodec} for the version-gated layout).
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAlterConfigsRequest(short version, AlterConfigsRequest req) {
        return AlterConfigsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an AlterConfigs request body at the pinned version.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AlterConfigsRequest decodeAlterConfigsRequest(short version, ByteBuffer buf) {
        return AlterConfigsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an AlterConfigs response body (pinned version; v1 byte-identical, v2 flexible;
     * leading ThrottleTimeMs written with the spec default per {@link AlterConfigsCodec}).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAlterConfigsResponse(short version, AlterConfigsResponse resp) {
        return AlterConfigsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an AlterConfigs response body at the pinned version.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AlterConfigsResponse decodeAlterConfigsResponse(short version, ByteBuffer buf) {
        return AlterConfigsCodec.decodeResponse(version, buf);
    }

    // ===== AddOffsetsToTxn (25) =====

    /**
     * Encodes an AddOffsetsToTxn request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAddOffsetsToTxnRequest(short version, AddOffsetsToTxnRequest req) {
        return AddOffsetsToTxnCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an AddOffsetsToTxn request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AddOffsetsToTxnRequest decodeAddOffsetsToTxnRequest(short version, ByteBuffer buf) {
        return AddOffsetsToTxnCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an AddOffsetsToTxn response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAddOffsetsToTxnResponse(short version, AddOffsetsToTxnResponse resp) {
        return AddOffsetsToTxnCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an AddOffsetsToTxn response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AddOffsetsToTxnResponse decodeAddOffsetsToTxnResponse(short version, ByteBuffer buf) {
        return AddOffsetsToTxnCodec.decodeResponse(version, buf);
    }

    // ===== TxnOffsetCommit (28) =====

    /**
     * Encodes a TxnOffsetCommit request body (pinned version).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeTxnOffsetCommitRequest(short version, TxnOffsetCommitRequest req) {
        return TxnOffsetCommitCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a TxnOffsetCommit request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static TxnOffsetCommitRequest decodeTxnOffsetCommitRequest(short version, ByteBuffer buf) {
        return TxnOffsetCommitCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a TxnOffsetCommit response body (pinned version).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeTxnOffsetCommitResponse(short version, TxnOffsetCommitResponse resp) {
        return TxnOffsetCommitCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a TxnOffsetCommit response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static TxnOffsetCommitResponse decodeTxnOffsetCommitResponse(short version, ByteBuffer buf) {
        return TxnOffsetCommitCodec.decodeResponse(version, buf);
    }

    // ===== EndTxn (26) =====

    /**
     * Encodes an EndTxn request body (pinned version).
     *
     * @param req the request
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeEndTxnRequest(short version, EndTxnRequest req) {
        return EndTxnCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an EndTxn request body (pinned version).
     *
     * @param buf the request body
     * @return the decoded request
     * @since 0.1.0
     */
    public static EndTxnRequest decodeEndTxnRequest(short version, ByteBuffer buf) {
        return EndTxnCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an EndTxn response body (pinned version).
     *
     * @param resp the response
     * @return encoded body bytes
     * @since 0.1.0
     */
    public static byte[] encodeEndTxnResponse(short version, EndTxnResponse resp) {
        return EndTxnCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an EndTxn response body (pinned version).
     *
     * @param buf the response body
     * @return the decoded response
     * @since 0.1.0
     */
    public static EndTxnResponse decodeEndTxnResponse(short version, ByteBuffer buf) {
        return EndTxnCodec.decodeResponse(version, buf);
    }

    // ===== SaslHandshake (17) =====

    /**
     * Encodes a SaslHandshake request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeSaslHandshakeRequest(short version, SaslHandshakeRequest req) {
        return SaslHandshakeCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a SaslHandshake request body (v0).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static SaslHandshakeRequest decodeSaslHandshakeRequest(short version, ByteBuffer buf) {
        return SaslHandshakeCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a SaslHandshake response body (v0).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeSaslHandshakeResponse(short version, SaslHandshakeResponse resp) {
        return SaslHandshakeCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a SaslHandshake response body (v0).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static SaslHandshakeResponse decodeSaslHandshakeResponse(short version, ByteBuffer buf) {
        return SaslHandshakeCodec.decodeResponse(version, buf);
    }

    // ===== SaslAuthenticate (36) =====

    /**
     * Encodes a SaslAuthenticate request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeSaslAuthenticateRequest(short version, SaslAuthenticateRequest req) {
        return SaslAuthenticateCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a SaslAuthenticate request body (v0).
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static SaslAuthenticateRequest decodeSaslAuthenticateRequest(short version, ByteBuffer buf) {
        return SaslAuthenticateCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a SaslAuthenticate response body (v0: errorCode + errorMessage + authBytes).
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeSaslAuthenticateResponse(short version, SaslAuthenticateResponse resp) {
        return SaslAuthenticateCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a SaslAuthenticate response body (v0).
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static SaslAuthenticateResponse decodeSaslAuthenticateResponse(short version, ByteBuffer buf) {
        return SaslAuthenticateCodec.decodeResponse(version, buf);
    }

    // ===== LeaderAndIsr (4) =====

    /**
     * Encodes a LeaderAndIsr request body (versioned).
     *
     * @param version the protocol version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeLeaderAndIsrRequest(short version, LeaderAndIsrRequest req) {
        return LeaderAndIsrCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a LeaderAndIsr request body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static LeaderAndIsrRequest decodeLeaderAndIsrRequest(short version, ByteBuffer buf) {
        return LeaderAndIsrCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a LeaderAndIsr response body (versioned).
     *
     * @param version the protocol version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeLeaderAndIsrResponse(short version, LeaderAndIsrResponse resp) {
        return LeaderAndIsrCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a LeaderAndIsr response body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static LeaderAndIsrResponse decodeLeaderAndIsrResponse(short version, ByteBuffer buf) {
        return LeaderAndIsrCodec.decodeResponse(version, buf);
    }

    // ===== StopReplica (5) =====

    /**
     * Encodes a StopReplica request body (versioned).
     *
     * @param version the protocol version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeStopReplicaRequest(short version, StopReplicaRequest req) {
        return StopReplicaCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a StopReplica request body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static StopReplicaRequest decodeStopReplicaRequest(short version, ByteBuffer buf) {
        return StopReplicaCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a StopReplica response body (versioned).
     *
     * @param version the protocol version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeStopReplicaResponse(short version, StopReplicaResponse resp) {
        return StopReplicaCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a StopReplica response body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static StopReplicaResponse decodeStopReplicaResponse(short version, ByteBuffer buf) {
        return StopReplicaCodec.decodeResponse(version, buf);
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

    public static byte[] encodeWriteTxnMarkersRequest(short version, WriteTxnMarkersRequest req) {
        return WriteTxnMarkersCodec.encodeRequest(version, req);
    }

    public static WriteTxnMarkersRequest decodeWriteTxnMarkersRequest(short version, ByteBuffer buf) {
        return WriteTxnMarkersCodec.decodeRequest(version, buf);
    }

    public static byte[] encodeWriteTxnMarkersResponse(short version, WriteTxnMarkersResponse resp) {
        return WriteTxnMarkersCodec.encodeResponse(version, resp);
    }

    public static WriteTxnMarkersResponse decodeWriteTxnMarkersResponse(short version, ByteBuffer buf) {
        return WriteTxnMarkersCodec.decodeResponse(version, buf);
    }

    // ===== AlterPartitionReassignments (45) =====

    /**
     * Encodes an AlterPartitionReassignments request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeAlterPartitionReassignmentsRequest(short version, AlterPartitionReassignmentsRequest req) {
        return AlterPartitionReassignmentsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes an AlterPartitionReassignments request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static AlterPartitionReassignmentsRequest decodeAlterPartitionReassignmentsRequest(short version, ByteBuffer buf) {
        return AlterPartitionReassignmentsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes an AlterPartitionReassignments response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeAlterPartitionReassignmentsResponse(short version, AlterPartitionReassignmentsResponse resp) {
        return AlterPartitionReassignmentsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes an AlterPartitionReassignments response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static AlterPartitionReassignmentsResponse decodeAlterPartitionReassignmentsResponse(short version, ByteBuffer buf) {
        return AlterPartitionReassignmentsCodec.decodeResponse(version, buf);
    }

    // ===== ListPartitionReassignments (46) =====

    /**
     * Encodes a ListPartitionReassignments request body.
     *
     * @param req the request
     * @return the encoded bytes
     */
    public static byte[] encodeListPartitionReassignmentsRequest(short version, ListPartitionReassignmentsRequest req) {
        return ListPartitionReassignmentsCodec.encodeRequest(version, req);
    }

    /**
     * Decodes a ListPartitionReassignments request body.
     *
     * @param buf the buffer
     * @return the decoded request
     */
    public static ListPartitionReassignmentsRequest decodeListPartitionReassignmentsRequest(short version, ByteBuffer buf) {
        return ListPartitionReassignmentsCodec.decodeRequest(version, buf);
    }

    /**
     * Encodes a ListPartitionReassignments response body.
     *
     * @param resp the response
     * @return the encoded bytes
     */
    public static byte[] encodeListPartitionReassignmentsResponse(short version, ListPartitionReassignmentsResponse resp) {
        return ListPartitionReassignmentsCodec.encodeResponse(version, resp);
    }

    /**
     * Decodes a ListPartitionReassignments response body.
     *
     * @param buf the buffer
     * @return the decoded response
     */
    public static ListPartitionReassignmentsResponse decodeListPartitionReassignmentsResponse(short version, ByteBuffer buf) {
        return ListPartitionReassignmentsCodec.decodeResponse(version, buf);
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
