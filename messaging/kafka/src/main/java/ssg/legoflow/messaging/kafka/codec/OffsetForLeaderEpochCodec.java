package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.kafka.protocol.OffsetForLeaderEpochRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetForLeaderEpochResponse;
import ssg.legoflow.service.util.BufferPool;

/**
 * OffsetForLeaderEpoch codec (API key 23) — v0–v4.
 *
 * <p>Handles OffsetForLeaderEpoch requests and responses.
 *
 * <p>Flexible encoding (KIP-482) applies from v4:
 * <ul>
 *   <li>compact strings (varint N+1)</li>
 *   <li>varint array counts</li>
 *   <li>endTags (varint 0) per element with no tags</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class OffsetForLeaderEpochCodec {

    private static final Logger log = LoggerFactory.getLogger(OffsetForLeaderEpochCodec.class);

    /** Pinned version for tests. */
    public static final short PINNED_VERSION = (short) 0;

    private OffsetForLeaderEpochCodec() {}

    // ── Request ──────────────────────────────────────────────────────────────

    /**
     * Encodes an OffsetForLeaderEpoch request body (versioned).
     *
     * @param version the protocol version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeRequest(short version, OffsetForLeaderEpochRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(estimateRequestSize(version, req));
        encodeRequestBody(version, req, buf);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetForLeaderEpoch request body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static OffsetForLeaderEpochRequest decodeRequest(short version, ByteBuffer buf) {
        String clusterId = readString(version, buf);
        List<OffsetForLeaderEpochRequest.TopicData> topics = readTopics(version, buf);
        return new OffsetForLeaderEpochRequest(clusterId, topics);
    }

    // ── Response ─────────────────────────────────────────────────────────────

    /**
     * Encodes an OffsetForLeaderEpoch response body (versioned).
     *
     * @param version the protocol version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeResponse(short version, OffsetForLeaderEpochResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(estimateResponseSize(version, resp));
        encodeResponseBody(version, resp, buf);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes an OffsetForLeaderEpoch response body (versioned).
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static OffsetForLeaderEpochResponse decodeResponse(short version, ByteBuffer buf) {
        short errorCode = buf.getShort();
        List<OffsetForLeaderEpochResponse.TopicData> topics = readResponseTopics(version, buf);
        return new OffsetForLeaderEpochResponse(errorCode, topics);
    }

    // ── Estimate ─────────────────────────────────────────────────────────────

    private static int estimateRequestSize(short version, OffsetForLeaderEpochRequest req) {
        int size = 0;
        // clusterId
        if (req.clusterId() != null) {
            size += 2 + req.clusterId().length(); // fixed string length + bytes
        } else {
            size += 2; // null string
        }
        // topics
        size += 4; // topic count
        for (var t : req.topics()) {
            if (t.topic() != null) {
                size += 2 + t.topic().length();
            } else {
                size += 2;
            }
            size += 4; // partition count
            size += t.partitions().size() * 8; // partition + leaderEpoch
        }
        return size;
    }

    private static int estimateResponseSize(short version, OffsetForLeaderEpochResponse resp) {
        int size = 2; // errorCode
        size += 4; // topic count
        for (var t : resp.topics()) {
            if (t.topic() != null) {
                size += 2 + t.topic().length();
            } else {
                size += 2;
            }
            size += 4; // epochEndOffsets count
            size += t.epochEndOffsets().size() * 14; // partition + endOffset + errorCode
        }
        return size;
    }

    // ── Encode ───────────────────────────────────────────────────────────────

    private static void encodeRequestBody(short version, OffsetForLeaderEpochRequest req, ByteBuffer buf) {
        writeString(version, buf, req.clusterId());
        writeIntList(version, buf, req.topics().size());
        for (var t : req.topics()) {
            writeString(version, buf, t.topic());
            writeIntList(version, buf, t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partition());
                buf.putInt(p.leaderEpoch());
            }
            if (version >= 4) {
                writeEndTags(buf, 1);
            }
        }
        if (version >= 4) {
            writeEndTags(buf, 1);
        }
    }

    private static void encodeResponseBody(short version, OffsetForLeaderEpochResponse resp, ByteBuffer buf) {
        buf.putShort(resp.errorCode());
        writeIntList(version, buf, resp.topics().size());
        for (var t : resp.topics()) {
            writeString(version, buf, t.topic());
            writeIntList(version, buf, t.epochEndOffsets().size());
            for (var e : t.epochEndOffsets()) {
                buf.putInt(e.partition());
                buf.putLong(e.endOffset());
                buf.putShort(e.errorCode());
            }
            if (version >= 4) {
                writeEndTags(buf, 1);
            }
        }
        if (version >= 4) {
            writeEndTags(buf, 1);
        }
    }

    // ── Decode ───────────────────────────────────────────────────────────────

    private static String readString(short version, ByteBuffer buf) {
        if (version >= 4) {
            int n = readVarint(buf);
            if (n == 0) {
                return null;
            }
            byte[] bytes = new byte[n - 1];
            buf.get(bytes);
            return new String(bytes);
        }
        int len = buf.getShort();
        if (len < 0) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes);
    }

    private static List<OffsetForLeaderEpochRequest.TopicData> readTopics(short version, ByteBuffer buf) {
        int count = readVarint(version, buf);
        List<OffsetForLeaderEpochRequest.TopicData> topics = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = readString(version, buf);
            int partCount = readVarint(version, buf);
            List<OffsetForLeaderEpochRequest.PartitionData> parts = new ArrayList<>(partCount < 0 ? 0 : partCount);
            for (int j = 0; j < partCount; j++) {
                int partition = buf.getInt();
                int leaderEpoch = buf.getInt();
                parts.add(new OffsetForLeaderEpochRequest.PartitionData(partition, leaderEpoch));
            }
            if (version >= 4) {
                readEndTags(buf);
            }
            topics.add(new OffsetForLeaderEpochRequest.TopicData(topic, parts));
        }
        if (version >= 4) {
            readEndTags(buf);
        }
        return topics;
    }

    private static List<OffsetForLeaderEpochResponse.TopicData> readResponseTopics(short version, ByteBuffer buf) {
        int count = readVarint(version, buf);
        List<OffsetForLeaderEpochResponse.TopicData> topics = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = readString(version, buf);
            int count2 = readVarint(version, buf);
            List<OffsetForLeaderEpochResponse.EpochEndOffset> offsets = new ArrayList<>(count2 < 0 ? 0 : count2);
            for (int j = 0; j < count2; j++) {
                int partition = buf.getInt();
                long endOffset = buf.getLong();
                short errorCode = buf.getShort();
                offsets.add(new OffsetForLeaderEpochResponse.EpochEndOffset(partition, endOffset, errorCode));
            }
            if (version >= 4) {
                readEndTags(buf);
            }
            topics.add(new OffsetForLeaderEpochResponse.TopicData(topic, offsets));
        }
        if (version >= 4) {
            readEndTags(buf);
        }
        return topics;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void writeString(short version, ByteBuffer buf, String s) {
        if (version >= 4) {
            if (s == null) {
                writeVarint(0, buf);
                return;
            }
            byte[] bytes = s.getBytes();
            writeVarint(bytes.length + 1, buf);
            buf.put(bytes);
            return;
        }
        if (s == null) {
            buf.putShort((short) -1);
            return;
        }
        byte[] bytes = s.getBytes();
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    private static void writeIntList(short version, ByteBuffer buf, int count) {
        if (version >= 4) {
            writeVarint(count, buf);
        } else {
            buf.putInt(count);
        }
    }

    private static int readVarint(short version, ByteBuffer buf) {
        if (version >= 4) {
            return readVarint(buf);
        }
        return buf.getInt();
    }

    private static int readVarint(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 35);
        return result;
    }

    private static void writeVarint(int value, ByteBuffer buf) {
        while ((value & 0xFFFFFF80) != 0) {
            buf.put((byte) (value & 0x7F | 0x80));
            value >>>= 7;
        }
        buf.put((byte) (value & 0x7F));
    }

    private static void writeEndTags(ByteBuffer buf, int count) {
        for (int i = 0; i < count; i++) {
            buf.put((byte) 0);
        }
    }

    private static void readEndTags(ByteBuffer buf) {
        buf.get(); // skip endTag (varint 0)
    }

    /**
     * Convert ByteBuffer to byte[].
     */
    static byte[] toBytes(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }
}