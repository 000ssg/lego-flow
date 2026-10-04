package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.DeleteTopicsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteTopicsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka DeleteTopics request (API key 20) and response — delete topics.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v6 is encoded so a frame
 * carrying any DeleteTopics version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/DeleteTopicsRequest.json} and
 * {@code DeleteTopicsResponse.json}):
 * <ul>
 *   <li>v0 (request): baseline — {@code TopicNames[]string} + {@code timeoutMs}.</li>
 *   <li>v1–v3 (request): byte-identical to v0 (spec: "Versions 0, 1, 2, and 3 are the same").</li>
 *   <li>v1+: response adds {@code ThrottleTimeMs} (int32). Spec default ({@code 0}) written,
 *       read + discarded (the model does not expose it).</li>
 *   <li>v4+ (both): flexible encoding (KIP-482): compact arrays/strings and tagged fields.</li>
 *   <li>v5+: response adds {@code ErrorMessage} (nullable string) per result. Spec default
 *       ({@code null}) written, read + discarded.</li>
 *   <li>v6 (request): reorganizes into {@code Topics[]DeleteTopicState[Name?, TopicId]}
 *       replacing {@code TopicNames}. The model exposes names only: each entry is written
 *       with its name and the absent default TopicId (all-zero uuid), TopicId is read
 *       + discarded; {@code Name} is nullable on the wire (read via {@code readCompactString}).</li>
 *   <li>v6 (response): adds per-result {@code TopicId} (uuid, between Name and ErrorCode).
 *       All-zero uuid written (model does not expose it), read + discarded; {@code Name}
 *       becomes nullable on the wire.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class DeleteTopicsCodec {

    /** Pinned version for client/broker DeleteTopics interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v4+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 4;
    /** v6 reorganizes the request into Topics[]DeleteTopicState. */
    private static final short TOPICS_REORGANIZED = 6;
    /** Highest API version with a defined wire format in the spec (v6). */
    private static final short MAX_VERSION = 6;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private DeleteTopicsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "DeleteTopics v" + version + " not implemented (range 0–6)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteTopicsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(DeleteTopicsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a DeleteTopics request body at the given API version.
     *
     * @param version the API version (0–6)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, DeleteTopicsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(req);
        }
        if (version < TOPICS_REORGANIZED) {
            return flexibleNamesRequest(req);
        }
        return flexibleTopicsRequest(req);
    }

    /**
     * Decode a DeleteTopics request body at the given API version.
     *
     * @param version the API version (0–6)
     * @param buf     the request body
     * @return the decoded request
     */
    public static DeleteTopicsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(buf);
        }
        if (version < TOPICS_REORGANIZED) {
            return flexibleNamesRequest(buf);
        }
        return flexibleTopicsRequest(buf);
    }

    // ── Fixed-width request (v0–v3, byte-identical) ─────────────────────
    // Order: numNames, [ Name(int16 string) ], timeoutMs

    private static byte[] fixedRequest(DeleteTopicsRequest req) {
        int size = 4; // numNames
        for (String name : req.topicNames()) {
            size += 2 + name.getBytes(StandardCharsets.UTF_8).length; // Name
        }
        size += 4; // timeoutMs

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.topicNames().size());
        for (String name : req.topicNames()) {
            KafkaCodecPrimitives.writeString(buf, name);
        }
        buf.putInt(req.timeoutMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteTopicsRequest fixedRequest(ByteBuffer b) {
        int count = b.getInt();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(KafkaCodecPrimitives.readString(b));
        }
        int timeoutMs = b.getInt();
        return new DeleteTopicsRequest(names, timeoutMs);
    }

    // ── Flexible request (v4–v5) ────────────────────────────────────────
    // Order: numNames(varint, KIP-482 N+1), [ Name(compact) ], timeoutMs, endTags

    private static byte[] flexibleNamesRequest(DeleteTopicsRequest req) {
        int n = req.topicNames().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numNames (KIP-482: N+1)
        for (String name : req.topicNames()) {
            size += compactStringSize(name); // Name
        }
        size += 4; // timeoutMs (fixed-width even in flexible)
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (String name : req.topicNames()) {
            KafkaCodecPrimitives.writeCompactString(buf, name);
        }
        buf.putInt(req.timeoutMs());
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteTopicsRequest flexibleNamesRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<String> names = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            names.add(KafkaCodecPrimitives.readCompactString(b));
        }
        int timeoutMs = b.getInt();
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteTopicsRequest(names, timeoutMs);
    }

    // ── Flexible request (v6) ───────────────────────────────────────────
    // Order: numTopics(varint, KIP-482 N+1),
    //   [ Name(compact, nullable), TopicId(uuid 16 bytes), endTags ] ], timeoutMs, endTags

    private static byte[] flexibleTopicsRequest(DeleteTopicsRequest req) {
        int n = req.topicNames().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numTopics (KIP-482: N+1)
        for (String name : req.topicNames()) {
            size += compactStringSize(name); // Name
            size += 16; // TopicId (uuid)
            size += 1; // per-entry endTags (0)
        }
        size += 4; // timeoutMs
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (String name : req.topicNames()) {
            KafkaCodecPrimitives.writeCompactString(buf, name);
            for (int i = 0; i < 16; i++) {
                buf.put((byte) 0); // TopicId absent default (all-zero uuid)
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-entry endTags
        }
        buf.putInt(req.timeoutMs());
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteTopicsRequest flexibleTopicsRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<String> names = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            // Name is nullable in v6: varint 0 = null. The model carries names, so the
            // null marker is preserved in the list as a null entry.
            names.add(KafkaCodecPrimitives.readCompactString(b));
            b.position(b.position() + 16); // TopicId, read + discarded
            KafkaCodecPrimitives.skipTaggedFields(b); // per-entry
        }
        int timeoutMs = b.getInt();
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteTopicsRequest(names, timeoutMs);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DeleteTopicsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(DeleteTopicsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a DeleteTopics response body at the given API version.
     *
     * @param version the API version (0–6)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, DeleteTopicsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(version, resp);
        }
        return flexibleResponse(version, resp);
    }

    /**
     * Decode a DeleteTopics response body at the given API version.
     *
     * @param version the API version (0–6)
     * @param buf     the response body
     * @return the decoded response
     */
    public static DeleteTopicsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(version, buf);
        }
        return flexibleResponse(version, buf);
    }

    // ── Fixed-width response (v0–v3) ────────────────────────────────────
    // Order: [ ThrottleTimeMs(1+) ], numResults, [ Name, ErrorCode ]

    private static byte[] fixedResponse(short version, DeleteTopicsResponse resp) {
        int size = 0;
        if (version >= 1) {
            size += 4; // ThrottleTimeMs
        }
        size += 4; // numResults
        for (DeleteTopicsResponse.TopicResult topic : resp.responses()) {
            size += 2 + topic.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 2; // ErrorCode
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 1) {
            buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        }
        buf.putInt(resp.responses().size());
        for (DeleteTopicsResponse.TopicResult topic : resp.responses()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putShort(topic.errorCode());
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteTopicsResponse fixedResponse(short version, ByteBuffer b) {
        if (version >= 1) {
            b.getInt(); // ThrottleTimeMs, read + discarded
        }
        int count = b.getInt();
        List<DeleteTopicsResponse.TopicResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = KafkaCodecPrimitives.readString(b);
            short errorCode = b.getShort();
            results.add(new DeleteTopicsResponse.TopicResult(name, errorCode));
        }
        return new DeleteTopicsResponse(results);
    }

    // ── Flexible response (v4–v6) ───────────────────────────────────────
    // Order: ThrottleTimeMs, numResults(varint, KIP-482 N+1),
    //   [ Name(compact, nullable in v6), [ TopicId(6+) ], ErrorCode,
    //     [ ErrorMessage(5+, compact-nullable) ], endTags ] ], endTags

    private static byte[] flexibleResponse(short version, DeleteTopicsResponse resp) {
        int n = resp.responses().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(n + 1); // numResults (KIP-482: N+1)
        for (DeleteTopicsResponse.TopicResult topic : resp.responses()) {
            size += compactNullableStringSize(topic.name()); // Name (nullable in v6)
            if (version >= TOPICS_REORGANIZED) {
                size += 16; // TopicId (uuid)
            }
            size += 2; // ErrorCode
            if (version >= 5) {
                size += 1; // ErrorMessage absent default (varint 0)
            }
            size += 1; // per-entry endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DeleteTopicsResponse.TopicResult topic : resp.responses()) {
            KafkaCodecPrimitives.writeCompactString(buf, topic.name());
            if (version >= TOPICS_REORGANIZED) {
                for (int i = 0; i < 16; i++) {
                    buf.put((byte) 0); // TopicId absent default (all-zero uuid)
                }
            }
            buf.putShort(topic.errorCode());
            if (version >= 5) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage absent (null)
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-entry endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DeleteTopicsResponse flexibleResponse(short version, ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DeleteTopicsResponse.TopicResult> results = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            // Name is nullable in v6: readCompactString returns null for varint 0.
            String name = KafkaCodecPrimitives.readCompactString(b);
            if (version >= TOPICS_REORGANIZED) {
                b.position(b.position() + 16); // TopicId, read + discarded
            }
            short errorCode = b.getShort();
            if (version >= 5) {
                // ErrorMessage is a nullable compact string: varint 0 = null, otherwise
                // varint(len+1) + data. readCompactString consumes the data when present,
                // keeping the buffer in sync; the value is discarded (model does not carry it).
                KafkaCodecPrimitives.readCompactString(b);
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-entry
            results.add(new DeleteTopicsResponse.TopicResult(name, errorCode));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DeleteTopicsResponse(results);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a non-null string in flexible (compact) encoding. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }

    /** Size of a nullable string in flexible (compact) encoding (null = 1-byte varint 0). */
    private static int compactNullableStringSize(String s) {
        if (s == null) {
            return KafkaCodecPrimitives.varintSize(0);
        }
        return compactStringSize(s);
    }
}
