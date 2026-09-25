package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ProduceRequest;
import ssg.legoflow.messaging.kafka.protocol.ProduceResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Produce codec (API key 0) — publish records to topic partitions.
 *
 * <p>Phase 6a execution order step 3 (Record I/O). Versions implemented one sub-task
 * at a time against the spec in
 * {@code messaging/kafka/doc/spec/message/Produce{Request,Response}.json}
 * (Kafka 3.6.1: request v0–v9, flexible 9+; response v0–v9, flexible 9+).
 *
 * <ul>
 *   <li>v0 — request: Acks(int16) + TimeoutMs(int32) + TopicData[](Name(string16) +
 *       PartitionData[](Index(int32) + Records(Nullable bytes))). No TransactionalId
 *       (added v3); no ThrottleTimeMs in the response (added v1); no LogAppendTimeMs in
 *       the response partition (added v2). The in-house client and in-memory broker both
 *       pin v0, so v0 is the only version on the wire today.</li>
 *   <li>v1 — request byte-identical to v0 (spec: "Version 1 and 2 are the same as
 *       version 0"); response adds a trailing ThrottleTimeMs(int32) after the TopicData
 *       array. The v1 request shares the v0 methods; the v1 response has dedicated
 *       methods (the partition layout is still v0 — LogAppendTimeMs arrives in v2).</li>
 *   <li>v2 — request still byte-identical to v0; each response partition gains
 *       LogAppendTimeMs(int64) after BaseOffset (spec default -1, ignorable), the
 *       trailing ThrottleTimeMs(int32) is retained from v1.</li>
 *   <li>v3 — the request gains a leading nullable TransactionalId(string) before Acks
 *       ("or null if the producer is not transactional") — the first request framing
 *       change since v0, so the v3 request has dedicated methods; the response is
 *       unchanged vs v2 (LogStartOffset arrives in v5) and shares the v2 methods.</li>
 *   <li>v4 — unchanged in both directions (spec: no field version ranges differ at
 *       v4+ vs v3); all four dispatches fall through to the v3 methods.</li>
 *   <li>v5 — request unchanged; the response partition gains LogStartOffset(int64)
 *       after LogAppendTimeMs (spec default -1, unavailable); dedicated response
 *       methods, partition width 22 to 30 bytes.</li>
 *   <li>v6 — unchanged in both directions (spec: no field version ranges differ at
 *       v6+ vs v5; RecordErrors/ErrorMessage arrive in v8); all four dispatches fall
 *       through to the v3/v5 methods.</li>
 *   <li>v7 — unchanged in both directions (spec: no field version ranges differ at
 *       v7+ vs v6); all four dispatches fall through to the v3/v5 methods.</li>
 *   <li>v8 — request unchanged; the response partition gains RecordErrors
 *       ([]BatchIndexAndErrorMessage: BatchIndex int32 + BatchIndexErrorMessage
 *       string|null) and a trailing nullable ErrorMessage(string) after LogStartOffset
 *       (both ignorable). Dedicated response methods, partition width 30 to 38+ bytes.</li>
 *   <li>v9 — no field-layout change vs v8; the whole message switches to Kafka
 *       flexible encoding ("flexibleVersions: 9+"): array counts and all string/bytes
 *       lengths become unsigned varints (count + 1, null string = 0, null bytes = 1),
 *       fixed-width integers are unchanged. The v9 request header additionally carries
 *       the flexible bit (apiKey | 0x8000) — handled at frame level by
 *       {@link KafkaCodec#encodeRequest} / {@link ApiKey#isFlexible}, not here.
 *       v9 is the terminal version of the 3.6.1 Produce spec; v10+ throws
 *       {@link CodecNotImplementedException}.</li>
 * </ul>
 *
 * <p>The {@link ProduceRequest} model keeps {@code transactionalId} for v3+; at v0–v2 it
 * is never written or read (decoded as null), at v3 it is the leading field.
 * {@link ProduceResponse} keeps {@code throttleTimeMs} (v1+), the partition-level
 * {@code logAppendTimeMs} (v2+) and {@code logStartOffset} (v5+); at v0–v4 the absent
 * values are discarded on encode and defaulted (0 / -1 / -1) on decode.
 *
 * @since 0.1.0
 */
public final class ProduceCodec {

    /** Spec (3.6.1) highest version for API 0. */
    public static final short SPEC_MAX_VERSION = 9;

    /** The version the in-house client and broker pin for Produce requests. */
    public static final short PINNED_VERSION = 0;

    private ProduceCodec() {
    }

    /**
     * Encodes the Produce request body for the given version.
     *
     * @param version the negotiated version
     * @param req     the request
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeRequest(short version, ProduceRequest req) {
        switch (version) {
            case 0:
            case 1: // v1 request is byte-identical to v0 (spec: "Version 1 and 2 are the same as version 0")
            case 2: // v2 request unchanged vs v1
                return encodeRequestV0(req);
            case 3: // v3 request adds a leading nullable TransactionalId
            case 4: // v4 request unchanged vs v3
            case 5: // v5 request unchanged vs v4
            case 6: // v6 request unchanged vs v5
            case 7: // v7 request unchanged vs v6
            case 8: // v8 request unchanged vs v7
                return encodeRequestV3(req);
            case 9: // v9 request: flexible encoding (varint length prefixes)
                return encodeRequestV9(req);
            default:
                // v10+ is beyond the 3.6.1 spec (validVersions 0-9) — no code path.
                throw new CodecNotImplementedException("Produce request v" + version + " not implemented");
        }
    }

    /**
     * Decodes the Produce request body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded request
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static ProduceRequest decodeRequest(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
            case 1: // v1 request byte-identical to v0
            case 2: // v2 request unchanged vs v1
                return decodeRequestV0(buf);
            case 3: // v3 request adds a leading nullable TransactionalId
            case 4: // v4 request unchanged vs v3
            case 5: // v5 request unchanged vs v4
            case 6: // v6 request unchanged vs v5
            case 7: // v7 request unchanged vs v6
            case 8: // v8 request unchanged vs v7
                return decodeRequestV3(buf);
            case 9: // v9 request: flexible encoding (varint length prefixes)
                return decodeRequestV9(buf);
            default:
                // v10+ is beyond the 3.6.1 spec (validVersions 0-9) — no code path.
                throw new CodecNotImplementedException("Produce request v" + version + " not implemented");
        }
    }

    /**
     * Encodes the Produce response body for the given version.
     *
     * @param version the negotiated version
     * @param resp    the response
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeResponse(short version, ProduceResponse resp) {
        switch (version) {
            case 0:
                return encodeResponseV0(resp);
            case 1:
                return encodeResponseV1(resp);
            case 2:
                return encodeResponseV2(resp);
            case 3: // v3 response unchanged vs v2 (LogStartOffset arrives in v5)
            case 4: // v4 response unchanged vs v3
                return encodeResponseV2(resp);
            case 5: // v5 response partition gains LogStartOffset(int64)
            case 6: // v6 response unchanged vs v5
            case 7: // v7 response unchanged vs v6
                return encodeResponseV5(resp);
            case 8: // v8 response partition gains RecordErrors + ErrorMessage
                return encodeResponseV8(resp);
            case 9: // v9 response: flexible encoding (varint length prefixes)
                return encodeResponseV9(resp);
            default:
                // v10+ is beyond the 3.6.1 spec (validVersions 0-9) — no code path.
                throw new CodecNotImplementedException("Produce response v" + version + " not implemented");
        }
    }

    /**
     * Decodes the Produce response body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded response
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static ProduceResponse decodeResponse(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeResponseV0(buf);
            case 1:
                return decodeResponseV1(buf);
            case 2:
                return decodeResponseV2(buf);
            case 3: // v3 response unchanged vs v2 (LogStartOffset arrives in v5)
            case 4: // v4 response unchanged vs v3
                return decodeResponseV2(buf);
            case 5: // v5 response partition gains LogStartOffset(int64)
            case 6: // v6 response unchanged vs v5
            case 7: // v7 response unchanged vs v6
                return decodeResponseV5(buf);
            case 8: // v8 response partition gains RecordErrors + ErrorMessage
                return decodeResponseV8(buf);
            case 9: // v9 response: flexible encoding (varint length prefixes)
                return decodeResponseV9(buf);
            default:
                // v10+ is beyond the 3.6.1 spec (validVersions 0-9) — no code path.
                throw new CodecNotImplementedException("Produce response v" + version + " not implemented");
        }
    }

    // ===== v0 — request: Acks, TimeoutMs, TopicData{Name, PartitionData{Index, Records}}
    // ===== v0 — response: TopicData{Name, PartitionResponse{Index, ErrorCode, BaseOffset}} =====

    private static byte[] encodeRequestV0(ProduceRequest req) {
        // Fixed overhead: Acks(int16) + TimeoutMs(int32) + topic count(int32) = 10.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition: 4 (index) + 4 (record length) + record length.
        int size = 10;
        for (var topic : req.topicData()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (var pd : topic.partitionData()) {
                size += 8 + (pd.records() != null ? pd.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putShort(req.acks());
        buf.putInt(req.timeoutMs());
        buf.putInt(req.topicData().size());
        for (var topic : req.topicData()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionData().size());
            for (var pd : topic.partitionData()) {
                buf.putInt(pd.index());
                KafkaCodecPrimitives.writeBytesField(buf, pd.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceRequest decodeRequestV0(ByteBuffer buf) {
        short acks = buf.getShort();
        int timeout = buf.getInt();
        int topicCount = buf.getInt();
        List<ProduceRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceRequest.PartitionData> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new ProduceRequest.PartitionData(index, records));
            }
            topics.add(new ProduceRequest.TopicData(name, partitions));
        }
        // TransactionalId does not exist at v0 (added v3) — no transaction at v0.
        return new ProduceRequest(null, acks, timeout, topics);
    }

    // ===== v3 — request: TransactionalId(nullable string), then the v0 body =====
    // ===== v3 — response byte-identical to v2 (shared V2 methods below) =====

    private static byte[] encodeRequestV3(ProduceRequest req) {
        // Fixed overhead: TransactionalId(nullable string: 2 + name if present)
        // + Acks(int16) + TimeoutMs(int32) + topic count(int32).
        int size = 10 + (req.transactionalId() != null
                ? 2 + req.transactionalId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                : 2);
        for (var topic : req.topicData()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (var pd : topic.partitionData()) {
                size += 8 + (pd.records() != null ? pd.records().length : 0);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeNullableString(buf, req.transactionalId());
        buf.putShort(req.acks());
        buf.putInt(req.timeoutMs());
        buf.putInt(req.topicData().size());
        for (var topic : req.topicData()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionData().size());
            for (var pd : topic.partitionData()) {
                buf.putInt(pd.index());
                KafkaCodecPrimitives.writeBytesField(buf, pd.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceRequest decodeRequestV3(ByteBuffer buf) {
        // Leading nullable TransactionalId:string — the only v3 delta vs v0.
        String transactionalId = KafkaCodecPrimitives.readNullableString(buf);
        short acks = buf.getShort();
        int timeout = buf.getInt();
        int topicCount = buf.getInt();
        List<ProduceRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceRequest.PartitionData> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                byte[] records = KafkaCodecPrimitives.readBytesField(buf);
                partitions.add(new ProduceRequest.PartitionData(index, records));
            }
            topics.add(new ProduceRequest.TopicData(name, partitions));
        }
        return new ProduceRequest(transactionalId, acks, timeout, topics);
    }

    private static byte[] encodeResponseV0(ProduceResponse resp) {
        // Fixed overhead: topic count(int32) = 4.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition response: 4 (index) + 2 (error) + 8 (offset) = 14.
        int size = 4;
        for (var topic : resp.responses()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            size += 14 * topic.partitionResponses().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.responses().size());
        for (var topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionResponses().size());
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs does not exist at v0 (added v2) — discarded.
            }
        }
        // ThrottleTimeMs does not exist at v0 (added v1) — discarded.
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV0(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs absent at v0 (added v2) — defaulted.
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset, -1));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // ThrottleTimeMs absent at v0 (added v1) — defaulted.
        return new ProduceResponse(topics, 0);
    }

    // ===== v1 — request byte-identical to v0 (shared V0 methods above).
    // ===== v1 — response: TopicData array (v0 partition layout), then ThrottleTimeMs(int32) =====

    private static byte[] encodeResponseV1(ProduceResponse resp) {
        // Fixed overhead: topic count(int32) + ThrottleTimeMs(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition response: 4 (index) + 2 (error) + 8 (offset) = 14.
        int size = 8;
        for (var topic : resp.responses()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            size += 14 * topic.partitionResponses().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.responses().size());
        for (var topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionResponses().size());
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs does not exist until v2 — discarded.
            }
        }
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV1(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs absent at v1 (added v2) — defaulted.
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset, -1));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // Trailing ThrottleTimeMs:int32 — signed int32 per spec.
        int throttleTimeMs = buf.getInt();
        return new ProduceResponse(topics, throttleTimeMs);
    }

    // ===== v2 — request byte-identical to v0 (shared V0 methods above).
    // ===== v2 — response: per-partition LogAppendTimeMs(int64) after BaseOffset, trailing ThrottleTimeMs(int32) =====

    private static byte[] encodeResponseV2(ProduceResponse resp) {
        // Fixed overhead: topic count(int32) + ThrottleTimeMs(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition response: 4 (index) + 2 (error) + 8 (offset) + 8 (logAppendTime) = 22.
        int size = 8;
        for (var topic : resp.responses()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            size += 22 * topic.partitionResponses().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.responses().size());
        for (var topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionResponses().size());
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs:int64 — spec default -1 when the broker has no value;
                // the carried model value is written verbatim.
                buf.putLong(pr.logAppendTimeMs());
            }
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV2(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs:int64 — new in v2, spec default -1 (ignorable).
                long logAppendTimeMs = buf.getLong();
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset, logAppendTimeMs));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        int throttleTimeMs = buf.getInt();
        return new ProduceResponse(topics, throttleTimeMs);
    }

    // ===== v5 — request unchanged vs v3 (shared V3 methods above).
    // ===== v5 — response: per-partition LogStartOffset(int64) after LogAppendTimeMs,
    // =====   trailing ThrottleTimeMs(int32) retained (partition width 22 to 30 bytes) =====

    private static byte[] encodeResponseV5(ProduceResponse resp) {
        // Fixed overhead: topic count(int32) + ThrottleTimeMs(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition response: 4 (index) + 2 (error) + 8 (offset) + 8 (logAppendTime)
        // + 8 (logStartOffset) = 30.
        int size = 8;
        for (var topic : resp.responses()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            size += 30 * topic.partitionResponses().size();
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.responses().size());
        for (var topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionResponses().size());
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs:int64 — v2+, carried value written verbatim.
                buf.putLong(pr.logAppendTimeMs());
                // LogStartOffset:int64 — new in v5, spec default -1 when the broker
                // has no value; the carried model value is written verbatim.
                buf.putLong(pr.logStartOffset());
            }
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV5(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs:int64 — v2+, spec default -1.
                long logAppendTimeMs = buf.getLong();
                // LogStartOffset:int64 — new in v5, spec default -1.
                long logStartOffset = buf.getLong();
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset,
                        logAppendTimeMs, logStartOffset));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        int throttleTimeMs = buf.getInt();
        return new ProduceResponse(topics, throttleTimeMs);
    }

    // ===== v8 — request unchanged vs v7 (shared V3 methods above).
    // ===== v8 — response: per-partition RecordErrors (a count-prefixed array of
    // =====   {BatchIndex:int32, BatchIndexErrorMessage:string|null}) and a trailing
    // =====   ErrorMessage(string|null) after LogStartOffset (both ignorable) =====

    private static byte[] encodeResponseV8(ProduceResponse resp) {
        // Fixed overhead: topic count(int32) + ThrottleTimeMs(int32) = 8.
        // Per topic: 4 (partition count) + 2 (name length) + name.
        // Per partition response: 4 (index) + 2 (error) + 8 (baseOffset) + 8 (logAppendTime)
        // + 8 (logStartOffset) = 30, plus 4 (recordErrors count) + per-entry 4 (batchIndex)
        // + 2 (len) + msg, plus 2 (len) + msg for ErrorMessage (0 each when null).
        int size = 8;
        for (var topic : resp.responses()) {
            size += 4 + 2 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (var pr : topic.partitionResponses()) {
                size += 30 + 4;
                var errs = pr.recordErrors();
                if (errs != null) {
                    for (var e : errs) {
                        int entryLen = e.batchIndexErrorMessage() == null ? 0
                                : e.batchIndexErrorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                        size += 4 + 2 + entryLen;
                    }
                }
                var msg = pr.errorMessage();
                if (msg != null) {
                    size += 2 + msg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                }
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(resp.responses().size());
        for (var topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitionResponses().size());
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs:int64 — v2+, carried value written verbatim.
                buf.putLong(pr.logAppendTimeMs());
                // LogStartOffset:int64 — v5+, spec default -1.
                buf.putLong(pr.logStartOffset());
                // RecordErrors:[]BatchIndexAndErrorMessage — v8+, ignorable; write an
                // empty array when the model carries none.
                var errs = pr.recordErrors();
                buf.putInt(errs == null ? 0 : errs.size());
                if (errs != null) {
                    for (var e : errs) {
                        buf.putInt(e.batchIndex());
                        KafkaCodecPrimitives.writeNullableString(buf, e.batchIndexErrorMessage());
                    }
                }
                // ErrorMessage:string|null — v8+, ignorable.
                KafkaCodecPrimitives.writeNullableString(buf, pr.errorMessage());
            }
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV8(ByteBuffer buf) {
        int topicCount = buf.getInt();
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs:int64 — v2+, spec default -1.
                long logAppendTimeMs = buf.getLong();
                // LogStartOffset:int64 — v5+, spec default -1.
                long logStartOffset = buf.getLong();
                // RecordErrors:[]BatchIndexAndErrorMessage — v8+, ignorable.
                int errorCount = buf.getInt();
                List<ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage> errs =
                        new ArrayList<>(errorCount);
                for (int k = 0; k < errorCount; k++) {
                    int batchIndex = buf.getInt();
                    String batchIndexErrorMessage = KafkaCodecPrimitives.readNullableString(buf);
                    errs.add(new ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage(
                            batchIndex, batchIndexErrorMessage));
                }
                // ErrorMessage:string|null — v8+, ignorable.
                String errorMessage = KafkaCodecPrimitives.readNullableString(buf);
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset,
                        logAppendTimeMs, logStartOffset, errs, errorMessage));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        int throttleTimeMs = buf.getInt();
        return new ProduceResponse(topics, throttleTimeMs);
    }

    // ===== v9 — request/response: flexible encoding (Kafka 3.0+).
    // ===== Field layouts identical to v3 (request) and v8 (response); every array count
    // ===== and string/bytes length becomes an unsigned varint (value + 1, null string =
    // ===== varint 0, null bytes = varint 1). Fixed-width integers are unchanged.
    // ===== The 3.6.1 spec declares no tagged_fields trailer, so the body ends with the
    // ===== last field. The flexible header bit (apiKey | 0x8000) is frame-level —
    // ===== see KafkaCodec.encodeRequest and ApiKey.isFlexible.

    private static byte[] encodeRequestV9(ProduceRequest req) {
        // Overhead estimate: every count/length varint is worst-case 5 bytes.
        // TransactionalId: 5 (varint len) + name; Acks(2) + TimeoutMs(4);
        // topic count(5); per topic: 5 (part count varint) + 5 (name len varint) + name;
        // per partition: 4 (index) + 5 (records len varint) + record length.
        int size = 2 + 4;
        if (req.transactionalId() != null) {
            size += 5 + req.transactionalId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } else {
            size += 1; // null = varint 0
        }
        size += 5; // topic count varint
        for (var topic : req.topicData()) {
            size += 5 + 5 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (var pd : topic.partitionData()) {
                size += 4 + 5 + (pd.records() != null ? pd.records().length : 1);
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        // TransactionalId: nullable compact string (null = varint 0).
        KafkaCodecPrimitives.writeCompactString(buf, req.transactionalId());
        buf.putShort(req.acks());
        buf.putInt(req.timeoutMs());
        // TopicData:[]TopicProduceData — compact array (count + 1).
        KafkaCodecPrimitives.writeVarint(buf, req.topicData().size() + 1);
        for (var topic : req.topicData()) {
            // Name: non-nullable compact string.
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, topic.name());
            // PartitionData:[]PartitionProduceData — compact array.
            KafkaCodecPrimitives.writeVarint(buf, topic.partitionData().size() + 1);
            for (var pd : topic.partitionData()) {
                buf.putInt(pd.index());
                // Records: nullable compact bytes (null = varint 1).
                KafkaCodecPrimitives.writeCompactBytes(buf, pd.records());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceRequest decodeRequestV9(ByteBuffer buf) {
        // Leading nullable TransactionalId — v3+ field, now a compact string.
        String transactionalId = KafkaCodecPrimitives.readCompactString(buf);
        short acks = buf.getShort();
        int timeout = buf.getInt();
        // TopicData:[]TopicProduceData — compact array count.
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<ProduceRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<ProduceRequest.PartitionData> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                byte[] records = KafkaCodecPrimitives.readCompactBytes(buf);
                partitions.add(new ProduceRequest.PartitionData(index, records));
            }
            topics.add(new ProduceRequest.TopicData(name, partitions));
        }
        return new ProduceRequest(transactionalId, acks, timeout, topics);
    }

    private static byte[] encodeResponseV9(ProduceResponse resp) {
        // Overhead estimate: every count/length varint is worst-case 5 bytes.
        // Topic count(5) + ThrottleTimeMs(4).
        int size = 5 + 4;
        for (var topic : resp.responses()) {
            size += 5 + 5 + topic.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (var pr : topic.partitionResponses()) {
                // 4 (index) + 2 (error) + 8 (baseOffset) + 8 (logAppendTime) + 8 (logStartOffset) = 30,
                // + 5 (recordErrors count varint) + per-entry 4 (batchIndex) + 5 (msg len varint) + msg,
                // + 5 (errorMessage len varint, 1 when null) + msg.
                size += 30 + 5;
                var errs = pr.recordErrors();
                if (errs != null) {
                    for (var e : errs) {
                        size += 4 + 5 + (e.batchIndexErrorMessage() == null ? 1
                                : e.batchIndexErrorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                    }
                }
                var msg = pr.errorMessage();
                size += msg == null ? 1
                        : 5 + msg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        // Responses:[]TopicProduceResponse — compact array (count + 1).
        KafkaCodecPrimitives.writeVarint(buf, resp.responses().size() + 1);
        for (var topic : resp.responses()) {
            // Name: non-nullable compact string.
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, topic.name());
            // PartitionResponses:[]PartitionProduceResponse — compact array.
            KafkaCodecPrimitives.writeVarint(buf, topic.partitionResponses().size() + 1);
            for (var pr : topic.partitionResponses()) {
                buf.putInt(pr.partitionIndex());
                buf.putShort(pr.errorCode());
                buf.putLong(pr.baseOffset());
                // LogAppendTimeMs:int64 — v2+, carried value written verbatim.
                buf.putLong(pr.logAppendTimeMs());
                // LogStartOffset:int64 — v5+, spec default -1.
                buf.putLong(pr.logStartOffset());
                // RecordErrors:[]BatchIndexAndErrorMessage — v8+, ignorable; compact array.
                var errs = pr.recordErrors();
                KafkaCodecPrimitives.writeVarint(buf, (errs == null ? 0 : errs.size()) + 1);
                if (errs != null) {
                    for (var e : errs) {
                        buf.putInt(e.batchIndex());
                        // BatchIndexErrorMessage: nullable compact string.
                        KafkaCodecPrimitives.writeCompactString(buf, e.batchIndexErrorMessage());
                    }
                }
                // ErrorMessage: nullable compact string — v8+, ignorable.
                KafkaCodecPrimitives.writeCompactString(buf, pr.errorMessage());
            }
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        buf.putInt(resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ProduceResponse decodeResponseV9(ByteBuffer buf) {
        // Responses:[]TopicProduceResponse — compact array count.
        int topicCount = KafkaCodecPrimitives.readVarint(buf) - 1;
        List<ProduceResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
            int partCount = KafkaCodecPrimitives.readVarint(buf) - 1;
            List<ProduceResponse.PartitionResponse> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int index = buf.getInt();
                short errorCode = buf.getShort();
                long baseOffset = buf.getLong();
                // LogAppendTimeMs:int64 — v2+, spec default -1.
                long logAppendTimeMs = buf.getLong();
                // LogStartOffset:int64 — v5+, spec default -1.
                long logStartOffset = buf.getLong();
                // RecordErrors:[]BatchIndexAndErrorMessage — v8+, ignorable; compact array.
                int errorCount = KafkaCodecPrimitives.readVarint(buf) - 1;
                List<ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage> errs =
                        new ArrayList<>(errorCount);
                for (int k = 0; k < errorCount; k++) {
                    int batchIndex = buf.getInt();
                    String batchIndexErrorMessage = KafkaCodecPrimitives.readCompactString(buf);
                    errs.add(new ProduceResponse.PartitionResponse.BatchIndexAndErrorMessage(
                            batchIndex, batchIndexErrorMessage));
                }
                // ErrorMessage: nullable compact string — v8+, ignorable.
                String errorMessage = KafkaCodecPrimitives.readCompactString(buf);
                partitions.add(new ProduceResponse.PartitionResponse(index, errorCode, baseOffset,
                        logAppendTimeMs, logStartOffset, errs, errorMessage));
            }
            topics.add(new ProduceResponse.TopicResponse(name, partitions));
        }
        // Trailing ThrottleTimeMs:int32 — retained from v1.
        int throttleTimeMs = buf.getInt();
        return new ProduceResponse(topics, throttleTimeMs);
    }
}
