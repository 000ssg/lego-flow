package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.DescribeGroupsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated versioned codec for DescribeGroups (API key 15), v0–v5.
 *
 * <p>Version history (per the vendored {@code DescribeGroupsRequest.json} /
 * {@code DescribeGroupsResponse.json}, frozen order tables in
 * {@code doc/spec/order/DescribeGroups.{Request,Response}.txt}):
 *
 * <ul>
 *   <li><b>Request</b>: v0–v2 {@code Groups[]string} (non-nullable). v3+ adds
 *       {@code IncludeAuthorizedOperations(bool)} (KIP-556). v5: flexible
 *       encoding (KIP-482) — array count as N+1 varint, trailing tagged-fields
 *       trailer (zero here).</li>
 *   <li><b>Response</b>: v0 {@code Groups[]DescribedGroup}; v1+ leading
 *       {@code ThrottleTimeMs(int32)} (ignorable); v3+ per-group
 *       {@code AuthorizedOperations(int32)} (KIP-556, spec default
 *       {@code Integer.MIN_VALUE}); v4+ per-member nullable
 *       {@code GroupInstanceId(string)} (KIP-343, spec default null); v5:
 *       flexible encoding (KIP-482) — compact strings, varint array counts,
 *       per-member / per-group / top-level tagged-fields trailers (all zero
 *       here).</li>
 * </ul>
 * Fields the in-house models do not expose (IncludeAuthorizedOperations,
 * GroupInstanceId, AuthorizedOperations) are written with their spec defaults
 * on encode and read + discarded on decode. At the pinned v0, both bodies are
 * exactly the legacy field layouts.
 *
 * @since 0.1.0
 */
public final class DescribeGroupsCodec {

    /** Spec minimum version (Kafka 0.9). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.0+). */
    public static final short MAX_VERSION = 5;

    /** Flexible (KIP-482) encoding starts at v5; strings become compact. */
    public static final short FLEXIBLE_START = 5;

    private DescribeGroupsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "DescribeGroups v" + version + " not implemented (range 0–5)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a request body at the given API version.
     *
     * @param version the API version (0–5)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, DescribeGroupsRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        var groups = req.groups();

        int size = flexible ? varintSize(groups.size() + 1) : 4; // Groups count
        for (String g : groups) {
            size += stringSize(flexible, g);
        }
        if (version >= 3) {
            size += 1; // IncludeAuthorizedOperations (bool)
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, groups.size() + 1); // Groups count, KIP-482: N+1
        } else {
            buf.putInt(groups.size()); // Groups count
        }
        for (String g : groups) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, g);
            } else {
                KafkaCodecPrimitives.writeString(buf, g);
            }
        }
        if (version >= 3) {
            buf.put((byte) 0); // IncludeAuthorizedOperations — spec default false
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
    public static DescribeGroupsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int count = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        var groups = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            groups.add(flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf)
                    : KafkaCodecPrimitives.readString(buf));
        }
        if (version >= 3) {
            buf.get(); // IncludeAuthorizedOperations — read + discarded
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new DescribeGroupsRequest(groups);
    }

    // ── Response ────────────────────────────────────────────────────────

    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–5)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, DescribeGroupsResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        var groups = resp.groups();

        int size = 0;
        if (version >= 1) {
            size += 4; // ThrottleTimeMs (int32, v1+)
        }
        size += flexible ? varintSize(groups.size() + 1) : 4; // Groups count
        for (var g : groups) {
            size += 2; // ErrorCode (int16)
            size += stringSize(flexible, g.groupId());
            size += stringSize(flexible, g.state());
            size += nullableStringSize(flexible, g.protocolType());
            size += nullableStringSize(flexible, g.protocol());
            size += flexible ? varintSize(g.members().size() + 1) : 4; // Members count
            for (var m : g.members()) {
                size += stringSize(flexible, m.memberId());
                if (version >= 4) {
                    size += stringSize(flexible, null); // GroupInstanceId — spec default null
                }
                size += stringSize(flexible, m.clientId());
                size += stringSize(flexible, m.clientHost());
                size += bytesSize(flexible, m.memberMetadata());
                size += bytesSize(flexible, m.memberAssignment());
                if (flexible) {
                    size += 1; // per-member endTags
                }
            }
            if (version >= 3) {
                size += 4; // AuthorizedOperations (int32, v3+)
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
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, groups.size() + 1); // Groups count, KIP-482: N+1
        } else {
            buf.putInt(groups.size()); // Groups count
        }
        for (var g : groups) {
            buf.putShort(g.errorCode()); // ErrorCode
            writeString(flexible, buf, g.groupId()); // GroupId
            writeString(flexible, buf, g.state()); // GroupState
            writeNullableString(flexible, buf, g.protocolType()); // ProtocolType — spec nullable
            writeNullableString(flexible, buf, g.protocol()); // ProtocolData — spec nullable
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, g.members().size() + 1); // Members count, N+1
            } else {
                buf.putInt(g.members().size()); // Members count
            }
            for (var m : g.members()) {
                writeString(flexible, buf, m.memberId()); // MemberId
                if (version >= 4) {
                    writeNullableString(flexible, buf, null); // GroupInstanceId — spec default null
                }
                writeString(flexible, buf, m.clientId()); // ClientId
                writeString(flexible, buf, m.clientHost()); // ClientHost
                writeBytes(flexible, buf, m.memberMetadata()); // MemberMetadata
                writeBytes(flexible, buf, m.memberAssignment()); // MemberAssignment
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-member endTags
                }
            }
            if (version >= 3) {
                buf.putInt(Integer.MIN_VALUE); // AuthorizedOperations — spec default -2147483648
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
     * @param version the API version (0–5)
     * @param buf     the response body
     * @return the decoded response
     */
    public static DescribeGroupsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 1) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        int count = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        var groups = new java.util.ArrayList<DescribeGroupsResponse.GroupDescription>(count);
        for (int i = 0; i < count; i++) {
            short errorCode = buf.getShort(); // ErrorCode
            String groupId = readString(flexible, buf); // GroupId
            String state = readString(flexible, buf); // GroupState
            String protocolType = readNullableString(flexible, buf); // ProtocolType — spec nullable
            String protocol = readNullableString(flexible, buf); // ProtocolData — spec nullable
            int memberCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // N+1
            var members = new java.util.ArrayList<DescribeGroupsResponse.MemberDescription>(memberCount);
            for (int j = 0; j < memberCount; j++) {
                String mId = readString(flexible, buf); // MemberId
                if (version >= 4) {
                    readNullableString(flexible, buf); // GroupInstanceId — read + discarded
                }
                String cId = readString(flexible, buf); // ClientId
                String cHost = readString(flexible, buf); // ClientHost
                byte[] metadata = readBytes(flexible, buf); // MemberMetadata
                byte[] assignment = readBytes(flexible, buf); // MemberAssignment
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-member
                }
                members.add(new DescribeGroupsResponse.MemberDescription(
                        mId, cId, cHost, metadata, assignment));
            }
            if (version >= 3) {
                buf.getInt(); // AuthorizedOperations — read + discarded
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-group
            }
            groups.add(new DescribeGroupsResponse.GroupDescription(
                    errorCode, groupId, state, protocolType, protocol, members));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new DescribeGroupsResponse(groups);
    }

    // ── Field helpers ───────────────────────────────────────────────────

    /**
     * On-the-wire size of a non-nullable string field. Fixed-width: int16 length
     * prefix (2 bytes) + UTF-8 bytes. Flexible: varint(length + 1) + UTF-8 bytes.
     */
    private static int stringSize(boolean flexible, String s) {
        if (s == null) {
            return flexible ? 1 : 2; // null marker (varint(1) flexible; int16 -1 legacy)
        }
        if (flexible) {
            return KafkaCodecPrimitives.varintSize(s.getBytes(StandardCharsets.UTF_8).length + 1);
        }
        return 2 + s.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * On-the-wire size of a nullable string. Fixed-width: int16 length prefix (2 bytes,
     * -1 for null) + UTF-8 bytes. Flexible: varint(0) for null (KIP-482), otherwise
     * varint(length + 1) + UTF-8 bytes.
     */
    private static int nullableStringSize(boolean flexible, String s) {
        if (s == null) {
            return flexible ? 1 : 2; // null marker (varint(0) flexible; int16 -1 legacy)
        }
        if (flexible) {
            return KafkaCodecPrimitives.varintSize(s.getBytes(StandardCharsets.UTF_8).length + 1);
        }
        return 2 + s.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * On-the-wire size of a (nullable) bytes field. Fixed-width: int32 length
     * prefix (4 bytes) + data (or nothing when null). Flexible: varint
     * (length + 1, or 1 for null) + data.
     */
    private static int bytesSize(boolean flexible, byte[] b) {
        if (flexible) {
            return KafkaCodecPrimitives.varintSize(b == null ? 1 : b.length + 1) + (b == null ? 0 : b.length);
        }
        return 4 + (b == null ? 0 : b.length);
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

    /**
     * Writes a nullable string: flexible = varint(0) for null (KIP-482); legacy = int16 -1.
     */
    private static void writeNullableString(boolean flexible, ByteBuffer buf, String s) {
        if (flexible) {
            KafkaCodecPrimitives.writeCompactString(buf, s); // null -> varint(0)
        } else {
            KafkaCodecPrimitives.writeNullableString(buf, s); // null -> int16 -1
        }
    }

    /** Reads a nullable string (flexible: varint(0) for null; legacy: int16 -1). */
    private static String readNullableString(boolean flexible, ByteBuffer buf) {
        return flexible
                ? KafkaCodecPrimitives.readCompactString(buf)
                : KafkaCodecPrimitives.readNullableString(buf);
    }

    private static void writeBytes(boolean flexible, ByteBuffer buf, byte[] b) {
        if (flexible) {
            KafkaCodecPrimitives.writeCompactBytes(buf, b);
        } else {
            KafkaCodecPrimitives.writeBytesField(buf, b);
        }
    }

    private static byte[] readBytes(boolean flexible, ByteBuffer buf) {
        return flexible
                ? KafkaCodecPrimitives.readCompactBytes(buf)
                : KafkaCodecPrimitives.readBytesField(buf);
    }

    private static int varintSize(int value) {
        return KafkaCodecPrimitives.varintSize(value);
    }
}
