package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.LeaveGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.LeaveGroupResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated versioned codec for LeaveGroup (API key 13), v0–v5.
 *
 * <p>Version history (per the vendored {@code LeaveGroupRequest.json} /
 * {@code LeaveGroupResponse.json}, frozen order tables in
 * {@code doc/spec/order/LeaveGroup.{Request,Response}.txt}):
 *
 * <ul>
 *   <li><b>Request</b>: v0–v2 {@code GroupId(string) + MemberId(string)} — singular,
 *       non-nullable member ID. v3+ batch processing (KIP-343): the singular
 *       {@code MemberId} is replaced by {@code Members[] { MemberId(string),
 *       GroupInstanceId(string, nullable) + Reason(string, nullable, v5+, KIP-800) }}.
 *       v4+: flexible encoding (KIP-482) — compact strings, array count as N+1 varint,
 *       trailing tagged-fields trailers (per-struct and top-level, all zero here).</li>
 *   <li><b>Response</b>: v0 {@code ErrorCode(int16)}; v1+ adds leading
 *       {@code ThrottleTimeMs(int32)} (ignorable); v3+ adds {@code Members[] {
 *       MemberId(string), GroupInstanceId(string, nullable), ErrorCode(int16) }} (KIP-343);
 *       v4+: flexible encoding (v5 is wire-identical to v4).</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode and
 * read + discarded on decode. At v3+, the request {@code Members} list carries a single
 * {@code MemberIdentity} mirroring the model's {@code memberId} (with {@code GroupInstanceId}
 * null and {@code Reason} null at v5+), preserving the v0–v2 member identity; on decode, the
 * first member's ID populates {@code memberId} (empty string when the list is empty). The
 * response per-member array is read + discarded. At the pinned v0, both bodies are exactly the
 * legacy two-field / one-field layouts.
 *
 * @since 0.1.0
 */
public final class LeaveGroupCodec {

    /** Spec minimum version (Kafka 0.9). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.1+ / KIP-800 era). */
    public static final short MAX_VERSION = 5;

    /** Flexible (KIP-482) encoding starts at v4; strings become compact. */
    public static final short FLEXIBLE_START = 4;

    private LeaveGroupCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "LeaveGroup v" + version + " not implemented (range 0–5)");
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
     * @param version the API version (0–5)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, LeaveGroupRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = stringSize(flexible, req.groupId()); // GroupId
        if (version >= 3) {
            // Batch (KIP-343): Members[] — single entry mirroring the model's memberId.
            size += flexible ? varintSize(2) : 4; // 1 member, N+1 varint at v4+
            size += stringSize(flexible, req.memberId()); // MemberIdentity.MemberId
            size += stringSize(flexible, null); // MemberIdentity.GroupInstanceId — spec default null
            if (version >= 5) {
                size += stringSize(flexible, null); // MemberIdentity.Reason — spec default null
            }
            size += flexible ? 1 : 0; // per-member endTags
        } else {
            size += stringSize(flexible, req.memberId()); // MemberId (v0–v2, singular)
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
        if (version >= 3) {
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 1 + 1); // Members count, KIP-482: N+1
            } else {
                buf.putInt(1); // Members count (v3)
            }
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId
            } else {
                KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId
            }
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // GroupInstanceId — v3 legacy
            }
            if (version >= 5) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // Reason — spec default null
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-member endTags
            }
        } else {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId
            } else {
                KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId
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
    public static LeaveGroupRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                : KafkaCodecPrimitives.readString(buf); // GroupId
        String memberId;
        if (version >= 3) {
            // Batch (KIP-343): read the full member list; first entry populates the model.
            int memberCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            String firstMemberId = "";
            for (int i = 0; i < memberCount; i++) {
                String mId = flexible
                        ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                        : KafkaCodecPrimitives.readString(buf); // MemberId
                if (i == 0) {
                    firstMemberId = mId;
                }
                // GroupInstanceId — read + discarded.
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf);
                } else {
                    KafkaCodecPrimitives.readNullableString(buf);
                }
                if (version >= 5) {
                    KafkaCodecPrimitives.readCompactString(buf); // Reason — read + discarded
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-member
                }
            }
            memberId = firstMemberId;
        } else {
            memberId = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId (v0–v2)
                    : KafkaCodecPrimitives.readString(buf); // MemberId (v0–v2)
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new LeaveGroupRequest(groupId, memberId);
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, LeaveGroupResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 1) {
            size += 4; // ThrottleTimeMs (int32, v1+)
        }
        size += 2; // ErrorCode (int16)
        if (version >= 3) {
            size += flexible ? varintSize(1) : 4; // Members count — 0 members, spec default
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 1) {
            buf.putInt(0); // ThrottleTimeMs — spec default 0
        }
        buf.putShort(resp.errorCode()); // ErrorCode
        if (version >= 3) {
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0 + 1); // Members count, KIP-482: N+1
            } else {
                buf.putInt(0); // Members count (v3)
            }
            // Empty per-member list (in-house model carries no per-member array).
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
    public static LeaveGroupResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 1) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode = buf.getShort(); // ErrorCode
        if (version >= 3) {
            // Batch (KIP-343): per-member responses — read + discarded (in-house model
            // carries only the top-level error code).
            int memberCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            for (int i = 0; i < memberCount; i++) {
                if (flexible) {
                    KafkaCodecPrimitives.readCompactStringNonNullable(buf); // MemberId
                } else {
                    KafkaCodecPrimitives.readString(buf); // MemberId
                }
                // GroupInstanceId — read + discarded.
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf);
                } else {
                    KafkaCodecPrimitives.readNullableString(buf);
                }
                buf.getShort(); // per-member ErrorCode — read + discarded
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-member
                }
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new LeaveGroupResponse(errorCode);
    }
}
