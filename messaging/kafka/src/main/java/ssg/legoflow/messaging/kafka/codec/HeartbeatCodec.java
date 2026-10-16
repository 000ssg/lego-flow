package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.HeartbeatRequest;
import ssg.legoflow.messaging.kafka.protocol.HeartbeatResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka <b>Consumer Groups</b> Heartbeat API (key 12), versions v0–v4, both
 * directions.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the in-house
 * client sends, {@code ApiKey.HEARTBEAT.key()} + version 0), while the full version range v0–v4
 * is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/Heartbeat.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/HeartbeatRequest.json} and
 * {@code HeartbeatResponse.json}, which are byte-equivalent to the Apache Kafka spec):
 * <ul>
 *   <li><b>Request</b>: {@code GroupId(string) + GenerationId(int32) + MemberId(string)} — then,
 *       <b>v3+</b>, {@code GroupInstanceId(string, nullable)} (KIP-345).</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from <b>v1+</b>, ignorable)
 *       + {@code ErrorCode(int16)}.</li>
 *   <li><b>v4+</b>: flexible (KIP-482) encoding — compact strings, varint counts, and a
 *       top-level tagged-fields trailer (all zero here).</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode and
 * read + discarded on decode: the request {@code GroupInstanceId} (null, v3+) and the response
 * {@code ThrottleTimeMs} (0, v1+).
 *
 * @since 0.1.0
 */
public final class HeartbeatCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version. */
    public static final short MAX_VERSION = 4;

    /** Flexible (KIP-482) encoding starts at v4; strings become compact. */
    public static final short FLEXIBLE_START = 4;

    /** Version the request gained {@code GroupInstanceId(string, nullable)} (KIP-345). */
    public static final short GROUP_INSTANCE_START = 3;

    /** Version the response gained a leading {@code ThrottleTimeMs(int32)}. */
    public static final short THROTTLE_TIME_START = 1;

    private HeartbeatCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "Heartbeat v" + version + " not implemented (range 0–4)");
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
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, HeartbeatRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        size += stringSize(flexible, req.groupId()); // GroupId
        size += 4; // GenerationId (int32)
        size += stringSize(flexible, req.memberId()); // MemberId
        if (version >= GROUP_INSTANCE_START) {
            size += stringSize(flexible, null); // GroupInstanceId (nullable) — spec default
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
        buf.putInt(req.generationId()); // GenerationId
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId
        } else {
            KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId
        }
        if (version >= GROUP_INSTANCE_START) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // GroupInstanceId — spec default null
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
     * @param version the API version (0–4)
     * @param buf     the request body
     * @return the decoded request
     */
    public static HeartbeatRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                : KafkaCodecPrimitives.readString(buf); // GroupId
        int generationId = buf.getInt(); // GenerationId
        String memberId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                : KafkaCodecPrimitives.readString(buf); // MemberId
        if (version >= GROUP_INSTANCE_START) {
            String groupInstanceId = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // GroupInstanceId — read + discarded (model has no field)
                    : KafkaCodecPrimitives.readNullableString(buf); // GroupInstanceId — read + discarded
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new HeartbeatRequest(groupId, generationId, memberId);
    }

    // ── Response ────────────────────────────────────────────────────────
    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, HeartbeatResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= THROTTLE_TIME_START) {
            size += 4; // ThrottleTimeMs (int32) — spec default
        }
        size += 2; // ErrorCode (int16)
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= THROTTLE_TIME_START) {
            buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        }
        buf.putShort(resp.errorCode()); // ErrorCode
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    /**
     * Decode a response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static HeartbeatResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= THROTTLE_TIME_START) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode = buf.getShort(); // ErrorCode
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new HeartbeatResponse(errorCode);
    }
}
