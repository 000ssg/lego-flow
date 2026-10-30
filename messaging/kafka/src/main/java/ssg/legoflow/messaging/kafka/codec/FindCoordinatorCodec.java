package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.FindCoordinatorRequest;
import ssg.legoflow.messaging.kafka.protocol.FindCoordinatorResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Codec for the Kafka <b>Consumer Groups</b> FindCoordinator API (key 10), versions v0–v4, both
 * directions.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the in-house
 * client sends, {@code ApiKey.FIND_COORDINATOR.key()} + version 0), while the full version range
 * v0–v4 is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/FindCoordinator.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/FindCoordinatorRequest.json} and
 * {@code FindCoordinatorResponse.json}, which are byte-equivalent to the Apache Kafka spec):
 * <ul>
 *   <li><b>Request</b>: {@code Key(string)} in v0–v3 (fixed int16-prefixed through v2, flexible
 *       compact from v3); {@code KeyType(int8)} from v1. In v1–v3 the order is {@code Key, KeyType}.
 *       At <b>v4</b> (KIP-699 batching) the {@code Key} is replaced by {@code KeyType(int8),
 *       CoordinatorKeys([]string)}; the single in-house key is synthesized as a one-element
 *       {@code CoordinatorKeys} array, and decode collapses the first element back into the model.</li>
 *   <li><b>Response</b>: leading {@code ThrottleTimeMs(int32)} (present from <b>v1+</b>); then, in
 *       <b>v0–v3</b>, {@code ErrorCode(int16), ErrorMessage(string, nullable, v1–v3), NodeId(int32),
 *       Host(string), Port(int32)}; at <b>v4</b> (KIP-699) these are replaced by
 *       {@code Coordinators[]Coordinator { Key(string), NodeId(int32), Host(string), Port(int32),
 *       ErrorCode(int16), ErrorMessage(string, nullable) }}. The single in-house coordinator maps to
 *       a one-element v4 array; the first element is used on decode.</li>
 *   <li><b>v3+</b>: flexible (KIP-482) encoding — compact strings, array counts as N+1 varints,
 *       and trailing tagged-fields trailers (all zero here).</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode and read +
 * discarded on decode: the response {@code ThrottleTimeMs} (0) at v1+, the top-level
 * {@code ErrorMessage} (null) at v1–v3, and the v4 {@code Coordinator.Key} ("") and per-coordinator
 * {@code ErrorMessage} (null). The in-house request carries one (key, keyType) pair; v4 synthesizes
 * it as a one-element {@code CoordinatorKeys} array and decode collapses the first element.
 *
 * @since 0.1.0
 */
public final class FindCoordinatorCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.1+ / KIP-699 batched coordinator lookup). */
    public static final short MAX_VERSION = 4;

    /** Flexible (KIP-482) encoding starts at v3; strings become compact. */
    public static final short FLEXIBLE_START = 3;

    /** Version the request gained {@code KeyType(int8)}. */
    public static final short KEY_TYPE_START = 1;

    /** Version the request/response switched to the batched layout (KIP-699). */
    public static final short BATCH_START = 4;

    /** Version the response gained {@code ThrottleTimeMs(int32)}. */
    public static final short THROTTLE_TIME_START = 1;

    /** Version the response gained a top-level {@code ErrorMessage(string, nullable)}. */
    public static final short ERROR_MESSAGE_START = 1;

    /** Version pinned for the client/broker interaction (the in-house client sends v0). */

    private FindCoordinatorCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "FindCoordinator v" + version + " not implemented (range 0–4)");
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
    public static byte[] encodeRequest(short version, FindCoordinatorRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= KEY_TYPE_START) {
            size += 1; // KeyType (int8) — present in v1–v4
        }
        if (version >= BATCH_START) {
            size += varintSize(2); // CoordinatorKeys count (KIP-482: N+1 = 2 for the single in-house key)
            size += stringSize(flexible, req.key()); // CoordinatorKeys[0]
        } else {
            size += stringSize(flexible, req.key()); // Key
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= BATCH_START) {
            // v4+: KeyType(int8), CoordinatorKeys([]string)
            buf.put((byte) req.keyType()); // KeyType
            KafkaCodecPrimitives.writeVarint(buf, 2); // CoordinatorKeys count (KIP-482: N+1 = 2)
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.key()); // CoordinatorKeys[0]
        } else if (version >= KEY_TYPE_START) {
            // v1–v3: Key(string), KeyType(int8) — spec order: Key first, then KeyType
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, req.key()); // Key
            } else {
                KafkaCodecPrimitives.writeString(buf, req.key()); // Key
            }
            buf.put((byte) req.keyType()); // KeyType
        } else {
            // v0: Key(string)
            KafkaCodecPrimitives.writeString(buf, req.key()); // Key
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
    public static FindCoordinatorRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        String key;
        byte keyType;
        if (version >= BATCH_START) {
            // v4+: KeyType(int8), CoordinatorKeys([]string) — collapse the first key into the model.
            keyType = buf.get(); // KeyType
            int count = KafkaCodecPrimitives.readVarint(buf) - 1; // CoordinatorKeys count (KIP-482: N+1)
            if (count <= 0) {
                key = null;
            } else {
                key = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // CoordinatorKeys[0]
                // Remaining keys (count - 1) carry data the single-key model cannot hold; read + discard.
                for (int i = 1; i < count; i++) {
                    KafkaCodecPrimitives.readCompactStringNonNullable(buf);
                }
            }
        } else if (version >= KEY_TYPE_START) {
            // v1–v3: Key(string), KeyType(int8) — spec order: Key first, then KeyType
            key = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Key
                    : KafkaCodecPrimitives.readString(buf); // Key
            keyType = buf.get(); // KeyType
        } else {
            // v0: Key(string)
            key = KafkaCodecPrimitives.readString(buf); // Key
            keyType = FindCoordinatorRequest.KEY_TYPE_GROUP; // v0 has no KeyType; spec default 0
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new FindCoordinatorRequest(key, keyType);
    }

    // ── Response ────────────────────────────────────────────────────────
    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, FindCoordinatorResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= THROTTLE_TIME_START) {
            size += 4; // ThrottleTimeMs (int32)
        }
        if (version >= BATCH_START) {
            // v4+: Coordinators[]Coordinator — the single in-house coordinator is element 0.
            size += varintSize(2); // Coordinators count (KIP-482: N+1 = 2)
            size += stringSize(flexible, ""); // Coordinator[0].Key — spec default, model does not expose it
            size += 4; // Coordinator[0].NodeId (int32)
            size += stringSize(flexible, resp.host()); // Coordinator[0].Host
            size += 4; // Coordinator[0].Port (int32)
            size += 2; // Coordinator[0].ErrorCode (int16)
            size += stringSize(flexible, null); // Coordinator[0].ErrorMessage (nullable)
            size += 1; // per-coordinator endTags (struct trailer)
        } else {
            size += 2; // ErrorCode (int16, v0–v3)
            if (version >= ERROR_MESSAGE_START) {
                size += stringSize(flexible, null); // ErrorMessage (nullable, v1–v3)
            }
            size += 4; // NodeId (int32, v0–v3)
            size += stringSize(flexible, resp.host()); // Host (v0–v3)
            size += 4; // Port (int32, v0–v3)
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= THROTTLE_TIME_START) {
            buf.putInt(0); // ThrottleTimeMs — spec default, model does not expose it
        }
        if (version >= BATCH_START) {
            // v4+: ThrottleTimeMs, Coordinators[]Coordinator { Key, NodeId, Host, Port, ErrorCode,
            // ErrorMessage }. The single in-house coordinator is element 0.
            KafkaCodecPrimitives.writeVarint(buf, 2); // Coordinators count (KIP-482: N+1 = 2)
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, ""); // Coordinator[0].Key — spec default
            buf.putInt(resp.nodeId()); // Coordinator[0].NodeId
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, resp.host()); // Coordinator[0].Host
            buf.putInt(resp.port()); // Coordinator[0].Port
            buf.putShort(resp.errorCode()); // Coordinator[0].ErrorCode
            KafkaCodecPrimitives.writeCompactString(buf, null); // Coordinator[0].ErrorMessage — spec default null
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-coordinator endTags (struct trailer)
        } else {
            buf.putShort(resp.errorCode()); // ErrorCode (int16, v0–v3)
            if (version >= ERROR_MESSAGE_START) {
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, null); // ErrorMessage (nullable, v1–v3) — default
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, null); // ErrorMessage (nullable, v1–v3) — default
                }
            }
            buf.putInt(resp.nodeId()); // NodeId (int32, v0–v3)
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, resp.host()); // Host (v0–v3)
            } else {
                KafkaCodecPrimitives.writeString(buf, resp.host()); // Host (v0–v3)
            }
            buf.putInt(resp.port()); // Port (int32, v0–v3)
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
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static FindCoordinatorResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= THROTTLE_TIME_START) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode;
        int nodeId;
        String host;
        int port;
        if (version >= BATCH_START) {
            // v4+: Coordinators[]Coordinator — read the first coordinator, discard the rest.
            int count = KafkaCodecPrimitives.readVarint(buf) - 1; // Coordinators count (KIP-482: N+1)
            if (count <= 0) {
                errorCode = 0;
                nodeId = -1;
                host = null;
                port = -1;
            } else {
                // Coordinator[0]: Key, NodeId, Host, Port, ErrorCode, ErrorMessage.
                KafkaCodecPrimitives.readCompactStringNonNullable(buf); // Key — discarded
                nodeId = buf.getInt(); // NodeId
                host = KafkaCodecPrimitives.readCompactStringNonNullable(buf); // Host
                port = buf.getInt(); // Port
                errorCode = buf.getShort(); // ErrorCode
                KafkaCodecPrimitives.readCompactString(buf); // ErrorMessage — discarded
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-coordinator endTags (struct trailer)
                // Remaining coordinators (count - 1) carry data the single-coordinator model cannot hold.
                for (int i = 1; i < count; i++) {
                    skipCoordinatorStruct(buf);
                }
            }
        } else {
            errorCode = buf.getShort(); // ErrorCode (int16, v0–v3)
            if (version >= ERROR_MESSAGE_START) {
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf); // ErrorMessage (nullable, v1–v3) — discarded
                } else {
                    KafkaCodecPrimitives.readNullableString(buf); // ErrorMessage (nullable, v1–v3) — discarded
                }
            }
            nodeId = buf.getInt(); // NodeId (int32, v0–v3)
            host = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Host (v0–v3)
                    : KafkaCodecPrimitives.readString(buf); // Host (v0–v3)
            port = buf.getInt(); // Port (int32, v0–v3)
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new FindCoordinatorResponse(errorCode, nodeId, host, port);
    }

    /** Skips a v4+ Coordinator struct (compact key, int32, compact host, int32, int16, compact message, trailer). */
    private static void skipCoordinatorStruct(ByteBuffer buf) {
        KafkaCodecPrimitives.readCompactStringNonNullable(buf); // Key
        buf.getInt(); // NodeId
        KafkaCodecPrimitives.readCompactStringNonNullable(buf); // Host
        buf.getInt(); // Port
        buf.getShort(); // ErrorCode
        KafkaCodecPrimitives.readCompactString(buf); // ErrorMessage (nullable)
        KafkaCodecPrimitives.skipTaggedFields(buf); // per-coordinator endTags (struct trailer)
    }
}
