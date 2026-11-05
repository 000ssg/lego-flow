package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.messaging.kafka.protocol.ControlledShutdownRequest;
import ssg.legoflow.messaging.kafka.protocol.ControlledShutdownResponse;
import ssg.legoflow.service.util.BufferPool;

/**
 * ControlledShutdown codec (API key 7) — v0–v1.
 *
 * <p>Handles graceful broker shutdown requests and responses.
 *
 * @since 0.1.0
 */
public final class ControlledShutdownCodec {

    private static final Logger log = LoggerFactory.getLogger(ControlledShutdownCodec.class);

    /** Pinned version for tests. */
    public static final short PINNED_VERSION = 1;

    private ControlledShutdownCodec() {
    }

    // ── Request ──────────────────────────────────────────────────────────────

    /**
     * Encodes a ControlledShutdown request body.
     *
     * @param version the protocol version
     * @param req     the request
     * @return the encoded bytes
     */
    public static byte[] encodeRequest(short version, ControlledShutdownRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(estimateRequestSize(version, req));
        encodeRequestBody(version, req, buf);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ControlledShutdown request body.
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded request
     */
    public static ControlledShutdownRequest decodeRequest(short version, ByteBuffer buf) {
        ControlledShutdownRequest req = decodeRequestBody(version, buf);
        if (log.isDebugEnabled()) {
            log.debug("Decoded ControlledShutdown request v{}: brokerId={}",
                    version, req.brokerId());
        }
        return req;
    }

    private static int estimateRequestSize(short version, ControlledShutdownRequest req) {
        return 4; // brokerId
    }

    private static void encodeRequestBody(short version, ControlledShutdownRequest req, ByteBuffer buf) {
        buf.putInt(req.brokerId());
    }

    private static ControlledShutdownRequest decodeRequestBody(short version, ByteBuffer buf) {
        int brokerId = buf.getInt();
        return new ControlledShutdownRequest(brokerId);
    }

    // ── Response ─────────────────────────────────────────────────────────────

    /**
     * Encodes a ControlledShutdown response body.
     *
     * @param version the protocol version
     * @param resp    the response
     * @return the encoded bytes
     */
    public static byte[] encodeResponse(short version, ControlledShutdownResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(estimateResponseSize(version, resp));
        encodeResponseBody(version, resp, buf);
        buf.flip();
        return toBytes(buf);
    }

    /**
     * Decodes a ControlledShutdown response body.
     *
     * @param version the protocol version
     * @param buf     the buffer
     * @return the decoded response
     */
    public static ControlledShutdownResponse decodeResponse(short version, ByteBuffer buf) {
        ControlledShutdownResponse resp = decodeResponseBody(version, buf);
        if (log.isDebugEnabled()) {
            log.debug("Decoded ControlledShutdown response v{}: errorCode={}",
                    version, resp.errorCode());
        }
        return resp;
    }

    private static int estimateResponseSize(short version, ControlledShutdownResponse resp) {
        int size = 2; // errorCode
        size += 4; // partitionsRemaining count
        for (var p : resp.partitionsRemaining()) {
            size += 2 + p.topic().getBytes().length; // topic (varlen)
            size += 4; // partition
        }
        return size;
    }

    private static void encodeResponseBody(short version, ControlledShutdownResponse resp, ByteBuffer buf) {
        buf.putShort(resp.errorCode());
        List<ControlledShutdownResponse.TopicPartitionData> partitions = resp.partitionsRemaining();
        buf.putInt(partitions.size());
        for (var p : partitions) {
            writeString(buf, p.topic());
            buf.putInt(p.partition());
        }
    }

    private static ControlledShutdownResponse decodeResponseBody(short version, ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ControlledShutdownResponse.TopicPartitionData> partitions = new ArrayList<>(count < 0 ? 0 : count);
        for (int i = 0; i < count; i++) {
            String topic = readString(buf);
            int partition = buf.getInt();
            partitions.add(new ControlledShutdownResponse.TopicPartitionData(topic, partition));
        }
        return new ControlledShutdownResponse(errorCode, partitions);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void writeString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes();
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    private static String readString(ByteBuffer buf) {
        int len = buf.getShort();
        if (len < 0) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes);
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