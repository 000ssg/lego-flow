package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.TxnOffsetCommitRequest;
import ssg.legoflow.messaging.kafka.protocol.TxnOffsetCommitResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka <b>Transaction</b> API (key 28), versions v0–v3, both directions.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the
 * in-house client sends), while the full version range v0–v3 is encoded so a frame carrying
 * any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in
 * {@code doc/spec/order/TxnOffsetCommit.Request.txt} / {@code .Response.txt}; mirrors
 * {@code doc/spec/message/TxnOffsetCommitRequest.json} and {@code TxnOffsetCommitResponse.json}):
 * <ul>
 *   <li><b>Request</b>: {@code TransactionalId(string) + GroupId(string) +
 *       ProducerId(int64) + ProducerEpoch(int16)}, then — <b>v3 only</b> — the
 *       {@code GenerationId(int32) + MemberId(string, non-nullable) +
 *       GroupInstanceId(string, nullable)} triple, then
 *       {@code Topics[] { Name(string), Partitions[] { PartitionIndex(int32),
 *       CommittedOffset(int64), CommittedLeaderEpoch(int32, v2+),
 *       CommittedMetadata(string, nullable) } }}.</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from v0, unlike the
 *       old inline facade which omitted it and would desynchronize against real brokers)
 *       + {@code Topics[] { Name(string), Partitions[] { PartitionIndex(int32),
 *       ErrorCode(int16) } }}.</li>
 *   <li><b>v3</b>: flexible encoding (KIP-482) — compact strings, array counts as N+1
 *       varints, and trailing tagged-fields trailers (all zero here).</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode
 * and read + discarded on decode: request {@code GenerationId} (-1), {@code MemberId} ("")
 * and {@code GroupInstanceId} (null) at v3+, request {@code CommittedLeaderEpoch} (-1) at
 * v2+, and the response {@code ThrottleTimeMs} (0).
 *
 * @since 0.1.0
 */
public final class TxnOffsetCommitCodec {

    /** Spec minimum version (Kafka 2.5.0). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.0+). */
    public static final short MAX_VERSION = 3;

    /** Flexible (KIP-482) encoding starts at v3; strings become compact. */
    public static final short FLEXIBLE_START = 3;

    /** Version pinned for the client/broker interaction. */

    private TxnOffsetCommitCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "TxnOffsetCommit v" + version + " not implemented (range 0–3)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────
    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, TxnOffsetCommitRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int size = 10; // ProducerId (int64) + ProducerEpoch (int16)
        size += stringSize(flexible, req.transactionalId());
        size += stringSize(flexible, req.groupId());
        if (version == 3) {
            size += 4; // GenerationId (int32)
            size += 2; // MemberId — spec default "", non-nullable (varint 2 / int16 0 + 0)
            size += 1; // GroupInstanceId — null → varint 0
        }
        size += flexible ? varintSize(req.topics().size() + 1) : 4; // Topics count
        for (var t : req.topics()) {
            byte[] tb = t.name().getBytes(StandardCharsets.UTF_8);
            size += flexible ? (varintSize(tb.length + 1) + tb.length) : (2 + tb.length); // Name
            size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
            size += t.partitions().size() * 12; // PartitionIndex (int32) + CommittedOffset (int64)
            if (version >= 2) {
                size += t.partitions().size() * 4; // CommittedLeaderEpoch (int32, v2+)
            }
            for (var p : t.partitions()) {
                size += stringSize(flexible, p.metadata()); // CommittedMetadata (nullable)
            }
            if (flexible) {
                size += t.partitions().size() * 2; // per-partition endTags (1 byte each)
                size += 1; // per-topic endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, req.transactionalId());
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, req.transactionalId());
        }
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, req.groupId());
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, req.groupId());
        }
        buf.putLong(req.producerId());
        buf.putShort(req.producerEpoch());
        if (version == 3) {
            buf.putInt(-1); // GenerationId — spec default, model does not expose it
            KafkaCodecPrimitives.writeCompactString(buf, ""); // MemberId — spec default ""
            KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — null
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(req.topics().size());
        }
        for (var t : req.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name());
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name());
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(t.partitions().size());
            }
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putLong(p.committedOffset());
                if (version >= 2) {
                    buf.putInt(-1); // CommittedLeaderEpoch — spec default, model does not expose it
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, p.metadata());
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, p.metadata());
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
     * @param version the API version (0–3)
     * @param buf     the request body
     * @return the decoded request
     */
    public static TxnOffsetCommitRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String transactionalId = flexible
                ? KafkaCodecPrimitives.readCompactString(buf)
                : KafkaCodecPrimitives.readNullableString(buf);
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactString(buf)
                : KafkaCodecPrimitives.readNullableString(buf);
        long producerId = buf.getLong();
        short producerEpoch = buf.getShort();
        if (version == 3) {
            buf.getInt(); // GenerationId — read + discarded (model does not expose it)
            KafkaCodecPrimitives.readCompactString(buf); // MemberId — read + discarded
            KafkaCodecPrimitives.readCompactString(buf); // GroupInstanceId — read + discarded
        }
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        java.util.List<TxnOffsetCommitRequest.TopicData> topics = new java.util.ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf)
                    : KafkaCodecPrimitives.readString(buf);
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            java.util.List<TxnOffsetCommitRequest.PartitionData> parts = new java.util.ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                long committedOffset = buf.getLong();
                if (version >= 2) {
                    buf.getInt(); // CommittedLeaderEpoch — read + discarded
                }
                String metadata = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf)
                        : KafkaCodecPrimitives.readNullableString(buf);
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                parts.add(new TxnOffsetCommitRequest.PartitionData(partitionIndex, committedOffset, metadata));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new TxnOffsetCommitRequest.TopicData(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new TxnOffsetCommitRequest(transactionalId, groupId, producerId, producerEpoch, topics);
    }

    // ── Response ─────────────────────────────────────────────────────────
    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, TxnOffsetCommitResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int size = 4; // ThrottleTimeMs (int32, spec default 0 — model does not expose it)
        size += flexible ? varintSize(resp.topics().size() + 1) : 4; // Topics count
        for (var t : resp.topics()) {
            byte[] tb = t.name().getBytes(StandardCharsets.UTF_8);
            size += flexible ? (varintSize(tb.length + 1) + tb.length) : (2 + tb.length); // Name
            size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
            size += t.partitions().size() * 6; // PartitionIndex (int32) + ErrorCode (int16)
            if (flexible) {
                size += t.partitions().size() * 2; // per-partition endTags (1 byte each)
                size += 1; // per-topic endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(0); // ThrottleTimeMs — spec default, written first (old inline facade omitted it)
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(resp.topics().size());
        }
        for (var t : resp.topics()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, t.name());
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name());
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
     * @param version the API version (0–3)
     * @param buf     the response body
     * @return the decoded response
     */
    public static TxnOffsetCommitResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        buf.getInt(); // ThrottleTimeMs — read + discarded (model does not expose it)
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        java.util.List<TxnOffsetCommitResponse.TopicData> topics = new java.util.ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf)
                    : KafkaCodecPrimitives.readString(buf);
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            java.util.List<TxnOffsetCommitResponse.PartitionData> parts = new java.util.ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                int partitionIndex = buf.getInt();
                short errorCode = buf.getShort();
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                parts.add(new TxnOffsetCommitResponse.PartitionData(partitionIndex, errorCode));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new TxnOffsetCommitResponse.TopicData(name, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new TxnOffsetCommitResponse(topics);
    }

    // ── Size helpers ────────────────────────────────────────────────────

    /**
     * On-the-wire size of a (nullable) string field. Fixed-width: int16 length prefix
     * (2 bytes) + UTF-8 bytes (or nothing when null). Flexible: varint length prefix
     * (length + 1 for non-null, 0 for null).
     */
    private static int stringSize(boolean flexible, String s) {
        if (flexible) {
            int len = s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
            return varintSize(len + (s == null ? 0 : 1));
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
}
