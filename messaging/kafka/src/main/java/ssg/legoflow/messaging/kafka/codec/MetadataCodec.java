package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.MetadataRequest;
import ssg.legoflow.messaging.kafka.protocol.MetadataResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka <b>Metadata/Cluster</b> Metadata API (key 3), versions v0–v12, both
 * directions.
 * <p>
 * Client/broker interaction is versioned from v0 (the baseline the in-house
 * client sends, {@code ApiKey.METADATA.key()} + version 0), while the full version range v0–v12
 * is encoded so a frame carrying any version can be produced or parsed.
 * <p>
 * Spec-correct wire layout (frozen order tables in {@code doc/spec/order/Metadata.Request.txt}
 * / {@code .Response.txt}; mirrors {@code doc/spec/message/MetadataRequest.json} and
 * {@code MetadataResponse.json}, which are byte-equivalent to Apache Kafka 3.6.1):
 * <ul>
 *   <li><b>Request</b>: {@code Topics[] { Name(string) }} — v0: non-nullable array, <i>empty
 *       array = all topics</i>; v1+: nullable array, <i>null = all topics</i> (legacy
 *       {@code int32(-1)}, flexible varint 0). Then, <b>v4+</b>, {@code
 *       AllowAutoTopicCreation(bool)}; <b>v8+</b>, {@code IncludeClusterAuthorizedOperations}
 *       (removed at v11, KIP-700) + {@code IncludeTopicAuthorizedOperations} (bools).</li>
 *   <li><b>Response</b>: {@code Brokers[] { NodeId(int32), Host(string), Port(int32) }} — then,
 *       <b>v1+</b>, per-broker {@code Rack(string, nullable)} — then, <b>v2+</b>, {@code
 *       ClusterId(string, nullable)} — then, <b>v1+</b>, {@code ControllerId(int32)} — then
 *       {@code Topics[] { ErrorCode(int16), Name(string), Partitions[] { ErrorCode(int16),
 *       PartitionIndex(int32), LeaderId(int32), ReplicaNodes[]int32, IsrNodes[]int32 } }} —
 *       with, <b>v1+</b>, per-topic {@code IsInternal(bool)}; <b>v5+</b>, per-partition
 *       {@code OfflineReplicas[]int32} (after IsrNodes); <b>v7+</b>, per-partition {@code
 *       LeaderEpoch(int32)} (after LeaderId); <b>v8+</b>, per-topic {@code
 *       TopicAuthorizedOperations(int32)}; <b>v8–v10</b>, trailing {@code
 *       ClusterAuthorizedOperations(int32)}; <b>v3+</b>, leading {@code ThrottleTimeMs(int32)}.
 *       Response Name becomes nullable at v12+.</li>
 *   <li><b>v9+</b>: flexible encoding (KIP-482) — compact strings, array counts as N+1 varints
 *       (null array = varint 0), and trailing tagged-fields trailers (varint 0): per-topic
 *       (request), per-broker, per-partition, per-topic, top-level (response). <b>v10+</b> adds
 *       the 16-byte {@code TopicId(uuid)}: per request topic <i>before</i> Name; per response
 *       topic <i>after</i> Name.</li>
 * </ul>
 * Fields the in-house models do not expose are written with their spec defaults on encode and
 * read + discarded on decode: request {@code IncludeClusterAuthorizedOperations} (false, v8–v10),
 * {@code IncludeTopicAuthorizedOperations} (false, v8+), per-topic {@code TopicId} (16 zero
 * bytes, v10+); the response {@code ThrottleTimeMs} (0, v3+), per-broker {@code Rack} (null, v1+),
 * {@code ClusterId} (null, v2+), {@code ControllerId} (-1, v1+), per-topic {@code IsInternal}
 * (false, v1+), per-partition {@code OfflineReplicas} (empty, v5+), per-partition {@code
 * LeaderEpoch} (-1, v7+), per-topic {@code TopicId} (16 zero bytes, v10+), per-topic {@code
 * TopicAuthorizedOperations} (Integer.MIN_VALUE, v8+), trailing {@code ClusterAuthorizedOperations}
 * (Integer.MIN_VALUE, v8–v10). The request {@code AllowAutoTopicCreation} (v4+) IS a model field
 * and round-trips; at v0–v3 it is absent on the wire and decodes to the model default {@code false}.
 *
 * <p>v0 baseline fix: the old inline facade encoded a null topic list as count {@code -1}
 * (a v1+ construct) inside a v0 frame; the spec v0 wire for "all topics" is an <i>empty</i>
 * array (count 0). This codec writes count 0 for null topics at v0 and {@code -1} only at v1+.
 *
 * @since 0.1.0
 */
public final class MetadataCodec {

    /** Spec minimum version (Kafka 0.8.2). */
    public static final short MIN_VERSION = 0;

    /** Spec maximum version (Kafka 3.6+ / KIP-848 era). */
    public static final short MAX_VERSION = 12;

    /** Flexible (KIP-482) encoding starts at v9; strings become compact. */
    public static final short FLEXIBLE_START = 9;

    private static final byte[] ZERO_UUID = new byte[16];

    private MetadataCodec() {
    }

    private static void checkVersion(short version) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "Metadata v" + version + " not implemented (range 0–12)");
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
     * @param version the API version (0–12)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, MetadataRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        List<String> topics = req.topics();

        int size = 0;
        if (topics == null) {
            // v1+ null-array marker ("all topics"): legacy int32(-1) / flexible varint(0).
            // v0 has no nullable array: null topics is written as the empty array (count 0).
            size += flexible ? 1 : 4;
        } else {
            size += flexible ? varintSize(topics.size() + 1) : 4;
            for (String name : topics) {
                if (version >= 10) {
                    size += 16; // TopicId (uuid) — spec default: all zero bytes
                }
                size += stringSize(flexible, name); // Name
                if (flexible) {
                    size += 1; // per-topic endTags
                }
            }
        }
        if (version >= 4) {
            size += 1; // AllowAutoTopicCreation (bool)
        }
        if (version >= 8 && version <= 10) {
            size += 1; // IncludeClusterAuthorizedOperations (bool)
        }
        if (version >= 8) {
            size += 1; // IncludeTopicAuthorizedOperations (bool)
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (topics == null) {
            if (version < 1) {
                buf.putInt(0); // v0: empty array = all topics
            } else if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // null array = varint(0)
            } else {
                buf.putInt(-1); // v1–v8 legacy null array
            }
        } else {
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, topics.size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(topics.size());
            }
            for (String name : topics) {
                if (version >= 10) {
                    KafkaCodecPrimitives.writeUuid(buf, ZERO_UUID); // TopicId — spec default
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, name); // Name (nullable at v10+)
                } else {
                    KafkaCodecPrimitives.writeString(buf, name); // Name
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
                }
            }
        }
        if (version >= 4) {
            buf.put((byte) (req.allowAutoTopicCreation() ? 1 : 0)); // AllowAutoTopicCreation
        }
        if (version >= 8 && version <= 10) {
            buf.put((byte) 0); // IncludeClusterAuthorizedOperations — spec default false
        }
        if (version >= 8) {
            buf.put((byte) 0); // IncludeTopicAuthorizedOperations — spec default false
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
     * @param version the API version (0–12)
     * @param buf     the request body
     * @return the decoded request
     */
    public static MetadataRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int topicCount;
        if (flexible) {
            topicCount = KafkaCodecPrimitives.readVarint(buf) - 1; // KIP-482: N+1; null = varint(0) → -1
        } else {
            topicCount = buf.getInt();
        }
        List<String> topics;
        if (topicCount < 0) {
            topics = null; // v1+: null array = all topics
        } else if (version < 1 && topicCount == 0) {
            topics = null; // v0: empty array = all topics (spec 3.6.1 comment)
        } else {
            topics = new ArrayList<>(topicCount);
            for (int i = 0; i < topicCount; i++) {
                if (version >= 10) {
                    KafkaCodecPrimitives.readUuid(buf); // TopicId — read + discarded
                }
                String name = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf) // Name (nullable at v10+)
                        : KafkaCodecPrimitives.readString(buf); // Name
                topics.add(name);
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
                }
            }
        }
        boolean allowAutoTopicCreation = false; // v0–v3: field absent, spec default false
        if (version >= 4) {
            allowAutoTopicCreation = buf.get() != 0; // AllowAutoTopicCreation
        }
        if (version >= 8 && version <= 10) {
            buf.get(); // IncludeClusterAuthorizedOperations — read + discarded
        }
        if (version >= 8) {
            buf.get(); // IncludeTopicAuthorizedOperations — read + discarded
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new MetadataRequest(topics, allowAutoTopicCreation);
    }

    // ── Response ────────────────────────────────────────────────────────
    /**
     * Encode a response body at the given API version.
     *
     * @param version the API version (0–12)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, MetadataResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;

        int size = 0;
        if (version >= 3) {
            size += 4; // ThrottleTimeMs (int32) — spec default 0
        }
        // Brokers
        size += flexible ? varintSize(resp.brokers().size() + 1) : 4;
        for (var b : resp.brokers()) {
            size += 4; // NodeId
            size += stringSize(flexible, b.host()); // Host
            size += 4; // Port
            if (version >= 1) {
                size += stringSize(flexible, null); // Rack — spec default null
            }
            if (flexible) {
                size += 1; // per-broker endTags
            }
        }
        if (version >= 2) {
            size += stringSize(flexible, null); // ClusterId — spec default null
        }
        if (version >= 1) {
            size += 4; // ControllerId — spec default -1
        }
        // Topics
        size += flexible ? varintSize(resp.topics().size() + 1) : 4;
        for (var t : resp.topics()) {
            size += 2; // ErrorCode (int16)
            size += stringSize(flexible, t.name()); // Name
            if (version >= 10) {
                size += 16; // TopicId (uuid) — spec default: all zero bytes
            }
            if (version >= 1) {
                size += 1; // IsInternal — spec default false
            }
            size += flexible ? varintSize(t.partitions().size() + 1) : 4;
            for (var p : t.partitions()) {
                size += 2; // ErrorCode (int16)
                size += 4; // PartitionIndex
                size += 4; // LeaderId
                if (version >= 7) {
                    size += 4; // LeaderEpoch — spec default -1
                }
                size += flexible ? varintSize(p.replicaIds().size() + 1) : 4;
                size += 4 * p.replicaIds().size();
                size += flexible ? varintSize(p.isrIds().size() + 1) : 4;
                size += 4 * p.isrIds().size();
                if (version >= 5) {
                    size += flexible ? varintSize(1) : 4; // OfflineReplicas — empty array
                }
                if (flexible) {
                    size += 1; // per-partition endTags
                }
            }
            if (version >= 8) {
                size += 4; // TopicAuthorizedOperations — spec default Integer.MIN_VALUE
            }
            if (flexible) {
                size += 1; // per-topic endTags
            }
        }
        if (version >= 8 && version <= 10) {
            size += 4; // ClusterAuthorizedOperations — spec default Integer.MIN_VALUE
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        if (version >= 3) {
            buf.putInt(0); // ThrottleTimeMs — spec default
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.brokers().size() + 1);
        } else {
            buf.putInt(resp.brokers().size());
        }
        for (var b : resp.brokers()) {
            buf.putInt(b.nodeId()); // NodeId
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, b.host()); // Host
            } else {
                KafkaCodecPrimitives.writeString(buf, b.host()); // Host
            }
            buf.putInt(b.port()); // Port
            if (version >= 1) {
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, null); // Rack — spec default null
                } else {
                    KafkaCodecPrimitives.writeNullableString(buf, null); // Rack — legacy null
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-broker endTags
            }
        }
        if (version >= 2) {
            if (flexible) {
                KafkaCodecPrimitives.writeCompactString(buf, null); // ClusterId — spec default null
            } else {
                KafkaCodecPrimitives.writeNullableString(buf, null); // ClusterId — legacy null
            }
        }
        if (version >= 1) {
            buf.putInt(-1); // ControllerId — spec default
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, resp.topics().size() + 1);
        } else {
            buf.putInt(resp.topics().size());
        }
        for (var t : resp.topics()) {
            buf.putShort(t.errorCode()); // ErrorCode
            if (flexible) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, t.name()); // Name
            } else {
                KafkaCodecPrimitives.writeString(buf, t.name()); // Name
            }
            if (version >= 10) {
                KafkaCodecPrimitives.writeUuid(buf, ZERO_UUID); // TopicId — spec default
            }
            if (version >= 1) {
                buf.put((byte) 0); // IsInternal — spec default false
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1);
            } else {
                buf.putInt(t.partitions().size());
            }
            for (var p : t.partitions()) {
                buf.putShort(p.errorCode()); // ErrorCode
                buf.putInt(p.partitionIndex()); // PartitionIndex
                buf.putInt(p.leaderId()); // LeaderId
                if (version >= 7) {
                    buf.putInt(-1); // LeaderEpoch — spec default
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, p.replicaIds().size() + 1);
                } else {
                    buf.putInt(p.replicaIds().size());
                }
                for (int r : p.replicaIds()) {
                    buf.putInt(r); // ReplicaNodes
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, p.isrIds().size() + 1);
                } else {
                    buf.putInt(p.isrIds().size());
                }
                for (int r : p.isrIds()) {
                    buf.putInt(r); // IsrNodes
                }
                if (version >= 5) {
                    if (flexible) {
                        KafkaCodecPrimitives.writeVarint(buf, 1); // OfflineReplicas — empty (N+1)
                    } else {
                        buf.putInt(0); // OfflineReplicas — empty
                    }
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                }
            }
            if (version >= 8) {
                buf.putInt(Integer.MIN_VALUE); // TopicAuthorizedOperations — spec default
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
            }
        }
        if (version >= 8 && version <= 10) {
            buf.putInt(Integer.MIN_VALUE); // ClusterAuthorizedOperations — spec default
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
     * @param version the API version (0–12)
     * @param buf     the response body
     * @return the decoded response
     */
    public static MetadataResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        if (version >= 3) {
            buf.getInt(); // ThrottleTimeMs — read + discarded
        }
        int brokerCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
        List<MetadataResponse.BrokerMetadata> brokers = new ArrayList<>(brokerCount);
        for (int i = 0; i < brokerCount; i++) {
            int nodeId = buf.getInt(); // NodeId
            String host = flexible
                    ? KafkaCodecPrimitives.readCompactStringNonNullable(buf) // Host
                    : KafkaCodecPrimitives.readString(buf); // Host
            int port = buf.getInt(); // Port
            if (version >= 1) {
                // Rack — read + discarded. Flexible (v9+): nullable compact; legacy (v1–v8): nullable string.
                if (flexible) {
                    KafkaCodecPrimitives.readCompactString(buf);
                } else {
                    KafkaCodecPrimitives.readNullableString(buf);
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-broker
            }
            brokers.add(new MetadataResponse.BrokerMetadata(nodeId, host, port));
        }
        if (version >= 2) {
            // ClusterId — read + discarded.
            if (flexible) {
                KafkaCodecPrimitives.readCompactString(buf);
            } else {
                KafkaCodecPrimitives.readNullableString(buf);
            }
        }
        if (version >= 1) {
            buf.getInt(); // ControllerId — read + discarded
        }
        int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
        List<MetadataResponse.TopicMetadata> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            short errorCode = buf.getShort(); // ErrorCode
            String name = flexible
                    ? KafkaCodecPrimitives.readCompactString(buf) // Name (nullable at v12+)
                    : KafkaCodecPrimitives.readString(buf); // Name
            if (version >= 10) {
                KafkaCodecPrimitives.readUuid(buf); // TopicId — read + discarded
            }
            if (version >= 1) {
                buf.get(); // IsInternal — read + discarded
            }
            int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
            List<MetadataResponse.PartitionMetadata> partitions = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                short pError = buf.getShort(); // ErrorCode
                int pIdx = buf.getInt(); // PartitionIndex
                int leader = buf.getInt(); // LeaderId
                if (version >= 7) {
                    buf.getInt(); // LeaderEpoch — read + discarded
                }
                int repCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
                List<Integer> replicas = new ArrayList<>(repCount);
                for (int k = 0; k < repCount; k++) {
                    replicas.add(buf.getInt()); // ReplicaNodes
                }
                int isrCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
                List<Integer> isrs = new ArrayList<>(isrCount);
                for (int k = 0; k < isrCount; k++) {
                    isrs.add(buf.getInt()); // IsrNodes
                }
                if (version >= 5) {
                    int offlineCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt();
                    for (int k = 0; k < offlineCount; k++) {
                        buf.getInt(); // OfflineReplicas — read + discarded
                    }
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                }
                partitions.add(new MetadataResponse.PartitionMetadata(pError, pIdx, leader, replicas, isrs));
            }
            if (version >= 8) {
                buf.getInt(); // TopicAuthorizedOperations — read + discarded
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
            }
            topics.add(new MetadataResponse.TopicMetadata(errorCode, name, partitions));
        }
        if (version >= 8 && version <= 10) {
            buf.getInt(); // ClusterAuthorizedOperations — read + discarded
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new MetadataResponse(brokers, topics);
    }
}
