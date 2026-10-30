package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.SyncGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.SyncGroupResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated versioned codec for SyncGroup (API key 14), v0–v5.
 *
 * <p>Version history (per the vendored {@code SyncGroupRequest.json} /
 * {@code SyncGroupResponse.json}, frozen order tables in
 * {@code doc/spec/order/SyncGroup.{Request,Response}.txt}):
 *
 * <ul>
 *   <li><b>Request</b>: v0–v2 {@code GroupId(string) + GenerationId(int32) +
 *       MemberId(string) + Assignments[] { MemberId(string), Assignment(bytes) }}.
 *       v3+ adds {@code GroupInstanceId(string, nullable, default null)} after
 *       {@code MemberId}; v5+ adds ignorable {@code ProtocolType(string, nullable)}
 *       and {@code ProtocolName(string, nullable)} after {@code GroupInstanceId}.
 *       v4+: flexible encoding (KIP-482) — compact strings, compact bytes,
 *       varint array counts (N+1), trailing tagged-fields trailers (per-assignment
 *       and top-level, all zero here). {@code GenerationId} stays a plain int32 in
 *       every version (KIP-482 does not convert int32 fields).</li>
 *   <li><b>Response</b>: v0 {@code ErrorCode(int16) + Assignment(bytes)}; v1+ adds
 *       leading {@code ThrottleTimeMs(int32)} (ignorable); v5+ adds ignorable
 *       {@code ProtocolType(string, nullable)} and {@code ProtocolName(string, nullable)}
 *       after {@code ErrorCode}; v4+: flexible encoding (compact bytes, varint lengths,
 *       top-level tagged-fields trailer). v5 is NOT wire-identical to v4 — the two
 *       protocol fields add 2 bytes (each null compact string is 1 byte).</li>
 * </ul>
 * Fields the in-house models do not expose ({@code GroupInstanceId},
 * {@code ProtocolType}, {@code ProtocolName}) are written with their spec default
 * {@code null} on encode and read + discarded on decode.
 *
 * <p>The {@code Assignment} bytes field is <b>non-nullable</b> in both messages
 * (the spec marks only {@code GroupInstanceId} and the protocol strings nullable).
 * A Java {@code null} therefore round-trips as the <i>empty</i> array — the same
 * non-nullable bytes semantics as JoinGroup's {@code Metadata} fields (write null
 * as empty; read never yields null).
 *
 * @since 0.1.0
 */
public final class SyncGroupCodec {

    /** Spec minimum version (Kafka 0.9). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.2+). */
    public static final short MAX_VERSION = 5;

    /** Flexible (KIP-482) encoding starts at v4. */
    public static final short FLEXIBLE_START = 4;

    private SyncGroupCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "SyncGroup v" + version + " not implemented (range 0–5)");
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
     * On-the-wire size of a non-nullable bytes field. Fixed-width: int32 length prefix
     * (4 bytes) + bytes. Flexible: varint length prefix (length + 1) + bytes.
     * A {@code null} value is sized as the empty array (non-nullable field).
     */
    private static int bytesSize(boolean flexible, byte[] b) {
        if (b == null) {
            b = new byte[0]; // non-nullable: null == empty
        }
        if (flexible) {
            return varintSize(b.length + 1) + b.length;
        }
        return 4 + b.length;
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
     * @param version the API version (0–5)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, SyncGroupRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = stringSize(flexible, req.groupId()); // GroupId
        size += 4; // GenerationId — plain int32 in every version (int32 stays int32 under KIP-482)
        size += stringSize(flexible, req.memberId()); // MemberId
        if (version >= 3) {
            size += stringSize(flexible, null); // GroupInstanceId — spec default null
        }
        if (version >= 5) {
            size += stringSize(flexible, null); // ProtocolType — spec default null
            size += stringSize(flexible, null); // ProtocolName — spec default null
        }
        int n = req.assignments().size();
        size += flexible ? varintSize(n + 1) : 4; // Assignments count
        for (var a : req.assignments()) {
            size += stringSize(flexible, a.memberId()); // Assignment.MemberId
            size += bytesSize(flexible, a.assignment()); // Assignment.Assignment
            if (flexible) {
                size += 1; // per-assignment endTags
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
        buf.putInt(req.generationId()); // GenerationId — int32 in every version
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId
        } else {
            KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId
        }
        if (version >= 3) {
            // GroupInstanceId — spec default null; int16 -1 in legacy (v3), varint 0 in flexible (v4+)
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null);
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null);
            }
        }
        if (version >= 5) {
            KafkaCodecPrimitives.writeCompactString(buf, null); // ProtocolType — spec default null
            KafkaCodecPrimitives.writeCompactString(buf, null); // ProtocolName — spec default null
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, n + 1); // Assignments count, KIP-482: N+1
        } else {
            buf.putInt(n); // Assignments count
        }
        for (var a : req.assignments()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, a.memberId()); // MemberId
            } else {
                KafkaCodecPrimitives.writeString(buf, a.memberId()); // MemberId
            }
            if (flexible) {
                writeCompactBytesNonNullable(buf, a.assignment()); // Assignment (non-nullable)
            } else {
                writeBytesFieldNonNullable(buf, a.assignment()); // Assignment (non-nullable)
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-assignment endTags
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
     * @param version the API version (0–5)
     * @param buf     the request body
     * @return the decoded request
     */
    public static SyncGroupRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                : KafkaCodecPrimitives.readString(buf); // GroupId
        int generationId = buf.getInt(); // GenerationId — int32 in every version
        String memberId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                : KafkaCodecPrimitives.readString(buf); // MemberId
        if (version >= 3) {
            // GroupInstanceId — read + discarded; int16 -1 in legacy (v3), varint 0 in flexible (v4+)
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        if (version >= 5) {
            KafkaCodecPrimitives.readCompactString(buf); // ProtocolType — read + discarded
            KafkaCodecPrimitives.readCompactString(buf); // ProtocolName — read + discarded
        }
        int assignCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<SyncGroupRequest.Assignment> assignments = new ArrayList<>(assignCount);
        for (int i = 0; i < assignCount; i++) {
            String mId = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                    : KafkaCodecPrimitives.readString(buf); // MemberId
            byte[] assignment = flexible
                    ? readCompactBytesNonNullable(buf) // Assignment (non-nullable)
                    : readBytesFieldNonNullable(buf); // Assignment (non-nullable)
            assignments.add(new SyncGroupRequest.Assignment(mId, assignment));
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-assignment
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new SyncGroupRequest(groupId, generationId, memberId, assignments);
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, SyncGroupResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 1) {
            size += 4; // ThrottleTimeMs (int32, v1+)
        }
        size += 2; // ErrorCode (int16)
        if (version >= 5) {
            size += stringSize(flexible, null); // ProtocolType — spec default null
            size += stringSize(flexible, null); // ProtocolName — spec default null
        }
        size += bytesSize(flexible, resp.assignment()); // Assignment
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 1) {
            buf.putInt(0); // ThrottleTimeMs — spec default 0
        }
        buf.putShort(resp.errorCode()); // ErrorCode
        if (version >= 5) {
            KafkaCodecPrimitives.writeCompactString(buf, null); // ProtocolType — spec default null
            KafkaCodecPrimitives.writeCompactString(buf, null); // ProtocolName — spec default null
        }
        if (flexible) {
            writeCompactBytesNonNullable(buf, resp.assignment()); // Assignment (non-nullable)
        } else {
            writeBytesFieldNonNullable(buf, resp.assignment()); // Assignment (non-nullable)
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
     * @param version the API version (0–5)
     * @param buf     the response body
     * @return the decoded response
     */
    public static SyncGroupResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 1) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode = buf.getShort(); // ErrorCode
        if (version >= 5) {
            KafkaCodecPrimitives.readCompactString(buf); // ProtocolType — read + discarded
            KafkaCodecPrimitives.readCompactString(buf); // ProtocolName — read + discarded
        }
        byte[] assignment = flexible
                ? readCompactBytesNonNullable(buf) // Assignment (non-nullable)
                : readBytesFieldNonNullable(buf); // Assignment (non-nullable)
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new SyncGroupResponse(errorCode, assignment);
    }

    // ── Non-nullable bytes helpers ──────────────────────────────────────
    // The spec marks only GroupInstanceId / ProtocolType / ProtocolName as
    // nullable; the Assignment bytes field is NON-nullable in both messages.
    // The primitives' write/readBytesField and write/readCompactBytes use the
    // NULLABLE bytes encoding (int32 -1 / varint 1 = null). These helpers give
    // the non-nullable semantics: an empty array is a distinct, valid value,
    // and a null input is written as empty rather than as a null marker.

    /**
     * Writes a non-nullable byte array in legacy encoding: int32 length + bytes.
     * A {@code null} value is treated as the empty array (int32 0).
     */
    private static void writeBytesFieldNonNullable(ByteBuffer buf, byte[] b) {
        if (b == null) {
            b = new byte[0];
        }
        buf.putInt(b.length);
        buf.put(b);
    }

    /**
     * Reads a non-nullable byte array in legacy encoding (int32 length + bytes).
     *
     * @return the bytes; the empty array if the length was 0 or negative (defensive —
     *         a non-nullable field never encodes a negative length)
     */
    private static byte[] readBytesFieldNonNullable(ByteBuffer buf) {
        int len = buf.getInt();
        if (len <= 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return bytes;
    }

    /**
     * Writes a non-nullable byte array in flexible encoding: varint(length + 1) + bytes.
     * A {@code null} value is treated as the empty array (varint 1).
     */
    private static void writeCompactBytesNonNullable(ByteBuffer buf, byte[] b) {
        if (b == null) {
            b = new byte[0];
        }
        KafkaCodecPrimitives.writeVarint(buf, b.length + 1);
        buf.put(b);
    }

    /**
     * Reads a non-nullable byte array in flexible encoding.
     *
     * @return the bytes; the empty array if the length varint was 1 (empty, not null)
     */
    private static byte[] readCompactBytesNonNullable(ByteBuffer buf) {
        int len = KafkaCodecPrimitives.readVarint(buf);
        len -= 1; // N+1 -> N
        if (len <= 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return bytes;
    }
}
