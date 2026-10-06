package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ListOffsetsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListOffsetsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka ListOffsets request (API key 2) and response — find the
 * offset of a partition for a given timestamp.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v1: the
 * earliest version that returns the per-partition {@code Timestamp} +
 * {@code Offset} pair), while the full version range v0–v8 is encoded so a
 * frame carrying any ListOffsets version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/ListOffsetsRequest.json}
 * and {@code ListOffsetsResponse.json}):
 * <ul>
 *   <li>v0 (request): partition carries {@code MaxNumOffsets} (int32) in
 *       addition to {@code PartitionIndex} + {@code Timestamp}; the in-house
 *       model does not expose that field, so the spec default ({@code 1}) is
 *       written and the value is read + discarded on decode.</li>
 *   <li>v0 (response): partition carries {@code OldStyleOffsets} (int64[])
 *       instead of the v1+ {@code Timestamp} + {@code Offset} pair; the model
 *       is reconstructed from the first element of the array.</li>
 *   <li>v2+: request adds {@code IsolationLevel} (int8); response adds
 *       {@code ThrottleTimeMs} (int32). The in-house model does not expose
 *       these fields, so a neutral default is written and the value is read +
 *       discarded on decode.</li>
 *   <li>v4+: request partition adds {@code CurrentLeaderEpoch} (int32);
 *       response partition adds {@code LeaderEpoch} (int32). Same handling.</li>
 *   <li>v6+ (flexible): compact arrays, compact strings and tagged fields
 *       (empty end marker).</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class ListOffsetsCodec {

    /** Pinned version for client/broker ListOffsets interaction (v1). */
    public static final short PINNED_VERSION = 1;

    /** Request {@code ReplicaId} default — -1 selects the coordinator/leader. */
    private static final int DEFAULT_REPLICA_ID = -1;
    /** Request {@code IsolationLevel} default — -1 (read_uncommitted). */
    private static final int DEFAULT_ISOLATION_LEVEL = -1;
    /** Request {@code CurrentLeaderEpoch} default — -1 (unknown). */
    private static final int DEFAULT_LEADER_EPOCH = -1;
    /** Request {@code MaxNumOffsets} default — 1 (single offset to report). */
    private static final int DEFAULT_MAX_NUM_OFFSETS = 1;
    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;
    /** Flexible-encoding versions (v6+): compact arrays + tagged fields. */
    private static final short FLEXIBLE_START = 6;
    /** Highest API version with a defined wire format in the spec (v8). */
    private static final short MAX_VERSION = 8;

    private ListOffsetsCodec() {
    }

    // ── Request ──────────────────────────────────────────────────────────

    /**
     * Encode a {@link ListOffsetsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(ListOffsetsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a {@link ListOffsetsRequest} at the given API version.
     *
     * @param version the API version (0–8)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, ListOffsetsRequest req) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleRequest(version, req);
        }
        return fixedRequest(version, req);
    }

    /**
     * Decode a ListOffsets request body at the given API version.
     *
     * @param version the API version (0–8)
     * @param buf     the request body
     * @return the decoded request
     */
    public static ListOffsetsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleRequest(version, buf);
        }
        return fixedRequest(version, buf);
    }

    // ── Fixed-width request (v0–v5) ──────────────────────────────────────
    // Order: ReplicaId, [IsolationLevel(2+)], numTopics,
    //   [ name, numPartitions, [ PartitionIndex, CurrentLeaderEpoch(4+),
    //     Timestamp, MaxNumOffsets(0-only) ] ]

    private static byte[] fixedRequest(short version, ListOffsetsRequest req) {
        int size = 4; // ReplicaId
        if (version >= 2) {
            size += 1; // IsolationLevel
        }
        size += 4; // numTopics
        for (ListOffsetsRequest.TopicOffsets topic : req.topics()) {
            size += 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 4; // numPartitions
            for (ListOffsetsRequest.PartitionOffsets partition : topic.partitions()) {
                size += 4 + 8; // PartitionIndex + Timestamp
                if (version >= 4) {
                    size += 4; // CurrentLeaderEpoch (v4+)
                }
                if (version == 0) {
                    size += 4; // MaxNumOffsets (v0 only)
                }
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_REPLICA_ID);
        if (version >= 2) {
            buf.put((byte) DEFAULT_ISOLATION_LEVEL);
        }
        buf.putInt(req.topics().size());
        for (ListOffsetsRequest.TopicOffsets topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (ListOffsetsRequest.PartitionOffsets partition : topic.partitions()) {
                buf.putInt(partition.partitionIndex());
                if (version >= 4) {
                    buf.putInt(DEFAULT_LEADER_EPOCH);
                }
                buf.putLong(partition.timestamp());
                if (version == 0) {
                    buf.putInt(DEFAULT_MAX_NUM_OFFSETS);
                }
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListOffsetsRequest fixedRequest(short version, ByteBuffer b) {
        b.getInt(); // ReplicaId, read + discard
        if (version >= 2) {
            b.get(); // IsolationLevel, read + discard
        }
        int numTopics = b.getInt();
        List<ListOffsetsRequest.TopicOffsets> topics = new ArrayList<>(numTopics);
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readString(b);
            int numPartitions = b.getInt();
            List<ListOffsetsRequest.PartitionOffsets> partitions =
                    new ArrayList<>(numPartitions);
            for (int p = 0; p < numPartitions; p++) {
                int partitionIndex = b.getInt();
                if (version >= 4) {
                    b.getInt(); // CurrentLeaderEpoch, read + discard
                }
                long timestamp = b.getLong();
                if (version == 0) {
                    b.getInt(); // MaxNumOffsets, read + discard
                }
                partitions.add(new ListOffsetsRequest.PartitionOffsets(partitionIndex, timestamp));
            }
            topics.add(new ListOffsetsRequest.TopicOffsets(name, partitions));
        }
        return new ListOffsetsRequest(topics);
    }

    // ── Flexible request (v6+) ───────────────────────────────────────────
    // Order: ReplicaId, IsolationLevel, numTopics(varint),
    //   [ name(compact), numPartitions(varint), [ PartitionIndex,
    //     CurrentLeaderEpoch, Timestamp, endTaggedFields ] , endTaggedFields ],
    //   endTaggedFields

    private static byte[] flexibleRequest(short version, ListOffsetsRequest req) {
        int size = 4; // ReplicaId
        size += 1; // IsolationLevel
        size += KafkaCodecPrimitives.varintSize(req.topics().size()); // numTopics
        for (ListOffsetsRequest.TopicOffsets topic : req.topics()) {
            size += compactStringSize(topic.name());
            size += KafkaCodecPrimitives.varintSize(topic.partitions().size());
            for (ListOffsetsRequest.PartitionOffsets partition : topic.partitions()) {
                size += 4 + 4 + 8 + 1; // PartitionIndex + CurrentLeaderEpoch + Timestamp + tags
            }
            size += 1; // end-of-tagged-fields per topic
        }
        size += 1; // end-of-tagged-fields
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_REPLICA_ID);
        buf.put((byte) DEFAULT_ISOLATION_LEVEL);
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size());
        for (ListOffsetsRequest.TopicOffsets topic : req.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, topic.name());
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size());
            for (ListOffsetsRequest.PartitionOffsets partition : topic.partitions()) {
                buf.putInt(partition.partitionIndex());
                buf.putInt(DEFAULT_LEADER_EPOCH);
                buf.putLong(partition.timestamp());
                KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields per topic
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListOffsetsRequest flexibleRequest(short version, ByteBuffer b) {
        b.getInt(); // ReplicaId, read + discard
        b.get(); // IsolationLevel, read + discard
        int numTopics = KafkaCodecPrimitives.readVarint(b);
        List<ListOffsetsRequest.TopicOffsets> topics = new ArrayList<>(numTopics);
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int numPartitions = KafkaCodecPrimitives.readVarint(b);
            List<ListOffsetsRequest.PartitionOffsets> partitions =
                    new ArrayList<>(numPartitions);
            for (int p = 0; p < numPartitions; p++) {
                int partitionIndex = b.getInt();
                b.getInt(); // CurrentLeaderEpoch, read + discard
                long timestamp = b.getLong();
                KafkaCodecPrimitives.skipTaggedFields(b);
                partitions.add(new ListOffsetsRequest.PartitionOffsets(partitionIndex, timestamp));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per topic
            topics.add(new ListOffsetsRequest.TopicOffsets(name, partitions));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // trailing
        return new ListOffsetsRequest(topics);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link ListOffsetsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(ListOffsetsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a {@link ListOffsetsResponse} at the given API version.
     *
     * @param version the API version (0–8)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, ListOffsetsResponse resp) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleResponse(version, resp);
        }
        return fixedResponse(version, resp);
    }

    /**
     * Decode a ListOffsets response body at the given API version.
     *
     * @param version the API version (0–8)
     * @param buf     the response body
     * @return the decoded response
     */
    public static ListOffsetsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleResponse(version, buf);
        }
        return fixedResponse(version, buf);
    }

    // ── Fixed-width response (v0–v5) ─────────────────────────────────────
    // Order: [ThrottleTimeMs(2+)], numTopics,
    //   [ name, numPartitions, [ PartitionIndex, ErrorCode,
    //     (v0: OldStyleOffsets[]int64) | (v1+: Timestamp, Offset,
    //      LeaderEpoch(4+)) ] ]

    private static byte[] fixedResponse(short version, ListOffsetsResponse resp) {
        int size = 0;
        if (version >= 2) {
            size += 4; // ThrottleTimeMs
        }
        size += 4; // numTopics
        for (ListOffsetsResponse.TopicResponse topic : resp.topics()) {
            size += 2 + topic.name().getBytes(StandardCharsets.UTF_8).length;
            size += 4; // numPartitions
            for (ListOffsetsResponse.PartitionResponse partition : topic.partitions()) {
                size += 4 + 2; // PartitionIndex + ErrorCode
                if (version == 0) {
                    size += 4 + 8; // OldStyleOffsets (int32 count + int64[0])
                } else {
                    size += 8 + 8; // Timestamp + Offset
                    if (version >= 4) {
                        size += 4; // LeaderEpoch (v4+)
                    }
                }
            }
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 2) {
            buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        }
        buf.putInt(resp.topics().size());
        for (ListOffsetsResponse.TopicResponse topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.partitions().size());
            for (ListOffsetsResponse.PartitionResponse partition : topic.partitions()) {
                buf.putInt(partition.partitionIndex());
                buf.putShort(partition.errorCode());
                if (version == 0) {
                    buf.putInt(1); // OldStyleOffsets count
                    buf.putLong(partition.offset());
                } else {
                    buf.putLong(partition.timestamp());
                    buf.putLong(partition.offset());
                    if (version >= 4) {
                        buf.putInt(DEFAULT_LEADER_EPOCH);
                    }
                }
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListOffsetsResponse fixedResponse(short version, ByteBuffer b) {
        if (version >= 2) {
            b.getInt(); // ThrottleTimeMs, read + discard
        }
        int numTopics = b.getInt();
        List<ListOffsetsResponse.TopicResponse> topics = new ArrayList<>(numTopics);
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readString(b);
            int numPartitions = b.getInt();
            List<ListOffsetsResponse.PartitionResponse> partitions =
                    new ArrayList<>(numPartitions);
            for (int p = 0; p < numPartitions; p++) {
                int partitionIndex = b.getInt();
                short errorCode = b.getShort();
                long timestamp;
                long offset;
                if (version == 0) {
                    int numOffsets = b.getInt();
                    offset = b.getLong(); // OldStyleOffsets[0]
                    for (int i = 1; i < numOffsets; i++) {
                        b.getLong(); // read + discard remaining offsets
                    }
                    timestamp = 0L; // v0 carries no timestamp
                } else {
                    timestamp = b.getLong();
                    offset = b.getLong();
                    if (version >= 4) {
                        b.getInt(); // LeaderEpoch, read + discard
                    }
                }
                partitions.add(new ListOffsetsResponse.PartitionResponse(
                        partitionIndex, errorCode, timestamp, offset));
            }
            topics.add(new ListOffsetsResponse.TopicResponse(name, partitions));
        }
        return new ListOffsetsResponse(topics);
    }

    // ── Flexible response (v6+) ──────────────────────────────────────────
    // Order: ThrottleTimeMs, numTopics(varint),
    //   [ name(compact), numPartitions(varint), [ PartitionIndex, ErrorCode,
    //     Timestamp, Offset, LeaderEpoch, endTaggedFields ], endTaggedFields ],
    //   endTaggedFields

    private static byte[] flexibleResponse(short version, ListOffsetsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += KafkaCodecPrimitives.varintSize(resp.topics().size()); // numTopics
        for (ListOffsetsResponse.TopicResponse topic : resp.topics()) {
            size += compactStringSize(topic.name());
            size += KafkaCodecPrimitives.varintSize(topic.partitions().size());
            for (ListOffsetsResponse.PartitionResponse partition : topic.partitions()) {
                size += 4 + 2 + 8 + 8 + 4 + 1; // + LeaderEpoch + tags
            }
            size += 1; // end-of-tagged-fields per topic
        }
        size += 1; // end-of-tagged-fields
        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, resp.topics().size());
        for (ListOffsetsResponse.TopicResponse topic : resp.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, topic.name());
            KafkaCodecPrimitives.writeVarint(buf, topic.partitions().size());
            for (ListOffsetsResponse.PartitionResponse partition : topic.partitions()) {
                buf.putInt(partition.partitionIndex());
                buf.putShort(partition.errorCode());
                buf.putLong(partition.timestamp());
                buf.putLong(partition.offset());
                buf.putInt(DEFAULT_LEADER_EPOCH);
                KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields per topic
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // end of tagged fields
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ListOffsetsResponse flexibleResponse(short version, ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discard
        int numTopics = KafkaCodecPrimitives.readVarint(b);
        List<ListOffsetsResponse.TopicResponse> topics = new ArrayList<>(numTopics);
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int numPartitions = KafkaCodecPrimitives.readVarint(b);
            List<ListOffsetsResponse.PartitionResponse> partitions =
                    new ArrayList<>(numPartitions);
            for (int p = 0; p < numPartitions; p++) {
                int partitionIndex = b.getInt();
                short errorCode = b.getShort();
                long timestamp = b.getLong();
                long offset = b.getLong();
                b.getInt(); // LeaderEpoch, read + discard
                KafkaCodecPrimitives.skipTaggedFields(b);
                partitions.add(new ListOffsetsResponse.PartitionResponse(
                        partitionIndex, errorCode, timestamp, offset));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per topic
            topics.add(new ListOffsetsResponse.TopicResponse(name, partitions));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // trailing
        return new ListOffsetsResponse(topics);
    }

    // ── Size helpers ─────────────────────────────────────────────────────

    /** Size of a compact non-nullable string: varint(len+1) + len bytes. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }

    // ── Version guard ────────────────────────────────────────────────────

    /**
     * Reject API versions without a defined wire format. The ListOffsets spec
     * defines v0–v8; anything beyond v8 (or below v0) has no wire layout, so
     * it throws {@link CodecNotImplementedException}.
     */
    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "ListOffsets v" + version + " has no defined wire format (spec covers v0–v"
                            + MAX_VERSION + ")");
        }
    }
}
