package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ApiVersionsRequest;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * ApiVersions codec (API key 18) — the version-negotiation entry point of the protocol.
 *
 * <p>Phase 6a execution order step 2 (Negotiation/Auth). Versions implemented one sub-task
 * at a time against the spec in
 * {@code messaging/kafka/doc/spec/message/ApiVersions{Request,Response}.json}
 * (Kafka 3.6.1: request v0–v3, flexible 3+; response v0–v3, flexible 3+).
 *
 * <ul>
 *   <li>v0 — request: no body; response: errorCode + []{apiKey,min,max}</li>
 *   <li>v1 — response adds {@code throttleTimeMs} (int32)</li>
 *   <li>v2 — unchanged vs v1 (sub-task pending)</li>
 *   <li>v3 — flexible encoding + request client name/version fields + response tagged fields
 *       (SupportedFeatures, FinalizedFeaturesEpoch, FinalizedFeatures, ZkMigrationReady)
 *       (sub-task pending)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class ApiVersionsCodec {

    /** Spec (3.6.1) highest version for API 18. */
    public static final short SPEC_MAX_VERSION = 3;

    private ApiVersionsCodec() {
    }

    /**
     * Encodes the ApiVersions request body for the given version.
     *
     * @param version the negotiated version
     * @param req     the request
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeRequest(short version, ApiVersionsRequest req) {
        switch (version) {
            case 0:
            case 1: // v1 request is byte-identical to v0 (no fields until v3 flexible)
                return encodeRequestV0(req);
            default:
                throw new CodecNotImplementedException("ApiVersions request v" + version + " not implemented");
        }
    }

    /**
     * Decodes the ApiVersions request body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded request
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static ApiVersionsRequest decodeRequest(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
            case 1:
                return decodeRequestV0(buf);
            default:
                throw new CodecNotImplementedException("ApiVersions request v" + version + " not implemented");
        }
    }

    /**
     * Encodes the ApiVersions response body for the given version.
     *
     * @param version the negotiated version
     * @param resp    the response
     * @return the encoded body bytes
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static byte[] encodeResponse(short version, ApiVersionsResponse resp) {
        switch (version) {
            case 0:
                return encodeResponseV0(resp);
            case 1:
                return encodeResponseV1(resp);
            default:
                throw new CodecNotImplementedException("ApiVersions response v" + version + " not implemented");
        }
    }

    /**
     * Decodes the ApiVersions response body.
     *
     * @param version the negotiated version
     * @param buf     the positioned body buffer
     * @return the decoded response
     * @throws CodecNotImplementedException if the version has no implemented code path
     */
    public static ApiVersionsResponse decodeResponse(short version, ByteBuffer buf) {
        switch (version) {
            case 0:
                return decodeResponseV0(buf);
            case 1:
                return decodeResponseV1(buf);
            default:
                throw new CodecNotImplementedException("ApiVersions response v" + version + " not implemented");
        }
    }

    // ===== v0 — layout: request (none); response: errorCode, []{apiKey,min,max} =====

    private static byte[] encodeRequestV0(ApiVersionsRequest req) {
        return new byte[0];
    }

    private static ApiVersionsRequest decodeRequestV0(ByteBuffer buf) {
        return new ApiVersionsRequest();
    }

    private static byte[] encodeResponseV0(ApiVersionsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + resp.apiKeys().size() * 6);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.apiKeys().size());
        for (var ak : resp.apiKeys()) {
            buf.putShort(ak.apiKey());
            buf.putShort(ak.minVersion());
            buf.putShort(ak.maxVersion());
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ApiVersionsResponse decodeResponseV0(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ApiVersionsResponse.ApiVersion> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(new ApiVersionsResponse.ApiVersion(buf.getShort(), buf.getShort(), buf.getShort()));
        }
        return new ApiVersionsResponse(errorCode, keys, 0L);
    }

    // ===== v1 — layout: response adds ThrottleTimeMs:int32 after the ApiKeys array =====
    // Request is byte-identical to v0 (shared above).

    private static byte[] encodeResponseV1(ApiVersionsResponse resp) {
        ByteBuffer buf = BufferPool.getBuffer(2 + 4 + resp.apiKeys().size() * 6 + 4);
        buf.putShort(resp.errorCode());
        buf.putInt(resp.apiKeys().size());
        for (var ak : resp.apiKeys()) {
            buf.putShort(ak.apiKey());
            buf.putShort(ak.minVersion());
            buf.putShort(ak.maxVersion());
        }
        buf.putInt((int) resp.throttleTimeMs());
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ApiVersionsResponse decodeResponseV1(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = buf.getInt();
        List<ApiVersionsResponse.ApiVersion> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(new ApiVersionsResponse.ApiVersion(buf.getShort(), buf.getShort(), buf.getShort()));
        }
        long throttleTimeMs = buf.getInt() & 0xFFFFFFFFL;
        return new ApiVersionsResponse(errorCode, keys, throttleTimeMs);
    }
}
