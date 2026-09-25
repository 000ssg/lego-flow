package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;

/**
 * SaslAuthenticate codec (API key 36) — the SASL token exchange.
 *
 * <p>Phase 6a execution order step 2 (Negotiation/Auth). Versions implemented one sub-task
 * at a time against the spec in
 * {@code messaging/kafka/doc/spec/message/SaslAuthenticate{Request,Response}.json}
 * (Kafka 3.6.1: request v0–v2, response v0–v2; flexible encoding from v2).
 *
 * <ul>
 *   <li>v0 — request: authBytes (int32-len); response: errorCode + errorMessage + authBytes</li>
 *   <li>v1 — response adds {@code sessionLifetimeMs} (int64)</li>
 *   <li>v2 — flexible encoding: same fields as v1, length fields become varints
 *       (compact nullable string for errorMessage, compact bytes for authBytes);
 *       fixed-width integers unchanged; the request header carries the flexible
 *       bit (apiKey | 0x8000) — encoded at frame level in {@code KafkaCodec}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class SaslAuthenticateCodec {

    /** Spec (3.6.1) highest version for API 36. */
    public static final short SPEC_MAX_VERSION = 2;

    private SaslAuthenticateCodec() {
    }

    /**
     * Encodes the SaslAuthenticate request body for the given version.
     *
     * @param version the negotiated version
     * @param req     the request
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeRequest(short version, SaslAuthenticateRequest req) {
        switch (version) {
            case 0:
            case 1: // v1 request is byte-identical to v0
                return encodeRequestV0(req);
            case 2: // v2: flexible encoding — same fields, compact (varint) framing
                return encodeRequestV2(req);
            default:
                throw new CodecNotImplementedException("SaslAuthenticate request v" + version + " not implemented");
        }
    }

    /**
     * Decodes the SaslAuthenticate request body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded request
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static SaslAuthenticateRequest decodeRequest(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
            case 1:
                return decodeRequestV0(buf);
            case 2:
                return decodeRequestV2(buf);
            default:
                throw new CodecNotImplementedException("SaslAuthenticate request v" + version + " not implemented");
        }
    }

    /**
     * Encodes the SaslAuthenticate response body for the given version.
     *
     * @param version the negotiated version
     * @param resp    the response
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeResponse(short version, SaslAuthenticateResponse resp) {
        switch (version) {
            case 0:
                return encodeResponseV0(resp);
            case 1:
                return encodeResponseV1(resp);
            case 2: // v2: flexible encoding — same fields as v1, compact framing
                return encodeResponseV2(resp);
            default:
                throw new CodecNotImplementedException("SaslAuthenticate response v" + version + " not implemented");
        }
    }

    /**
     * Decodes the SaslAuthenticate response body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded response
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static SaslAuthenticateResponse decodeResponse(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeResponseV0(buf);
            case 1:
                return decodeResponseV1(buf);
            case 2:
                return decodeResponseV2(buf);
            default:
                throw new CodecNotImplementedException("SaslAuthenticate response v" + version + " not implemented");
        }
    }

    // ===== v0 — layout: request: authBytes; response: errorCode, errorMessage, authBytes =====

    private static byte[] encodeRequestV0(SaslAuthenticateRequest req) {
        byte[] authBytes = req.authBytes() != null ? req.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(4 + authBytes.length);
        KafkaCodecPrimitives.writeBytesField(buf, authBytes);
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslAuthenticateRequest decodeRequestV0(ByteBuffer buf) {
        return new SaslAuthenticateRequest(KafkaCodecPrimitives.readBytesField(buf));
    }

    private static byte[] encodeResponseV0(SaslAuthenticateResponse resp) {
        byte[] authBytes = resp.authBytes() != null ? resp.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + authBytes.length + 16);
        buf.putShort(resp.errorCode());
        KafkaCodecPrimitives.writeString(buf, resp.errorMessage());
        KafkaCodecPrimitives.writeBytesField(buf, authBytes);
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslAuthenticateResponse decodeResponseV0(ByteBuffer buf) {
        short errorCode = buf.getShort();
        String errorMessage = KafkaCodecPrimitives.readString(buf);
        byte[] authBytes = KafkaCodecPrimitives.readBytesField(buf);
        return new SaslAuthenticateResponse(errorCode, errorMessage, authBytes, 0);
    }

    // ===== v1 — layout: response adds SessionLifetimeMs:int64 after authBytes =====
    // Request v1 is byte-identical to v0 (shared above).

    private static byte[] encodeResponseV1(SaslAuthenticateResponse resp) {
        byte[] authBytes = resp.authBytes() != null ? resp.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + authBytes.length + 8);
        buf.putShort(resp.errorCode());
        KafkaCodecPrimitives.writeString(buf, resp.errorMessage());
        KafkaCodecPrimitives.writeBytesField(buf, authBytes);
        buf.putLong(resp.sessionLifetimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslAuthenticateResponse decodeResponseV1(ByteBuffer buf) {
        short errorCode = buf.getShort();
        String errorMessage = KafkaCodecPrimitives.readString(buf);
        byte[] authBytes = KafkaCodecPrimitives.readBytesField(buf);
        long sessionLifetimeMs = buf.getLong();
        return new SaslAuthenticateResponse(errorCode, errorMessage, authBytes, sessionLifetimeMs);
    }

    // ===== v2 — flexible encoding: same fields as v1, length fields become varints.
    // Request: AuthBytes bytes (non-nullable → varint(len+1)).
    // Response: ErrorCode int16 (fixed) + ErrorMessage nullable string (compact:
    // varint 0 = null) + AuthBytes bytes (compact) + SessionLifetimeMs int64 (fixed).
    // No tagged fields in the 3.6.1 schema (v2 has no 3+-tagged fields).

    private static byte[] encodeRequestV2(SaslAuthenticateRequest req) {
        // v2 request is nullable bytes: null = varint 1 (distinct from empty = varint 1+0).
        ByteBuffer buf = BufferPool.getBuffer(5 + (req.authBytes() != null ? req.authBytes().length : 0));
        KafkaCodecPrimitives.writeCompactBytes(buf, req.authBytes());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslAuthenticateRequest decodeRequestV2(ByteBuffer buf) {
        return new SaslAuthenticateRequest(KafkaCodecPrimitives.readCompactBytes(buf));
    }

    private static byte[] encodeResponseV2(SaslAuthenticateResponse resp) {
        byte[] authBytes = resp.authBytes() != null ? resp.authBytes() : new byte[0];
        ByteBuffer buf = BufferPool.getBuffer(2 + 8 + 8 + authBytes.length);
        buf.putShort(resp.errorCode());
        KafkaCodecPrimitives.writeCompactString(buf, resp.errorMessage());
        KafkaCodecPrimitives.writeCompactBytes(buf, authBytes);
        buf.putLong(resp.sessionLifetimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static SaslAuthenticateResponse decodeResponseV2(ByteBuffer buf) {
        short errorCode = buf.getShort();
        String errorMessage = KafkaCodecPrimitives.readCompactString(buf);
        byte[] authBytes = KafkaCodecPrimitives.readCompactBytes(buf);
        long sessionLifetimeMs = buf.getLong();
        return new SaslAuthenticateResponse(errorCode, errorMessage, authBytes, sessionLifetimeMs);
    }
}
