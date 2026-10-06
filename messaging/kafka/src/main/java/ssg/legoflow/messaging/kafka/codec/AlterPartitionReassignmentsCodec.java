package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.AlterPartitionReassignmentsRequest;
import ssg.legoflow.messaging.kafka.protocol.AlterPartitionReassignmentsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka AlterPartitionReassignments request (API key 45) and response —
 * alter the replication factor and/or replica assignment for a set of partitions.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends). v0 is the only spec version and it uses flexible
 * encoding (KIP-482) from the start — compact arrays/strings + tagged fields on every
 * struct level (partition, topic, top).
 * <p>
 * Wire order (mirrors {@code doc/spec/message/AlterPartitionReassignmentsRequest.json}
 * and {@code AlterPartitionReassignmentsResponse.json}):
 * <ul>
 *   <li>request: {@code TimeoutMs(int32)},
 *       {@code Topics(compact array; nullable) [ TopicName(nullable compact string),
 *       Partitions(compact array; nullable) [ PartitionId(int32),
 *       ProposedReplicas(nullable compact array of int32), endTags ], endTags ], endTags}.</li>
 *   <li>response: {@code ThrottleTimeMs(int32)}, {@code ErrorCode(int16)},
 *       {@code ErrorMessage(nullable compact string)},
 *       {@code Topics(compact array; nullable) [ TopicName(nullable compact string),
 *       Partitions(compact array; nullable) [ PartitionId(int32), ErrorCode(int16),
 *       ErrorMessage(nullable compact string), endTags ], endTags ], endTags}.</li>
 * </ul>
 * Response fields not exposed by the in-house model ({@code ThrottleTimeMs},
 * {@code ErrorMessage}, per-partition {@code ErrorMessage}) are written with spec
 * defaults (null) and read + discarded.
 *
 * @since 0.1.0
 */
public final class AlterPartitionReassignmentsCodec {

    /** Pinned version for client/broker AlterPartitionReassignments interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Highest (and only) API version with a defined wire format in the spec (v0). */
    private static final short MAX_VERSION = 0;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private AlterPartitionReassignmentsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "AlterPartitionReassignments v" + version + " not implemented (range 0–0)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link AlterPartitionReassignmentsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(AlterPartitionReassignmentsRequest req) {
        return flexibleRequest(req);
    }

    /**
     * Encode an AlterPartitionReassignments request body at the given API version.
     *
     * @param version the API version (0)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, AlterPartitionReassignmentsRequest req) {
        checkVersion(version);
        return flexibleRequest(req);
    }

    /**
     * Decode an AlterPartitionReassignments request body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the request body
     * @return the decoded request
     */
    public static AlterPartitionReassignmentsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        return flexibleRequest(buf);
    }

    // ── Flexible request (v0) ───────────────────────────────────────────
    // Order: TimeoutMs(int32, fixed even in flexible),
    //   Topics(varint, KIP-482 N+1; never null) [ TopicName(nullable compact),
    //     Partitions(varint, KIP-482 N+1; never null) [ PartitionId(int32),
    //       ProposedReplicas(varint, N+1; null = 0) [ int32 ], endTags ], endTags ],
    //   endTags

    private static byte[] flexibleRequest(AlterPartitionReassignmentsRequest req) {
        int topicCount = req.topics().size();
        int size = 4; // TimeoutMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(topicCount + 1); // Topics (KIP-482: N+1)
        for (AlterPartitionReassignmentsRequest.TopicReassignment t : req.topics()) {
            size += compactNullableStringSize(t.topic()); // TopicName (nullable)
            int partCount = t.partitions().size();
            size += KafkaCodecPrimitives.varintSize(partCount + 1); // Partitions (KIP-482: N+1)
            for (AlterPartitionReassignmentsRequest.PartitionReassignment p : t.partitions()) {
                size += 4; // PartitionId
                size += compactNullableIntArraySize(p.replicas()); // ProposedReplicas (nullable)
                size += 1; // per-partition endTags (0)
            }
            size += 1; // per-topic endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.timeoutMs());
        KafkaCodecPrimitives.writeVarint(buf, topicCount + 1); // KIP-482: N+1
        for (AlterPartitionReassignmentsRequest.TopicReassignment t : req.topics()) {
            writeCompactNullableString(buf, t.topic());
            int partCount = t.partitions().size();
            KafkaCodecPrimitives.writeVarint(buf, partCount + 1); // KIP-482: N+1
            for (AlterPartitionReassignmentsRequest.PartitionReassignment p : t.partitions()) {
                buf.putInt(p.partition());
                writeCompactNullableIntArray(buf, p.replicas()); // null = varint 0
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterPartitionReassignmentsRequest flexibleRequest(ByteBuffer b) {
        int timeoutMs = b.getInt();
        int topicCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<AlterPartitionReassignmentsRequest.TopicReassignment> topics = new ArrayList<>(Math.max(topicCount, 0));
        for (int i = 0; i < topicCount; i++) {
            String topic = readCompactNullable(b);
            int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<AlterPartitionReassignmentsRequest.PartitionReassignment> parts = new ArrayList<>(Math.max(partCount, 0));
            for (int j = 0; j < partCount; j++) {
                int partition = b.getInt();
                List<Integer> replicas = readCompactNullableIntArray(b); // 0 = null
                KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                parts.add(new AlterPartitionReassignmentsRequest.PartitionReassignment(partition, replicas));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new AlterPartitionReassignmentsRequest.TopicReassignment(topic, parts));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new AlterPartitionReassignmentsRequest(timeoutMs, topics);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link AlterPartitionReassignmentsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(AlterPartitionReassignmentsResponse resp) {
        return flexibleResponse(resp);
    }

    /**
     * Encode an AlterPartitionReassignments response body at the given API version.
     *
     * @param version the API version (0)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, AlterPartitionReassignmentsResponse resp) {
        checkVersion(version);
        return flexibleResponse(resp);
    }

    /**
     * Decode an AlterPartitionReassignments response body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the response body
     * @return the decoded response
     */
    public static AlterPartitionReassignmentsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        return flexibleResponse(buf);
    }

    // ── Flexible response (v0) ──────────────────────────────────────────
    // Order: ThrottleTimeMs(int32), ErrorCode(int16), ErrorMessage(nullable compact),
    //   Topics(varint, KIP-482 N+1; never null) [ TopicName(nullable compact),
    //     Partitions(varint, KIP-482 N+1; never null) [ PartitionId(int32), ErrorCode(int16),
    //       ErrorMessage(nullable compact), endTags ], endTags ], endTags

    private static byte[] flexibleResponse(AlterPartitionReassignmentsResponse resp) {
        int topicCount = resp.topics().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += 2; // ErrorCode
        size += 1; // ErrorMessage null (varint 0)
        size += KafkaCodecPrimitives.varintSize(topicCount + 1); // Topics (KIP-482: N+1)
        for (AlterPartitionReassignmentsResponse.TopicResult t : resp.topics()) {
            size += compactNullableStringSize(t.topic()); // TopicName (nullable)
            int partCount = t.partitions().size();
            size += KafkaCodecPrimitives.varintSize(partCount + 1); // Partitions (KIP-482: N+1)
            for (AlterPartitionReassignmentsResponse.PartitionResult p : t.partitions()) {
                size += 4; // PartitionId
                size += 2; // ErrorCode
                size += 1; // ErrorMessage null (varint 0)
                size += 1; // per-partition endTags (0)
            }
            size += 1; // per-topic endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS); // ThrottleTimeMs (spec default)
        buf.putShort(resp.errorCode());
        KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage null (spec default)
        KafkaCodecPrimitives.writeVarint(buf, topicCount + 1); // KIP-482: N+1
        for (AlterPartitionReassignmentsResponse.TopicResult t : resp.topics()) {
            writeCompactNullableString(buf, t.topic());
            int partCount = t.partitions().size();
            KafkaCodecPrimitives.writeVarint(buf, partCount + 1); // KIP-482: N+1
            for (AlterPartitionReassignmentsResponse.PartitionResult p : t.partitions()) {
                buf.putInt(p.partition());
                buf.putShort(p.errorCode());
                KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage null (spec default)
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterPartitionReassignmentsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        skipCompactNullable(b); // ErrorMessage (nullable compact), read + discarded
        int topicCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<AlterPartitionReassignmentsResponse.TopicResult> topics = new ArrayList<>(Math.max(topicCount, 0));
        for (int i = 0; i < topicCount; i++) {
            String topic = readCompactNullable(b);
            int partCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<AlterPartitionReassignmentsResponse.PartitionResult> parts = new ArrayList<>(Math.max(partCount, 0));
            for (int j = 0; j < partCount; j++) {
                int partition = b.getInt();
                short partError = b.getShort();
                skipCompactNullable(b); // per-partition ErrorMessage, read + discarded
                KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                parts.add(new AlterPartitionReassignmentsResponse.PartitionResult(partition, partError));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new AlterPartitionReassignmentsResponse.TopicResult(topic, parts));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new AlterPartitionReassignmentsResponse(errorCode, topics);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a nullable compact string (varint 0 if null). */
    private static int compactNullableStringSize(String s) {
        if (s == null) {
            return 1; // varint 0
        }
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }

    /** Size of a nullable compact array of fixed-width int32 (varint 0 if null). */
    private static int compactNullableIntArraySize(List<Integer> values) {
        if (values == null) {
            return 1; // varint 0
        }
        return KafkaCodecPrimitives.varintSize(values.size() + 1) + 4 * values.size();
    }

    /** Write a nullable compact string (varint 0 if null). */
    private static void writeCompactNullableString(ByteBuffer buf, String s) {
        if (s == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0);
            return;
        }
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        KafkaCodecPrimitives.writeVarint(buf, len + 1);
        buf.put(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Write a nullable compact array of fixed-width int32 (varint 0 if null). */
    private static void writeCompactNullableIntArray(ByteBuffer buf, List<Integer> values) {
        if (values == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0);
            return;
        }
        KafkaCodecPrimitives.writeVarint(buf, values.size() + 1); // KIP-482: N+1
        for (int v : values) {
            buf.putInt(v);
        }
    }

    /** Read a nullable compact string. */
    private static String readCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len == 0) {
            return null;
        }
        byte[] data = new byte[len - 1];
        b.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Skip a nullable compact string. */
    private static void skipCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len != 0) {
            b.position(b.position() + len - 1);
        }
    }

    /** Read a nullable compact array of fixed-width int32 (varint 0 = null). */
    private static List<Integer> readCompactNullableIntArray(ByteBuffer b) {
        int count = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
        if (count == 0) {
            return null;
        }
        List<Integer> values = new ArrayList<>(count - 1);
        for (int i = 0; i < count - 1; i++) {
            values.add(b.getInt());
        }
        return values;
    }
}
