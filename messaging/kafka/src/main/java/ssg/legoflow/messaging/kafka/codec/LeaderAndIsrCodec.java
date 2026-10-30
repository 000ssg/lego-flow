package ssg.legoflow.messaging.kafka.codec;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.protocol.LeaderAndIsrRequest;
import ssg.legoflow.messaging.kafka.protocol.LeaderAndIsrResponse;

/**
 * LeaderAndIsr codec (API key 4), versions v0–v7.
 *
 * <p>Request: leader/ISR state sent from controller to brokers.
 * Response: per-partition error codes.
 *
 * <p>Version summary:
 * <ul>
 *   <li>v0: flat {@code UngroupedPartitionStates}, no broker epoch, no topic IDs</li>
 *   <li>v1: + {@code IsNew} on each partition state</li>
 *   <li>v2: + {@code BrokerEpoch}, partitions grouped by {@code TopicStates}</li>
 *   <li>v3: + {@code AddingReplicas}, {@code RemovingReplicas}</li>
 *   <li>v4: first flexible (KIP-482) version</li>
 *   <li>v5: + {@code Type}, {@code TopicId} on topics and partitions; response restructured to {@code Topics}</li>
 *   <li>v6: + {@code LeaderRecoveryState}</li>
 *   <li>v7: + {@code isKRaftController}</li>
 * </ul>
 */
public final class LeaderAndIsrCodec {

    private static final Logger LOG = LoggerFactory.getLogger(LeaderAndIsrCodec.class);

    /** Minimum supported version. */
    public static final short MIN_VERSION = 0;
    /** Maximum supported version. */
    public static final short MAX_VERSION = 7;
    /** First flexible version (KIP-482). */
    public static final short FLEXIBLE_START = 4;
    /** Pinned version for unversioned facade compatibility. */
    public static final short PINNED_VERSION = 0;

    private LeaderAndIsrCodec() {
    }

    // ── Request ──────────────────────────────────────────────────────────────

    /**
     * Encodes a LeaderAndIsr request.
     *
     * @param version the protocol version
     * @param req     the request
     * @return encoded bytes
     */
    public static byte[] encodeRequest(short version, LeaderAndIsrRequest req) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported LeaderAndIsr version: " + version);
        }
        int size = estimateRequestSize(version, req);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeRequestBody(version, req, buf);
        return toBytes(buf);
    }

    /**
     * Decodes a LeaderAndIsr request.
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded request
     */
    public static LeaderAndIsrRequest decodeRequest(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported LeaderAndIsr version: " + version);
        }
        int controllerId = buf.getInt();

        // v7: isKRaftController comes BEFORE controllerEpoch
        boolean isKRaftController = LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER;
        if (version >= 7) {
            isKRaftController = buf.get() != 0;
        }

        int controllerEpoch = buf.getInt();

        long brokerEpoch = LeaderAndIsrRequest.DEFAULT_BROKER_EPOCH;
        if (version >= 2) {
            brokerEpoch = buf.getLong();
        }

        int type = LeaderAndIsrRequest.DEFAULT_TYPE;
        if (version >= 5) {
            type = buf.get();
        }

        List<LeaderAndIsrRequest.PartitionState> partitionStates;
        List<LeaderAndIsrRequest.TopicState> topicStates;

        if (version <= 1) {
            // v0-v1: UngroupedPartitionStates
            partitionStates = readPartitionStates(version, buf);
            topicStates = List.of();
        } else {
            // v2+: TopicStates
            partitionStates = List.of();
            topicStates = readTopicStates(version, buf);
        }

        List<LeaderAndIsrRequest.LiveLeader> liveLeaders = readLiveLeaders(version, buf);

        return new LeaderAndIsrRequest(
                controllerId, controllerEpoch, brokerEpoch, isKRaftController, type,
                partitionStates, topicStates, liveLeaders
        );
    }

    private static List<LeaderAndIsrRequest.PartitionState> readPartitionStates(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<LeaderAndIsrRequest.PartitionState> states = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = version <= 1 ? readString(buf) : null;
            int partition = buf.getInt();
            int controllerEpoch = buf.getInt();
            int leader = buf.getInt();
            int leaderEpoch = buf.getInt();
            List<Integer> isr = readIntList(buf);
            int partitionEpoch = buf.getInt();
            List<Integer> replicas = readIntList(buf);
            boolean isNew = version >= 1 && buf.get() != 0;
            List<Integer> addingReplicas = version >= 3 ? readNullableIntList(buf) : null;
            List<Integer> removingReplicas = version >= 3 ? readNullableIntList(buf) : null;
            int leaderRecoveryState = version >= 6 ? buf.get() : 0;
            states.add(new LeaderAndIsrRequest.PartitionState(
                    topic, partition, controllerEpoch, leader, leaderEpoch, isr,
                    partitionEpoch, replicas, isNew, addingReplicas, removingReplicas, leaderRecoveryState
            ));
        }
        return states;
    }

    private static List<LeaderAndIsrRequest.TopicState> readTopicStates(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<LeaderAndIsrRequest.TopicState> topics = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topicName = readString(buf);
            UUID topicId = version >= 5 ? readNullableUuid(buf) : null;
            List<LeaderAndIsrRequest.PartitionState> partitionStates = readPartitionStates(version, buf);
            topics.add(new LeaderAndIsrRequest.TopicState(topicName, topicId, partitionStates));
        }
        return topics;
    }

    private static List<LeaderAndIsrRequest.LiveLeader> readLiveLeaders(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<LeaderAndIsrRequest.LiveLeader> leaders = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            int brokerId = buf.getInt();
            String hostName = readString(buf);
            int port = buf.getInt();
            leaders.add(new LeaderAndIsrRequest.LiveLeader(brokerId, hostName, port));
        }
        return leaders;
    }

    // ── Response ─────────────────────────────────────────────────────────────

    /**
     * Encodes a LeaderAndIsr response.
     *
     * @param version the protocol version
     * @param resp    the response
     * @return encoded bytes
     */
    public static byte[] encodeResponse(short version, LeaderAndIsrResponse resp) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported LeaderAndIsr version: " + version);
        }
        int size = estimateResponseSize(version, resp);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeResponseBody(version, resp, buf);
        return toBytes(buf);
    }

    /**
     * Decodes a LeaderAndIsr response.
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded response
     */
    public static LeaderAndIsrResponse decodeResponse(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported LeaderAndIsr version: " + version);
        }
        short errorCode = buf.getShort();

        List<LeaderAndIsrResponse.PartitionError> partitionErrors;
        List<LeaderAndIsrResponse.TopicError> topics;

        if (version <= 4) {
            // v0-v4: flat PartitionErrors
            partitionErrors = readPartitionErrors(version, buf);
            topics = List.of();
        } else {
            // v5+: Topics keyed by TopicId
            partitionErrors = List.of();
            topics = readTopics(version, buf);
        }

        return new LeaderAndIsrResponse(errorCode, partitionErrors, topics);
    }

    private static List<LeaderAndIsrResponse.PartitionError> readPartitionErrors(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<LeaderAndIsrResponse.PartitionError> errors = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topicName = version <= 4 ? readString(buf) : null;
            int partitionIndex = buf.getInt();
            short errorCode = buf.getShort();
            errors.add(new LeaderAndIsrResponse.PartitionError(topicName, partitionIndex, errorCode));
        }
        return errors;
    }

    private static List<LeaderAndIsrResponse.TopicError> readTopics(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<LeaderAndIsrResponse.TopicError> topics = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            UUID topicId = readNullableUuid(buf);
            List<LeaderAndIsrResponse.PartitionError> partitionErrors = readPartitionErrors(version, buf);
            topics.add(new LeaderAndIsrResponse.TopicError(topicId, partitionErrors));
        }
        return topics;
    }

    // ── Estimate ─────────────────────────────────────────────────────────────

    private static int estimateRequestSize(short version, LeaderAndIsrRequest req) {
        int size = 4; // controllerId
        if (version >= 7) size += 1; // isKRaftController
        size += 4; // controllerEpoch
        if (version >= 2) size += 8; // brokerEpoch
        if (version >= 5) size += 1; // type
        if (version <= 1) {
            size += 4; // UngroupedPartitionStates count
            for (LeaderAndIsrRequest.PartitionState ps : req.partitionStates()) {
                if (version <= 1) size += stringSize(ps.topic());
                size += 4 + 4 + 4 + 4 + intListSize(ps.isr())
                        + 4 + 4 + intListSize(ps.replicas()); // +4 for partitionEpoch, +4 for controllerEpoch
                if (version >= 1) size += 1; // isNew
                if (version >= 3) {
                    size += nullableIntListSize(ps.addingReplicas());
                    size += nullableIntListSize(ps.removingReplicas());
                }
                if (version >= 6) size += 1; // leaderRecoveryState
            }
        } else {
            size += 4; // TopicStates count
            for (LeaderAndIsrRequest.TopicState ts : req.topicStates()) {
                size += stringSize(ts.topicName());
                if (version >= 5) size += 16; // TopicId uuid
                size += 4 + ts.partitionStates().size() * 4; // PartitionStates count + elements
                for (LeaderAndIsrRequest.PartitionState ps : ts.partitionStates()) {
                    size += 4 + 4 + 4 + 4 + intListSize(ps.isr())
                            + 4 + 4 + intListSize(ps.replicas()); // +4 for partitionEpoch, +4 for controllerEpoch
                    if (version >= 1) size += 1;
                    if (version >= 3) {
                        size += nullableIntListSize(ps.addingReplicas());
                        size += nullableIntListSize(ps.removingReplicas());
                    }
                    if (version >= 6) size += 1;
                }
            }
        }
        size += 4 + liveLeaderSize(req.liveLeaders()); // LiveLeaders count + elements
        return Math.max(64, size);
    }

    private static int estimateResponseSize(short version, LeaderAndIsrResponse resp) {
        int size = 2; // errorCode
        if (version <= 4) {
            size += 4; // PartitionErrors count
            for (LeaderAndIsrResponse.PartitionError pe : resp.partitionErrors()) {
                if (version <= 4) size += stringSize(pe.topicName());
                size += 4 + 2;
            }
        } else {
            size += 4; // Topics count
            for (LeaderAndIsrResponse.TopicError te : resp.topics()) {
                size += 16; // TopicId uuid
                size += 4 + partitionErrorSize(te.partitionErrors()); // PartitionErrors count + elements
            }
        }
        return Math.max(64, size);
    }

    // ── Encode bodies ────────────────────────────────────────────────────────

    private static void encodeRequestBody(short version, LeaderAndIsrRequest req, ByteBuffer buf) {
        buf.putInt(req.controllerId());
        if (version >= 7) {
            buf.put((byte) (req.isKRaftController() ? 1 : 0));
        }
        buf.putInt(req.controllerEpoch());
        if (version >= 2) buf.putLong(req.brokerEpoch());
        if (version >= 5) buf.put((byte) req.type());
        if (version <= 1) {
            buf.putInt(req.partitionStates().size());
            for (LeaderAndIsrRequest.PartitionState ps : req.partitionStates()) {
                if (version <= 1) writeString(buf, ps.topic());
                buf.putInt(ps.partition());
                buf.putInt(ps.controllerEpoch());
                buf.putInt(ps.leader());
                buf.putInt(ps.leaderEpoch());
                writeIntList(buf, ps.isr());
                buf.putInt(ps.partitionEpoch());
                writeIntList(buf, ps.replicas());
                if (version >= 1) buf.put((byte) (ps.isNew() ? 1 : 0));
                if (version >= 3) writeNullableIntList(buf, ps.addingReplicas());
                if (version >= 3) writeNullableIntList(buf, ps.removingReplicas());
                if (version >= 6) buf.put((byte) ps.leaderRecoveryState());
            }
        } else {
            buf.putInt(req.topicStates().size());
            for (LeaderAndIsrRequest.TopicState ts : req.topicStates()) {
                writeString(buf, ts.topicName());
                if (version >= 5) writeUuid(buf, ts.topicId());
                // Encode PartitionStates inline
                List<LeaderAndIsrRequest.PartitionState> pss = ts.partitionStates();
                buf.putInt(pss.size());
                for (LeaderAndIsrRequest.PartitionState ps : pss) {
                    buf.putInt(ps.partition());
                    buf.putInt(ps.controllerEpoch());
                    buf.putInt(ps.leader());
                    buf.putInt(ps.leaderEpoch());
                    writeIntList(buf, ps.isr());
                    buf.putInt(ps.partitionEpoch());
                    writeIntList(buf, ps.replicas());
                    if (version >= 1) buf.put((byte) (ps.isNew() ? 1 : 0));
                    if (version >= 3) writeNullableIntList(buf, ps.addingReplicas());
                    if (version >= 3) writeNullableIntList(buf, ps.removingReplicas());
                    if (version >= 6) buf.put((byte) ps.leaderRecoveryState());
                }
            }
        }
        buf.putInt(req.liveLeaders().size());
        for (LeaderAndIsrRequest.LiveLeader ll : req.liveLeaders()) {
            buf.putInt(ll.brokerId());
            writeString(buf, ll.hostName());
            buf.putInt(ll.port());
        }
    }

    private static void encodeResponseBody(short version, LeaderAndIsrResponse resp, ByteBuffer buf) {
        buf.putShort(resp.errorCode());
        if (version <= 4) {
            buf.putInt(resp.partitionErrors().size());
            for (LeaderAndIsrResponse.PartitionError pe : resp.partitionErrors()) {
                if (version <= 4) writeString(buf, pe.topicName());
                buf.putInt(pe.partitionIndex());
                buf.putShort(pe.errorCode());
            }
        } else {
            buf.putInt(resp.topics().size());
            for (LeaderAndIsrResponse.TopicError te : resp.topics()) {
                writeUuid(buf, te.topicId());
                buf.putInt(te.partitionErrors().size());
                for (LeaderAndIsrResponse.PartitionError pe : te.partitionErrors()) {
                    buf.putInt(pe.partitionIndex());
                    buf.putShort(pe.errorCode());
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Size of a fixed string (int16 length + UTF-8 bytes).
     */
    private static int stringSize(String s) {
        if (s == null) return 2;
        return 2 + s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Size of an int array (int32 count + elements).
     */
    private static int intListSize(List<Integer> list) {
        return 4 + list.size() * 4;
    }

    /**
     * Size of a nullable int array (int32 count, -1 = null).
     */
    private static int nullableIntListSize(List<Integer> list) {
        if (list == null) return 4;
        return 4 + list.size() * 4;
    }

    /**
     * Size of a LiveLeader (int32 + string + int32).
     */
    private static int liveLeaderSize(List<LeaderAndIsrRequest.LiveLeader> leaders) {
        int size = 0;
        for (LeaderAndIsrRequest.LiveLeader ll : leaders) {
            size += 4 + stringSize(ll.hostName()) + 4;
        }
        return size;
    }

    /**
     * Size of a PartitionError (string + int32 + int16).
     */
    private static int partitionErrorSize(List<LeaderAndIsrResponse.PartitionError> errors) {
        int size = 0;
        for (LeaderAndIsrResponse.PartitionError pe : errors) {
            size += stringSize(pe.topicName()) + 4 + 2;
        }
        return size;
    }

    /**
     * Read a nullable fixed-length string.
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
     * Read a string (fixed for non-flexible, compact for flexible).
     */
    static String readString(short version, ByteBuffer buf) {
        return readString(buf);
    }

    /**
     * Write a string (fixed for non-flexible, compact for flexible).
     */
    static void writeString(short version, ByteBuffer buf, String s) {
        writeString(buf, s);
    }

    /**
     * Read an int array (int32 count + elements).
     */
    static List<Integer> readIntList(ByteBuffer buf) {
        int count = buf.getInt();
        List<Integer> list = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            list.add(buf.getInt());
        }
        return list;
    }

    /**
     * Write an int array (int32 count + elements).
     */
    static void writeIntList(ByteBuffer buf, List<Integer> list) {
        buf.putInt(list.size());
        for (int x : list) {
            buf.putInt(x);
        }
    }

    /**
     * Read a nullable int array (int32 count, -1 = null).
     */
    static List<Integer> readNullableIntList(ByteBuffer buf) {
        int count = buf.getInt();
        if (count == -1) {
            return null;
        }
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.getInt());
        }
        return list;
    }

    /**
     * Write a nullable int array (int32 count, -1 = null).
     */
    static void writeNullableIntList(ByteBuffer buf, List<Integer> list) {
        if (list == null) {
            buf.putInt(-1);
            return;
        }
        writeIntList(buf, list);
    }

    /**
     * Read a UUID (16 bytes, all zeros = UUID.ZERO).
     */
    static UUID readNullableUuid(ByteBuffer buf) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (buf.get() & 0xFF);
        }
        for (int i = 0; i < 8; i++) {
            lsb = (lsb << 8) | (buf.get() & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    /**
     * Write a nullable UUID.
     */
    static void writeUuid(ByteBuffer buf, UUID uuid) {
        if (uuid == null) {
            for (int i = 0; i < 16; i++) buf.put((byte) 0);
            return;
        }
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 7; i >= 0; i--) buf.put((byte) (msb >> (i * 8)));
        for (int i = 7; i >= 0; i--) buf.put((byte) (lsb >> (i * 8)));
    }

    /**
     * Write KIP-482 endTags: single varint(0) byte.
     */
    static void writeEndTags(ByteBuffer buf) {
        writeVarint(buf, 0);
    }

    /**
     * Skip tagged fields (read varint count, then skip that many tags).
     */
    static void skipEndTags(ByteBuffer buf) {
        int count = readVarint(buf);
        for (int i = 0; i < count; i++) {
            readVarint(buf); // tag
            int len = readVarint(buf);
            buf.position(buf.position() + len);
        }
    }

    /**
     * Convert ByteBuffer to byte[] (handles flip and limit).
     */
    static byte[] toBytes(ByteBuffer buf) {
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    // ── Varint helpers ───────────────────────────────────────────────────────

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
     * Reads an unsigned int32 varint.
     */
    static int readVarint(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = buf.get() & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return result;
    }

    // ── Compact string helpers ───────────────────────────────────────────────

    /**
     * Number of bytes a compact string occupies.
     */
    static int compactStringSize(String s) {
        if (s == null) return 1;
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return varintSize(bytes.length + 1) + bytes.length;
    }

    /**
     * Writes a compact string (varint length+1 + UTF-8, varint(0) = null).
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
     * Reads a compact string (varint length+1 + UTF-8, varint(0) = null).
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

    // ── Size estimators ──────────────────────────────────────────────────────

    static int compactIntListSize(List<Integer> list) {
        if (list == null || list.isEmpty()) return 1;
        return varintSize(list.size() + 1) + list.size() * 4;
    }

    static int compactNullableIntListSize(List<Integer> list) {
        if (list == null || list.isEmpty()) return 1;
        return varintSize(list.size() + 1) + list.size() * 4;
    }
}
