package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.DeleteGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteGroupsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka DeleteGroups request (API key 42) and response — delete the
 * metadata of consumer groups.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v2 is encoded so a frame
 * carrying any DeleteGroups version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/DeleteGroupsRequest.json} and
 * {@code DeleteGroupsResponse.json}):
 * <ul>
 *   <li>v0 (both): baseline. Request: {@code Groups[] { GroupId }}. Response: leading
 *       {@code ThrottleTimeMs} (int32, present from v0) + {@code Results[] { GroupId,
 *       ErrorCode }}.</li>
 *   <li>v1 (both): byte-identical to v0 (spec: "Version 1 is the same as version 0").</li>
 *   <li>v2 (both): flexible encoding (KIP-482): compact arrays/strings and tagged fields on
 *       every struct level (result, top). No tagged fields are emitted (all zero); received
 *       tagged fields are skipped.</li>
 * </ul>
 * The response {@code ThrottleTimeMs} is not exposed by the in-house model: the spec default
 * (0) is written and the value is read + discarded.
 *
 * @since 0.1.0
 */
public final class DeleteGroupsCodec {

    /** Pinned version for client/broker DeleteGroups interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v2+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 2;

    /** Highest API version with a defined wire format in the spec (v2). */
    private static final short MAX_VERSION = 2;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private DeleteGroupsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "DeleteGroups v" + version + " not implemented (range 0–2)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteGroupsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(DeleteGroupsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a DeleteGroups request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, DeleteGroupsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(req);
        }
        return flexibleRequest(req);
    }

    /**
     * Decode a DeleteGroups request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the request body
     * @return the decoded request
     */
    public static DeleteGroupsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(buf);
        }
        return flexibleRequest(buf);
    }

    // ── Fixed-width request (v0–v1, byte-identical) ─────────────────────
    // Order: numGroups(int32), [ GroupId(int16 string) ]

    private static byte[] fixedRequest(DeleteGroupsRequest req) {
        int size = 4; // numGroups
        for (String g : req.groups()) {
            size += 2 + g.getBytes(StandardCharsets.UTF_8).length; // GroupId
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.groups().size());
        for (String g : req.groups()) {
            KafkaCodecPrimitives.writeString(buf, g);
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteGroupsRequest fixedRequest(ByteBuffer b) {
        int count = b.getInt();
        List<String> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            groups.add(KafkaCodecPrimitives.readString(b));
        }
        return new DeleteGroupsRequest(groups);
    }

    // ── Flexible request (v2) ───────────────────────────────────────────
    // Order: numGroups(varint, KIP-482 N+1), [ GroupId(compact string) ], endTags

    private static byte[] flexibleRequest(DeleteGroupsRequest req) {
        int n = req.groups().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numGroups (KIP-482: N+1)
        for (String g : req.groups()) {
            size += compactStringSize(g); // GroupId
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (String g : req.groups()) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, g);
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteGroupsRequest flexibleRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<String> groups = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            groups.add(KafkaCodecPrimitives.readCompactStringNonNullable(b));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteGroupsRequest(groups);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteGroupsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(DeleteGroupsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a DeleteGroups response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, DeleteGroupsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode a DeleteGroups response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the response body
     * @return the decoded response
     */
    public static DeleteGroupsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v1, byte-identical) ────────────────────
    // Order: ThrottleTimeMs(int32, present from v0), numResults(int32),
    //   [ GroupId(int16 string), ErrorCode(int16) ]

    private static byte[] fixedResponse(DeleteGroupsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 4; // numResults
        for (DeleteGroupsResponse.GroupResult r : resp.results()) {
            size += 2 + r.groupId().getBytes(StandardCharsets.UTF_8).length; // GroupId
            size += 2; // ErrorCode
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putInt(resp.results().size());
        for (DeleteGroupsResponse.GroupResult r : resp.results()) {
            KafkaCodecPrimitives.writeString(buf, r.groupId());
            buf.putShort(r.errorCode());
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteGroupsResponse fixedResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int count = b.getInt();
        List<DeleteGroupsResponse.GroupResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String groupId = KafkaCodecPrimitives.readString(b);
            short errorCode = b.getShort();
            results.add(new DeleteGroupsResponse.GroupResult(groupId, errorCode));
        }
        return new DeleteGroupsResponse(results);
    }

    // ── Flexible response (v2) ──────────────────────────────────────────
    // Order: ThrottleTimeMs(int32), numResults(varint, KIP-482 N+1),
    //   [ GroupId(compact string), ErrorCode(int16), endTags ] ], endTags

    private static byte[] flexibleResponse(DeleteGroupsResponse resp) {
        int n = resp.results().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(n + 1); // numResults (KIP-482: N+1)
        for (DeleteGroupsResponse.GroupResult r : resp.results()) {
            size += compactStringSize(r.groupId()); // GroupId
            size += 2; // ErrorCode
            size += 1; // per-result endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DeleteGroupsResponse.GroupResult r : resp.results()) {
            KafkaCodecPrimitives.writeCompactStringNonNullable(buf, r.groupId());
            buf.putShort(r.errorCode());
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-result endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteGroupsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DeleteGroupsResponse.GroupResult> results = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String groupId = KafkaCodecPrimitives.readCompactStringNonNullable(b);
            short errorCode = b.getShort();
            KafkaCodecPrimitives.skipTaggedFields(b); // per-result
            results.add(new DeleteGroupsResponse.GroupResult(groupId, errorCode));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteGroupsResponse(results);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a non-null string in flexible (compact) encoding. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }
}
