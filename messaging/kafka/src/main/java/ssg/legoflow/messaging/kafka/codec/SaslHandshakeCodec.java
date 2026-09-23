package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * SaslHandshake codec (API key 17) — negotiates the SASL mechanism.
 *
 * <p>Phase 6a execution order step 2 (Negotiation/Auth). Versions implemented one sub-task
 * at a time against the spec in
 * {@code messaging/kafka/doc/spec/message/SaslHandshake{Request,Response}.json}
 * (Kafka 3.6.1: request v0–v1; response v0–v1; no flexible encoding).
 *
 * <ul>
 *   <li>v0 — request: mechanism; response: errorCode + []mechanism</li>
 *   <li>v1 — unchanged vs v0 in the 3.6.1 schema (both request and response are
 *       byte-identical); its code path lands as the v1 sub-task, per the matrix row</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class SaslHandshakeCodec {

    /** Spec (3.6.1) highest version for API 17. */
    public static final short SPEC_MAX_VERSION = 1;

    private SaslHandshakeCodec() {
    }

    /**
     * Encodes the SaslHandshake request body for the given version.
     *
     * @param version the negotiated version
     * @param req     the request
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeRequest(short version, SaslHandshakeRequest req) {
        switch (version) {
            case 0:
                return encodeRequestV0(req);
            default:
                throw new CodecNotImplementedException("SaslHandshake request v" + version + " not implemented");
        }
    }

    /**
     * Decodes the SaslHandshake request body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded request
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static SaslHandshakeRequest decodeRequest(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeRequestV0(buf);
            default:
                throw new CodecNotImplementedException("SaslHandshake request v" + version + " not implemented");
        }
    }

    /**
     * Encodes the SaslHandshake response body for the given version.
     *
     * @param version the negotiated version
     * @param resp    the response
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeResponse(short version, SaslHandshakeResponse resp) {
        switch (version) {
            case 0:
                return encodeResponseV0(resp);
            default:
                throw new CodecNotImplementedException("SaslHandshake response v" + version + " not implemented");
        }
    }

    /**
     * Decodes the SaslHandshake response body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded response
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static SaslHandshakeResponse decodeResponse(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeResponseV0(buf);
            default:
                throw new CodecNotImplementedException("SaslHandshake response v" + version + " not implemented");
        }
    }

    // ===== v0 — layout: request: mechanism; response: errorCode, []mechanism =====

    private static byte[] encodeRequestV0(SaslHandshakeRequest req) {
        ByteBuffer buf = BufferPool.getBuffer(256);
        KafkaCodecPrimitives.writeString(buf, req.mechanism());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslHandshakeRequest decodeRequestV0(ByteBuffer buf) {
        return new SaslHandshakeRequest(KafkaCodecPrimitives.readString(buf));
    }

    private static byte[] encodeResponseV0(SaslHandshakeResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(4096);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.mechanisms().size());
        for (String m : resp.mechanisms()) {
            KafkaCodecPrimitives.writeString(buf, m);
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslHandshakeResponse decodeResponseV0(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<String> mechanisms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            mechanisms.add(KafkaCodecPrimitives.readString(buf));
        }
        return new SaslHandshakeResponse(errorCode, mechanisms);
    }
}
