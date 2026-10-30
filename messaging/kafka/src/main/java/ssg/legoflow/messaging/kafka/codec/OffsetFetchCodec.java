package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.OffsetFetchRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetFetchResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka <b>Consumer Groups</b> OffsetFetch API (key 9), versions v0–v8, both
 * directions.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the in-house
 * client sends, {@code ApiKey.OFFSET_FETCH.key()} + version 0), while the full version range
 * v0–v8 is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/OffsetFetch.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/OffsetFetchRequest.json} and
 * {@code OffsetFetchResponse.json}, which are byte-equivalent to the Apache Kafka spec):
 * <ul>
 *   <li><b>Request</b>: {@code GroupId(string)} — then, <b>v0–v7</b>, {@code Topics[] nullable
 *       { Name(string), PartitionIndexes([]int32) }}; then, <b>v7+</b>, {@code RequireStable(bool)}.
 *       At <b>v8</b> the leading {@code GroupId + Topics} pair is replaced by {@code Groups[]
 *       { groupId(string), Topics[] nullable { Name(string), PartitionIndexes([]int32) } }},
 *       followed by {@code RequireStable(bool)}.</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from <b>v3+</b>); then
 *       {@code Topics[] { Name(string), Partitions[] { PartitionIndex(int32),
 *       CommittedOffset(int64), CommittedLeaderEpoch(int32, v5–7), Metadata(string, nullable),
 *       ErrorCode(int16) } }}; then, <b>v2–7</b>, a top-level {@code ErrorCode(int16)}. At <b>v8</b>
 *       the top-level {@code Topics + ErrorCode} are replaced by {@code Groups[] { groupId(string),
 *       Topics[] { …same partitions… }, ErrorCode(int16) }}.</li>
 *   <li><b>v6–v8</b>: flexible (KIP-482) encoding — compact strings, array counts as N+1 varints,
 *       and trailing tagged-fields trailers (all zero here).</li>
 * </ul>
 * The per-partition response order is {@code PartitionIndex, CommittedOffset,
 * CommittedLeaderEpoch (v5–7), Metadata, ErrorCode}. Fields the in-house models do not expose are
 * written with their spec defaults on encode and read + discarded on decode: the request
 * {@code RequireStable} (false) at v7+ and the response {@code ThrottleTimeMs} (0) at v3+, the
 * top-level {@code ErrorCode} (0) at v2–7, and the per-partition {@code CommittedLeaderEpoch} (-1)
 * at v5–7. The v8 {@code Groups[]} layout is synthesized from the single-group in-house models:
 * encode wraps the model's one group (request) or topics (response) in a single-element array
 * with the spec's group-level {@code ErrorCode} default (0); decode collapses the first group's
 * topics back into the model.
 *
 * @since 0.1.0
 */
public final class OffsetFetchCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.6+ / KIP-848 multi-group fetch). */
    public static final short MAX_VERSION = 8;

    /** Flexible (KIP-482) encoding starts at v6; strings become compact. */
    public static final short FLEXIBLE_START = 6;

    /** Version the request gained {@code RequireStable(bool)}. */
    public static final short REQUIRE_STABLE_START = 7;

    /** Version the request/response switched to the multi-group {@code Groups[]} layout. */
    public static final short GROUPS_START = 8;

    /** Version the response gained {@code ThrottleTimeMs(int32)}. */
    public static final short THROTTLE_TIME_START = 3;

    /** Version the response gained a top-level {@code ErrorCode(int16)}. */
    public static final short TOP_LEVEL_ERROR_START = 2;

    /** Version the response partitions gained {@code CommittedLeaderEpoch(int32)}. */
    public static final short COMMITTED_LEADER_EPOCH_START = 5;

    /** Version pinned for the client/broker interaction (the in-house client sends v0). */

    private OffsetFetchCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "OffsetFetch v" + version + " not implemented (range 0–8)");
        }
    }

    /**
     * On-the-wire size of a (nullable) string field. Fixed-width: int16 length prefix
     * (2 bytes) + UTF-8 bytes (or nothing when null). Flexible: varint length prefix
     * (length + 1 for non-null, 0 for null) + UTF-8 bytes.
     */
    private static int stringSize(boolean flexible, String s) {
        if (flexible) {
            int len = s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
            return varintSize(len + (s == null ? 0 : 1)) + len;
        }
        return 2 + (s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * Number of bytes an unsigned int32 varint occupies (1–5); mirrors
     * {@link KafkaCodecPrimitives#varintSize(int)} (package-private, same package).
     */
    private static int varintSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    // ── Request ─────────────────────────────────────────────────────────
    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–8)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, OffsetFetchRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= GROUPS_START) {
            size += varintSize(2); // Groups count (KIP-482: N+1 = 2 for the single in-house group)
            size += stringSize(flexible, req.groupId()); // Groups[0].groupId
            size += flexible ? varintSize(req.topics().size() + 1) : 4; // Groups[0].Topics count
        } else {
            size += stringSize(flexible, req.groupId()); // GroupId
            size += flexible ? varintSize(req.topics().size() + 1) : 4; // Topics count
        }
        for (var t : req.topics()) {
            size += stringSize(flexible, t.name()); // Name
            size += flexible ? varintSize(t.partitionIndexes().size() + 1) : 4; // PartitionIndexes count
            size += t.partitionIndexes().size() * 4; // PartitionIndexes (int32)
            if (flexible) {
                size += 1; // per-topic endTags
            }
        }
        if (version >= REQUIRE_STABLE_START) {
            size += 1; // RequireStable (bool)
        }
        if (version >= GROUPS_START && flexible) {
            size += 1; // per-group endTags (v8)
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= GROUPS_START) {
            KafkaCodecPrimitives.writeVarint(buf, 2); // Groups count (KIP-482: N+1 = 2 for the single in-house group)
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.groupId()); // Groups[0].groupId
            } else {
                KafkaCodecPrimitives.writeString(buf, req.groupId()); // Groups[0].groupId
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // Groups[0].Topics count
            } else {
                buf.putInt(req.topics().size());
            }
        } else {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.groupId()); // GroupId
            } else {
                KafkaCodecPrimitives.writeString(buf, req.groupId()); // GroupId
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // Topics count
            } else {
                buf.putInt(req.topics().size());
            }
        }
        for (var t : req.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name()); // Name
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name()); // Name
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitionIndexes().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(t.partitionIndexes().size());
            }
            for (int pi : t.partitionIndexes()) {
                buf.putInt(pi); // PartitionIndexes (int32)
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        if (version >= GROUPS_START && flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-group endTags (v8) — terminates Groups[0]
        }
        if (version >= REQUIRE_STABLE_START) {
            buf.put((byte) 0); // RequireStable — spec default false, model does not expose it
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    /**
     * Decode a request body at the given API version.
     *
     * @param version the API version (0–8)
     * @param buf     the request body
     * @return the decoded request
     */
    public static OffsetFetchRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId;
        List<OffsetFetchRequest.TopicPartitions> topics;
        if (version >= GROUPS_START) {
            int groupCount = KafkaCodecPrimitives.readVarint(buf) - 1; // KIP-482: N+1
            if (groupCount <= 0) {
                groupId = null;
                topics = new ArrayList<>();
            } else {
                // Groups[0] — collapse the first group's data into the in-house model.
                groupId = flexible
                        ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Groups[0].groupId
                        : KafkaCodecPrimitives.readString(buf); // Groups[0].groupId
                int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // Topics count
                List<OffsetFetchRequest.TopicPartitions> first = new ArrayList<>(topicCount);
                for (int i = 0; i < topicCount; i++) {
                    String name = flexible
                            ? KafkaCodecPrimitives.readCompactString(buf) // Name
                            : KafkaCodecPrimitives.readString(buf); // Name
                    int piCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
                    List<Integer> pis = new ArrayList<>(piCount);
                    for (int j = 0; j < piCount; j++) {
                        pis.add(buf.getInt()); // PartitionIndexes (int32)
                    }
                    if (flexible) {
                        KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
                    }
                    first.add(new OffsetFetchRequest.TopicPartitions(name, pis));
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
                }
                // Remaining groups (groupCount - 1) carry data the single-group model cannot
                // hold; read + discard their bodies.
                for (int g = 1; g < groupCount; g++) {
                    readAndDiscardRequestGroup(buf, flexible);
                }
                topics = first;
            }
        } else {
            groupId = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                    : KafkaCodecPrimitives.readString(buf); // GroupId
            int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // Topics count
            topics = new ArrayList<>(topicCount);
            for (int i = 0; i < topicCount; i++) {
                String name = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf) // Name
                        : KafkaCodecPrimitives.readString(buf); // Name
                int piCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
                List<Integer> pis = new ArrayList<>(piCount);
                for (int j = 0; j < piCount; j++) {
                    pis.add(buf.getInt()); // PartitionIndexes (int32)
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
                }
                topics.add(new OffsetFetchRequest.TopicPartitions(name, pis));
            }
        }
        if (version >= REQUIRE_STABLE_START) {
            buf.get(); // RequireStable (bool) — read + discarded
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new OffsetFetchRequest(groupId, topics);
    }

    /** Reads + discards the body of one v8 request group (groupId + Topics[] with endTags). */
    private static void readAndDiscardRequestGroup(ByteBuffer buf, boolean flexible) {
        if (flexible) {
            KafkaCodecPrimitives.readCompactStringNonNullable(buf); // groupId
        } else {
            KafkaCodecPrimitives.readString(buf); // groupId
        }
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
        for (int i = 0; i < topicCount; i++) {
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf); // Name
            } else {
                KafkaCodecPrimitives.readString(buf); // Name
            }
            int piCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
            for (int j = 0; j < piCount; j++) {
                buf.getInt(); // PartitionIndexes
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
        }
    }

    // ── Response ────────────────────────────────────────────────────────
    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–8)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, OffsetFetchResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= THROTTLE_TIME_START) {
            size += 4; // ThrottleTimeMs (int32)
        }
        if (version >= GROUPS_START) {
            size += varintSize(2); // Groups count (KIP-482: N+1 = 2 for the single in-house group)
            size += stringSize(flexible, null); // Groups[0].groupId — spec default (null)
            size += flexible ? varintSize(resp.topics().size() + 1) : 4; // Groups[0].Topics count
        } else {
            size += flexible ? varintSize(resp.topics().size() + 1) : 4; // Topics count
        }
        int partPrefix = 12; // PartitionIndex (int32) + CommittedOffset (int64)
        int partLeaderEpoch = version >= COMMITTED_LEADER_EPOCH_START ? 4 : 0; // CommittedLeaderEpoch (v5–7)
        for (var t : resp.topics()) {
            size += stringSize(flexible, t.name()); // Name
            size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
            for (var p : t.partitions()) {
                size += partPrefix; // per-partition fixed prefix (int32 + int64, both modes)
                size += partLeaderEpoch; // CommittedLeaderEpoch (v5–7)
                size += stringSize(flexible, p.metadata()); // Metadata (nullable)
                size += 2; // ErrorCode (int16)
                if (flexible) {
                    size += 1; // per-partition endTags
                }
            }
            if (flexible) {
                size += 1; // per-topic endTags
            }
        }
        if (version >= TOP_LEVEL_ERROR_START && version < GROUPS_START) {
            size += 2; // top-level ErrorCode (int16, v2–7)
        }
        if (version >= GROUPS_START) {
            size += 2; // Groups[0].ErrorCode (int16, v8)
        }
        if (flexible) {
            size += 1; // per-group endTags (v8)
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= THROTTLE_TIME_START) {
            buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        }
        if (version >= GROUPS_START) {
            KafkaCodecPrimitives.writeVarint(buf, 2); // Groups count (KIP-482: N+1 = 2 for the single in-house group)
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, ""); // Groups[0].groupId — spec default
            } else {
                KafkaCodecPrimitives.writeString(buf, ""); // Groups[0].groupId — spec default
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // Groups[0].Topics count
            } else {
                buf.putInt(resp.topics().size());
            }
        } else {
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // Topics count
            } else {
                buf.putInt(resp.topics().size());
            }
        }
        for (var t : resp.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name()); // Name
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name()); // Name
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(t.partitions().size());
            }
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.committedOffset());
                if (version >= COMMITTED_LEADER_EPOCH_START) {
                    buf.putInt(-1); // CommittedLeaderEpoch — spec default, model does not expose it
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, p.metadata()); // Metadata (nullable)
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, p.metadata()); // Metadata (nullable)
                }
                buf.putShort(p.errorCode());
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        if (version >= TOP_LEVEL_ERROR_START && version < GROUPS_START) {
            buf.putShort((short) 0); // top-level ErrorCode — spec default, model does not expose it (v2–7)
        }
        if (version >= GROUPS_START) {
            buf.putShort((short) 0); // Groups[0].ErrorCode — spec default, model does not expose it (v8)
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-group endTags
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    /**
     * Decode a response body at the given API version.
     *
     * @param version the API version (0–8)
     * @param buf     the response body
     * @return the decoded response
     */
    public static OffsetFetchResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= THROTTLE_TIME_START) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        List<OffsetFetchResponse.TopicResponse> topics;
        if (version >= GROUPS_START) {
            int groupCount = KafkaCodecPrimitives.readVarint(buf) - 1; // KIP-482: N+1
            if (groupCount <= 0) {
                topics = new ArrayList<>();
            } else {
                readAndDiscardResponseGroupId(buf, flexible); // Groups[0].groupId — model does not expose it
                topics = readResponseTopics(buf, flexible, version);
                buf.getShort(); // Groups[0].ErrorCode — read + discarded (after Topics, per spec field order)
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
                }
                // Remaining groups carry data the single-group model cannot hold; read + discard.
                for (int g = 1; g < groupCount; g++) {
                    readAndDiscardResponseGroup(buf, flexible, version);
                }
            }
        } else {
            topics = readResponseTopics(buf, flexible, version);
            if (version >= TOP_LEVEL_ERROR_START) {
                buf.getShort(); // top-level ErrorCode — read + discarded (v2–7)
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new OffsetFetchResponse(topics);
    }

    private static void readAndDiscardResponseGroupId(ByteBuffer buf, boolean flexible) {
        if (flexible) {
            KafkaCodecPrimitives.readCompactStringNonNullable(buf);
        } else {
            KafkaCodecPrimitives.readString(buf);
        }
    }

    private static List<OffsetFetchResponse.TopicResponse> readResponseTopics(ByteBuffer buf,
            boolean flexible, short version) {
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
        List<OffsetFetchResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // Name
                    : KafkaCodecPrimitives.readString(buf); // Name
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            List<OffsetFetchResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                long committedOffset = buf.getLong();
                if (version >= COMMITTED_LEADER_EPOCH_START) {
                    buf.getInt(); // CommittedLeaderEpoch — read + discarded
                }
                String metadata = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf) // Metadata (nullable)
                        : KafkaCodecPrimitives.readNullableString(buf); // Metadata (nullable)
                short errorCode = buf.getShort();
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                parts.add(new OffsetFetchResponse.PartitionResponse(
                        partitionIndex, committedOffset, metadata, errorCode));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new OffsetFetchResponse.TopicResponse(name, parts));
        }
        return topics;
    }

    private static void readAndDiscardResponseGroup(ByteBuffer buf, boolean flexible, short version) {
        readAndDiscardResponseGroupId(buf, flexible); // groupId
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
        for (int i = 0; i < topicCount; i++) {
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf); // Name
            } else {
                KafkaCodecPrimitives.readString(buf); // Name
            }
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
            for (int j = 0; j < partCount; j++) {
                buf.getInt(); // PartitionIndex
                buf.getLong(); // CommittedOffset
                if (version >= COMMITTED_LEADER_EPOCH_START) {
                    buf.getInt(); // CommittedLeaderEpoch
                }
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf); // Metadata
                } else {
                    KafkaCodecPrimitives.readNullableString(buf); // Metadata
                }
                buf.getShort(); // ErrorCode
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
        }
        buf.getShort(); // group ErrorCode — after Topics, per spec field order
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
        }
    }
}
