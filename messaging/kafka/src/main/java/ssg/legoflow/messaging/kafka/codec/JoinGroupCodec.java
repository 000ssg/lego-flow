package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.JoinGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.JoinGroupResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka <b>Consumer Groups</b> JoinGroup API (key 11), versions v0–v9, both
 * directions.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline the in-house
 * client sends, {@code ApiKey.JOIN_GROUP.key()} + version 0), while the full version range v0–v9
 * is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/JoinGroup.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/JoinGroupRequest.json} and
 * {@code JoinGroupResponse.json}, which are byte-equivalent to Apache Kafka 3.6.1):
 * <ul>
 *   <li><b>Request</b>: {@code GroupId(string) + SessionTimeoutMs(int32)} — then, <b>v1+</b>,
 *       {@code RebalanceTimeoutMs(int32)}; then {@code MemberId(string)} — then, <b>v5+</b>,
 *       {@code GroupInstanceId(string, nullable)}; then {@code ProtocolType(string)} +
 *       {@code Protocols[] { Name(string, mapKey), Metadata(bytes) }} — then, <b>v8+</b>,
 *       {@code Reason(string, nullable)} (KIP-800).</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from <b>v2+</b>) +
 *       {@code ErrorCode(int16) + GenerationId(int32)} — then, <b>v7+</b>, {@code
 *       ProtocolType(string, nullable)} (KIP-559) — then {@code ProtocolName(string, nullable
 *       from v7+)} + {@code Leader(string)} — then, <b>v9+</b>, {@code SkipAssignment(bool)} —
 *       then {@code MemberId(string)} + {@code Members[] { MemberId(string), GroupInstanceId(string,
 *       nullable, v5+), Metadata(bytes) }}.</li>
 *   <li><b>v6+</b>: flexible encoding (KIP-482) — compact strings, array counts as N+1 varints,
 *       and trailing tagged-fields trailers (per-struct and top-level, all zero here). v8 is
 *       wire-identical to v7 except the added {@code Reason} (request); v9 adds
 *       {@code SkipAssignment} (response).</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode and
 * read + discarded on decode: request {@code GroupInstanceId} (null) at v5+ and {@code Reason}
 * (null) at v8+; the response {@code ThrottleTimeMs} (0) at v2+, {@code ProtocolType} (null) at
 * v7+, {@code SkipAssignment} (false) at v9+, and per-member {@code GroupInstanceId} (null) at
 * v5+. The request {@code RebalanceTimeoutMs} (v1+) and response {@code ProtocolName} ARE model
 * fields and round-trip. At the pinned v0, the request {@code RebalanceTimeoutMs} is absent on the
 * wire (spec: introduced at v1), so a v0 decode reports the spec default {@code -1}.
 *
 * @since 0.1.0
 */
public final class JoinGroupCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.4+ / KIP-848 era). */
    public static final short MAX_VERSION = 9;

    /** Flexible (KIP-482) encoding starts at v6; strings become compact. */
    public static final short FLEXIBLE_START = 6;

    /** Version pinned for the client/broker interaction (the in-house client sends v0). */
    public static final short PINNED_VERSION = 0;

    private JoinGroupCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "JoinGroup v" + version + " not implemented (range 0–9)");
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
     * On-the-wire size of a non-nullable {@code bytes} field. Fixed-width: int32 length prefix
     * (4 bytes) + bytes. Flexible: varint length prefix (length + 1) + bytes. An empty array is
     * a distinct, valid value (length 0), so flexible uses varint(1) — never the null marker.
     */
    private static int bytesSize(boolean flexible, byte[] b) {
        int len = b == null ? 0 : b.length;
        return (flexible ? varintSize(len + 1) : 4) + len;
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
    public static byte[] encodeRequest(JoinGroupRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–9)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, JoinGroupRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = stringSize(flexible, req.groupId()); // GroupId
        size += 4; // SessionTimeoutMs (int32)
        if (version >= 1) {
            size += 4; // RebalanceTimeoutMs (int32, v1+)
        }
        size += stringSize(flexible, req.memberId()); // MemberId
        if (version >= 5) {
            size += stringSize(flexible, null); // GroupInstanceId — spec default null
        }
        size += stringSize(flexible, req.protocolType()); // ProtocolType
        size += flexible ? varintSize(req.protocols().size() + 1) : 4; // Protocols count
        for (var p : req.protocols()) {
            size += stringSize(flexible, p.name()); // Name
            size += bytesSize(flexible, p.metadata()); // Metadata (bytes, non-nullable)
            if (flexible) {
                size += 1; // per-protocol endTags
            }
        }
        if (version >= 8) {
            size += stringSize(flexible, null); // Reason — spec default null
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
        buf.putInt(req.sessionTimeoutMs()); // SessionTimeoutMs
        if (version >= 1) {
            buf.putInt(req.rebalanceTimeoutMs()); // RebalanceTimeoutMs (v1+)
        }
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.memberId()); // MemberId
        } else {
            KafkaCodecPrimitives.writeString(buf, req.memberId()); // MemberId
        }
        if (version >= 5) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // GroupInstanceId — v5 legacy
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.protocolType()); // ProtocolType
        } else {
            KafkaCodecPrimitives.writeString(buf, req.protocolType()); // ProtocolType
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, req.protocols().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(req.protocols().size());
        }
        for (var p : req.protocols()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, p.name()); // Name
            } else {
                KafkaCodecPrimitives.writeString(buf, p.name()); // Name
            }
            if (flexible) {
                writeCompactBytesNonNullable(buf, p.metadata()); // Metadata
            } else {
                KafkaCodecPrimitives.writeBytesField(buf, p.metadata()); // Metadata
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-protocol endTags
            }
        }
        if (version >= 8) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // Reason — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // Reason — v8 legacy (defensive)
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
    public static JoinGroupRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String groupId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // GroupId
                : KafkaCodecPrimitives.readString(buf); // GroupId
        int sessionTimeout = buf.getInt(); // SessionTimeoutMs
        int rebalanceTimeout = -1; // spec default; only present v1+
        if (version >= 1) {
            rebalanceTimeout = buf.getInt(); // RebalanceTimeoutMs (v1+)
        }
        String memberId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                : KafkaCodecPrimitives.readString(buf); // MemberId
        if (version >= 5) {
            // GroupInstanceId — read + discarded. Flexible (v6+): nullable compact string;
            // non-flexible v5: legacy nullable string (int16 length prefix).
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        String protocolType = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // ProtocolType
                : KafkaCodecPrimitives.readString(buf); // ProtocolType
        int protocolCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<JoinGroupRequest.Protocol> protocols = new ArrayList<>(protocolCount);
        for (int i = 0; i < protocolCount; i++) {
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Name
                    : KafkaCodecPrimitives.readString(buf); // Name
            byte[] metadata = flexible
                    ? readCompactBytesNonNullable(buf) // Metadata
                    : KafkaCodecPrimitives.readBytesField(buf); // Metadata
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-protocol
            }
            protocols.add(new JoinGroupRequest.Protocol(name, metadata));
        }
        if (version >= 8) {
            // Reason — read + discarded. Flexible (v8+): nullable compact string.
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new JoinGroupRequest(groupId, sessionTimeout, rebalanceTimeout, memberId,
                protocolType, protocols);
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(JoinGroupResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–9)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, JoinGroupResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 2) {
            size += 4; // ThrottleTimeMs (int32)
        }
        size += 2; // ErrorCode (int16)
        size += 4; // GenerationId (int32)
        if (version >= 7) {
            size += stringSize(flexible, null); // ProtocolType — spec default null
        }
        size += stringSize(flexible, resp.protocolName()); // ProtocolName (nullable v7+)
        size += stringSize(flexible, resp.leader()); // Leader
        if (version >= 9) {
            size += 1; // SkipAssignment (bool, 1 byte)
        }
        size += stringSize(flexible, resp.memberId()); // MemberId
        size += flexible ? varintSize(resp.members().size() + 1) : 4; // Members count
        for (var m : resp.members()) {
            size += stringSize(flexible, m.memberId()); // MemberId
            if (version >= 5) {
                size += stringSize(flexible, null); // GroupInstanceId — spec default null
            }
            size += bytesSize(flexible, m.metadata()); // Metadata (bytes, non-nullable)
            if (flexible) {
                size += 1; // per-member endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 2) {
            buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        }
        buf.putShort(resp.errorCode()); // ErrorCode
        buf.putInt(resp.generationId()); // GenerationId
        if (version >= 7) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // ProtocolType — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // ProtocolType — v7 legacy (defensive)
            }
        }
        if (version >= 7) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, resp.protocolName()); // ProtocolName (nullable)
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, resp.protocolName()); // ProtocolName (v7 legacy)
            }
        } else if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, resp.protocolName()); // ProtocolName (v6 non-nullable)
        } else {
            KafkaCodecPrimitives.writeString(buf, resp.protocolName()); // ProtocolName (v0–5 non-nullable)
        }
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, resp.leader()); // Leader
        } else {
            KafkaCodecPrimitives.writeString(buf, resp.leader()); // Leader
        }
        if (version >= 9) {
            buf.put((byte) 0); // SkipAssignment — spec default false, model does not expose it
        }
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, resp.memberId()); // MemberId
        } else {
            KafkaCodecPrimitives.writeString(buf, resp.memberId()); // MemberId
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.members().size() + 1); // KIP-482: N+1
        } else {
            buf.putInt(resp.members().size());
        }
        for (var m : resp.members()) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, m.memberId()); // MemberId
            } else {
                KafkaCodecPrimitives.writeString(buf, m.memberId()); // MemberId
            }
            if (version >= 5) {
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, null); // GroupInstanceId — spec default null
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, null); // GroupInstanceId — v5 legacy
                }
            }
            if (flexible) {
                writeCompactBytesNonNullable(buf, m.metadata()); // Metadata
            } else {
                KafkaCodecPrimitives.writeBytesField(buf, m.metadata()); // Metadata
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-member endTags
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
    public static JoinGroupResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 2) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode = buf.getShort(); // ErrorCode
        int generationId = buf.getInt(); // GenerationId
        if (version >= 7) {
            // ProtocolType — read + discarded. Flexible (v7+): nullable compact string.
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        String protocolName;
        if (version >= 7) {
            protocolName = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // ProtocolName (nullable)
                    : KafkaCodecPrimitives.readNullableString(buf); // ProtocolName (v7 legacy)
        } else if (flexible) {
            protocolName = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // ProtocolName (v6)
        } else {
            protocolName = KafkaCodecPrimitives.readString(buf); // ProtocolName (v0–5)
        }
        String leader = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Leader
                : KafkaCodecPrimitives.readString(buf); // Leader
        if (version >= 9) {
            buf.get(); // SkipAssignment — read + discarded (1 byte)
        }
        String memberId = flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                : KafkaCodecPrimitives.readString(buf); // MemberId
        int memberCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<JoinGroupResponse.Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            String mId = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // MemberId
                    : KafkaCodecPrimitives.readString(buf); // MemberId
            if (version >= 5) {
                // GroupInstanceId — read + discarded. Flexible (v6+): nullable compact string;
                // non-flexible v5: legacy nullable string.
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf);
                } else {
                    KafkaCodecPrimitives.readNullableString(buf);
                }
            }
            byte[] metadata = flexible
                    ? readCompactBytesNonNullable(buf) // Metadata
                    : KafkaCodecPrimitives.readBytesField(buf); // Metadata
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-member
            }
            members.add(new JoinGroupResponse.Member(mId, metadata));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new JoinGroupResponse(errorCode, generationId, protocolName, leader, memberId, members);
    }

    // ── Flexible bytes helpers (non-nullable) ───────────────────────────
    // The primitives' write/readCompactBytes use the NULLABLE KIP-482 bytes encoding
    // (varint 1 = null). JoinGroup's Metadata fields are NON-nullable bytes, where an empty
    // array is a distinct, valid value (varint 1 = empty, not null). These helpers give the
    // non-nullable semantics: varint(length + 1) + bytes, never a null marker.

    /**
     * Writes a non-nullable byte array in flexible encoding: varint(length + 1) + bytes.
     * A null value is treated as the empty array (varint 1), matching the non-nullable default.
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
     * @return the bytes; the empty array if the length varint was 1 (defensive — empty, not null)
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
