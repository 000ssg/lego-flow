package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.FetchRequest;
import ssg.legoflow.messaging.kafka.protocol.FetchResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetch codec (API key 1) — consume records from topic partitions.
 *
 * <p>Phase 6a execution order step 3 (Record I/O). Versions implemented one sub-task
 * at a time against the spec in
 * {@code messaging/kafka/doc/spec/message/Fetch{Request,Response}.json}
 * (Kafka 3.6.1: request v0–v15, flexible 12+; response v0–v15, flexible 12+).
 *
 * <ul>
 *   <li>v0 — request: ReplicaId(int32, -1 for consumer) + MaxWaitMs(int32) + MinBytes(int32)
 *       + Topics[](Topic(string16) + Partitions[](Partition(int32) + FetchOffset(int64) +
 *       PartitionMaxBytes(int32))). No MaxBytes (v3+), no IsolationLevel (v4+), no
 *       SessionId/SessionEpoch (v7+), no CurrentLeaderEpoch (v9+), no RackId (v11+), no
 *       ClusterId (v12+, tagged), no TopicId (v13+), no LastFetchedEpoch (v12+), no
 *       ForgottenTopicsData (v7+), no ReplicaState (v15+, tagged). Response:
 *       Responses[](Topic(string16) + Partitions[](PartitionIndex(int32) +
 *       ErrorCode(int16) + HighWatermark(int64) + Records(nullable bytes))). No
 *       ThrottleTimeMs (v1+), no top-level ErrorCode/SessionId (v7+), no
 *       LastStableOffset/AbortedTransactions (v4+), no LogStartOffset (v5+), no
 *       PreferredReadReplica (v11+), no TopicId (v13+). The in-house client and
 *       in-memory broker both pin v0, so v0 is the only version on the wire today.
 *       The earlier inline façade layout wrote MaxBytes (a v3+ field) and a response
 *       ThrottleTimeMs (a v1+ field) while omitting ReplicaId (a v0+ field) — a v1/v3-
 *       shaped body under a v0 frame; v0 here is spec-correct.</li>
 *   <li>v1 — the request is byte-identical to v0 (spec: "Version 1 is the same as
 *       version 0"); the response adds a leading ThrottleTimeMs(int32) — dedicated
 *       {@code encode/decodeResponseV1} methods (partition layout unchanged, body width
 *       +4). v2 (first version handling message format v1) is wire-identical to v1 in
 *       both directions and lands in its own row.</li>
 * </ul>
 *
 * <p>The {@link FetchRequest} model keeps {@code maxBytes} for v3+; at v0–v2 it is never
 * written or read. {@link FetchResponse} keeps {@code throttleTimeMs} for v1+; at v0 the
 * value is discarded on encode and defaulted to 0 on decode.
 *
 * @since 0.1.0
 */
public final class FetchCodec {

    /** Spec (3.6.1) highest version for API 1. */
    public static final short SPEC_MAX_VERSION = 15;

    /** The version the in-house client and broker pin for Fetch requests. */
    public static final short PINNED_VERSION = 0;

    private FetchCodec() {
    }

    /**
     * Encodes the Fetch request body for the given version.
     *
     * @param version the negotiated version
     * @param req     the request
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeRequest(short version, FetchRequest req) {
        switch (version) {
            case 0:
            case 1: // v1 request is byte-identical to v0 (spec: "Version 1 is the same as version 0")
            case 2: // v2 request unchanged vs v1 (first to handle message format v1, no field change)
                return encodeRequestV0(req);
            default:
                // v3+ (MaxBytes int32, then IsolationLevel/SessionId/... per the spec)
                // is not implemented yet — no code path.
                throw new CodecNotImplementedException("Fetch request v" + version + " not implemented");
        }
    }

    /**
     * Decodes the Fetch request body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded request
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static FetchRequest decodeRequest(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
            case 1: // v1 request byte-identical to v0
            case 2: // v2 request unchanged vs v1
                return decodeRequestV0(buf);
            default:
                // v3+ is not implemented yet — no code path.
                throw new CodecNotImplementedException("Fetch request v" + version + " not implemented");
        }
    }

    /**
     * Encodes the Fetch response body for the given version.
     *
     * @param version the negotiated version
     * @param resp    the response
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeResponse(short version, FetchResponse resp) {
        switch (version) {
            case 0:
                return encodeResponseV0(resp);
            case 1: // v1 adds a leading ThrottleTimeMs(int32)
                return encodeResponseV1(resp);
            default:
                // v2+ (unchanged wire at v2, then IsolationLevel/SessionId/... per the spec)
                // is not implemented yet — no code path.
                throw new CodecNotImplementedException("Fetch response v" + version + " not implemented");
        }
    }

    /**
     * Decodes the Fetch response body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded response
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static FetchResponse decodeResponse(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeResponseV0(buf);
            case 1: // v1 adds a leading ThrottleTimeMs(int32)
                return decodeResponseV1(buf);
            default:
                // v2+ (unchanged wire at v2, then IsolationLevel/SessionId/... per the spec)
                // is not implemented yet — no code path.
                throw new CodecNotImplementedException("Fetch response v" + version + " not implemented");
        }
    }

    // ===== v0 — request: ReplicaId, MaxWaitMs, MinBytes, Topics{Topic, Partitions{Partition, FetchOffset, PartitionMaxBytes}}
    // ===== v0 — response: Responses{Topic, Partitions{PartitionIndex, ErrorCode, HighWatermark, Records}} =====

    private static byte[] encodeRequestV0(FetchRequest req) {
        // Fixed overhead: ReplicaId(int32) + MaxWaitMs(int32) + MinBytes(int32)
        // + topic count(int32) = 16.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 8 (fetch offset) + 4 (partition max bytes) = 16.
        int size = 16;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 16 * topic.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.topics().size());
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pf : topic.partitions()) {
                buf.putInt(pf.partition());
                buf.putLong(pf.fetchOffset());
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV0(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                long fetchOffset = buf.getLong();
                int partitionMaxBytes = buf.getInt();
                partitions.add(new FetchRequest.PartitionFetch(partition, fetchOffset, partitionMaxBytes));
            }
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        // MaxBytes does not exist at v0 (added v3) — the carried value is not decoded.
        return new FetchRequest(replicaId, maxWait, minBytes, 0, topics);
    }

    private static byte[] encodeResponseV0(FetchResponse resp) {
        // Fixed overhead: topic count(int32) = 4.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 2 (error code) + 8 (high watermark)
        // + 4 (records length) + record length.
        int size = 4;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 18 + (pr.records() != null ? pr.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.topics().size());
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV0(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short errorCode = buf.getShort();
                long highWatermark = buf.getLong();
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, errorCode, highWatermark, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        // ThrottleTimeMs does not exist at v0 (added v1) — default 0 (carried value discarded).
        return new FetchResponse(0, topics);
    }

    // ===== v1 — response: ThrottleTimeMs, Responses{Topic, Partitions{PartitionIndex, ErrorCode, HighWatermark, Records}} =====
    // v1 request is byte-identical to v0 (spec: "Version 1 is the same as version 0") — the request
    // methods above already handle it.

    private static byte[] encodeResponseV1(FetchResponse resp) {
        // Fixed overhead: ThrottleTimeMs(int32) + topic count(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 2 (error code) + 8 (high watermark)
        // + 4 (records length) + record length.
        int size = 8;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 18 + (pr.records() != null ? pr.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.throttleTimeMs());
        buf.putInt(resp.topics().size());
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV1(ByteBuffer buf) {
        int throttleTimeMs = buf.getInt();
        int topicCount = buf.getInt();
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short errorCode = buf.getShort();
                long highWatermark = buf.getLong();
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, errorCode, highWatermark, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        return new FetchResponse(throttleTimeMs, topics);
    }
}
