package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ssg.legoflow.messaging.kafka.protocol.OffsetDeleteRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetDeleteResponse;
import ssg.legoflow.service.util.BufferPool;

/**
 * Dedicated version-aware codec for the OffsetDelete API (API key 47).
 *
 * <p><b>Spec:</b> {@code doc/spec/message/OffsetDeleteRequest.json} /
 * {@code OffsetDeleteResponse.json}.
 * <ul>
 *   <li><b>Both:</b> {@code validVersions: "0"}, {@code flexibleVersions: "none"} — a single
 *       fixed-width v0 wire format; no flexible/tagged-field encoding.</li>
 *   <li><b>Request:</b> {@code GroupId(string)} + {@code Topics[]}
 *       ({@code Name(string)} + {@code Partitions[]} of {@code PartitionIndex(int32)}).</li>
 *   <li><b>Response:</b> {@code ErrorCode(int16)} + {@code ThrottleTimeMs(int32, ignorable)}
 *       + {@code Topics[]} ({@code Name(string)} + {@code Partitions[]} of
 *       {@code PartitionIndex(int32)} + {@code ErrorCode(int16)}).</li>
 * </ul>
 *
 * <p><b>Models unchanged:</b> the in-house {@code OffsetDeleteResponse} model exposes only
 * {@code errorCode} + {@code topics}; it has no {@code ThrottleTimeMs} field. Per the
 * "models unchanged if possible" rule the codec writes {@code ThrottleTimeMs} as 0 on encode
 * and reads + discards it on decode.
 */
public final class OffsetDeleteCodec {

    /** Pinned version for client/broker OffsetDelete interaction (v0). */
    public static final short MIN_VERSION = 0;

    /** Highest API version with a defined wire format in the spec (v0). */
    public static final short MAX_VERSION = 0;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    private OffsetDeleteCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "OffsetDelete v" + version + " not implemented (range 0)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────
    // Order: GroupId(string), Topics array count, [ Name(string), Partitions count,
    //        [ PartitionIndex(int32) ] ]

    /**
     * Encode an OffsetDelete request body at the given API version.
     *
     * @param version the API version (0)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, OffsetDeleteRequest req) {
        checkVersion(version);
        int size = 2 + req.groupId().getBytes(StandardCharsets.UTF_8).length; // GroupId
        size += 4; // Topics count
        for (var t : req.topics()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // Partitions count
            size += 4 * t.partitions().size(); // PartitionIndex each (int32)
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeString(buf, req.groupId());
        buf.putInt(req.topics().size());
        for (var t : req.topics()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    /**
     * Decode an OffsetDelete request body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the request body
     * @return the decoded request
     */
    public static OffsetDeleteRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        String groupId = KafkaCodecPrimitives.readString(buf);
        int topicCount = buf.getInt();
        List<OffsetDeleteRequest.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<OffsetDeleteRequest.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetDeleteRequest.PartitionData(buf.getInt()));
            }
            topics.add(new OffsetDeleteRequest.TopicData(name, parts));
        }
        return new OffsetDeleteRequest(groupId, topics);
    }

    // ── Response ────────────────────────────────────────────────────────
    // Order: ErrorCode(int16), ThrottleTimeMs(int32), Topics array count,
    //        [ Name(string), Partitions count, [ PartitionIndex(int32), ErrorCode(int16) ] ]

    /**
     * Encode an OffsetDelete response body at the given API version.
     *
     * @param version the API version (0)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, OffsetDeleteResponse resp) {
        checkVersion(version);
        int size = 2; // ErrorCode
        size += 4; // ThrottleTimeMs
        size += 4; // Topics count
        for (var t : resp.topics()) {
            size += 2 + t.name().getBytes(StandardCharsets.UTF_8).length; // Name
            size += 4; // Partitions count
            size += (4 + 2) * t.partitions().size(); // PartitionIndex(int32) + ErrorCode(int16) each
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putShort(resp.errorCode());
        buf.putInt(DEFAULT_THROTTLE_TIME_MS); // ThrottleTimeMs — spec default 0
        buf.putInt(resp.topics().size());
        for (var t : resp.topics()) {
            KafkaCodecPrimitives.writeString(buf, t.name());
            buf.putInt(t.partitions().size());
            for (var p : t.partitions()) {
                buf.putInt(p.partitionIndex());
                buf.putShort(p.errorCode());
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    /**
     * Decode an OffsetDelete response body at the given API version.
     *
     * @param version the API version (0)
     * @param buf     the response body
     * @return the decoded response
     */
    public static OffsetDeleteResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        short errorCode = buf.getShort();
        buf.getInt(); // ThrottleTimeMs — read + discarded (model does not expose it)
        int topicCount = buf.getInt();
        List<OffsetDeleteResponse.TopicData> topics = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            String name = KafkaCodecPrimitives.readString(buf);
            int partCount = buf.getInt();
            List<OffsetDeleteResponse.PartitionData> parts = new ArrayList<>(partCount);
            for (int j = 0; j < partCount; j++) {
                parts.add(new OffsetDeleteResponse.PartitionData(buf.getInt(), buf.getShort()));
            }
            topics.add(new OffsetDeleteResponse.TopicData(name, parts));
        }
        return new OffsetDeleteResponse(errorCode, topics);
    }
}
