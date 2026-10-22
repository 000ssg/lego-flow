package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.kafka.protocol.StopReplicaRequest;
import ssg.legoflow.messaging.kafka.protocol.StopReplicaResponse;

/**
 * StopReplica codec (API key 5, v0-v4, flexible from v2).
 *
 * <p>Handles encoding and decoding of StopReplica request and response bodies.
 *
 * @since 0.1.0
 */
public final class StopReplicaCodec {

    private static final Logger LOG = LoggerFactory.getLogger(StopReplicaCodec.class);

    /** Spec minimum version. */
    public static final short MIN_VERSION = 0;
    /** Spec maximum version. */
    public static final short MAX_VERSION = 4;
    /** Flexible (KIP-482) encoding starts at v2. */
    public static final short FLEXIBLE_START = 2;
    /** Pinned version for facade methods. */
    public static final short PINNED_VERSION = 0;

    private StopReplicaCodec() {
    }

    // ── Request ──────────────────────────────────────────────────────────────

    /**
     * Encodes a StopReplica request body (versioned).
     *
     * @param version the protocol version
     * @param req     the request
     * @return encoded bytes
     */
    public static byte[] encodeRequest(short version, StopReplicaRequest req) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported StopReplica version: " + version);
        }
        int size = estimateRequestSize(version, req);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeRequestBody(version, req, buf);
        return toBytes(buf);
    }

    /**
     * Decodes a StopReplica request body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded request
     */
    public static StopReplicaRequest decodeRequest(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported StopReplica version: " + version);
        }
        int controllerId = buf.getInt();
        int controllerEpoch = buf.getInt();

        long brokerEpoch = StopReplicaRequest.DEFAULT_BROKER_EPOCH;
        if (version >= 1) {
            brokerEpoch = buf.getLong();
        }

        boolean isKRaftController = version >= 4 && buf.get() != 0;
        boolean deletePartitions = version <= 2 && buf.get() != 0;

        List<StopReplicaRequest.TopicPartitionData> partitions;
        List<StopReplicaRequest.TopicData> topics;
        List<StopReplicaRequest.TopicState> topicStates;

        if (version == 0) {
            partitions = readTopicPartitionDataList(buf);
            topics = List.of();
            topicStates = List.of();
        } else if (version <= 2) {
            partitions = List.of();
            topics = readTopicDataList(version, buf);
            topicStates = List.of();
        } else {
            partitions = List.of();
            topics = List.of();
            topicStates = readTopicStateList(version, buf);
        }

        return new StopReplicaRequest(
                controllerId, controllerEpoch, brokerEpoch, isKRaftController,
                deletePartitions, partitions, topics, topicStates);
    }

    private static List<StopReplicaRequest.TopicPartitionData> readTopicPartitionDataList(ByteBuffer buf) {
        int count = buf.getInt();
        List<StopReplicaRequest.TopicPartitionData> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = readString(buf);
            int partition = buf.getInt();
            list.add(new StopReplicaRequest.TopicPartitionData(topic, partition));
        }
        return list;
    }

    private static List<StopReplicaRequest.TopicData> readTopicDataList(short version, ByteBuffer buf) {
        int count = isFlexible(version) ? readVarint(buf) - 1 : buf.getInt();
        List<StopReplicaRequest.TopicData> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topicName = isFlexible(version) ? readCompactString(buf) : readString(buf);
            List<Integer> partitionIndexes = readIntList(version, buf);
            list.add(new StopReplicaRequest.TopicData(topicName, partitionIndexes));
        }
        return list;
    }

    private static List<StopReplicaRequest.TopicState> readTopicStateList(short version, ByteBuffer buf) {
        int count = isFlexible(version) ? readVarint(buf) - 1 : buf.getInt();
        List<StopReplicaRequest.TopicState> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topicName = isFlexible(version) ? readCompactString(buf) : readString(buf);
            List<StopReplicaRequest.PartitionState> partitionStates = readPartitionStateList(version, buf);
            list.add(new StopReplicaRequest.TopicState(topicName, partitionStates));
        }
        return list;
    }

    private static List<StopReplicaRequest.PartitionState> readPartitionStateList(short version, ByteBuffer buf) {
        int count = isFlexible(version) ? readVarint(buf) - 1 : buf.getInt();
        List<StopReplicaRequest.PartitionState> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            int partition = buf.getInt();
            int leaderEpoch = buf.getInt();
            boolean deletePartition = buf.get() != 0;
            list.add(new StopReplicaRequest.PartitionState(partition, leaderEpoch, deletePartition));
        }
        return list;
    }

    // ── Response ─────────────────────────────────────────────────────────────

    /**
     * Encodes a StopReplica response body (versioned).
     *
     * @param version the protocol version
     * @param resp    the response
     * @return encoded bytes
     */
    public static byte[] encodeResponse(short version, StopReplicaResponse resp) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported StopReplica version: " + version);
        }
        int size = estimateResponseSize(version, resp);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeResponseBody(version, resp, buf);
        return toBytes(buf);
    }

    /**
     * Decodes a StopReplica response body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded response
     */
    public static StopReplicaResponse decodeResponse(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported StopReplica version: " + version);
        }
        short errorCode = buf.getShort();
        List<StopReplicaResponse.PartitionResult> partitions = readPartitionResultList(version, buf);
        return new StopReplicaResponse(errorCode, partitions);
    }

    private static List<StopReplicaResponse.PartitionResult> readPartitionResultList(short version, ByteBuffer buf) {
        int count = isFlexible(version) ? readVarint(buf) - 1 : buf.getInt();
        List<StopReplicaResponse.PartitionResult> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = readString(buf);
            int partition = buf.getInt();
            short errorCode = buf.getShort();
            list.add(new StopReplicaResponse.PartitionResult(topic, partition, errorCode));
        }
        return list;
    }

    // ── Estimate ─────────────────────────────────────────────────────────────

    private static int estimateRequestSize(short version, StopReplicaRequest req) {
        int size = 4 + 4; // controllerId + controllerEpoch
        if (version >= 1) size += 8; // brokerEpoch
        if (version >= 4) size += 1; // isKRaftController
        if (version <= 2) size += 1; // deletePartitions

        if (version == 0) {
            size += 4; // UngroupedPartitions count
            for (StopReplicaRequest.TopicPartitionData p : req.partitions()) {
                size += stringSize(p.topic()) + 4;
            }
        } else if (version <= 2) {
            size += isFlexible(version) ? varintSize(req.topics().size() + 1) : 4;
            for (StopReplicaRequest.TopicData t : req.topics()) {
                size += isFlexible(version) ? compactStringSize(t.topicName()) : stringSize(t.topicName());
                size += isFlexible(version) ? varintSize(t.partitionIndexes().size() + 1) : 4 + t.partitionIndexes().size() * 4;
            }
        } else {
            size += isFlexible(version) ? varintSize(req.topicStates().size() + 1) : 4;
            for (StopReplicaRequest.TopicState ts : req.topicStates()) {
                size += isFlexible(version) ? compactStringSize(ts.topicName()) : stringSize(ts.topicName());
                size += isFlexible(version) ? varintSize(ts.partitionStates().size() + 1) : 4;
                for (StopReplicaRequest.PartitionState ps : ts.partitionStates()) {
                    size += 4 + 4 + 1; // partition + leaderEpoch + deletePartition
                }
            }
        }
        return Math.max(64, size);
    }

    private static int estimateResponseSize(short version, StopReplicaResponse resp) {
        int size = 2; // errorCode
        size += isFlexible(version) ? varintSize(resp.partitions().size() + 1) : 4;
        for (StopReplicaResponse.PartitionResult pr : resp.partitions()) {
            size += stringSize(pr.topic()) + 4 + 2;
        }
        return Math.max(64, size);
    }

    // ── Encode bodies ────────────────────────────────────────────────────────

    private static void encodeRequestBody(short version, StopReplicaRequest req, ByteBuffer buf) {
        buf.putInt(req.controllerId());
        buf.putInt(req.controllerEpoch());
        if (version >= 1) buf.putLong(req.brokerEpoch());
        if (version >= 4) buf.put((byte) (req.isKRaftController() ? 1 : 0));
        if (version <= 2) buf.put((byte) (req.deletePartitions() ? 1 : 0));

        if (version == 0) {
            buf.putInt(req.partitions().size());
            for (StopReplicaRequest.TopicPartitionData p : req.partitions()) {
                writeString(buf, p.topic());
                buf.putInt(p.partition());
            }
        } else if (version <= 2) {
            if (isFlexible(version)) {
                writeVarint(buf, req.topics().size() + 1);
            } else {
                buf.putInt(req.topics().size());
            }
            for (StopReplicaRequest.TopicData t : req.topics()) {
                if (isFlexible(version)) {
                    writeCompactString(buf, t.topicName());
                } else {
                    writeString(buf, t.topicName());
                }
                writeIntList(version, buf, t.partitionIndexes());
            }
        } else {
            if (isFlexible(version)) {
                writeVarint(buf, req.topicStates().size() + 1);
            } else {
                buf.putInt(req.topicStates().size());
            }
            for (StopReplicaRequest.TopicState ts : req.topicStates()) {
                if (isFlexible(version)) {
                    writeCompactString(buf, ts.topicName());
                } else {
                    writeString(buf, ts.topicName());
                }
                if (isFlexible(version)) {
                    writeVarint(buf, ts.partitionStates().size() + 1);
                } else {
                    buf.putInt(ts.partitionStates().size());
                }
                for (StopReplicaRequest.PartitionState ps : ts.partitionStates()) {
                    buf.putInt(ps.partition());
                    buf.putInt(ps.leaderEpoch());
                    buf.put((byte) (ps.deletePartition() ? 1 : 0));
                }
            }
        }
    }

    private static void encodeResponseBody(short version, StopReplicaResponse resp, ByteBuffer buf) {
        buf.putShort(resp.errorCode());
        if (isFlexible(version)) {
            writeVarint(buf, resp.partitions().size() + 1);
        } else {
            buf.putInt(resp.partitions().size());
        }
        for (StopReplicaResponse.PartitionResult pr : resp.partitions()) {
            writeString(buf, pr.topic());
            buf.putInt(pr.partition());
            buf.putShort(pr.errorCode());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Check if version is flexible (KIP-482).
     */
    static boolean isFlexible(short version) {
        return version >= FLEXIBLE_START;
    }

    /**
     * Size of a fixed string (int16 length + UTF-8 bytes).
     */
    private static int stringSize(String s) {
        if (s == null) return 2;
        return 2 + s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Size of a compact string (varint length+1 + UTF-8 bytes).
     */
    private static int compactStringSize(String s) {
        if (s == null) return 1;
        return varintSize(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1)
                + s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Size of an int array (varint count for flexible, int32 count for non-flexible).
     */
    private static int intListSize(short version, List<Integer> list) {
        if (isFlexible(version)) {
            return varintSize(list.size() + 1);
        }
        return 4 + list.size() * 4;
    }

    /**
     * Number of bytes an unsigned int32 varint occupies (1–5).
     */
    static int varintSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    /**
     * Writes an unsigned int32 varint (7 bits per byte, little-endian groups).
     */
    static void writeVarint(ByteBuffer buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }

    /**
     * Reads an unsigned int32 varint (7 bits per byte, little-endian groups).
     */
    static int readVarint(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 32);
        return result;
    }

    /**
     * Writes a compact string (varint length+1 + UTF-8, varint 0 = null).
     */
    static void writeCompactString(ByteBuffer buf, String s) {
        if (s == null) {
            writeVarint(buf, 0);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarint(buf, bytes.length + 1);
        buf.put(bytes);
    }

    /**
     * Reads a compact string (varint length+1 + UTF-8, varint 0 = null).
     */
    static String readCompactString(ByteBuffer buf) {
        int len = readVarint(buf) - 1;
        if (len < 0) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Write a fixed-length string.
     */
    static void writeString(ByteBuffer buf, String s) {
        if (s == null) {
            buf.putShort((short) -1);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    /**
     * Read a fixed-length string.
     */
    static String readString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len == -1) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Write an int array (varint count for flexible, int32 count for non-flexible).
     */
    static void writeIntList(short version, ByteBuffer buf, List<Integer> list) {
        if (isFlexible(version)) {
            writeVarint(buf, list.size() + 1);
        } else {
            buf.putInt(list.size());
        }
        for (int x : list) {
            buf.putInt(x);
        }
    }

    /**
     * Read an int array (varint count for flexible, int32 count for non-flexible).
     */
    static List<Integer> readIntList(short version, ByteBuffer buf) {
        int count = isFlexible(version) ? readVarint(buf) - 1 : buf.getInt();
        List<Integer> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            list.add(buf.getInt());
        }
        return list;
    }

    /**
     * Convert ByteBuffer to byte[].
     */
    static byte[] toBytes(ByteBuffer buf) {
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}
