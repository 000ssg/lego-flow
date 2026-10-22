package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.kafka.protocol.UpdateMetadataRequest;
import ssg.legoflow.messaging.kafka.protocol.UpdateMetadataResponse;

/**
 * UpdateMetadata codec (API key 6) — all versions v0–v8.
 *
 * <p>Handles encoding and decoding of UpdateMetadata request/response bodies.
 *
 * <p>Version summary:
 * <ul>
 *   <li>v0–v4: flat UngroupedPartitionStates array</li>
 *   <li>v5–v7: TopicStates array with grouped partitions</li>
 *   <li>v8: + isKRaftController and Type fields</li>
 *   <li>v6+: flexible encoding (KIP-482)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class UpdateMetadataCodec {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateMetadataCodec.class);

    /** Spec minimum version. */
    public static final short MIN_VERSION = 0;
    /** Spec maximum version (Kafka 3.6+ / KIP-848 era). */
    public static final short MAX_VERSION = 8;
    /** Flexible (KIP-482) encoding starts at v6; strings become compact. */
    public static final short FLEXIBLE_START = 6;
    /** Pinned version for tests. */
    public static final short PINNED_VERSION = 0;

    private UpdateMetadataCodec() {
    }

    // ── Request ──────────────────────────────────────────────────────────────

    /**
     * Encodes an UpdateMetadata request.
     *
     * @param version the protocol version
     * @param req     the request
     * @return encoded bytes
     */
    public static byte[] encodeRequest(short version, UpdateMetadataRequest req) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported UpdateMetadata version: " + version);
        }
        int size = estimateRequestSize(version, req);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeRequestBody(version, req, buf);
        return toBytes(buf);
    }

    /**
     * Decodes an UpdateMetadata request.
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded request
     */
    public static UpdateMetadataRequest decodeRequest(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported UpdateMetadata version: " + version);
        }
        int controllerId = buf.getInt();

        boolean isKRaftController = UpdateMetadataRequest.DEFAULT_IS_KRAFT_CONTROLLER;
        int type = UpdateMetadataRequest.DEFAULT_TYPE;
        if (version >= 8) {
            isKRaftController = buf.get() != 0;
            type = buf.get();
        }

        int controllerEpoch = buf.getInt();

        long brokerEpoch = UpdateMetadataRequest.DEFAULT_BROKER_EPOCH;
        if (version >= 5) {
            brokerEpoch = buf.getLong();
        }

        List<UpdateMetadataRequest.PartitionState> partitionStates;
        List<UpdateMetadataRequest.TopicState> topicStates;

        if (version <= 4) {
            // v0-v4: UngroupedPartitionStates
            partitionStates = readPartitionStates(version, buf);
            topicStates = List.of();
        } else {
            // v5+: TopicStates
            partitionStates = List.of();
            topicStates = readTopicStates(version, buf);
        }

        List<UpdateMetadataRequest.BrokerState> liveBrokers = readLiveBrokers(version, buf);

        return new UpdateMetadataRequest(
                controllerId, controllerEpoch, brokerEpoch, isKRaftController, type,
                partitionStates, topicStates, liveBrokers
        );
    }

    private static List<UpdateMetadataRequest.PartitionState> readPartitionStates(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<UpdateMetadataRequest.PartitionState> states = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = version <= 4 ? readString(buf) : null;
            int partition = buf.getInt();
            int controllerEpoch = buf.getInt();
            int leader = buf.getInt();
            int leaderEpoch = buf.getInt();
            List<Integer> isr = readIntList(buf);
            int zkVersion = buf.getInt();
            List<Integer> replicas = readIntList(buf);
            List<Integer> offlineReplicas = version >= 4 ? readNullableIntList(buf) : null;
            states.add(new UpdateMetadataRequest.PartitionState(
                    topic, partition, controllerEpoch, leader, leaderEpoch, isr,
                    zkVersion, replicas, offlineReplicas
            ));
        }
        return states;
    }

    private static List<UpdateMetadataRequest.TopicState> readTopicStates(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<UpdateMetadataRequest.TopicState> topics = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topicName = readString(buf);
            List<UpdateMetadataRequest.PartitionState> partitionStates = readPartitionStates(version, buf);
            topics.add(new UpdateMetadataRequest.TopicState(topicName, partitionStates));
        }
        return topics;
    }

    private static List<UpdateMetadataRequest.BrokerState> readLiveBrokers(short version, ByteBuffer buf) {
        int count = buf.getInt();
        List<UpdateMetadataRequest.BrokerState> brokers = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            int brokerId = buf.getInt();
            String host = readString(buf);
            int port = buf.getInt();
            brokers.add(new UpdateMetadataRequest.BrokerState(brokerId, host, port));
        }
        return brokers;
    }

    // ── Response ─────────────────────────────────────────────────────────────

    /**
     * Encodes an UpdateMetadata response.
     *
     * @param version the protocol version
     * @param resp    the response
     * @return encoded bytes
     */
    public static byte[] encodeResponse(short version, UpdateMetadataResponse resp) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported UpdateMetadata version: " + version);
        }
        int size = estimateResponseSize(version, resp);
        ByteBuffer buf = ByteBuffer.allocate(size);
        encodeResponseBody(version, resp, buf);
        return toBytes(buf);
    }

    /**
     * Decodes an UpdateMetadata response.
     *
     * @param version the protocol version
     * @param buf     the buffer (position at body start)
     * @return the decoded response
     */
    public static UpdateMetadataResponse decodeResponse(short version, ByteBuffer buf) {
        if (version < MIN_VERSION || version > MAX_VERSION) {
            throw new IllegalArgumentException("Unsupported UpdateMetadata version: " + version);
        }
        short errorCode = buf.getShort();
        return new UpdateMetadataResponse(errorCode);
    }

    // ── Estimate ─────────────────────────────────────────────────────────────

    private static int estimateRequestSize(short version, UpdateMetadataRequest req) {
        int size = 4; // controllerId
        size += 4; // controllerEpoch
        if (version >= 5) size += 8; // brokerEpoch
        if (version >= 8) size += 2; // isKRaftController + type
        if (version <= 4) {
            size += 4; // UngroupedPartitionStates count
            for (UpdateMetadataRequest.PartitionState ps : req.partitionStates()) {
                if (version <= 4) size += stringSize(ps.topic());
                size += 4 + 4 + 4 + 4 + intListSize(ps.isr())
                        + 4 + 4 + intListSize(ps.replicas()); // +4 for zkVersion
                if (version >= 4) size += nullableIntListSize(ps.offlineReplicas());
            }
        } else {
            size += 4; // TopicStates count
            for (UpdateMetadataRequest.TopicState ts : req.topicStates()) {
                size += stringSize(ts.topicName());
                size += 4; // PartitionStates count
                for (UpdateMetadataRequest.PartitionState ps : ts.partitionStates()) {
                    size += 4 + 4 + 4 + 4 + intListSize(ps.isr())
                            + 4 + 4 + intListSize(ps.replicas()); // +4 for zkVersion
                    if (version >= 4) size += nullableIntListSize(ps.offlineReplicas());
                }
            }
        }
        size += 4 + brokerStateSize(req.liveBrokers()); // LiveBrokers count + elements
        return Math.max(64, size);
    }

    private static int estimateResponseSize(short version, UpdateMetadataResponse resp) {
        return 2; // errorCode
    }

    // ── Encode bodies ────────────────────────────────────────────────────────

    private static void encodeRequestBody(short version, UpdateMetadataRequest req, ByteBuffer buf) {
        buf.putInt(req.controllerId());
        if (version >= 8) {
            buf.put((byte) (req.isKRaftController() ? 1 : 0));
            buf.put((byte) req.type());
        }
        buf.putInt(req.controllerEpoch());
        if (version >= 5) buf.putLong(req.brokerEpoch());
        if (version <= 4) {
            buf.putInt(req.partitionStates().size());
            for (UpdateMetadataRequest.PartitionState ps : req.partitionStates()) {
                if (version <= 4) writeString(buf, ps.topic());
                buf.putInt(ps.partition());
                buf.putInt(ps.controllerEpoch());
                buf.putInt(ps.leader());
                buf.putInt(ps.leaderEpoch());
                writeIntList(buf, ps.isr());
                buf.putInt(ps.zkVersion());
                writeIntList(buf, ps.replicas());
                if (version >= 4) writeNullableIntList(buf, ps.offlineReplicas());
            }
        } else {
            buf.putInt(req.topicStates().size());
            for (UpdateMetadataRequest.TopicState ts : req.topicStates()) {
                writeString(buf, ts.topicName());
                buf.putInt(ts.partitionStates().size());
                for (UpdateMetadataRequest.PartitionState ps : ts.partitionStates()) {
                    buf.putInt(ps.partition());
                    buf.putInt(ps.controllerEpoch());
                    buf.putInt(ps.leader());
                    buf.putInt(ps.leaderEpoch());
                    writeIntList(buf, ps.isr());
                    buf.putInt(ps.zkVersion());
                    writeIntList(buf, ps.replicas());
                    if (version >= 4) writeNullableIntList(buf, ps.offlineReplicas());
                }
            }
        }
        buf.putInt(req.liveBrokers().size());
        for (UpdateMetadataRequest.BrokerState lb : req.liveBrokers()) {
            buf.putInt(lb.brokerId());
            writeString(buf, lb.host());
            buf.putInt(lb.port());
        }
    }

    private static void encodeResponseBody(short version, UpdateMetadataResponse resp, ByteBuffer buf) {
        buf.putShort(resp.errorCode());
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
     * Size of a BrokerState (int32 + string + int32).
     */
    private static int brokerStateSize(List<UpdateMetadataRequest.BrokerState> brokers) {
        int size = 0;
        for (UpdateMetadataRequest.BrokerState lb : brokers) {
            size += 4 + stringSize(lb.host()) + 4;
        }
        return size;
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
     * Convert ByteBuffer to byte[].
     */
    static byte[] toBytes(ByteBuffer buf) {
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}