package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ListGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListGroupsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated versioned codec for ListGroups (API key 16), v0–v4.
 *
 * <p>Version history (per the vendored {@code ListGroupsRequest.json} /
 * {@code ListGroupsResponse.json}, frozen order tables in
 * {@code doc/spec/order/ListGroups.{Request,Response}.txt}):
 *
 * <ul>
 *   <li><b>Request</b>: v0–v2 empty body. v3: first flexible version (KIP-482) —
 *       trailing tagged-fields trailer (zero here). v4+ adds {@code StatesFilter
 *       ([]string)} — in-house model is empty, so an empty array is written on
 *       encode and the filter is read + discarded on decode.</li>
 *   <li><b>Response</b>: v0+ {@code ErrorCode(int16)} + {@code Groups
 *       []ListedGroup{GroupId, ProtocolType}}; v1+ leading
 *       {@code ThrottleTimeMs(int32)} (ignorable, in-house model has no field —
 *       written as zero, read + discarded); v3+: flexible encoding (KIP-482) —
 *       compact non-nullable strings, varint group count (N+1), per-group and
 *       top-level tagged-fields trailers (zero here); v4+ per-group
 *       {@code GroupState(string)} (KIP-518, ignorable) — in-house
 *       {@code GroupListing} exposes {@code state}, so it is written + read at
 *       v4+.</li>
 * </ul>
 * At the pinned v0, both bodies are exactly the legacy field layouts.
 *
 * @since 0.1.0
 */
public final class ListGroupsCodec {

    /** Spec minimum version (Kafka 0.9). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 2.7+). */
    public static final short MAX_VERSION = 4;

    /** Flexible (KIP-482) encoding starts at v3; strings become compact. */
    public static final short FLEXIBLE_START = 3;

    private ListGroupsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "ListGroups v" + version + " not implemented (range 0–4)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, ListGroupsRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 4) {
            size += KafkaCodecPrimitives.varintSize(1); // StatesFilter count (0+1) — in-house model is empty
        }
        if (flexible) {
            size += 1; // top-level endTags
        }
        if (size == 0) {
            return new byte[0];
        }
        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 4) {
            KafkaCodecPrimitives.writeVarint(buf, 1); // StatesFilter count = 0+1 (KIP-482); empty array
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
    public static ListGroupsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 4) {
            int count = KafkaCodecPrimitives.readVarint(buf) - 1; // StatesFilter count, KIP-482: N+1
            for (int i = 0; i < count; i++) {
                KafkaCodecPrimitives.readCompactStringNonNullable(buf); // read + discarded
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new ListGroupsRequest();
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, ListGroupsResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        var groups = resp.groups();

        int size = 0;
        if (version >= 1) {
            size += 4; // ThrottleTimeMs (int32, v1+)
        }
        size += 2; // ErrorCode (int16)
        size += flexible ? KafkaCodecPrimitives.varintSize(groups.size() + 1) : 4; // Groups count
        for (var g : groups) {
            size += stringSize(flexible, g.groupId());
            size += stringSize(flexible, g.protocolType());
            if (version >= 4) {
                size += stringSize(flexible, g.state());
            }
            if (flexible) {
                size += 1; // per-group endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 1) {
            buf.putInt(0); // ThrottleTimeMs — spec default 0
        }
        buf.putShort(resp.errorCode()); // ErrorCode
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, groups.size() + 1); // Groups count, KIP-482: N+1
        } else {
            buf.putInt(groups.size()); // Groups count
        }
        for (var g : groups) {
            writeString(flexible, buf, g.groupId()); // GroupId
            writeString(flexible, buf, g.protocolType()); // ProtocolType
            if (version >= 4) {
                writeString(flexible, buf, g.state()); // GroupState — KIP-518
            }
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
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static ListGroupsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 1) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        short errorCode = buf.getShort(); // ErrorCode
        int count = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        var groups = new java.util.ArrayList<ListGroupsResponse.GroupListing>(count);
        for (int i = 0; i < count; i++) {
            String groupId = readString(flexible, buf); // GroupId
            String protocolType = readString(flexible, buf); // ProtocolType
            String state = version >= 4 ? readString(flexible, buf) : null; // GroupState — KIP-518
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
            }
            groups.add(new ListGroupsResponse.GroupListing(groupId, protocolType, state));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new ListGroupsResponse(errorCode, groups);
    }

    // ── Field helpers ───────────────────────────────────────────────────

    /**
     * On-the-wire size of a non-nullable string field. Fixed-width: int16 length
     * prefix (2 bytes) + UTF-8 bytes. Flexible: varint(length + 1) + UTF-8 bytes.
     */
    private static int stringSize(boolean flexible, String s) {
        if (s == null) {
            return flexible ? 1 : 2; // empty marker (varint(1) flexible; int16 0 legacy)
        }
        if (flexible) {
            return KafkaCodecPrimitives.varintSize(s.getBytes(StandardCharsets.UTF_8).length + 1);
        }
        return 2 + s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void writeString(boolean flexible, ByteBuffer buf, String s) {
        if (flexible) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, s); // null -> "" (varint(1))
        } else {
            KafkaCodecPrimitives.writeString(buf, s == null ? "" : s); // non-nullable; null -> ""
        }
    }

    private static String readString(boolean flexible, ByteBuffer buf) {
        return flexible
                ? KafkaCodecPrimitives.readCompactStringNonNullable(buf)
                : KafkaCodecPrimitives.readString(buf);
    }
}
