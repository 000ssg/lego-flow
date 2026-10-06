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
 *       +4).</li>
 *   <li>v2 — unchanged version: wire-identical to v1 in both directions (v2 is the first
 *       version handling message format v1; no field change) — all four dispatches
 *       fall through (request to the v0 methods, response to the v1 methods).</li>
 *   <li>v3 — the request adds MaxBytes(int32) after MinBytes — dedicated
 *       {@code encode/decodeRequestV3} methods. The response is wire-identical to
 *       v1/v2 (v3 changes the request only) and falls through to the v1 methods.</li>
 *   <li>v4 — both directions change (structural branch): the request adds
 *       IsolationLevel(int8) after MaxBytes — dedicated {@code encode/decodeRequestV4};
 *       the response adds LastStableOffset(int64) + AbortedTransactions
 *       ([](ProducerId(int64), FirstOffset(int64))) after HighWatermark per partition —
 *       dedicated {@code encode/decodeResponseV4}. The {@code FetchRequest} model gains
 *       {@code isolationLevel} (v4+; 0=read_committed, 1=read_uncommitted) and the
 *       {@code FetchResponse.PartitionResponse} model gains {@code lastStableOffset}
 *       (v4+, -1 default) + {@code abortedTransactions} (v4+, null/empty default);
 *       compatibility constructors cover pre-v4 call sites.</li>
 *   <li>v5 — both directions change (structural branch): the per-partition request
 *       layout gains LogStartOffset(int64) after FetchOffset (spec: "The earliest
 *       available offset of the follower replica. The field is only used when the
 *       request is sent by the follower."; default -1) — dedicated
 *       {@code encode/decodeRequestV5}; the per-partition response layout gains
 *       LogStartOffset(int64) after LastStableOffset (spec: "The current log start
 *       offset."; default -1) — dedicated {@code encode/decodeResponseV5}.
 *       Compatibility constructors cover pre-v5 call sites ({@code logStartOffset = -1}).</li>
 *   <li>v6 — unchanged version: wire-identical to v5 in both directions (spec: no field
 *       change) — all four dispatches fall through to the v5 methods (request encodes/
 *       decodes via {@code encode/decodeRequestV5}, response via
 *       {@code encode/decodeResponseV5}).</li>
 *   <li>v7 — both directions change (structural branch, fetch sessions): the request
 *       gains {@code SessionId(int32)} + {@code SessionEpoch(int32)} after
 *       {@code IsolationLevel} (spec: "The ID of the session as assigned by the
 *       broker" / "The epoch of the session as assigned by the broker"; defaults 0 /
 *       -1) and a trailing {@code ForgottenTopicsData[]} array after {@code Topics}
 *       (each entry {@code Topic(string)} + {@code Partitions[]int32}}, an empty
 *       partition list means the whole topic) — dedicated
 *       {@code encode/decodeRequestV7}; the response gains top-level
 *       {@code ErrorCode(int16)} + {@code SessionId(int32)} after {@code ThrottleTimeMs}
 *       — dedicated {@code encode/decodeResponseV7}. The {@code FetchRequest} model
 *       gains {@code sessionId} (default 0), {@code sessionEpoch} (default -1),
 *       {@code forgottenTopics} (default empty) + a new {@code ForgottenTopic} record;
 *       the {@code FetchResponse} model gains {@code errorCode} (default 0) +
 *       {@code sessionId} (default 0). Compatibility constructors cover pre-v7 call
 *       sites.</li>
 *   <li>v8 — unchanged version: wire-identical to v7 in both directions (no field
 *       change in either schema) — all four dispatches fall through to the v7 methods
 *       (request encodes/decodes via {@code encode/decodeRequestV7}, response via
 *       {@code encode/decodeResponseV7}).</li>
 *   <li>v9 — the request per-partition layout gains {@code CurrentLeaderEpoch(int32)}
 *       after {@code Partition} (spec: "The current leader epoch of the partition.";
 *       default -1) — dedicated {@code encode/decodeRequestV9}. The response is
 *       wire-identical to v7/v8 (no response field change at v9) and falls through
 *       to the v7 response methods. The {@code FetchRequest.PartitionFetch} model
 *       gains {@code currentLeaderEpoch} (v9+; -1 default); compatibility
 *       constructors cover pre-v9 call sites.</li>
 *   <li>v10 — unchanged version: wire-identical to v9 in both directions (no field
 *       change in either schema) — all four dispatches fall through (request to the
 *       v9 methods, response to the v7 methods).</li>
 *   <li>v11 — both directions change (structural branch): the request gains
 *       {@code RackId(string)} after {@code ForgottenTopicsData} (spec: "Rack ID of the
 *       consumer making this request"; default "") — dedicated
 *       {@code encode/decodeRequestV11}; the per-partition response layout gains
 *       {@code PreferredReadReplica(int32)} after {@code AbortedTransactions} (spec:
 *       "The preferred read replica for the consumer to use on its next fetch request";
 *       default -1) — dedicated {@code encode/decodeResponseV11}. The
 *       {@code FetchRequest} model gains {@code rackId} (v11+; "" default) and the
 *       {@code FetchResponse.PartitionResponse} model gains
 *       {@code preferredReadReplica} (v11+; -1 default); compatibility constructors
 *       cover pre-v11 call sites.</li>
 *   <li>v12 — both directions switch to Kafka flexible encoding (array counts and
 *       string/bytes lengths become unsigned varints; null string = varint 0, null
 *       bytes = varint 1; fixed-width integers unchanged) and add new fields —
 *       request: per-partition {@code LastFetchedEpoch(int32)} after
 *       {@code FetchOffset} (spec: "The epoch of the last fetched record or -1 if
 *       there is none") + trailing tagged section with {@code ClusterId(string|null)}
 *       at tag 0 (written only when non-null); response: per-partition trailing
 *       tagged section with {@code DivergingEpoch(EpochEndOffset)} tag 0,
 *       {@code CurrentLeader(LeaderIdAndEpoch)} tag 1, {@code SnapshotId(SnapshotId)}
 *       tag 2 (each written only when non-null). Wire order per the 3.6.1 schema
 *       (verified against the 3.6.1 generated source): request partition = Partition,
 *       CurrentLeaderEpoch, FetchOffset, LastFetchedEpoch, LogStartOffset,
 *       PartitionMaxBytes + trailing section; response partition = PartitionIndex,
 *       ErrorCode, HighWatermark, LastStableOffset, LogStartOffset, AbortedTransactions
 *       (each element: ProducerId, FirstOffset + trailing section), PreferredReadReplica,
 *       Records, then the per-partition trailing section. Nullable v12 bytes use the
 *       real-Kafka convention: null = varint(0), data = varint(length + 1) + bytes.
 *       The flexible header bit (apiKey | 0x8000) is frame-level — see
 *       {@link KafkaCodec} and {@link ApiKey#isFlexible}.
 *       Dedicated {@code encode/decodeRequestV12}/{@code encode/decodeResponseV12}.</li>
 *   <li>v13 — both directions change (structural branch): the {@code Topic(string)} name is
 *       replaced by a non-nullable fixed 16-byte {@code TopicId(uuid)} in all three
 *       topic-carrying structs (request {@code Topics}, request {@code ForgottenTopicsData},
 *       response {@code Responses}) — dedicated {@code encode/decodeRequestV13}/
 *       {@code encode/decodeResponseV13}; the topic *name* is absent from the v13+ wire, so
 *       decoded models carry {@code name == null}. Everything else (flexible encoding,
 *       LastFetchedEpoch, per-partition tagged section, tagged ClusterId) is wire-identical
 *       to v12. The {@code TopicFetch}/{@code ForgottenTopic}/{@code TopicResponse} models
 *       gain {@code topicId} ({@code byte[16]}, all-zeros default) via compatibility
 *       constructors covering pre-v13 call sites.</li>
 *   <li>v14 — unchanged version: wire-identical to v13 in both directions (no field
 *       change) — all four dispatches fall through to the v13 methods.</li>
 *   <li>v15 — the request is a structural branch (KIP-903): the top-level
 *       {@code ReplicaId(int32)} (spec versions 0-14) is removed and replaced by a
 *       trailing tagged {@code ReplicaState} struct at tag 1 (spec versions 15+;
 *       ignorable), whose fields use standard fixed-width encoding —
 *       {@code ReplicaId(int32)} at tag 0 + {@code ReplicaEpoch(int64)} at tag 1
 *       (both default -1). The body therefore starts at {@code MaxWaitMs}; the
 *       replica state is written only when {@code replicaId != -1 || replicaEpoch != -1}
 *       (the consumer case — the in-house client's case — omits the tag entirely).
 *       The {@code FetchRequest} model gains {@code replicaEpoch} (v15+; -1 default)
 *       via compatibility constructors covering pre-v15 call sites. Response is
 *       wire-identical to v13/v14 and falls through to {@code encode/decodeResponseV13}.</li>
 * </ul>
 *
 * <p>The {@link FetchRequest} model keeps {@code maxBytes} for v3+; at v0–v2 it is never
 * written or read (decoded as 0). {@link FetchResponse} keeps {@code throttleTimeMs}
 * for v1+; at v0 the value is discarded on encode and defaulted to 0 on decode.
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
            case 3: // v3 adds MaxBytes(int32) after MinBytes
                return encodeRequestV3(req);
            case 4: // v4 adds IsolationLevel(int8) after MaxBytes
                return encodeRequestV4(req);
            case 5: // v5 adds LogStartOffset(int64) to the per-partition layout (after FetchOffset)
            case 6: // v6 request is wire-identical to v5 (spec: no field change; v7 adds
                // SessionId/SessionEpoch/ForgottenTopicsData)
                return encodeRequestV5(req);
            case 7: // v7 adds SessionId/SessionEpoch after IsolationLevel + trailing ForgottenTopicsData
            case 8: // v8 request is wire-identical to v7 (no field change; v9 adds CurrentLeaderEpoch)
                return encodeRequestV7(req);
            case 9: // v9 request per-partition layout adds CurrentLeaderEpoch(int32) after Partition
            case 10: // v10 request is wire-identical to v9 (no field change)
                return encodeRequestV9(req);
            case 11: // v11 request appends RackId(string) after ForgottenTopicsData
                return encodeRequestV11(req);
            case 12: // v12 request: flexible encoding + LastFetchedEpoch + tagged ClusterId
                return encodeRequestV12(req);
            case 13: // v13 request: TopicId (uuid) replaces the topic name in Topics and
                // ForgottenTopicsData; the rest is wire-identical to v12
            case 14: // v14 request is wire-identical to v13 (no field change; v15 removes
                // the top-level ReplicaId and adds the tagged ReplicaState)
                return encodeRequestV13(req);
            case 15: // v15 request: no top-level ReplicaId; trailing tagged ReplicaState
                return encodeRequestV15(req);
            default:
                // v16+ is not implemented yet — no code path.
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
            case 3: // v3 adds MaxBytes(int32) after MinBytes
                return decodeRequestV3(buf);
            case 4: // v4 adds IsolationLevel(int8) after MaxBytes
                return decodeRequestV4(buf);
            case 5: // v5 adds LogStartOffset(int64) to the per-partition layout (after FetchOffset)
            case 6: // v6 request wire-identical to v5
                return decodeRequestV5(buf);
            case 7: // v7 adds SessionId/SessionEpoch + trailing ForgottenTopicsData
            case 8: // v8 request wire-identical to v7
                return decodeRequestV7(buf);
            case 9: // v9 request per-partition layout adds CurrentLeaderEpoch(int32) after Partition
            case 10: // v10 request is wire-identical to v9 (no field change)
                return decodeRequestV9(buf);
            case 11: // v11 request appends RackId(string) after ForgottenTopicsData
                return decodeRequestV11(buf);
            case 12: // v12 request: flexible encoding + LastFetchedEpoch + tagged ClusterId
                return decodeRequestV12(buf);
            case 13: // v13 request: TopicId (uuid) replaces the topic name; rest wire-identical to v12
            case 14: // v14 request wire-identical to v13
                return decodeRequestV13(buf);
            case 15: // v15 request: no top-level ReplicaId; trailing tagged ReplicaState
                return decodeRequestV15(buf);
            default:
                // v16+ is not implemented yet — no code path.
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
            case 2: // v2 response is wire-identical to v1 (no field change; v2 is the
                // first version handling message format v1)
            case 3: // v3 response is wire-identical to v1/v2 (only the request changed at v3)
                return encodeResponseV1(resp);
            case 4: // v4 response adds LastStableOffset + AbortedTransactions per partition
                return encodeResponseV4(resp);
            case 5: // v5 response adds LogStartOffset(int64) per partition (after LastStableOffset)
            case 6: // v6 response is wire-identical to v5 (spec: no field change; v7 adds
                // top-level ErrorCode/SessionId)
                return encodeResponseV5(resp);
            case 7: // v7 response adds top-level ErrorCode(int16) + SessionId(int32) after ThrottleTimeMs
            case 8: // v8 response wire-identical to v7
            case 9: // v9 response is wire-identical to v7 (v9 changes the request only)
            case 10: // v10 response wire-identical to v9/v7
                return encodeResponseV7(resp);
            case 11: // v11 response adds per-partition PreferredReadReplica(int32) after AbortedTransactions
                return encodeResponseV11(resp);
            case 12: // v12 response: flexible encoding + per-partition tagged section
                return encodeResponseV12(resp);
            case 13: // v13 response: TopicId (uuid) replaces the topic name in Responses;
                // the rest is wire-identical to v12
            case 14: // v14 response wire-identical to v13
            case 15: // v15 response wire-identical to v13 (v15 changes the request only —
                // tagged ReplicaState)
                return encodeResponseV13(resp);
            default:
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
            case 2: // v2 response wire-identical to v1
            case 3: // v3 response wire-identical to v1/v2
                return decodeResponseV1(buf);
            case 4: // v4 response adds LastStableOffset + AbortedTransactions per partition
                return decodeResponseV4(buf);
            case 5: // v5 response adds LogStartOffset(int64) per partition (after LastStableOffset)
            case 6: // v6 response wire-identical to v5
                return decodeResponseV5(buf);
            case 7: // v7 response adds top-level ErrorCode(int16) + SessionId(int32)
            case 8: // v8 response wire-identical to v7
            case 9: // v9 response wire-identical to v7 (v9 changes the request only)
            case 10: // v10 response wire-identical to v9/v7
                return decodeResponseV7(buf);
            case 11: // v11 response adds per-partition PreferredReadReplica(int32) after AbortedTransactions
                return decodeResponseV11(buf);
            case 12: // v12 response: flexible encoding + per-partition tagged section
                return decodeResponseV12(buf);
            case 13: // v13 response: TopicId (uuid) replaces the topic name; rest wire-identical to v12
            case 14: // v14 response wire-identical to v13
            case 15: // v15 response wire-identical to v13 (v15 changes the request only)
                return decodeResponseV13(buf);
            default:
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

    // ===== v3 — request: ReplicaId, MaxWaitMs, MinBytes, MaxBytes, Topics{Topic, Partitions{Partition, FetchOffset, PartitionMaxBytes}} =====
    // v3 response is wire-identical to v1/v2 (only the request changed at v3) — the v1
    // response methods above already handle it.

    private static byte[] encodeRequestV3(FetchRequest req) {
        // Fixed overhead: ReplicaId(int32) + MaxWaitMs(int32) + MinBytes(int32)
        // + MaxBytes(int32) + topic count(int32) = 20.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 8 (fetch offset) + 4 (partition max bytes) = 16.
        int size = 20;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 16 * topic.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes()); // v3+: MaxBytes after MinBytes (spec field order)
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

    private static FetchRequest decodeRequestV3(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
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
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, topics);
    }

    // ===== v4 — request: ReplicaId, MaxWaitMs, MinBytes, MaxBytes, IsolationLevel, Topics{...} =====
    // v4 response: ThrottleTimeMs, Responses{Topic, Partitions{PartitionIndex, ErrorCode,
    // HighWatermark, LastStableOffset, AbortedTransactions[]{ProducerId, FirstOffset}, Records}}

    private static byte[] encodeRequestV4(FetchRequest req) {
        // Fixed overhead: ReplicaId(int32) + MaxWaitMs(int32) + MinBytes(int32)
        // + MaxBytes(int32) + IsolationLevel(int8) + topic count(int32) = 21.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 8 (fetch offset) + 4 (partition max bytes) = 16.
        int size = 21;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 16 * topic.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes());
        buf.put((byte) req.isolationLevel()); // v4+: IsolationLevel int8 after MaxBytes (spec field order)
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

    private static FetchRequest decodeRequestV4(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt();
        int isolationLevel = buf.get() & 0xff; // v4+: IsolationLevel int8
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
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel, topics);
    }

    private static byte[] encodeResponseV4(FetchResponse resp) {
        // Fixed overhead: ThrottleTimeMs(int32) + topic count(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 2 (error code) + 8 (high watermark)
        // + 8 (last stable offset, v4+) + 4 (aborted count, v4+)
        // + 4 (records length) + record length = 30 + 16*aborted + records.
        int size = 8;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 30 + (pr.abortedTransactions() != null ? 16 * pr.abortedTransactions().size() : 0);
                size += (pr.records() != null ? pr.records().length : 0);
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
                buf.putLong(pr.lastStableOffset()); // v4+
                var aborted = pr.abortedTransactions() != null ? pr.abortedTransactions() : List.<FetchResponse.AbortedTransaction>of();
                buf.putInt(aborted.size()); // v4+
                for (var at : aborted) {
                    buf.putLong(at.producerId());
                    buf.putLong(at.firstOffset());
                }
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV4(ByteBuffer buf) {
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
                long lastStableOffset = buf.getLong(); // v4+
                int abortedCount = buf.getInt(); // v4+
                List<FetchResponse.AbortedTransaction> aborted = new ArrayList<>(abortedCount);
                for (int k = 0; k < abortedCount; k++) {
                    long producerId = buf.getLong();
                    long firstOffset = buf.getLong();
                    aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                }
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, errorCode, highWatermark,
                        lastStableOffset, aborted, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        return new FetchResponse(throttleTimeMs, topics);
    }

    // ===== v5 — request: v4 body + per-partition LogStartOffset(int64) after FetchOffset =====
    // v5 adds LogStartOffset to the per-partition request layout ("The earliest available
    // offset of the follower replica. The field is only used when the request is sent by
    // the follower."); spec default -1.

    private static byte[] encodeRequestV5(FetchRequest req) {
        // Same as v4 (21 fixed bytes: 4+4+4+4+1+4) plus 8 bytes per partition for the
        // new LogStartOffset(int64) after FetchOffset.
        int size = 21;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 24 * topic.partitions().size(); // 16 (v4 per-partition) + 8 (LogStartOffset, v5+)
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes()); // v3+
        buf.put((byte) req.isolationLevel()); // v4+
        buf.putInt(req.topics().size());
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pf : topic.partitions()) {
                buf.putInt(pf.partition());
                buf.putLong(pf.fetchOffset());
                buf.putLong(pf.logStartOffset()); // v5+
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV5(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt();
        int isolationLevel = buf.get() & 0xff; // v4+
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                long fetchOffset = buf.getLong();
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                partitions.add(new FetchRequest.PartitionFetch(partition, fetchOffset, partitionMaxBytes,
                        logStartOffset));
            }
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel, topics);
    }

    // ===== v5 — response: v4 body + per-partition LogStartOffset(int64) after LastStableOffset =====
    // v5 adds LogStartOffset to the per-partition response layout ("The current log start
    // offset."); spec default -1.

    private static byte[] encodeResponseV5(FetchResponse resp) {
        // Fixed overhead: ThrottleTimeMs(int32) + topic count(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 2 (error code) + 8 (high watermark)
        // + 8 (last stable offset, v4+) + 8 (log start offset, v5+)
        // + 4 (aborted count, v4+) + 4 (records length) + record length
        // = 38 + 16*aborted + records.
        int size = 8;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 38 + (pr.abortedTransactions() != null ? 16 * pr.abortedTransactions().size() : 0);
                size += (pr.records() != null ? pr.records().length : 0);
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
                buf.putLong(pr.lastStableOffset()); // v4+
                buf.putLong(pr.logStartOffset()); // v5+
                var aborted = pr.abortedTransactions() != null ? pr.abortedTransactions()
                        : List.<FetchResponse.AbortedTransaction>of();
                buf.putInt(aborted.size()); // v4+
                for (var at : aborted) {
                    buf.putLong(at.producerId());
                    buf.putLong(at.firstOffset());
                }
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV5(ByteBuffer buf) {
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
                long lastStableOffset = buf.getLong(); // v4+
                long logStartOffset = buf.getLong(); // v5+
                int abortedCount = buf.getInt(); // v4+
                List<FetchResponse.AbortedTransaction> aborted = new ArrayList<>(abortedCount);
                for (int k = 0; k < abortedCount; k++) {
                    long producerId = buf.getLong();
                    long firstOffset = buf.getLong();
                    aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                }
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, errorCode, highWatermark,
                        lastStableOffset, logStartOffset, aborted, -1, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        return new FetchResponse(throttleTimeMs, topics);
    }

    // ===== v7 — request: v5 body + SessionId(int32) + SessionEpoch(int32) after IsolationLevel
    // ===== + trailing ForgottenTopicsData[] (Topic(string) + Partitions[]int32) after Topics =====
    // v7 adds fetch sessions to the request: SessionId/SessionEpoch between IsolationLevel and
    // Topics ("The ID/epoch of the session as assigned by the broker"; spec defaults 0 / -1) and
    // a ForgottenTopicsData[] array after Topics (each entry a Topic string + Partitions[]int32;
    // an empty partition list means the whole topic is forgotten). v8 request is wire-identical
    // to v7 (no field change in the 3.6.1 schema) — both dispatch to these methods.

    private static byte[] encodeRequestV7(FetchRequest req) {
        // v5 request fixed overhead (ReplicaId+MaxWaitMs+MinBytes+MaxBytes+IsolationLevel
        // +topic count = 21) + 8 (SessionId int32 + SessionEpoch int32, v7+)
        // + 4 (ForgottenTopicsData array count, v7+).
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 24 (v5 per-partition).
        // Per forgotten topic: 4 (partition count) + 2 (name length) + name + 4*partitions.
        int size = 21 + 8 + 4;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 24 * topic.partitions().size();
        }
        for (var ft : req.forgottenTopics()) {
            size += 4 + 2 + ft.name().getBytes(StandardCharsets.UTF_8).length;
            size += 4 * ft.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes()); // v3+
        buf.put((byte) req.isolationLevel()); // v4+
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        buf.putInt(req.topics().size());
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pf : topic.partitions()) {
                buf.putInt(pf.partition());
                buf.putLong(pf.fetchOffset());
                buf.putLong(pf.logStartOffset()); // v5+
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        var forgotten = req.forgottenTopics() != null ? req.forgottenTopics() : List.<FetchRequest.ForgottenTopic>of();
        buf.putInt(forgotten.size()); // v7+
        for (var ft : forgotten) {
            KafkaCodecPrimitives.writeString(buf, ft.name()); // v7-12+
            buf.putInt(ft.partitions().size()); // v7+
            for (int partition : ft.partitions()) {
                buf.putInt(partition); // v7+
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV7(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                long fetchOffset = buf.getLong();
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                partitions.add(new FetchRequest.PartitionFetch(partition, fetchOffset, partitionMaxBytes,
                        logStartOffset));
            }
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        int forgottenCount = buf.getInt(); // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf); // v7-12+
            int partCount = buf.getInt(); // v7+
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt()); // v7+
            }
            forgotten.add(new FetchRequest.ForgottenTopic(name, partitions));
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, "");
    }

    // ===== v7 — response: v5 body + top-level ErrorCode(int16) + SessionId(int32)
    // ===== after ThrottleTimeMs (the per-partition layout is unchanged at v7/v8) =====
    // v7 adds fetch sessions to the response: a top-level ErrorCode (spec: "The error
    // code of the whole response if it errored out") and a SessionId (spec: "The ID of the
    // session as assigned by the broker") between ThrottleTimeMs and Responses. The
    // per-partition layout is unchanged at v7 (DivergingEpoch/CurrentLeader/SnapshotId are
    // v12+ tagged fields; PreferredReadReplica is v11+). v8 response is wire-identical to
    // v7 — both dispatch to these methods.

    private static byte[] encodeResponseV7(FetchResponse resp) {
        // v5 response fixed overhead (ThrottleTimeMs + topic count = 8) + 6
        // (ErrorCode int16 + SessionId int32, v7+).
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 38 (v5 per-partition) + 16*aborted + records.
        int size = 8 + 6;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 38 + (pr.abortedTransactions() != null ? 16 * pr.abortedTransactions().size() : 0);
                size += (pr.records() != null ? pr.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.throttleTimeMs());
        buf.putShort(resp.errorCode()); // v7+
        buf.putInt(resp.sessionId()); // v7+
        buf.putInt(resp.topics().size());
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                buf.putLong(pr.lastStableOffset()); // v4+
                buf.putLong(pr.logStartOffset()); // v5+
                var aborted = pr.abortedTransactions() != null ? pr.abortedTransactions()
                        : List.<FetchResponse.AbortedTransaction>of();
                buf.putInt(aborted.size()); // v4+
                for (var at : aborted) {
                    buf.putLong(at.producerId());
                    buf.putLong(at.firstOffset());
                }
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV7(ByteBuffer buf) {
        int throttleTimeMs = buf.getInt();
        short errorCode = buf.getShort(); // v7+
        int sessionId = buf.getInt(); // v7+
        int topicCount = buf.getInt();
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short partError = buf.getShort();
                long highWatermark = buf.getLong();
                long lastStableOffset = buf.getLong(); // v4+
                long logStartOffset = buf.getLong(); // v5+
                int abortedCount = buf.getInt(); // v4+
                List<FetchResponse.AbortedTransaction> aborted = new ArrayList<>(abortedCount);
                for (int k = 0; k < abortedCount; k++) {
                    long producerId = buf.getLong();
                    long firstOffset = buf.getLong();
                    aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                }
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, partError, highWatermark,
                        lastStableOffset, logStartOffset, aborted, -1, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        return new FetchResponse(throttleTimeMs, errorCode, sessionId, topics);
    }

    // ===== v9 — request: v7 body + CurrentLeaderEpoch(int32) after each Partition (v9+ field) =====
    // v9 adds the leader epoch to the per-partition request layout (spec: "The current
    // leader epoch of the partition."; default -1), written immediately after Partition.
    // The v10 request is wire-identical to v9 (no field change) — both dispatch to these
    // methods. The v9/v10 RESPONSE is wire-identical to v7/v8 (v9 changes the request
    // only) and falls through to the v7 response methods above.

    private static byte[] encodeRequestV9(FetchRequest req) {
        // v7 request fixed overhead (ReplicaId+MaxWaitMs+MinBytes+MaxBytes+IsolationLevel
        // +topic count = 21) + 8 (SessionId int32 + SessionEpoch int32, v7+)
        // + 4 (ForgottenTopicsData array count, v7+).
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 28 (v7 24 + CurrentLeaderEpoch int32, v9+).
        // Per forgotten topic: 4 (partition count) + 2 (name length) + name + 4*partitions.
        int size = 21 + 8 + 4;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 28 * topic.partitions().size();
        }
        for (var ft : req.forgottenTopics()) {
            size += 4 + 2 + ft.name().getBytes(StandardCharsets.UTF_8).length;
            size += 4 * ft.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes()); // v3+
        buf.put((byte) req.isolationLevel()); // v4+
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        buf.putInt(req.topics().size());
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pf : topic.partitions()) {
                buf.putInt(pf.partition());
                buf.putInt(pf.currentLeaderEpoch()); // v9+
                buf.putLong(pf.fetchOffset());
                buf.putLong(pf.logStartOffset()); // v5+
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        var forgotten = req.forgottenTopics() != null ? req.forgottenTopics() : List.<FetchRequest.ForgottenTopic>of();
        buf.putInt(forgotten.size()); // v7+
        for (var ft : forgotten) {
            KafkaCodecPrimitives.writeString(buf, ft.name()); // v7-12+
            buf.putInt(ft.partitions().size()); // v7+
            for (int partition : ft.partitions()) {
                buf.putInt(partition); // v7+
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV9(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int currentLeaderEpoch = buf.getInt(); // v9+
                long fetchOffset = buf.getLong();
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                partitions.add(new FetchRequest.PartitionFetch(partition, currentLeaderEpoch, fetchOffset,
                        partitionMaxBytes, logStartOffset));
            }
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        int forgottenCount = buf.getInt(); // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf); // v7-12+
            int partCount = buf.getInt(); // v7+
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt()); // v7+
            }
            forgotten.add(new FetchRequest.ForgottenTopic(name, partitions));
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, "");
    }

    // ===== v11 — request: v9 body + RackId(string) after ForgottenTopicsData (v11+ field) =====
    // v11 adds the consumer rack ID to the request (spec: "Rack ID of the consumer making
    // this request"; default "", written as a non-nullable int16-length string). It is the
    // last field of the v11 request — after ForgottenTopicsData. The v11 response per-
    // partition layout adds PreferredReadReplica(int32) after AbortedTransactions (spec:
    // "The preferred read replica for the consumer to use on its next fetch request";
    // default -1) and before Records.

    private static byte[] encodeRequestV11(FetchRequest req) {
        // v9 request fixed overhead (ReplicaId+MaxWaitMs+MinBytes+MaxBytes+IsolationLevel
        // +topic count = 21) + 8 (SessionId int32 + SessionEpoch int32, v7+)
        // + 4 (ForgottenTopicsData array count, v7+)
        // + 2 (RackId string length, v11+).
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 28 (v9 per-partition).
        // Per forgotten topic: 4 (partition count) + 2 (name length) + name + 4*partitions.
        String rack = req.rackId() != null ? req.rackId() : "";
        int rackLen = rack.getBytes(StandardCharsets.UTF_8).length;
        int size = 21 + 8 + 4 + 2 + rackLen;
        for (var topic : req.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 28 * topic.partitions().size();
        }
        for (var ft : req.forgottenTopics()) {
            size += 4 + 2 + ft.name().getBytes(StandardCharsets.UTF_8).length;
            size += 4 * ft.partitions().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes()); // v3+
        buf.put((byte) req.isolationLevel()); // v4+
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        buf.putInt(req.topics().size());
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pf : topic.partitions()) {
                buf.putInt(pf.partition());
                buf.putInt(pf.currentLeaderEpoch()); // v9+
                buf.putLong(pf.fetchOffset());
                buf.putLong(pf.logStartOffset()); // v5+
                buf.putInt(pf.partitionMaxBytes());
            }
        }
        var forgotten = req.forgottenTopics() != null ? req.forgottenTopics() : List.<FetchRequest.ForgottenTopic>of();
        buf.putInt(forgotten.size()); // v7+
        for (var ft : forgotten) {
            KafkaCodecPrimitives.writeString(buf, ft.name()); // v7-12+
            buf.putInt(ft.partitions().size()); // v7+
            for (int partition : ft.partitions()) {
                buf.putInt(partition); // v7+
            }
        }
        KafkaCodecPrimitives.writeString(buf, rack); // v11+: RackId after ForgottenTopicsData
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV11(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = buf.getInt();
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int currentLeaderEpoch = buf.getInt(); // v9+
                long fetchOffset = buf.getLong();
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                partitions.add(new FetchRequest.PartitionFetch(partition, currentLeaderEpoch, fetchOffset,
                        partitionMaxBytes, logStartOffset));
            }
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        int forgottenCount = buf.getInt(); // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf); // v7-12+
            int partCount = buf.getInt(); // v7+
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt()); // v7+
            }
            forgotten.add(new FetchRequest.ForgottenTopic(name, partitions));
        }
        String rackId = KafkaCodecPrimitives.readString(buf); // v11+
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, rackId);
    }

    // ===== v11 — response: v7 body + per-partition PreferredReadReplica(int32) =====
    // after AbortedTransactions and before Records (v11+ field, spec default -1).

    private static byte[] encodeResponseV11(FetchResponse resp) {
        // v7 response fixed overhead (ThrottleTimeMs+ErrorCode+SessionId+topic count = 14)
        // + per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 38 (v7 per-partition) + 4 (PreferredReadReplica int32, v11+)
        // + 16*aborted + records.
        int size = 8 + 6;
        for (var topic : resp.topics()) {
            size += 4 + 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            for (var pr : topic.partitions()) {
                size += 42 + (pr.abortedTransactions() != null ? 16 * pr.abortedTransactions().size() : 0);
                size += (pr.records() != null ? pr.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.throttleTimeMs());
        buf.putShort(resp.errorCode()); // v7+
        buf.putInt(resp.sessionId()); // v7+
        buf.putInt(resp.topics().size());
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                buf.putLong(pr.lastStableOffset()); // v4+
                buf.putLong(pr.logStartOffset()); // v5+
                var aborted = pr.abortedTransactions() != null ? pr.abortedTransactions()
                        : List.<FetchResponse.AbortedTransaction>of();
                buf.putInt(aborted.size()); // v4+
                for (var at : aborted) {
                    buf.putLong(at.producerId());
                    buf.putLong(at.firstOffset());
                }
                buf.putInt(pr.preferredReadReplica()); // v11+: PreferredReadReplica after AbortedTransactions
                KafkaCodecPrimitives.writeBytesField(buf, pr.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV11(ByteBuffer buf) {
        int throttleTimeMs = buf.getInt();
        short errorCode = buf.getShort(); // v7+
        int sessionId = buf.getInt(); // v7+
        int topicCount = buf.getInt();
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short partError = buf.getShort();
                long highWatermark = buf.getLong();
                long lastStableOffset = buf.getLong(); // v4+
                long logStartOffset = buf.getLong(); // v5+
                int abortedCount = buf.getInt(); // v4+
                List<FetchResponse.AbortedTransaction> aborted = new ArrayList<>(abortedCount);
                for (int k = 0; k < abortedCount; k++) {
                    long producerId = buf.getLong();
                    long firstOffset = buf.getLong();
                    aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                }
                int preferredReadReplica = buf.getInt(); // v11+
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, partError, highWatermark,
                        lastStableOffset, logStartOffset, aborted, preferredReadReplica, records));
            }
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        return new FetchResponse(throttleTimeMs, errorCode, sessionId, topics);
    }

    // ===== v12 — flexible encoding: compact strings, varint array counts (count + 1), and a
    // trailing tagged section on every struct. Request adds LastFetchedEpoch(int32) after
    // FetchOffset and carries ClusterId as a compact string in the trailing tagged section
    // (tag 0, written only when non-null). Response adds a per-partition trailing tagged
    // section with DivergingEpoch (tag 0: Epoch int32 + EndOffset int64), CurrentLeader
    // (tag 1: LeaderId int32 + LeaderEpoch int32) and SnapshotId (tag 2: EndOffset int64 +
    // Epoch int32). Nullable Records use the real-Kafka flexible convention verified against
    // the 3.6.1 generated source: null = varint(0), data = varint(length + 1) + bytes.
    // (The shared writeCompactBytes helper uses null = varint(1); that known deviation is
    // recorded for the interop phase and is deliberately NOT reused here.)

    private static byte[] encodeRequestV12(FetchRequest req) {
        // Fixed: replicaId 4 + maxWaitMs 4 + minBytes 4 + maxBytes 4 + isolationLevel 1
        // + sessionId 4 + sessionEpoch 4 = 25; topics varint(count + 1); per topic:
        // varint(name + 1) + name + varint(parts + 1) + per partition 33 (32 field bytes +
        // trailing varint(0)); per forgotten: varint(name + 1) + name + varint(parts + 1)
        // + 4 * parts + 1; rackId varint(len + 1) + len; trailing section 1 count + the
        // optional ClusterId tag (tag 1 + size varint + compact string).
        int size = 25 + KafkaCodecPrimitives.varintSize(req.topics().size() + 1);
        for (var topic : req.topics()) {
            byte[] name = topic.name().getBytes(StandardCharsets.UTF_8);
            size += KafkaCodecPrimitives.varintSize(name.length + 1) + name.length
                    + KafkaCodecPrimitives.varintSize(topic.partitions().size() + 1)
                    + 33 * topic.partitions().size();
        }
        size += KafkaCodecPrimitives.varintSize(req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            byte[] name = ft.name().getBytes(StandardCharsets.UTF_8);
            size += KafkaCodecPrimitives.varintSize(name.length + 1) + name.length
                    + KafkaCodecPrimitives.varintSize(ft.partitions().size() + 1)
                    + 4 * ft.partitions().size() + 1;
        }
        byte[] rack = req.rackId().getBytes(StandardCharsets.UTF_8);
        size += KafkaCodecPrimitives.varintSize(rack.length + 1) + rack.length;
        size += 1; // trailing tagged section count
        if (req.clusterId() != null) {
            byte[] cid = req.clusterId().getBytes(StandardCharsets.UTF_8);
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            size += 1 + KafkaCodecPrimitives.varintSize(valueLen) + valueLen; // tag + size + value
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes());
        buf.put((byte) req.isolationLevel());
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1);
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, topic.name());
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size() + 1);
            for (var p : topic.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.currentLeaderEpoch()); // v9+
                buf.putLong(p.fetchOffset());
                buf.putInt(p.lastFetchedEpoch()); // v12+: after FetchOffset
                buf.putLong(p.logStartOffset()); // v5+
                buf.putInt(p.partitionMaxBytes());
                KafkaCodecPrimitives.writeVarint(buf, 0); // FetchPartition trailing section
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // FetchTopic trailing section
        }
        KafkaCodecPrimitives.writeVarint(buf, req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, ft.name());
            KafkaCodecPrimitives.writeVarint(buf, ft.partitions().size() + 1);
            for (int partition : ft.partitions()) {
                buf.putInt(partition);
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // ForgottenTopic trailing section
        }
        KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.rackId()); // v11+
        if (req.clusterId() != null) {
            KafkaCodecPrimitives.writeVarint(buf, 1); // one tagged field
            byte[] cid = req.clusterId().getBytes(StandardCharsets.UTF_8);
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0: ClusterId
            KafkaCodecPrimitives.writeVarint(buf, valueLen);
            KafkaCodecPrimitives.writeVarint(buf, cid.length + 1);
            buf.put(cid);
        } else {
            KafkaCodecPrimitives.writeVarint(buf, 0); // no tagged fields
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV12(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int currentLeaderEpoch = buf.getInt(); // v9+
                long fetchOffset = buf.getLong();
                int lastFetchedEpoch = buf.getInt(); // v12+
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
                partitions.add(new FetchRequest.PartitionFetch(partition, currentLeaderEpoch,
                        fetchOffset, lastFetchedEpoch, partitionMaxBytes, logStartOffset));
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            topics.add(new FetchRequest.TopicFetch(name, partitions));
        }
        int forgottenCount = KafkaCodecPrimitives.readVarint(buf) - 1; // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt());
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            forgotten.add(new FetchRequest.ForgottenTopic(name, partitions));
        }
        String rackId = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // v11+
        String clusterId = null;
        int tagCount = KafkaCodecPrimitives.readVarint(buf); // v12+ trailing section
        for (int i = 0; i < tagCount; i++) {
            int tag = KafkaCodecPrimitives.readVarint(buf);
            int size = KafkaCodecPrimitives.readVarint(buf);
            int contentStart = buf.position();
            if (tag == 0) {
                int len = KafkaCodecPrimitives.readVarint(buf) - 1; // compact string prefix
                byte[] bytes = new byte[len];
                if (len > 0) {
                    buf.get(bytes);
                }
                clusterId = new String(bytes, StandardCharsets.UTF_8);
            }
            buf.position(contentStart + size);
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, rackId, clusterId, -1L);
    }

    // ===== v12 — response: flexible encoding + a per-partition trailing tagged section
    // (DivergingEpoch tag 0, CurrentLeader tag 1, SnapshotId tag 2; absent tags decode to
    // null). PreferredReadReplica stays int32; Records become flexible nullable bytes.

    private static byte[] encodeResponseV12(FetchResponse resp) {
        // Fixed: throttleTimeMs 4 + errorCode 2 + sessionId 4 = 10; topics varint(count + 1);
        // per topic: varint(name + 1) + name + varint(parts + 1); per partition: 34 field
        // bytes (idx 4 + err 2 + hwm 8 + lso 8 + logStart 8 + preferredReadReplica 4) +
        // aborted (varint + 17 per element) + records (varint) + trailing tagged section
        // (1 count + per present tag: tag 1 + size varint + struct 13/9/13); topic and
        // top-level trailing sections are varint(0).
        int size = 10 + KafkaCodecPrimitives.varintSize(resp.topics().size() + 1);
        for (var topic : resp.topics()) {
            byte[] name = topic.name().getBytes(StandardCharsets.UTF_8);
            size += KafkaCodecPrimitives.varintSize(name.length + 1) + name.length
                    + KafkaCodecPrimitives.varintSize(topic.partitions().size() + 1);
            for (var pr : topic.partitions()) {
                size += 34;
                var aborted = pr.abortedTransactions();
                int n = aborted == null ? 0 : aborted.size();
                size += aborted == null ? 1 : KafkaCodecPrimitives.varintSize(n + 1);
                size += 17 * n;
                size += pr.records() == null ? 1
                        : KafkaCodecPrimitives.varintSize(pr.records().length + 1) + pr.records().length;
                size += 1; // trailing section count
                if (pr.divergingEpoch() != null) {
                    size += 1 + 1 + 13; // tag 0: 4 + 8 + 1
                }
                if (pr.currentLeader() != null) {
                    size += 1 + 1 + 9; // tag 1: 4 + 4 + 1
                }
                if (pr.snapshotId() != null) {
                    size += 1 + 1 + 13; // tag 2: 8 + 4 + 1
                }
            }
            size += 1; // FetchableTopicResponse trailing section
        }
        size += 1; // top-level trailing section
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.throttleTimeMs());
        buf.putShort(resp.errorCode()); // v7+
        buf.putInt(resp.sessionId()); // v7+
        KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1);
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, topic.name());
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size() + 1);
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                buf.putLong(pr.lastStableOffset()); // v4+
                buf.putLong(pr.logStartOffset()); // v5+
                var aborted = pr.abortedTransactions();
                if (aborted == null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0);
                } else {
                    KafkaCodecPrimitives.writeVarint(buf, aborted.size() + 1);
                    for (var at : aborted) {
                        buf.putLong(at.producerId());
                        buf.putLong(at.firstOffset());
                        KafkaCodecPrimitives.writeVarint(buf, 0); // v12: per-element trailing section
                    }
                }
                buf.putInt(pr.preferredReadReplica()); // v11+
                if (pr.records() == null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // flexible nullable bytes
                } else {
                    KafkaCodecPrimitives.writeVarint(buf, pr.records().length + 1);
                    buf.put(pr.records());
                }
                int tagCount = (pr.divergingEpoch() != null ? 1 : 0)
                        + (pr.currentLeader() != null ? 1 : 0)
                        + (pr.snapshotId() != null ? 1 : 0);
                KafkaCodecPrimitives.writeVarint(buf, tagCount);
                if (pr.divergingEpoch() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0: DivergingEpoch
                    KafkaCodecPrimitives.writeVarint(buf, 13); // 4 + 8 + 1
                    buf.putInt(pr.divergingEpoch().epoch());
                    buf.putLong(pr.divergingEpoch().endOffset());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
                if (pr.currentLeader() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 1); // tag 1: CurrentLeader
                    KafkaCodecPrimitives.writeVarint(buf, 9); // 4 + 4 + 1
                    buf.putInt(pr.currentLeader().leaderId());
                    buf.putInt(pr.currentLeader().leaderEpoch());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
                if (pr.snapshotId() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 2); // tag 2: SnapshotId
                    KafkaCodecPrimitives.writeVarint(buf, 13); // 8 + 4 + 1
                    buf.putLong(pr.snapshotId().endOffset());
                    buf.putInt(pr.snapshotId().epoch());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // FetchableTopicResponse trailing section
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level trailing section
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV12(ByteBuffer buf) {
        int throttleTimeMs = buf.getInt();
        short errorCode = buf.getShort(); // v7+
        int sessionId = buf.getInt(); // v7+
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short partError = buf.getShort();
                long highWatermark = buf.getLong();
                long lastStableOffset = buf.getLong(); // v4+
                long logStartOffset = buf.getLong(); // v5+
                int abortedVarint = KafkaCodecPrimitives.readVarint(buf); // v4+
                List<FetchResponse.AbortedTransaction> aborted = null;
                if (abortedVarint > 0) {
                    int abortedCount = abortedVarint - 1;
                    aborted = new ArrayList<>(abortedCount);
                    for (int k = 0; k < abortedCount; k++) {
                        long producerId = buf.getLong();
                        long firstOffset = buf.getLong();
                        KafkaCodecPrimitives.skipTaggedFields(buf); // v12: per-element section
                        aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                    }
                }
                int preferredReadReplica = buf.getInt(); // v11+
                int recordsVarint = KafkaCodecPrimitives.readVarint(buf);
                byte[] records = null;
                if (recordsVarint > 0) {
                    int len = recordsVarint - 1;
                    records = new byte[len];
                    if (len > 0) {
                        buf.get(records);
                    }
                }
                FetchResponse.DivergingEpoch divergingEpoch = null;
                FetchResponse.LeaderIdAndEpoch currentLeader = null;
                FetchResponse.SnapshotId snapshotId = null;
                int tagCount = KafkaCodecPrimitives.readVarint(buf); // v12+ trailing section
                for (int k = 0; k < tagCount; k++) {
                    int tag = KafkaCodecPrimitives.readVarint(buf);
                    int size = KafkaCodecPrimitives.readVarint(buf);
                    int contentStart = buf.position();
                    switch (tag) {
                        case 0 -> { // DivergingEpoch
                            int epoch = buf.getInt();
                            long endOffset = buf.getLong();
                            divergingEpoch = new FetchResponse.DivergingEpoch(epoch, endOffset);
                        }
                        case 1 -> { // CurrentLeader
                            int leaderId = buf.getInt();
                            int leaderEpoch = buf.getInt();
                            currentLeader = new FetchResponse.LeaderIdAndEpoch(leaderId, leaderEpoch);
                        }
                        case 2 -> { // SnapshotId
                            long endOffset = buf.getLong();
                            int epoch = buf.getInt();
                            snapshotId = new FetchResponse.SnapshotId(endOffset, epoch);
                        }
                        default -> { // unknown tag: skipped by the position restore below
                        }
                    }
                    buf.position(contentStart + size); // past the struct trailing section
                }
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, partError,
                        highWatermark, lastStableOffset, logStartOffset, aborted,
                        preferredReadReplica, records, divergingEpoch, currentLeader, snapshotId));
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // FetchableTopicResponse trailing section
            topics.add(new FetchResponse.TopicResponse(name, partitions));
        }
        KafkaCodecPrimitives.skipTaggedFields(buf); // top-level trailing section
        return new FetchResponse(throttleTimeMs, errorCode, sessionId, topics);
    }

    // ===== v13 — TopicId (uuid) replaces the topic name in all three topic-carrying
    // structs (request Topics, request ForgottenTopicsData, response Responses). Everything
    // else is wire-identical to v12 (flexible encoding, LastFetchedEpoch, per-partition
    // tagged section, tagged ClusterId). v13 and v14 request/response are byte-identical
    // (v14 adds no field; v15 introduces the request's tagged ReplicaState). The 16-byte
    // TopicId is non-nullable (spec: TopicId:uuid, versions 13+, no nullableVersions), so
    // there is no varint prefix — just the raw UUID bytes. The topic *name* is absent from
    // the v13+ wire, so decoded models carry name == null.

    private static byte[] encodeRequestV13(FetchRequest req) {
        // Fixed 25 (replicaId 4 + maxWaitMs 4 + minBytes 4 + maxBytes 4 + isolationLevel 1
        // + sessionId 4 + sessionEpoch 4); topics varint(count + 1); per topic: 16 (topicId)
        // + varint(parts + 1) + 33 per partition; per forgotten: 16 (topicId) + varint
        // (parts + 1) + 4 * parts + 1; rackId compact varint(len + 1) + len; trailing
        // section 1 count + optional ClusterId tag.
        int size = 25 + KafkaCodecPrimitives.varintSize(req.topics().size() + 1);
        for (var topic : req.topics()) {
            size += 16 // v13+ TopicId (replaces the v12 compact topic name)
                    + KafkaCodecPrimitives.varintSize(topic.partitions().size() + 1)
                    + 33 * topic.partitions().size();
        }
        size += KafkaCodecPrimitives.varintSize(req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            size += 16 // v13+ TopicId
                    + KafkaCodecPrimitives.varintSize(ft.partitions().size() + 1)
                    + 4 * ft.partitions().size() + 1;
        }
        byte[] rack = req.rackId().getBytes(StandardCharsets.UTF_8);
        size += KafkaCodecPrimitives.varintSize(rack.length + 1) + rack.length;
        size += 1; // trailing tagged section count
        if (req.clusterId() != null) {
            byte[] cid = req.clusterId().getBytes(StandardCharsets.UTF_8);
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            size += 1 + KafkaCodecPrimitives.varintSize(valueLen) + valueLen; // tag + size + value
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.replicaId());
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes());
        buf.put((byte) req.isolationLevel());
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1);
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeUuid(buf, topic.topicId()); // v13+ TopicId
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size() + 1);
            for (var p : topic.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.currentLeaderEpoch()); // v9+
                buf.putLong(p.fetchOffset());
                buf.putInt(p.lastFetchedEpoch()); // v12+: after FetchOffset
                buf.putLong(p.logStartOffset()); // v5+
                buf.putInt(p.partitionMaxBytes());
                KafkaCodecPrimitives.writeVarint(buf, 0); // FetchPartition trailing section
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // FetchTopic trailing section
        }
        KafkaCodecPrimitives.writeVarint(buf, req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            KafkaCodecPrimitives.writeUuid(buf, ft.topicId()); // v13+ TopicId
            KafkaCodecPrimitives.writeVarint(buf, ft.partitions().size() + 1);
            for (int partition : ft.partitions()) {
                buf.putInt(partition);
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // ForgottenTopic trailing section
        }
        KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.rackId()); // v11+
        if (req.clusterId() != null) {
            KafkaCodecPrimitives.writeVarint(buf, 1); // one tagged field
            byte[] cid = req.clusterId().getBytes(StandardCharsets.UTF_8);
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0: ClusterId
            KafkaCodecPrimitives.writeVarint(buf, valueLen);
            KafkaCodecPrimitives.writeVarint(buf, cid.length + 1);
            buf.put(cid);
        } else {
            KafkaCodecPrimitives.writeVarint(buf, 0); // no tagged fields
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchRequest decodeRequestV13(ByteBuffer buf) {
        int replicaId = buf.getInt();
        int maxWait = buf.getInt();
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            byte[] topicId = KafkaCodecPrimitives.readUuid(buf); // v13+ TopicId (name absent)
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int currentLeaderEpoch = buf.getInt(); // v9+
                long fetchOffset = buf.getLong();
                int lastFetchedEpoch = buf.getInt(); // v12+
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
                partitions.add(new FetchRequest.PartitionFetch(partition, currentLeaderEpoch,
                        fetchOffset, lastFetchedEpoch, partitionMaxBytes, logStartOffset));
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            topics.add(new FetchRequest.TopicFetch(null, topicId, partitions)); // name absent at v13+
        }
        int forgottenCount = KafkaCodecPrimitives.readVarint(buf) - 1; // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            byte[] topicId = KafkaCodecPrimitives.readUuid(buf); // v13+ TopicId (name absent)
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt());
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            forgotten.add(new FetchRequest.ForgottenTopic(null, topicId, partitions));
        }
        String rackId = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // v11+
        String clusterId = null;
        int tagCount = KafkaCodecPrimitives.readVarint(buf); // v12+ trailing section
        for (int i = 0; i < tagCount; i++) {
            int tag = KafkaCodecPrimitives.readVarint(buf);
            int size = KafkaCodecPrimitives.readVarint(buf);
            int contentStart = buf.position();
            if (tag == 0) {
                int len = KafkaCodecPrimitives.readVarint(buf) - 1; // compact string prefix
                byte[] bytes = new byte[len];
                if (len > 0) {
                    buf.get(bytes);
                }
                clusterId = new String(bytes, StandardCharsets.UTF_8);
            }
            buf.position(contentStart + size);
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, rackId, clusterId, -1L);
    }

    /**
     * Decodes the v15 Fetch request body — the KIP-903 structural branch of v13/v14. The only
     * wire difference from {@link #decodeRequestV13(ByteBuffer)} is the trailing tagged
     * section: the top-level {@code ReplicaId}(int32) is gone (the body now starts at
     * {@code MaxWaitMs}), and the replica state arrives as a tagged {@code ReplicaState}
     * struct at tag 1 (absent for a consumer request — replicaId and replicaEpoch stay at
     * their -1 defaults). The struct content is 13 standard bytes (int32 ReplicaId +
     * int64 ReplicaEpoch + a trailing tagged section, count 0).
     */
    private static FetchRequest decodeRequestV15(ByteBuffer buf) {
        int maxWait = buf.getInt(); // v15: no top-level ReplicaId — the body starts at MaxWaitMs
        int minBytes = buf.getInt();
        int maxBytes = buf.getInt(); // v3+
        int isolationLevel = buf.get() & 0xff; // v4+
        int sessionId = buf.getInt(); // v7+
        int sessionEpoch = buf.getInt(); // v7+
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<FetchRequest.TopicFetch> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            byte[] topicId = KafkaCodecPrimitives.readUuid(buf); // v13+ TopicId (name absent)
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<FetchRequest.PartitionFetch> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int currentLeaderEpoch = buf.getInt(); // v9+
                long fetchOffset = buf.getLong();
                int lastFetchedEpoch = buf.getInt(); // v12+
                long logStartOffset = buf.getLong(); // v5+
                int partitionMaxBytes = buf.getInt();
                KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
                partitions.add(new FetchRequest.PartitionFetch(partition, currentLeaderEpoch,
                        fetchOffset, lastFetchedEpoch, partitionMaxBytes, logStartOffset));
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            topics.add(new FetchRequest.TopicFetch(null, topicId, partitions)); // name absent at v13+
        }
        int forgottenCount = KafkaCodecPrimitives.readVarint(buf) - 1; // v7+
        List<FetchRequest.ForgottenTopic> forgotten = new ArrayList<>(forgottenCount);
        for (int i = 0; i < forgottenCount; i++) {
            byte[] topicId = KafkaCodecPrimitives.readUuid(buf); // v13+ TopicId (name absent)
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<Integer> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                partitions.add(buf.getInt());
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // v12+ trailing section
            forgotten.add(new FetchRequest.ForgottenTopic(null, topicId, partitions));
        }
        String rackId = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // v11+
        String clusterId = null;
        int replicaId = -1;
        long replicaEpoch = -1L;
        int tagCount = KafkaCodecPrimitives.readVarint(buf); // v12+ trailing section
        for (int i = 0; i < tagCount; i++) {
            int tag = KafkaCodecPrimitives.readVarint(buf);
            int size = KafkaCodecPrimitives.readVarint(buf);
            int contentStart = buf.position();
            switch (tag) {
                case 0: // ClusterId (v12+)
                    int len = KafkaCodecPrimitives.readVarint(buf) - 1; // compact string prefix
                    byte[] bytes = new byte[len];
                    if (len > 0) {
                        buf.get(bytes);
                    }
                    clusterId = new String(bytes, StandardCharsets.UTF_8);
                    break;
                case 1: // ReplicaState (KIP-903, v15+): int32 ReplicaId + int64 ReplicaEpoch
                    replicaId = buf.getInt();
                    replicaEpoch = buf.getLong();
                    KafkaCodecPrimitives.skipTaggedFields(buf); // ReplicaState trailing section
                    break;
                default: // unknown/ignorable tag — skip
                    break;
            }
            buf.position(contentStart + size);
        }
        return new FetchRequest(replicaId, maxWait, minBytes, maxBytes, isolationLevel,
                sessionId, sessionEpoch, topics, forgotten, rackId, clusterId, replicaEpoch);
    }

    // ===== v15 — KIP-903 structural branch: the top-level ReplicaId(int32) (spec versions
    // 0-14) is removed and the replica state moves into the trailing tagged section as a
    // tagged ReplicaState struct at tag 1 (spec versions 15+; ignorable). The struct is a
    // 13-byte flexible section — ReplicaId(int32) + ReplicaEpoch(int64), both STANDARD
    // fixed-width (not tagged inside the struct), followed by its own trailing tagged
    // section (varint count 0). Wire order of the two top-level tagged fields is ascending
    // by tag: ClusterId (tag 0, v12+) then ReplicaState (tag 1, v15+); the tagged section
    // count varint precedes both. The tag is written only when the replica state is present
    // (replicaId != -1 || replicaEpoch != -1) — the consumer case (replicaId = -1,
    // replicaEpoch = -1, the in-house client's case) omits it entirely. Everything else is
    // wire-identical to the v13/v14 request (flexible encoding, TopicId uuid,
    // LastFetchedEpoch, tagged ClusterId). Response v15 is wire-identical to v13/v14 and
    // stays on the V13 response methods.

    private static byte[] encodeRequestV15(FetchRequest req) {
        boolean replicaState = req.replicaId() != -1 || req.replicaEpoch() != -1;
        byte[] cid = req.clusterId() == null ? null : req.clusterId().getBytes(StandardCharsets.UTF_8);
        // Fixed 21 (v15 removes the v13/v14 top-level ReplicaId int32): maxWaitMs 4 + minBytes 4
        // + maxBytes 4 + isolationLevel 1 + sessionId 4 + sessionEpoch 4; topics varint
        // (count + 1); per topic 16 (TopicId) + varint(parts + 1) + 33 per partition + 1
        // (per-topic trailing section); per forgotten 16 + varint(parts + 1) + 4 * parts
        // + 1; rackId compact varint(len + 1) + len; trailing section 1 (count) + ClusterId
        // tag 0 (tag varint + size varint + compact string) + ReplicaState tag 1 (tag
        // varint + size varint 1 + struct: int32 + int64 + trailing varint = 13).
        int size = 21 + KafkaCodecPrimitives.varintSize(req.topics().size() + 1);
        for (var topic : req.topics()) {
            size += 16 // v13+ TopicId (replaces the v12 compact topic name)
                    + KafkaCodecPrimitives.varintSize(topic.partitions().size() + 1)
                    + 33 * topic.partitions().size()
                    + 1; // FetchTopic trailing section
        }
        size += KafkaCodecPrimitives.varintSize(req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            size += 16 // v13+ TopicId
                    + KafkaCodecPrimitives.varintSize(ft.partitions().size() + 1)
                    + 4 * ft.partitions().size()
                    + 1; // ForgottenTopic trailing section
        }
        byte[] rack = req.rackId().getBytes(StandardCharsets.UTF_8);
        size += KafkaCodecPrimitives.varintSize(rack.length + 1) + rack.length;
        size += 1; // trailing tagged section count
        if (cid != null) {
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            size += 1 + KafkaCodecPrimitives.varintSize(valueLen) + valueLen; // tag + size + value
        }
        if (replicaState) {
            size += 1 + 1 + 13; // tag 1 + size varint + struct (int32 + int64 + trailing varint)
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.maxWaitMs());
        buf.putInt(req.minBytes());
        buf.putInt(req.maxBytes());
        buf.put((byte) req.isolationLevel());
        buf.putInt(req.sessionId()); // v7+
        buf.putInt(req.sessionEpoch()); // v7+
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1);
        for (var topic : req.topics()) {
            KafkaCodecPrimitives.writeUuid(buf, topic.topicId()); // v13+ TopicId
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size() + 1);
            for (var p : topic.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.currentLeaderEpoch()); // v9+
                buf.putLong(p.fetchOffset());
                buf.putInt(p.lastFetchedEpoch()); // v12+: after FetchOffset
                buf.putLong(p.logStartOffset()); // v5+
                buf.putInt(p.partitionMaxBytes());
                KafkaCodecPrimitives.writeVarint(buf, 0); // FetchPartition trailing section
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // FetchTopic trailing section
        }
        KafkaCodecPrimitives.writeVarint(buf, req.forgottenTopics().size() + 1);
        for (var ft : req.forgottenTopics()) {
            KafkaCodecPrimitives.writeUuid(buf, ft.topicId()); // v13+ TopicId
            KafkaCodecPrimitives.writeVarint(buf, ft.partitions().size() + 1);
            for (int partition : ft.partitions()) {
                buf.putInt(partition);
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // ForgottenTopic trailing section
        }
        KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.rackId()); // v11+
        int tagCount = (req.clusterId() != null ? 1 : 0) + (replicaState ? 1 : 0);
        KafkaCodecPrimitives.writeVarint(buf, tagCount); // v12+ trailing section count
        if (req.clusterId() != null) {
            int valueLen = KafkaCodecPrimitives.varintSize(cid.length + 1) + cid.length;
            KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0: ClusterId
            KafkaCodecPrimitives.writeVarint(buf, valueLen);
            KafkaCodecPrimitives.writeVarint(buf, cid.length + 1);
            buf.put(cid);
        }
        if (replicaState) {
            KafkaCodecPrimitives.writeVarint(buf, 1); // tag 1: ReplicaState (KIP-903)
            KafkaCodecPrimitives.writeVarint(buf, 13); // struct: 4 + 8 + 1
            buf.putInt(req.replicaId()); // ReplicaState.ReplicaId (standard int32)
            buf.putLong(req.replicaEpoch()); // ReplicaState.ReplicaEpoch (standard int64)
            KafkaCodecPrimitives.writeVarint(buf, 0); // ReplicaState trailing section
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }
    private static byte[] encodeResponseV13(FetchResponse resp) {
        // Fixed 10 (throttleTimeMs 4 + errorCode 2 + sessionId 4); responses varint(count
        // + 1); per topic: 16 (topicId, replaces the v12 compact name) + varint(parts + 1);
        // per partition: 34 field bytes + aborted (varint + 17 per element) + records
        // (varint) + trailing tagged section (1 count + per present tag tag 1 + size
        // varint + struct 13/9/13); topic and top-level trailing sections are varint(0).
        int size = 10 + KafkaCodecPrimitives.varintSize(resp.topics().size() + 1);
        for (var topic : resp.topics()) {
            size += 16 // v13+ TopicId (replaces the v12 compact topic name)
                    + KafkaCodecPrimitives.varintSize(topic.partitions().size() + 1);
            for (var pr : topic.partitions()) {
                size += 34;
                var aborted = pr.abortedTransactions();
                int n = aborted == null ? 0 : aborted.size();
                size += aborted == null ? 1 : KafkaCodecPrimitives.varintSize(n + 1);
                size += 17 * n;
                size += pr.records() == null ? 1
                        : KafkaCodecPrimitives.varintSize(pr.records().length + 1) + pr.records().length;
                size += 1; // trailing section count
                if (pr.divergingEpoch() != null) {
                    size += 1 + 1 + 13; // tag 0: 4 + 8 + 1
                }
                if (pr.currentLeader() != null) {
                    size += 1 + 1 + 9; // tag 1: 4 + 4 + 1
                }
                if (pr.snapshotId() != null) {
                    size += 1 + 1 + 13; // tag 2: 8 + 4 + 1
                }
            }
            size += 1; // FetchableTopicResponse trailing section
        }
        size += 1; // top-level trailing section
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.throttleTimeMs());
        buf.putShort(resp.errorCode()); // v7+
        buf.putInt(resp.sessionId()); // v7+
        KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1);
        for (var topic : resp.topics()) {
            KafkaCodecPrimitives.writeUuid(buf, topic.topicId()); // v13+ TopicId
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size() + 1);
            for (var pr : topic.partitions()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.highWatermark());
                buf.putLong(pr.lastStableOffset()); // v4+
                buf.putLong(pr.logStartOffset()); // v5+
                var aborted = pr.abortedTransactions();
                if (aborted == null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0);
                } else {
                    KafkaCodecPrimitives.writeVarint(buf, aborted.size() + 1);
                    for (var at : aborted) {
                        buf.putLong(at.producerId());
                        buf.putLong(at.firstOffset());
                        KafkaCodecPrimitives.writeVarint(buf, 0); // v12: per-element trailing section
                    }
                }
                buf.putInt(pr.preferredReadReplica()); // v11+
                if (pr.records() == null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // flexible nullable bytes
                } else {
                    KafkaCodecPrimitives.writeVarint(buf, pr.records().length + 1);
                    buf.put(pr.records());
                }
                int tagCount = (pr.divergingEpoch() != null ? 1 : 0)
                        + (pr.currentLeader() != null ? 1 : 0)
                        + (pr.snapshotId() != null ? 1 : 0);
                KafkaCodecPrimitives.writeVarint(buf, tagCount);
                if (pr.divergingEpoch() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0: DivergingEpoch
                    KafkaCodecPrimitives.writeVarint(buf, 13); // 4 + 8 + 1
                    buf.putInt(pr.divergingEpoch().epoch());
                    buf.putLong(pr.divergingEpoch().endOffset());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
                if (pr.currentLeader() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 1); // tag 1: CurrentLeader
                    KafkaCodecPrimitives.writeVarint(buf, 9); // 4 + 4 + 1
                    buf.putInt(pr.currentLeader().leaderId());
                    buf.putInt(pr.currentLeader().leaderEpoch());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
                if (pr.snapshotId() != null) {
                    KafkaCodecPrimitives.writeVarint(buf, 2); // tag 2: SnapshotId
                    KafkaCodecPrimitives.writeVarint(buf, 13); // 8 + 4 + 1
                    buf.putLong(pr.snapshotId().endOffset());
                    buf.putInt(pr.snapshotId().epoch());
                    KafkaCodecPrimitives.writeVarint(buf, 0); // struct trailing section
                }
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // FetchableTopicResponse trailing section
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level trailing section
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static FetchResponse decodeResponseV13(ByteBuffer buf) {
        int throttleTimeMs = buf.getInt();
        short errorCode = buf.getShort(); // v7+
        int sessionId = buf.getInt(); // v7+
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<FetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            byte[] topicId = KafkaCodecPrimitives.readUuid(buf); // v13+ TopicId (name absent)
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<FetchResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short partError = buf.getShort();
                long highWatermark = buf.getLong();
                long lastStableOffset = buf.getLong(); // v4+
                long logStartOffset = buf.getLong(); // v5+
                int abortedVarint = KafkaCodecPrimitives.readVarint(buf); // v4+
                List<FetchResponse.AbortedTransaction> aborted = null;
                if (abortedVarint > 0) {
                    int abortedCount = abortedVarint - 1;
                    aborted = new ArrayList<>(abortedCount);
                    for (int k = 0; k < abortedCount; k++) {
                        long producerId = buf.getLong();
                        long firstOffset = buf.getLong();
                        KafkaCodecPrimitives.skipTaggedFields(buf); // v12: per-element section
                        aborted.add(new FetchResponse.AbortedTransaction(producerId, firstOffset));
                    }
                }
                int preferredReadReplica = buf.getInt(); // v11+
                int recordsVarint = KafkaCodecPrimitives.readVarint(buf);
                byte[] records = null;
                if (recordsVarint > 0) {
                    int len = recordsVarint - 1;
                    records = new byte[len];
                    if (len > 0) {
                        buf.get(records);
                    }
                }
                FetchResponse.DivergingEpoch divergingEpoch = null;
                FetchResponse.LeaderIdAndEpoch currentLeader = null;
                FetchResponse.SnapshotId snapshotId = null;
                int tagCount = KafkaCodecPrimitives.readVarint(buf); // v12+ trailing section
                for (int k = 0; k < tagCount; k++) {
                    int tag = KafkaCodecPrimitives.readVarint(buf);
                    int size = KafkaCodecPrimitives.readVarint(buf);
                    int contentStart = buf.position();
                    switch (tag) {
                        case 0 -> { // DivergingEpoch
                            int epoch = buf.getInt();
                            long endOffset = buf.getLong();
                            divergingEpoch = new FetchResponse.DivergingEpoch(epoch, endOffset);
                        }
                        case 1 -> { // CurrentLeader
                            int leaderId = buf.getInt();
                            int leaderEpoch = buf.getInt();
                            currentLeader = new FetchResponse.LeaderIdAndEpoch(leaderId, leaderEpoch);
                        }
                        case 2 -> { // SnapshotId
                            long endOffset = buf.getLong();
                            int epoch = buf.getInt();
                            snapshotId = new FetchResponse.SnapshotId(endOffset, epoch);
                        }
                        default -> { // unknown tag: skipped by the position restore below
                        }
                    }
                    buf.position(contentStart + size); // past the struct trailing section
                }
                partitions.add(new FetchResponse.PartitionResponse(partitionIndex, partError,
                        highWatermark, lastStableOffset, logStartOffset, aborted,
                        preferredReadReplica, records, divergingEpoch, currentLeader, snapshotId));
            }
            KafkaCodecPrimitives.skipTaggedFields(buf); // FetchableTopicResponse trailing section
            topics.add(new FetchResponse.TopicResponse(null, topicId, partitions)); // name absent at v13+
        }
        KafkaCodecPrimitives.skipTaggedFields(buf); // top-level trailing section
        return new FetchResponse(throttleTimeMs, errorCode, sessionId, topics);
    }
}
