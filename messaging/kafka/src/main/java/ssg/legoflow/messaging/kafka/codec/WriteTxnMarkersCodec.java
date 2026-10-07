package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.WriteTxnMarkersRequest;
import ssg.legoflow.messaging.kafka.protocol.WriteTxnMarkersResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec for the Kafka <b>WriteTxnMarkers</b> API (key 27), versions v0–v1, both directions.
 *
 * <p>Spec-correct wire layout (frozen order tables in
 * {@code doc/spec/order/WriteTxnMarkers.Request.txt} / {@code .Response.txt}):
 *
 * <ul>
 *   <li><b>Request v0</b> (fixed): top-level {@code Markers[] WritableTxnMarker}; each marker:
 *       {@code ProducerId(int64) + ProducerEpoch(int16) + TransactionResult(bool) +
 *       Topics[] { Name(string), PartitionIndexes[]int32 } + CoordinatorEpoch(int32)}.
 *       Coordinator epoch is the <i>last</i> marker field, and partitions are <i>grouped by
 *       topic</i> — the old inline facade wrote both incorrectly (coordinator epoch mid-marker,
 *       partitions as a flat topic+index list).</li>
 *   <li><b>Request v1</b>: same fixed fields but flexible encoding — array counts are N+1
 *       varints, topic names are compact strings, and per-partition / per-topic / per-marker /
 *       top-level tagged-fields trailers (all zero here).</li>
 *   <li><b>Response v0</b> (fixed): top-level {@code Markers[] WritableTxnMarkerResult}; each
 *       marker: {@code ProducerId(int64) + Topics[] { Name(string),
 *       Partitions[] { PartitionIndex(int32), ErrorCode(int16) } }}. There is <i>no</i>
 *       ThrottleTimeMs on this response (unlike most other Kafka responses).</li>
 *   <li><b>Response v1</b>: same fixed fields but flexible encoding (N+1 varints, compact
 *       strings, per-partition / per-topic / per-marker / top-level tagged fields).</li>
 * </ul>
 *
 * <p>The in-house {@link WriteTxnMarkersRequest#TxnMarker} carries a <i>flat</i>
 * {@code List<TopicPartitionData(topic, partition)>}; on encode the codec groups the partitions
 * by topic (insertion-ordered), and on decode it flattens the spec's per-topic groups back into
 * the flat list (topics in wire order, partitions in group order).
 *
 * <p>Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the fixed-width baseline).
 *
 * @since 0.1.0
 */
public final class WriteTxnMarkersCodec {

    /** Minimum supported API version (v0: fixed-width baseline). */
    public static final short MIN_VERSION = 0;

    /** Maximum supported API version (v1: flexible encoding, KIP-482). */
    public static final short MAX_VERSION = 1;

    /** First version using flexible encoding (KIP-482): v1. */
    public static final short FLEXIBLE_START = 1;

    /**
     * Pinned version for client/broker interaction (v0: the baseline layout the model was
     * designed against).
     */
    public static final short PINNED_VERSION = 0;

    private WriteTxnMarkersCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new IllegalArgumentException("unsupported WriteTxnMarkers version " + version
                    + " (supported: " + MIN_VERSION + "–" + MAX_VERSION + ")");
        }
    }

    // ── Request (v0 fixed / v1 flexible) ─────────────────────────────────
    // Order: Markers[] { ProducerId(int64), ProducerEpoch(int16),
    //   TransactionResult(bool), Topics[] { Name(string), PartitionIndexes[]int32 },
    //   CoordinatorEpoch(int32) } — coordinator epoch LAST, partitions grouped by topic.

    public static byte[] encodeRequest(WriteTxnMarkersRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    public static byte[] encodeRequest(short version, WriteTxnMarkersRequest req) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int size = flexible ? 0 : 4; // numMarkers: int32 (fixed) / varint (flexible, added below)
        if (flexible) {
            size += varintSize(req.markers().size() + 1); // numMarkers varint (KIP-482: N+1)
        }
        for (WriteTxnMarkersRequest.TxnMarker m : req.markers()) {
            size += 8; // ProducerId
            size += 2; // ProducerEpoch
            size += 1; // TransactionResult
            size += (flexible ? 0 : 4); // numTopics (varint N+1, sized below / int32)
            if (flexible) {
                size += varintSize(topicCount(m) + 1);
            }
            Map<String, List<Integer>> byTopic = groupByTopic(m);
            for (Map.Entry<String, List<Integer>> e : byTopic.entrySet()) {
                String topic = e.getKey();
                byte[] tb = topic.getBytes(StandardCharsets.UTF_8);
                size += flexible ? (varintSize(tb.length + 1) + tb.length) : (2 + tb.length); // Name (compact / fixed)
                size += flexible ? varintSize(e.getValue().size() + 1) : 4; // PartitionIndexes count
                size += e.getValue().size() * 4; // PartitionIndexes int32
                if (flexible) {
                    size += 1; // per-partition endTags (1 per partition)
                }
                if (flexible) {
                    size += 1; // per-topic endTags
                }
            }
            size += 4; // CoordinatorEpoch
            if (flexible) {
                size += 1; // per-marker endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        writeMarkersArray(buf, req, flexible);
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static int topicCount(WriteTxnMarkersRequest.TxnMarker m) {
        return groupByTopic(m).size();
    }

    private static void writeMarkersArray(ByteBuffer buf, WriteTxnMarkersRequest req, boolean flexible) {
        int n = req.markers().size();
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        } else {
            buf.putInt(n);
        }
        for (WriteTxnMarkersRequest.TxnMarker m : req.markers()) {
            buf.putLong(m.producerId());
            buf.putShort(m.producerEpoch());
            buf.put((byte) (m.committed() ? 1 : 0)); // TransactionResult
            Map<String, List<Integer>> byTopic = groupByTopic(m);
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, byTopic.size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(byTopic.size());
            }
            for (Map.Entry<String, List<Integer>> e : byTopic.entrySet()) {
                String topic = e.getKey();
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, topic);
                } else {
                    KafkaCodecPrimitives.writeString(buf, topic);
                }
                List<Integer> parts = e.getValue();
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, parts.size() + 1); // KIP-482: N+1
                } else {
                    buf.putInt(parts.size());
                }
                for (int idx : parts) {
                    buf.putInt(idx);
                    if (flexible) {
                        KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                    }
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
                }
            }
            buf.putInt(m.coordinatorEpoch()); // CoordinatorEpoch (LAST)
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-marker endTags
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
    }

    public static WriteTxnMarkersRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int markerCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<WriteTxnMarkersRequest.TxnMarker> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            long producerId = buf.getLong();
            short producerEpoch = buf.getShort();
            boolean committed = buf.get() == 1; // TransactionResult
            int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            List<WriteTxnMarkersRequest.TopicPartitionData> parts = new ArrayList<>();
            for (int j = 0; j < topicCount; j++) {
                String topic = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf)
                        : KafkaCodecPrimitives.readString(buf);
                int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
                for (int k = 0; k < partCount; k++) {
                    int idx = buf.getInt();
                    if (flexible) {
                        KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                    }
                    parts.add(new WriteTxnMarkersRequest.TopicPartitionData(topic, idx));
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
                }
            }
            int coordinatorEpoch = buf.getInt(); // CoordinatorEpoch (LAST)
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-marker
            }
            markers.add(new WriteTxnMarkersRequest.TxnMarker(
                    producerId, producerEpoch, coordinatorEpoch, committed, parts));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new WriteTxnMarkersRequest(markers);
    }

    /**
     * Groups the flat {@code partitions} list into an insertion-ordered map of topic →
     * partition indexes, preserving first-seen topic order and per-topic partition order.
     */
    private static Map<String, List<Integer>> groupByTopic(WriteTxnMarkersRequest.TxnMarker m) {
        Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
        for (WriteTxnMarkersRequest.TopicPartitionData p : m.partitions()) {
            byTopic.computeIfAbsent(p.topic(), k -> new ArrayList<>()).add(p.partition());
        }
        return byTopic;
    }

    // ── Response (v0 fixed / v1 flexible) ─────────────────────────────────
    // Order: Markers[] { ProducerId(int64), Topics[] { Name(string),
    //   Partitions[] { PartitionIndex(int32), ErrorCode(int16) } } } — no ThrottleTimeMs.

    public static byte[] encodeResponse(WriteTxnMarkersResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    public static byte[] encodeResponse(short version, WriteTxnMarkersResponse resp) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int size = flexible ? varintSize(resp.markers().size() + 1) : 4; // numMarkers (N+1 varint / int32)
        for (WriteTxnMarkersResponse.MarkerResult m : resp.markers()) {
            size += 8; // ProducerId
            size += flexible ? varintSize(m.topics().size() + 1) : 4; // Topics count (N+1 varint / int32)
            for (WriteTxnMarkersResponse.TopicResult t : m.topics()) {
                byte[] tb = t.topic().getBytes(StandardCharsets.UTF_8);
                size += flexible ? (varintSize(tb.length + 1) + tb.length) : (2 + tb.length); // Name (compact / fixed)
                size += flexible ? varintSize(t.partitions().size() + 1) : 4; // Partitions count
                size += t.partitions().size() * (4 + 2); // PartitionIndex int32 + ErrorCode int16
                if (flexible) {
                    size += t.partitions().size(); // per-partition endTags (1 per partition)
                }
                if (flexible) {
                    size += 1; // per-topic endTags
                }
            }
            if (flexible) {
                size += 1; // per-marker endTags
            }
        }
        if (flexible) {
            size += 1; // top-level endTags
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        int n = resp.markers().size();
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        } else {
            buf.putInt(n);
        }
        for (WriteTxnMarkersResponse.MarkerResult m : resp.markers()) {
            buf.putLong(m.producerId());
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, m.topics().size() + 1); // KIP-482: N+1
            } else {
                buf.putInt(m.topics().size());
            }
            for (WriteTxnMarkersResponse.TopicResult t : m.topics()) {
                if (flexible) {
                    KafkaCodecPrimitives.writeCompactString(buf, t.topic());
                } else {
                    KafkaCodecPrimitives.writeString(buf, t.topic());
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, t.partitions().size() + 1); // KIP-482: N+1
                } else {
                    buf.putInt(t.partitions().size());
                }
                for (WriteTxnMarkersResponse.PartitionResult p : t.partitions()) {
                    buf.putInt(p.partition());
                    buf.putShort(p.errorCode());
                    if (flexible) {
                        KafkaCodecPrimitives.writeVarint(buf, 0); // per-partition endTags
                    }
                }
                if (flexible) {
                    KafkaCodecPrimitives.writeVarint(buf, 0); // per-topic endTags
                }
            }
            if (flexible) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-marker endTags
            }
        }
        if (flexible) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    public static WriteTxnMarkersResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        boolean flexible = version >= FLEXIBLE_START;
        int markerCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
        List<WriteTxnMarkersResponse.MarkerResult> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            long producerId = buf.getLong();
            int topicCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
            List<WriteTxnMarkersResponse.TopicResult> topics = new ArrayList<>(topicCount);
            for (int j = 0; j < topicCount; j++) {
                String topic = flexible
                        ? KafkaCodecPrimitives.readCompactString(buf)
                        : KafkaCodecPrimitives.readString(buf);
                int partCount = flexible ? KafkaCodecPrimitives.readVarint(buf) - 1 : buf.getInt(); // KIP-482: N+1
                List<WriteTxnMarkersResponse.PartitionResult> parts = new ArrayList<>(partCount);
                for (int k = 0; k < partCount; k++) {
                    int partition = buf.getInt();
                    short errorCode = buf.getShort();
                    if (flexible) {
                        KafkaCodecPrimitives.skipTaggedFields(buf); // per-partition
                    }
                    parts.add(new WriteTxnMarkersResponse.PartitionResult(partition, errorCode));
                }
                if (flexible) {
                    KafkaCodecPrimitives.skipTaggedFields(buf); // per-topic
                }
                topics.add(new WriteTxnMarkersResponse.TopicResult(topic, parts));
            }
            if (flexible) {
                KafkaCodecPrimitives.skipTaggedFields(buf); // per-marker
            }
            markers.add(new WriteTxnMarkersResponse.MarkerResult(producerId, topics));
        }
        if (flexible) {
            KafkaCodecPrimitives.skipTaggedFields(buf); // top-level
        }
        return new WriteTxnMarkersResponse(markers);
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
}
