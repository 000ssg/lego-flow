package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.CreateTopicsRequest;
import ssg.legoflow.messaging.kafka.protocol.CreateTopicsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec for the Kafka CreateTopics request (API key 19) and response — create topics.
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v7 is encoded so a frame
 * carrying any CreateTopics version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/CreateTopicsRequest.json} and
 * {@code CreateTopicsResponse.json}):
 * <ul>
 *   <li>v0 (request): baseline — {@code Topics[Name, NumPartitions, ReplicationFactor,
 *       Assignments[PartitionIndex, BrokerIds], Configs[Name, Value]]} + {@code timeoutMs}.
 *       The manual {@code Assignments} array is mandatory on the wire (empty = automatic
 *       assignment); the in-house model exposes it, so it is written verbatim.</li>
 *   <li>v1+: request adds {@code validateOnly} (bool). The model does not expose it, so the
 *       spec default ({@code false}) is written and the value is read + discarded.</li>
 *   <li>v1+: response adds {@code ErrorMessage} (nullable string) per topic. The model does
 *       not expose it, so it is read + discarded.</li>
 *   <li>v2+: response adds {@code ThrottleTimeMs} (int32). Spec default ({@code 0}) written,
 *       read + discarded.</li>
 *   <li>v5+ (flexible): compact strings/arrays and tagged fields (per-struct trailing
 *       tagged-fields section). Response additionally adds per-topic {@code NumPartitions},
 *       {@code ReplicationFactor}, {@code Configs} and the tagged
 *       {@code TopicConfigErrorCode} (tag 0); all read + discarded.</li>
 *   <li>v7+: response adds per-topic {@code TopicId} (uuid, between Name and ErrorCode).
 *       Read + discarded.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class CreateTopicsCodec {

    /** Pinned version for client/broker CreateTopics interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v5+): compact arrays + tagged fields. */
    private static final short FLEXIBLE_START = 5;
    /** Highest API version with a defined wire format in the spec (v7). */
    private static final short MAX_VERSION = 7;

    /** Request {@code validateOnly} default — false (create, don't just validate). */
    private static final boolean DEFAULT_VALIDATE_ONLY = false;
    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private CreateTopicsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "CreateTopics v" + version + " not implemented (range 0–7)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link CreateTopicsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(CreateTopicsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a CreateTopics request body at the given API version.
     *
     * @param version the API version (0–7)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, CreateTopicsRequest req) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleRequest(version, req);
        }
        return fixedRequest(version, req);
    }

    /**
     * Decode a CreateTopics request body at the given API version.
     *
     * @param version the API version (0–7)
     * @param buf     the request body
     * @return the decoded request
     */
    public static CreateTopicsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleRequest(version, buf);
        }
        return fixedRequest(version, buf);
    }

    // ── Fixed-width request (v0–v4) ─────────────────────────────────────
    // Order: numTopics, [ name, numPartitions, repFactor,
    //   Assignments[ PartitionIndex, BrokerIds[] ], Configs[ Name, Value ] ] ],
    //   timeoutMs, [ validateOnly(1+) ]

    private static byte[] fixedRequest(short version, CreateTopicsRequest req) {
        int size = 4; // numTopics
        for (CreateTopicsRequest.TopicCreate topic : req.topics()) {
            size += 2 + topic.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // NumPartitions
            size += 2; // ReplicationFactor
            size += 4; // Assignments count
            for (CreateTopicsRequest.TopicCreate.Assignment a : topic.assignments()) {
                size += 4; // PartitionIndex
                size += 4; // BrokerIds count
                size += 4 * a.brokerIds().size(); // BrokerIds
            }
            size += 4; // Configs count
            for (Map.Entry<String, String> e : topic.configs().entrySet()) {
                size += 2 + e.getKey().getBytes(StandardCharsets.UTF_8).length; // Name
                // Value nullable: 2-byte length prefix + data (0 if null)
                size += 2 + (e.getValue() != null ? e.getValue().getBytes(StandardCharsets.UTF_8).length : 0);
            }
        }
        size += 4; // timeoutMs
        if (version >= 1) {
            size += 1; // validateOnly
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.topics().size());
        for (CreateTopicsRequest.TopicCreate topic : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putInt(topic.numPartitions());
            buf.putShort(topic.replicationFactor());
            // Assignments
            buf.putInt(topic.assignments().size());
            for (CreateTopicsRequest.TopicCreate.Assignment a : topic.assignments()) {
                buf.putInt(a.partitionIndex());
                buf.putInt(a.brokerIds().size());
                for (int broker : a.brokerIds()) {
                    buf.putInt(broker);
                }
            }
            // Configs
            buf.putInt(topic.configs().size());
            for (Map.Entry<String, String> e : topic.configs().entrySet()) {
                KafkaCodecPrimitives.writeString(buf, e.getKey());
                KafkaCodecPrimitives.writeNullableString(buf, e.getValue());
            }
        }
        buf.putInt(req.timeoutMs());
        if (version >= 1) {
            buf.put((byte) (DEFAULT_VALIDATE_ONLY ? 1 : 0));
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreateTopicsRequest fixedRequest(short version, ByteBuffer b) {
        int numTopics = b.getInt();
        List<CreateTopicsRequest.TopicCreate> topics = new ArrayList<>(numTopics);
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readString(b);
            int numPartitions = b.getInt();
            short repFactor = b.getShort();
            // Assignments
            int numAssignments = b.getInt();
            List<CreateTopicsRequest.TopicCreate.Assignment> assignments =
                    new ArrayList<>(numAssignments);
            for (int a = 0; a < numAssignments; a++) {
                int partitionIndex = b.getInt();
                int numBrokers = b.getInt();
                List<Integer> brokerIds = new ArrayList<>(numBrokers);
                for (int br = 0; br < numBrokers; br++) {
                    brokerIds.add(b.getInt());
                }
                assignments.add(new CreateTopicsRequest.TopicCreate.Assignment(partitionIndex, brokerIds));
            }
            // Configs
            int numConfigs = b.getInt();
            Map<String, String> configs = new LinkedHashMap<>(numConfigs);
            for (int c = 0; c < numConfigs; c++) {
                String cName = KafkaCodecPrimitives.readString(b);
                String cValue = KafkaCodecPrimitives.readNullableString(b);
                configs.put(cName, cValue);
            }
            topics.add(new CreateTopicsRequest.TopicCreate(name, numPartitions, repFactor, assignments, configs));
        }
        int timeoutMs = b.getInt();
        if (version >= 1) {
            b.get(); // validateOnly, read + discard
        }
        return new CreateTopicsRequest(topics, timeoutMs);
    }

    // ── Flexible request (v5–v7) ────────────────────────────────────────
    // Order: numTopics(varint), [ name(compact), numPartitions, repFactor,
    //   Assignments(varint[ PartitionIndex, BrokerIds(varint[ int32 ]), endTags ]),
    //   Configs(varint[ Name(compact), Value(compact-nullable), endTags ]), endTags ] ],
    //   timeoutMs, validateOnly(1+), endTags

    private static byte[] flexibleRequest(short version, CreateTopicsRequest req) {
        int size = KafkaCodecPrimitives.varintSize(req.topics().size() + 1); // numTopics
        for (CreateTopicsRequest.TopicCreate topic : req.topics()) {
            size += compactStringSize(topic.name()); // Name
            size += 4; // NumPartitions
            size += 2; // ReplicationFactor
            size += KafkaCodecPrimitives.varintSize(topic.assignments().size() + 1); // Assignments count
            for (CreateTopicsRequest.TopicCreate.Assignment a : topic.assignments()) {
                size += 4; // PartitionIndex
                size += KafkaCodecPrimitives.varintSize(a.brokerIds().size() + 1); // BrokerIds count
                size += 4 * a.brokerIds().size(); // BrokerIds
                size += 1; // per-assignment endTags (0)
            }
            size += KafkaCodecPrimitives.varintSize(topic.configs().size() + 1); // Configs count (KIP-482: N+1)
            for (Map.Entry<String, String> e : topic.configs().entrySet()) {
                size += compactStringSize(e.getKey()); // Name
                size += compactNullableStringSize(e.getValue()); // Value
                size += 1; // per-config endTags (0)
            }
            size += 1; // per-topic endTags (0)
        }
        size += 4; // timeoutMs
        if (version >= 1) {
            size += 1; // validateOnly
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, req.topics().size() + 1); // KIP-482: N+1
        for (CreateTopicsRequest.TopicCreate topic : req.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, topic.name());
            buf.putInt(topic.numPartitions());
            buf.putShort(topic.replicationFactor());
            // Assignments
            KafkaCodecPrimitives.writeVarint(buf, topic.assignments().size() + 1); // KIP-482: N+1
            for (CreateTopicsRequest.TopicCreate.Assignment a : topic.assignments()) {
                buf.putInt(a.partitionIndex());
                KafkaCodecPrimitives.writeVarint(buf, a.brokerIds().size() + 1); // KIP-482: N+1
                for (int broker : a.brokerIds()) {
                    buf.putInt(broker);
                }
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-assignment endTags
            }
            // Configs (non-nullable compact array in the request: N+1, never 0)
            KafkaCodecPrimitives.writeVarint(buf, topic.configs().size() + 1); // KIP-482: N+1
            for (Map.Entry<String, String> e : topic.configs().entrySet()) {
                KafkaCodecPrimitives.writeCompactString(buf, e.getKey());
                KafkaCodecPrimitives.writeCompactString(buf, e.getValue());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-config endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
        }
        buf.putInt(req.timeoutMs());
        if (version >= 1) {
            buf.put((byte) (DEFAULT_VALIDATE_ONLY ? 1 : 0));
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreateTopicsRequest flexibleRequest(short version, ByteBuffer b) {
        int numTopics = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<CreateTopicsRequest.TopicCreate> topics = new ArrayList<>(Math.max(numTopics, 0));
        for (int t = 0; t < numTopics; t++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            int numPartitions = b.getInt();
            short repFactor = b.getShort();
            // Assignments
            int numAssignments = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<CreateTopicsRequest.TopicCreate.Assignment> assignments =
                    new ArrayList<>(Math.max(numAssignments, 0));
            for (int a = 0; a < numAssignments; a++) {
                int partitionIndex = b.getInt();
                int numBrokers = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
                List<Integer> brokerIds = new ArrayList<>(Math.max(numBrokers, 0));
                for (int br = 0; br < numBrokers; br++) {
                    brokerIds.add(b.getInt());
                }
                KafkaCodecPrimitives.skipTaggedFields(b); // per-assignment
                assignments.add(new CreateTopicsRequest.TopicCreate.Assignment(partitionIndex, brokerIds));
            }
            // Configs
            int numConfigs = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            Map<String, String> configs = new LinkedHashMap<>(Math.max(numConfigs, 0));
            for (int c = 0; c < numConfigs; c++) {
                String cName = KafkaCodecPrimitives.readCompactString(b);
                String cValue = KafkaCodecPrimitives.readCompactString(b);
                configs.put(cName, cValue);
                KafkaCodecPrimitives.skipTaggedFields(b); // per-config
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic
            topics.add(new CreateTopicsRequest.TopicCreate(name, numPartitions, repFactor, assignments, configs));
        }
        int timeoutMs = b.getInt();
        if (version >= 1) {
            b.get(); // validateOnly, read + discard
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new CreateTopicsRequest(topics, timeoutMs);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link CreateTopicsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(CreateTopicsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a CreateTopics response body at the given API version.
     *
     * @param version the API version (0–7)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, CreateTopicsResponse resp) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleResponse(version, resp);
        }
        return fixedResponse(version, resp);
    }

    /**
     * Decode a CreateTopics response body at the given API version.
     *
     * @param version the API version (0–7)
     * @param buf     the response body
     * @return the decoded response
     */
    public static CreateTopicsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version >= FLEXIBLE_START) {
            return flexibleResponse(version, buf);
        }
        return fixedResponse(version, buf);
    }

    // ── Fixed-width response (v0–v4) ────────────────────────────────────
    // Order: [ ThrottleTimeMs(2+) ], numTopics,
    //   [ Name, [ TopicId(7+ — flexible-only, N/A here) ], ErrorCode,
    //     [ ErrorMessage(1+) ] ]

    private static byte[] fixedResponse(short version, CreateTopicsResponse resp) {
        int size = 0;
        if (version >= 2) {
            size += 4; // ThrottleTimeMs
        }
        size += 4; // numTopics
        for (CreateTopicsResponse.TopicResult topic : resp.topics()) {
            size += 2 + topic.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 2; // ErrorCode
            if (version >= 1) {
                size += 2; // ErrorMessage length prefix (0 if null/absent)
            }
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 2) {
            buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        }
        buf.putInt(resp.topics().size());
        for (CreateTopicsResponse.TopicResult topic : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, topic.name());
            buf.putShort(topic.errorCode());
            if (version >= 1) {
                // Model has no ErrorMessage; write the absent default (null).
                KafkaCodecPrimitives.writeNullableString(buf, null);
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreateTopicsResponse fixedResponse(short version, ByteBuffer b) {
        if (version >= 2) {
            b.getInt(); // ThrottleTimeMs, read + discard
        }
        int count = b.getInt();
        List<CreateTopicsResponse.TopicResult> topics = new ArrayList<>(count);
        for (int t = 0; t < count; t++) {
            String name = KafkaCodecPrimitives.readString(b);
            short errorCode = b.getShort();
            if (version >= 1) {
                // ErrorMessage is a nullable string: -1 length if absent. readNullableString
                // consumes the data bytes when present, so the buffer stays in sync in both
                // cases; the value is discarded (the model does not carry it).
                KafkaCodecPrimitives.readNullableString(b);
            }
            topics.add(new CreateTopicsResponse.TopicResult(name, errorCode));
        }
        return new CreateTopicsResponse(topics);
    }

    // ── Flexible response (v5–v7) ───────────────────────────────────────
    // Order: [ ThrottleTimeMs(2+) ], numTopics(varint),
    //   [ Name(compact), [ TopicId(7+) ], ErrorCode, [ ErrorMessage(1+, compact-nullable) ],
    //     [ NumPartitions(5+), ReplicationFactor(5+),
    //       Configs(5+, compact-nullable[ Name, Value, ReadOnly, ConfigSource, IsSensitive, endTags ]) ] ,
    //     endTags( [ TopicConfigErrorCode tag 0 (5+) ] ) ]

    private static byte[] flexibleResponse(short version, CreateTopicsResponse resp) {
        int size = 0;
        if (version >= 2) {
            size += 4; // ThrottleTimeMs
        }
        size += KafkaCodecPrimitives.varintSize(resp.topics().size() + 1); // numTopics (KIP-482: N+1)
        for (CreateTopicsResponse.TopicResult topic : resp.topics()) {
            size += compactStringSize(topic.name()); // Name
            if (version >= 7) {
                size += 16; // TopicId (uuid)
            }
            size += 2; // ErrorCode
            if (version >= 1) {
                size += 1; // ErrorMessage compact-nullable absent default (varint 0)
            }
            if (version >= 5) {
                size += 4; // NumPartitions
                size += 2; // ReplicationFactor
                size += 1; // Configs null (varint 0)
            }
            size += 1; // per-topic endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 2) {
            buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        }
        KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1); // KIP-482: N+1
        for (CreateTopicsResponse.TopicResult topic : resp.topics()) {
            KafkaCodecPrimitives.writeCompactString(buf, topic.name());
            if (version >= 7) {
                for (int i = 0; i < 16; i++) {
                    buf.put((byte) 0); // TopicId absent default (all-zero uuid)
                }
            }
            buf.putShort(topic.errorCode());
            if (version >= 1) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage absent (null)
            }
            if (version >= 5) {
                // Spec default -1 for both fields ("number of partitions of the topic",
                // "-1" = absent/not returned); non-nullable fixed-width even in flexible.
                buf.putInt(-1); // NumPartitions absent default
                buf.putShort((short) -1); // ReplicationFactor absent default
                KafkaCodecPrimitives.writeVarint(buf, 0); // Configs null compact array (KIP-482: 0 = null)
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags (0)
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags (0)
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static CreateTopicsResponse flexibleResponse(short version, ByteBuffer b) {
        if (version >= 2) {
            b.getInt(); // ThrottleTimeMs, read + discard
        }
        int count = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<CreateTopicsResponse.TopicResult> topics = new ArrayList<>(Math.max(count, 0));
        for (int t = 0; t < count; t++) {
            String name = KafkaCodecPrimitives.readCompactString(b);
            if (version >= 7) {
                b.position(b.position() + 16); // TopicId, read + discard
            }
            short errorCode = b.getShort();
            if (version >= 1) {
                // ErrorMessage is a nullable compact string: varint 0 = null, otherwise
                // varint(len+1) + data. readCompactString consumes the data when present,
                // keeping the buffer in sync; the value is discarded (model does not carry it).
                KafkaCodecPrimitives.readCompactString(b);
            }
            if (version >= 5) {
                b.getInt(); // NumPartitions, read + discard
                b.getShort(); // ReplicationFactor, read + discard
                int configs = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
                if (configs != 0) {
                    int n = configs - 1;
                    for (int c = 0; c < n; c++) {
                        // Name
                        int nameLen = KafkaCodecPrimitives.readVarint(b);
                        b.position(b.position() + nameLen - 1);
                        // Value (nullable)
                        int valLen = KafkaCodecPrimitives.readVarint(b);
                        if (valLen != 0) {
                            b.position(b.position() + valLen - 1);
                        }
                        b.get(); // ReadOnly
                        b.get(); // ConfigSource
                        b.get(); // IsSensitive
                        KafkaCodecPrimitives.skipTaggedFields(b); // per-config
                    }
                }
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-topic (incl. TopicConfigErrorCode)
            topics.add(new CreateTopicsResponse.TopicResult(name, errorCode));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new CreateTopicsResponse(topics);
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
