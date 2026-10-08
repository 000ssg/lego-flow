package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.OffsetCommitRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetCommitResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka <b>Consumer Groups</b> OffsetCommit API (key 8), versions v0–v9, both
 * directions.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline the in-house
 * client sends, {@code ApiKey.OFFSET_COMMIT.key()} + version 0), while the full version range
 * v0–v9 is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/OffsetCommit.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/OffsetCommitRequest.json} and
 * {@code OffsetCommitResponse.json}, which are byte-equivalent to Apache Kafka 3.6.1):
 * <ul>
 *   <li><b>Request</b>: {@code GroupId(string)} — then, <b>v1+</b>, {@code
 *       GenerationIdOrMemberEpoch(int32) + MemberId(string)}; then, <b>v7+</b>, {@code
 *       GroupInstanceId(string, nullable)}; then, <b>v2–4 only</b>, {@code RetentionTimeMs(int64)};
 *       then {@code Topics[] { Name(string), Partitions[] { PartitionIndex(int32),
 *       CommittedOffset(int64), CommitTimestamp(int64, v1 only), CommittedLeaderEpoch(int32, v6+),
 *       CommittedMetadata(string, nullable) } }}.</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from <b>v3+</b>) +
 *       {@code Topics[] { Name(string), Partitions[] { PartitionIndex(int32), ErrorCode(int16) } }}.</li>
 *   <li><b>v8–v9</b>: flexible encoding (KIP-482) — compact strings, array counts as N+1 varints,
 *       and trailing tagged-fields trailers (all zero here). v9 is wire-identical to v8 (KIP-848:
 *       same body; only the response error-code surface changes), and is the first version usable
 *       with the new consumer group protocol.</li>
 * </ul>
 * The per-partition order is {@code PartitionIndex, CommittedOffset, CommitTimestamp (v1),
 * CommittedLeaderEpoch (v6+), CommittedMetadata}; {@code RetentionTimeMs} (v2–4) is a top-level
 * request field placed before {@code Topics}; {@code ThrottleTimeMs} (v3+) is a leading response
 * field. Fields the in-house models do not expose are written with their spec defaults on encode
 * and read + discarded on decode: request {@code RetentionTimeMs} (-1) at v2–4, {@code
 * GroupInstanceId} (null) at v7+, {@code CommitTimestamp} (-1) at v1, and {@code
 * CommittedLeaderEpoch} (-1) at v6+; the response {@code ThrottleTimeMs} (0) at v3+.
 *
 * @since 0.1.0
 */
public final class OffsetCommitCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.4+ / KIP-848). */
    public static final short MAX_VERSION = 9;

    /** Flexible (KIP-482) encoding starts at v8; strings become compact. */
    public static final short FLEXIBLE_START = 8;

    /** Version pinned for the client/broker interaction (the in-house client sends v0). */
    public static final short PINNED_VERSION = 0;

    private OffsetCommitCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "OffsetCommit v" + version + " not implemented (range 0–9)");
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
     * Encode a request at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(OffsetCommitRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–9)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, OffsetCommitRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        // Precompute the exact body size (BufferPool allocates at least this much).
        // String sizing is exact-or-over (KIP-482 varint length + UTF-8 bytes); the
        // per-partition numeric prefix (int32 + int64) is fixed-width in BOTH modes —
        // KIP-482 only varint-encodes array counts and string lengths, never scalars.
        int size = stringSize(flexible, req.groupId()); // GroupId
        if (version >= 1) {
            size += 4; // GenerationIdOrMemberEpoch (int32)
            size += stringSize(flexible, req.memberId()); // MemberId
        }
        if (version >= 7) {
            size += stringSize(flexible, null); // GroupInstanceId — spec default null
        }
        if (version >= 2 && version <= 4) {
            size += 8; // RetentionTimeMs (int64)
        }
        size += flexible ? varintSize(req.topics().size() + 1) : 4; // Topics count
        int partPrefix = 12; // PartitionIndex (int32) + CommittedOffset (int64)
        for (var t : req.topics()) {
            size += stringSize(flexible, t.name()); // Name
            size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
            for (var p : t.partitions()) {
                size += partPrefix; // per-partition fixed prefix (int32 + int64, both modes)
                if (version == 1) {
                    size += 8; // CommitTimestamp (int64, v1 only)
                }
                if (version >= 6) {
                    size += 4; // CommittedLeaderEpoch (int32, v6+)
                }
                size += stringSize(flexible, p.metadata()); // CommittedMetadata (nullable)
                if (flexible) {
                    size += 1; // per-partition endTags
                }
            }
            if (flexible) {
                size += 1; // per-topic endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.groupId()); // GroupId
        } else {
            KafkaCodecPrimitives.writeString(buf, req.groupId()); // GroupId
        }
        if (version >= 1) {
            buf.putInt(req.generationId()); // GenerationIdOrMemberEpoch
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId (non-nullable)
            } else {
                KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId (non-nullable)
            }
        }
        if (version >= 7) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // GroupInstanceId — spec default null (v7 legacy)
            }
        }
        if (version >= 2 && version <= 4) {
            buf.putLong(-1L); // RetentionTimeMs — spec default, model does not expose it
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(req.topics().size());
        }
        for (var t : req.topics()) {
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
                if (version == 1) {
                    buf.putLong(-1L); // CommitTimestamp — spec default, model does not expose it
                }
                if (version >= 6) {
                    buf.putInt(-1); // CommittedLeaderEpoch — spec default, model does not expose it
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, p.metadata()); // CommittedMetadata (nullable)
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, p.metadata()); // CommittedMetadata (nullable)
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
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
     * @param version the API version (0–9)
     * @param buf     the request body
     * @return the decoded request
     */
    public static OffsetCommitRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                : KafkaCodecPrimitives.readString(buf); // GroupId
        int generationId = 0;
        String memberId = null;
        if (version >= 1) {
            generationId = buf.getInt(); // GenerationIdOrMemberEpoch
            memberId = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                    : KafkaCodecPrimitives.readString(buf); // MemberId
        }
        if (version >= 7) {
            // GroupInstanceId — read + discarded. Flexible (v8+): nullable compact string
            // (varint). Non-flexible v7: legacy nullable string (int16 length prefix).
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        if (version >= 2 && version <= 4) {
            buf.getLong(); // RetentionTimeMs — read + discarded
        }
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<OffsetCommitRequest.TopicOffsets> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // Name
                    : KafkaCodecPrimitives.readString(buf); // Name
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            List<OffsetCommitRequest.PartitionOffset> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                long committedOffset = buf.getLong();
                if (version == 1) {
                    buf.getLong(); // CommitTimestamp — read + discarded
                }
                if (version >= 6) {
                    buf.getInt(); // CommittedLeaderEpoch — read + discarded
                }
                String metadata = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf) // CommittedMetadata (nullable)
                        : KafkaCodecPrimitives.readNullableString(buf); // CommittedMetadata (nullable)
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                parts.add(new OffsetCommitRequest.PartitionOffset(partitionIndex, committedOffset, metadata));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new OffsetCommitRequest.TopicOffsets(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new OffsetCommitRequest(groupId, generationId, memberId, topics);
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(OffsetCommitResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–9)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, OffsetCommitResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 3) {
            size += 4; // ThrottleTimeMs (int32)
        }
        size += flexible ? varintSize(resp.topics().size() + 1) : 4; // Topics count
        int partPrefix = 6; // PartitionIndex (int32) + ErrorCode (int16)
        for (var t : resp.topics()) {
            size += stringSize(flexible, t.name()); // Name (non-nullable; same size formula)
            size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
            for (var p : t.partitions()) {
                size += partPrefix; // per-partition fixed prefix (int32 + int16, both modes)
                if (flexible) {
                    size += 1; // per-partition endTags
                }
            }
            if (flexible) {
                size += 1; // per-topic endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 3) {
            buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(resp.topics().size());
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
                buf.putShort(p.errorCode());
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
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
     * @param version the API version (0–9)
     * @param buf     the response body
     * @return the decoded response
     */
    public static OffsetCommitResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 3) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<OffsetCommitResponse.TopicResponse> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // Name
                    : KafkaCodecPrimitives.readString(buf); // Name
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            List<OffsetCommitResponse.PartitionResponse> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short errorCode = buf.getShort();
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                parts.add(new OffsetCommitResponse.PartitionResponse(partitionIndex, errorCode));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new OffsetCommitResponse.TopicResponse(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new OffsetCommitResponse(topics);
    }
}
