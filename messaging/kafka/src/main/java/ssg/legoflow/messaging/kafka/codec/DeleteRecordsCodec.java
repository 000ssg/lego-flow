package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.DeleteRecordsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteRecordsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka DeleteRecords request (API key 21) and response — truncate partition
 * log offsets to the requested offsets.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v2 is encoded so a frame
 * carrying any DeleteRecords version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/DeleteRecordsRequest.json} and
 * {@code DeleteRecordsResponse.json}):
 * <ul>
 *   <li>v0 (both): baseline. Request: {@code Topics[] { Name, Partitions[] { PartitionIndex,
 *       Offset } }} + {@code timeoutMs}. Response: leading {@code ThrottleTimeMs} (int32,
 *       present from v0) + per-topic/partition results with {@code LowWatermark} and
 *       {@code ErrorCode}. The model does not expose {@code ThrottleTimeMs}: the spec default
 *       ({@code 0}) is written, read + discarded.</li>
 *   <li>v1 (both): byte-identical to v0 (spec: "Version 1 is the same as version 0").</li>
 *   <li>v2 (both): flexible encoding (KIP-482): compact arrays/strings and tagged fields
 *       on every struct level (topic, partition, top). No tagged fields are emitted (all
 *       zero); received tagged fields are skipped. All fields keep fixed width except
 *       arrays (varint count = N+1) and the topic name (compact string).</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class DeleteRecordsCodec {

    /** Pinned version for client/broker DeleteRecords interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v2+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 2;

    /** Highest API version with a defined wire format in the spec (v2). */
    private static final short MAX_VERSION = 2;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private DeleteRecordsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "DeleteRecords v" + version + " not implemented (range 0–2)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteRecordsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(DeleteRecordsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a DeleteRecords request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, DeleteRecordsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(req);
        }
        return flexibleRequest(req);
    }

    /**
     * Decode a DeleteRecords request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the request body
     * @return the decoded request
     */
    public static DeleteRecordsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(buf);
        }
        return flexibleRequest(buf);
    }

    // ── Fixed-width request (v0–v1, byte-identical) ─────────────────────
    // Order: numTopics(int32), [ Name(int16 string), numPartitions(int32),
    //   (PartitionIndex(int32), Offset(int64)) ], timeoutMs(int32)

    private static byte[] fixedRequest(DeleteRecordsRequest req) {
        int size = 4; // numTopics
        for (DeleteRecordsRequest.TopicData t : req.topics()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // numPartitions
            for (int i = 0; i < t.partitions().size(); i++) {
                size += 4 + 8; // PartitionIndex + Offset
            }
        }
        size += 4; // timeoutMs

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.topics().size());
        for (DeleteRecordsRequest.TopicData t : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (DeleteRecordsRequest.PartitionData p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.offset());
            }
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteRecordsRequest fixedRequest(ByteBuffer b) {
        int topicCount = b.getInt();
        List<DeleteRecordsRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(b);
            int partCount = b.getInt();
            List<DeleteRecordsRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsRequest.PartitionData(b.getInt(), b.getLong()));
            }
            topics.add(new DeleteRecordsRequest.TopicData(name, parts));
        }
        int timeoutMs = b.getInt();
        return new DeleteRecordsRequest(topics, timeoutMs);
    }

    // ── Flexible request (v2) ───────────────────────────────────────────
    // Order: numTopics(varint, KIP-482 N+1), [ Name(compact), numPartitions(varint, N+1),
    //   (PartitionIndex(int32), Offset(int64)), endTags ] ], timeoutMs(int32), endTags

    private static byte[] flexibleRequest(DeleteRecordsRequest req) {
        int n = req.topics().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numTopics (KIP-482: N+1)
        for (DeleteRecordsRequest.TopicData t : req.topics()) {
            size += compactStringSize(t.name()); // Name
            size += KafkaCodecPrimitives.varintSize(t.partitions().size() + 1); // numPartitions
            size += t.partitions().size() * 12; // PartitionIndex + Offset per partition
            size += t.partitions().size(); // per-partition endTags (1 byte each, value 0)
            size += 1; // per-topic endTags (0)
        }
        size += 4; // timeoutMs (fixed-width even in flexible)
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DeleteRecordsRequest.TopicData t : req.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            for (DeleteRecordsRequest.PartitionData p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.offset());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        buf.putInt(req.timeoutMs());
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteRecordsRequest flexibleRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DeleteRecordsRequest.TopicData> topics = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<DeleteRecordsRequest.PartitionData> parts = new ArrayList<>(Math.max(partCount, 0));
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsRequest.PartitionData(b.getInt(), b.getLong()));
                KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new DeleteRecordsRequest.TopicData(name, parts));
        }
        int timeoutMs = b.getInt();
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteRecordsRequest(topics, timeoutMs);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteRecordsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(DeleteRecordsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a DeleteRecords response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, DeleteRecordsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode a DeleteRecords response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the response body
     * @return the decoded response
     */
    public static DeleteRecordsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v1, byte-identical) ────────────────────
    // Order: ThrottleTimeMs(int32, present from v0), numTopics(int32),
    //   [ Name(int16 string), numPartitions(int32),
    //     (PartitionIndex(int32), LowWatermark(int64), ErrorCode(int16)) ]

    private static byte[] fixedResponse(DeleteRecordsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 4; // numTopics
        for (DeleteRecordsResponse.TopicData t : resp.topics()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // numPartitions
            size += t.partitions().size() * (4 + 8 + 2); // PartitionIndex + LowWatermark + ErrorCode
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putInt(resp.topics().size());
        for (DeleteRecordsResponse.TopicData t : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (DeleteRecordsResponse.PartitionData p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.lowWatermark());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteRecordsResponse fixedResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int topicCount = b.getInt();
        List<DeleteRecordsResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(b);
            int partCount = b.getInt();
            List<DeleteRecordsResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsResponse.PartitionData(
                        b.getInt(), b.getLong(), b.getShort()));
            }
            topics.add(new DeleteRecordsResponse.TopicData(name, parts));
        }
        return new DeleteRecordsResponse(topics);
    }

    // ── Flexible response (v2) ──────────────────────────────────────────
    // Order: ThrottleTimeMs(int32), numTopics(varint, KIP-482 N+1),
    //   [ Name(compact), numPartitions(varint, N+1),
    //     (PartitionIndex(int32), LowWatermark(int64), ErrorCode(int16), endTags) ],
    //   endTags ] ], endTags

    private static byte[] flexibleResponse(DeleteRecordsResponse resp) {
        int n = resp.topics().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(n + 1); // numTopics (KIP-482: N+1)
        for (DeleteRecordsResponse.TopicData t : resp.topics()) {
            size += compactStringSize(t.name()); // Name
            size += KafkaCodecPrimitives.varintSize(t.partitions().size() + 1); // numPartitions
            for (int i = 0; i < t.partitions().size(); i++) {
                size += 14 + 1; // PartitionIndex + LowWatermark + ErrorCode + per-partition endTags
            }
            size += 1; // per-topic endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DeleteRecordsResponse.TopicData t : resp.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            for (DeleteRecordsResponse.PartitionData p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.lowWatermark());
                buf.putShort(p.errorCode());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteRecordsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DeleteRecordsResponse.TopicData> topics = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<DeleteRecordsResponse.PartitionData> parts = new ArrayList<>(Math.max(partCount, 0));
            for (int j = 0; j < partCount; j++) {
                parts.add(new DeleteRecordsResponse.PartitionData(
                        b.getInt(), b.getLong(), b.getShort()));
                KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new DeleteRecordsResponse.TopicData(name, parts));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteRecordsResponse(topics);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a non-null string in flexible (compact) encoding. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }
}
