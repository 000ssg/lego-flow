package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.CreatePartitionsRequest;
import ssg.legoflow.messaging.kafka.protocol.CreatePartitionsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka CreatePartitions request (API key 37) and response — increase the
 * number of partitions of a topic.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v3 is encoded so a frame
 * carrying any CreatePartitions version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/CreatePartitionsRequest.json} and
 * {@code CreatePartitionsResponse.json}):
 * <ul>
 *   <li>v0 (both): baseline. Request: {@code Topics[] { Name, Count, Assignments[] (nullable)
 *       { BrokerIds[] } }} + {@code TimeoutMs} + {@code ValidateOnly}. Response: leading
 *       {@code ThrottleTimeMs} (int32, present from v0) + per-topic {@code Results[] { Name,
 *       ErrorCode, ErrorMessage } }.</li>
 *   <li>v1 (both): byte-identical to v0 (spec: "Version 1 is the same as version 0").</li>
 *   <li>v2 (both): flexible encoding (KIP-482): compact arrays/strings and tagged fields on
 *       every struct level (topic, top). No tagged fields are emitted (all zero); received
 *       tagged fields are skipped. All fields keep fixed width except arrays (varint count =
 *       N+1, nullable null = 0) and the topic names (compact string).</li>
 *   <li>v3 (both): byte-identical to v2 (KIP-599 only affects the possible response error
 *       code, not the wire format).</li>
 * </ul>
 * The in-house models do not expose {@code Assignments} / {@code ValidateOnly} (request) or
 * {@code ThrottleTimeMs} / {@code ErrorMessage} (response): the spec defaults (null, false,
 * 0, null) are written and the values are read + discarded.
 *
 * @since 0.1.0
 */
public final class CreatePartitionsCodec {

    /** Pinned version for client/broker CreatePartitions interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v2+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 2;

    /** Highest API version with a defined wire format in the spec (v3). */
    private static final short MAX_VERSION = 3;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    /** Request {@code ValidateOnly} default — false (actually create). */
    private static final boolean DEFAULT_VALIDATE_ONLY = false;

    private CreatePartitionsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "CreatePartitions v" + version + " not implemented (range 0–3)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link CreatePartitionsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(CreatePartitionsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a CreatePartitions request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, CreatePartitionsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(req);
        }
        return flexibleRequest(req);
    }

    /**
     * Decode a CreatePartitions request body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the request body
     * @return the decoded request
     */
    public static CreatePartitionsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(buf);
        }
        return flexibleRequest(buf);
    }

    // ── Fixed-width request (v0–v1, byte-identical) ─────────────────────
    // Order: numTopics(int32), [ Name(int16 string), Count(int32),
    //   Assignments(int32 count, nullable -1 = null) [ BrokerIds(int32 count, int32[]) ] ],
    //   TimeoutMs(int32), ValidateOnly(bool)

    private static byte[] fixedRequest(CreatePartitionsRequest req) {
        int size = 4; // numTopics
        for (CreatePartitionsRequest.TopicNewPartitions t : req.topics()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // Count
            size += 4; // Assignments count (null = -1)
        }
        size += 4; // TimeoutMs
        size += 1; // ValidateOnly

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.topics().size());
        for (CreatePartitionsRequest.TopicNewPartitions t : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putInt(t.newCount());
            buf.putInt(-1); // Assignments null (spec default; model does not expose it)
        }
        buf.putInt(req.timeoutMs());
        buf.put((byte) (DEFAULT_VALIDATE_ONLY ? 1 : 0));
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreatePartitionsRequest fixedRequest(ByteBuffer b) {
        int topicCount = b.getInt();
        List<CreatePartitionsRequest.TopicNewPartitions> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(b);
            int count = b.getInt();
            skipFixedAssignments(b); // Assignments, read + discarded (model does not expose it)
            topics.add(new CreatePartitionsRequest.TopicNewPartitions(name, count));
        }
        int timeoutMs = b.getInt();
        b.get(); // ValidateOnly, read + discarded (model does not expose it)
        return new CreatePartitionsRequest(topics, timeoutMs);
    }

    // ── Flexible request (v2–v3, byte-identical) ────────────────────────
    // Order: numTopics(varint, KIP-482 N+1),
    //   [ Name(compact), Count(int32), Assignments(varint, N+1; null = 0)
    //     [ BrokerIds(varint, N+1; never null) [ int32 ] , endTags ] , endTags ] ],
    //   TimeoutMs(int32), ValidateOnly(bool), endTags

    private static byte[] flexibleRequest(CreatePartitionsRequest req) {
        int n = req.topics().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numTopics (KIP-482: N+1)
        for (CreatePartitionsRequest.TopicNewPartitions t : req.topics()) {
            size += compactStringSize(t.name()); // Name
            size += 4; // Count (fixed-width even in flexible)
            size += KafkaCodecPrimitives.varintSize(0); // Assignments null (varint 0)
            size += 1; // per-topic endTags (0)
        }
        size += 4; // TimeoutMs
        size += 1; // ValidateOnly
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (CreatePartitionsRequest.TopicNewPartitions t : req.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            buf.putInt(t.newCount());
            KafkaCodecPrimitives.writeVarint(buf, 0); // Assignments null
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        buf.putInt(req.timeoutMs());
        buf.put((byte) (DEFAULT_VALIDATE_ONLY ? 1 : 0));
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreatePartitionsRequest flexibleRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<CreatePartitionsRequest.TopicNewPartitions> topics = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int count = b.getInt();
            skipFlexibleAssignments(b); // Assignments, read + discarded (model does not expose it)
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new CreatePartitionsRequest.TopicNewPartitions(name, count));
        }
        int timeoutMs = b.getInt();
        b.get(); // ValidateOnly, read + discarded (model does not expose it)
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new CreatePartitionsRequest(topics, timeoutMs);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link CreatePartitionsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(CreatePartitionsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a CreatePartitions response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, CreatePartitionsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode a CreatePartitions response body at the given API version.
     *
     * @param version the API version (0–3)
     * @param buf     the response body
     * @return the decoded response
     */
    public static CreatePartitionsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v1, byte-identical) ────────────────────
    // Order: ThrottleTimeMs(int32, present from v0), numResults(int32),
    //   [ Name(int16 string), ErrorCode(int16), ErrorMessage(nullable int16 string) ]

    private static byte[] fixedResponse(CreatePartitionsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 4; // numResults
        for (CreatePartitionsResponse.TopicResult t : resp.results()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 2; // ErrorCode
            size += 2; // ErrorMessage length (-1 = null)
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putInt(resp.results().size());
        for (CreatePartitionsResponse.TopicResult t : resp.results()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putShort(t.errorCode());
            KafkaCodecPrimitives.writeNullableString(buf, null); // ErrorMessage null (spec default)
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreatePartitionsResponse fixedResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int count = b.getInt();
        List<CreatePartitionsResponse.TopicResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = KafkaCodecPrimitives.readString(b);
            short errorCode = b.getShort();
            KafkaCodecPrimitives.readNullableString(b); // ErrorMessage, read + discarded
            results.add(new CreatePartitionsResponse.TopicResult(name, errorCode));
        }
        return new CreatePartitionsResponse(results);
    }

    // ── Flexible response (v2–v3, byte-identical) ───────────────────────
    // Order: ThrottleTimeMs(int32), numResults(varint, KIP-482 N+1),
    //   [ Name(compact), ErrorCode(int16), ErrorMessage(nullable compact, null = 0), endTags ] ],
    //   endTags

    private static byte[] flexibleResponse(CreatePartitionsResponse resp) {
        int n = resp.results().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(n + 1); // numResults (KIP-482: N+1)
        for (CreatePartitionsResponse.TopicResult t : resp.results()) {
            size += compactStringSize(t.name()); // Name
            size += 2; // ErrorCode
            size += KafkaCodecPrimitives.varintSize(0); // ErrorMessage null (varint 0)
            size += 1; // per-result endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (CreatePartitionsResponse.TopicResult t : resp.results()) {
            KafkaCodecPrimitives.writeCompactString(buf, t.name());
            buf.putShort(t.errorCode());
            KafkaCodecPrimitives.writeCompactString(buf, null); // ErrorMessage null
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-result endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreatePartitionsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<CreatePartitionsResponse.TopicResult> results = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            short errorCode = b.getShort();
            KafkaCodecPrimitives.readCompactString(b); // ErrorMessage, read + discarded (nullable)
            KafkaCodecPrimitives.skipTaggedFields(b); // per-result
            results.add(new CreatePartitionsResponse.TopicResult(name, errorCode));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new CreatePartitionsResponse(results);
    }

    // ── Assignments skipping (model does not expose the field) ──────────

    /**
     * Skips a fixed-width nullable Assignments array: int32 count (-1 = null, 0 = empty),
     * then per entry an int32 BrokerIds count and that many int32 broker IDs.
     */
    private static void skipFixedAssignments(ByteBuffer b) {
        int count = b.getInt();
        if (count < 0) {
            return; // null array
        }
        for (int i = 0; i < count; i++) {
            int brokerCount = b.getInt();
            for (int j = 0; j < brokerCount; j++) {
                b.getInt();
            }
        }
    }

    /**
     * Skips a flexible nullable Assignments array: varint count (0 = null, otherwise N+1),
     * then per entry a varint BrokerIds count (N+1, never null), that many int32 broker IDs,
     * and the per-assignment tagged-fields section.
     */
    private static void skipFlexibleAssignments(ByteBuffer b) {
        int raw = KafkaCodecPrimitives.readVarint(b);
        if (raw == 0) {
            return; // null array
        }
        for (int i = 0; i < raw - 1; i++) {
            int brokerCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            for (int j = 0; j < brokerCount; j++) {
                b.getInt();
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-assignment
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a non-null string in flexible (compact) encoding. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }
}
