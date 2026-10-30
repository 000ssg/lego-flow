package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ListPartitionReassignmentsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListPartitionReassignmentsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka ListPartitionReassignments request (API key 46) and response —
 * list ongoing partition reassignments.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline that
 * {@code KafkaAdminClient} sends). v0 is the only spec version and it uses flexible
 * encoding (KIP-482) from the start — compact arrays/strings + tagged fields on every
 * struct level (partition, topic, top).
 * <p>
 * Wire order (mirrors {@code doc/spec/message/ListPartitionReassignmentsRequest.json}
 * and {@code ListPartitionReassignmentsResponse.json}):
 * <ul>
 *   <li>request: {@code TimeoutMs(int32)},
 *       {@code Topics(compact array; nullable) [ TopicName(nullable compact string),
 *       PartitionIndexes(compact array of int32), endTags ], endTags}.</li>
 *   <li>response: {@code ThrottleTimeMs(int32)}, {@code ErrorCode(int16)},
 *       {@code ErrorMessage(nullable compact string)},
 *       {@code Topics(compact array) [ TopicName(nullable compact string),
 *       Partitions(compact array) [ PartitionIndex(int32), Replicas(compact array of int32),
 *       AddingReplicas(compact array of int32), RemovingReplicas(compact array of int32),
 *       endTags ], endTags ], endTags}.</li>
 * </ul>
 * Response fields not exposed by the in-house model ({@code ThrottleTimeMs},
 * {@code ErrorMessage}) are written with spec defaults (null/0) and read + discarded.
 *
 * @since 0.1.0
 */
public final class ListPartitionReassignmentsCodec {

    /** Pinned version for client/broker ListPartitionReassignments interaction (v0). */
    public static final short MIN_VERSION = 0;

    /** Highest (and only) API version with a defined wire format in the spec (v0). */
    public static final short MAX_VERSION = 0;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private ListPartitionReassignmentsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "ListPartitionReassignments v" + version + " not implemented (range 0–0)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────
    /**
     * Encode a ListPartitionReassignments request body at the given API version.
     *
     * @param version the API version (0)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, ListPartitionReassignmentsRequest req) {
        checkVersion(version);
        return flexibleRequest(req);
    }

    /**
     * Decode a ListPartitionReassignments request body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the request body
     * @return the decoded request
     */
    public static ListPartitionReassignmentsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        return flexibleRequest(buf);
    }

    // ── Flexible request (v0) ───────────────────────────────────────────
    // Order: TimeoutMs(int32, fixed even in flexible),
    //   Topics(varint, KIP-482 N+1; null = 0) [ TopicName(nullable compact),
    //     PartitionIndexes(varint, N+1; null = 0) [ int32 ], endTags ], endTags

    private static byte[] flexibleRequest(ListPartitionReassignmentsRequest req) {
        List<ListPartitionReassignmentsRequest.TopicData> topics = req.topics();
        int topicCount = topics == null ? 0 : topics.size();
        int size = 4; // TimeoutMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(topicCount + 1); // Topics (KIP-482: N+1)
        if (topics != null) {
            for (ListPartitionReassignmentsRequest.TopicData t : topics) {
                size += compactNullableStringSize(t.topic()); // TopicName (nullable)
                size += compactIntArraySize(t.partitions()); // PartitionIndexes
                size += 1; // per-topic endTags (0)
            }
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.timeoutMs());
        if (topics == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // Topics null (spec default)
        } else {
            KafkaCodecPrimitives.writeVarint(buf, topicCount + 1); // KIP-482: N+1
            for (ListPartitionReassignmentsRequest.TopicData t : topics) {
                writeCompactNullableString(buf, t.topic());
                writeCompactIntArray(buf, t.partitions());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListPartitionReassignmentsRequest flexibleRequest(ByteBuffer b) {
        int timeoutMs = b.getInt();
        int topicCount = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
        if (topicCount == 0) {
            return new ListPartitionReassignmentsRequest(timeoutMs, null);
        }
        List<ListPartitionReassignmentsRequest.TopicData> topics = new ArrayList<>(topicCount - 1);
        for (int i = 0; i < topicCount - 1; i++) {
            String topic = readCompactNullable(b);
            List<Integer> parts = readCompactIntArray(b);
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new ListPartitionReassignmentsRequest.TopicData(topic, parts));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new ListPartitionReassignmentsRequest(timeoutMs, topics);
    }

    // ── Response ─────────────────────────────────────────────────────────
    /**
     * Encode a ListPartitionReassignments response body at the given API version.
     *
     * @param version the API version (0)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, ListPartitionReassignmentsResponse resp) {
        checkVersion(version);
        return flexibleResponse(resp);
    }

    /**
     * Decode a ListPartitionReassignments response body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the response body
     * @return the decoded response
     */
    public static ListPartitionReassignmentsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        return flexibleResponse(buf);
    }

    // ── Flexible response (v0) ──────────────────────────────────────────
    // Order: ThrottleTimeMs(int32), ErrorCode(int16), ErrorMessage(nullable compact),
    //   Topics(varint, KIP-482 N+1; null = 0) [ TopicName(nullable compact),
    //     Partitions(varint, N+1; null = 0) [ PartitionIndex(int32), Replicas(varint, N+1; null = 0) [ int32 ],
    //       AddingReplicas(varint, N+1; null = 0) [ int32 ], RemovingReplicas(varint, N+1; null = 0) [ int32 ],
    //       endTags ], endTags ], endTags

    private static byte[] flexibleResponse(ListPartitionReassignmentsResponse resp) {
        List<ListPartitionReassignmentsResponse.TopicResult> topics = resp.topics();
        int topicCount = topics == null ? 0 : topics.size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += 2; // ErrorCode
        size += 1; // ErrorMessage null (varint 0)
        size += KafkaCodecPrimitives.varintSize(topicCount + 1); // Topics (KIP-482: N+1)
        if (topics != null) {
            for (ListPartitionReassignmentsResponse.TopicResult t : topics) {
                size += compactNullableStringSize(t.topic()); // TopicName (nullable)
                int partCount = t.partitions() == null ? 0 : t.partitions().size();
                size += KafkaCodecPrimitives.varintSize(partCount + 1); // Partitions (KIP-482: N+1)
                if (t.partitions() != null) {
                    for (ListPartitionReassignmentsResponse.PartitionResult p : t.partitions()) {
                        size += 4; // PartitionIndex
                        size += compactIntArraySize(p.replicas()); // Replicas
                        size += compactIntArraySize(p.addingReplicas()); // AddingReplicas
                        size += compactIntArraySize(p.removingReplicas()); // RemovingReplicas
                        size += 1; // per-partition endTags (0)
                    }
                }
                size += 1; // per-topic endTags (0)
            }
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS); // ThrottleTimeMs (spec default)
        buf.putShort(resp.errorCode());
        KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage null (spec default)
        if (topics == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // Topics null (spec default)
        } else {
            KafkaCodecPrimitives.writeVarint(buf, topicCount + 1); // KIP-482: N+1
            for (ListPartitionReassignmentsResponse.TopicResult t : topics) {
                writeCompactNullableString(buf, t.topic());
                List<ListPartitionReassignmentsResponse.PartitionResult> parts = t.partitions();
                int partCount = parts == null ? 0 : parts.size();
                KafkaCodecPrimitives.writeVarint(buf, partCount + 1); // KIP-482: N+1
                if (parts != null) {
                    for (ListPartitionReassignmentsResponse.PartitionResult p : parts) {
                        buf.putInt(p.partition());
                        writeCompactIntArray(buf, p.replicas());
                        writeCompactIntArray(buf, p.addingReplicas());
                        writeCompactIntArray(buf, p.removingReplicas());
                        KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                    }
                }
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListPartitionReassignmentsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        short errorCode = b.getShort();
        skipCompactNullable(b); // ErrorMessage (nullable compact), read + discarded
        int topicCount = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
        if (topicCount == 0) {
            return new ListPartitionReassignmentsResponse(errorCode, null);
        }
        List<ListPartitionReassignmentsResponse.TopicResult> topics = new ArrayList<>(topicCount - 1);
        for (int i = 0; i < topicCount - 1; i++) {
            String topic = readCompactNullable(b);
            int partCount = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
            List<ListPartitionReassignmentsResponse.PartitionResult> parts;
            if (partCount == 0) {
                parts = null;
            } else {
                parts = new ArrayList<>(partCount - 1);
                for (int j = 0; j < partCount - 1; j++) {
                    int partition = b.getInt();
                    List<Integer> replicas = readCompactIntArray(b);
                    List<Integer> adding = readCompactIntArray(b);
                    List<Integer> removing = readCompactIntArray(b);
                    KafkaCodecPrimitives.skipTaggedFields(b); // per-partition
                    parts.add(new ListPartitionReassignmentsResponse.PartitionResult(partition, replicas, adding, removing));
                }
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new ListPartitionReassignmentsResponse.TopicResult(topic, parts));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new ListPartitionReassignmentsResponse(errorCode, topics);
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

    /** Size of a compact array of fixed-width int32 (varint 0 if null). */
    private static int compactIntArraySize(List<Integer> values) {
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

    /** Write a compact array of fixed-width int32 (varint 0 if null). */
    private static void writeCompactIntArray(ByteBuffer buf, List<Integer> values) {
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

    /** Read a compact array of fixed-width int32 (varint 0 = null). */
    private static List<Integer> readCompactIntArray(ByteBuffer b) {
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
