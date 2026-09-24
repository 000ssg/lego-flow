package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.ApiVersionsRequest;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
 *   <li>v2 — unchanged vs v1 (byte-identical request and response in the 3.6.1 schema;
 *       v2 only changes throttle semantics — brokers may send responses before throttling
 *       on quota violation)</li>
 *   <li>v3 — flexible encoding: request gains ClientSoftwareName/ClientSoftwareVersion
 *       (non-nullable compact strings); response array becomes a compact array with a
 *       tagged-fields count varint per element (after each element's fields), and gains
 *       the trailing tagged-fields section (SupportedFeatures tag 0, FinalizedFeaturesEpoch
 *       tag 1, FinalizedFeatures tag 2, ZkMigrationReady tag 3) — the section opens with a
 *       count varint and absent feature fields are omitted from the wire, matching
 *       the 3.6.1 generated source (Kafka 3.6.1 {@code ApiVersionsResponseData.writeTo})</li>
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
            case 2: // v2 request is byte-identical to v1 (no fields until v3 flexible)
                return encodeRequestV0(req);
            case 3: // v3: flexible encoding + client name/version fields
                return encodeRequestV3(req);
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
            case 2: // v2 request byte-identical to v1
                return decodeRequestV0(buf);
            case 3: // v3: flexible encoding
                return decodeRequestV3(buf);
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
            case 2: // v2 response byte-identical to v1 (no fields until v3 flexible)
                return encodeResponseV1(resp);
            case 3: // v3: flexible encoding + tagged feature fields
                return encodeResponseV3(resp);
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
            case 2: // v2 response byte-identical to v1
                return decodeResponseV1(buf);
            case 3: // v3: flexible encoding
                return decodeResponseV3(buf);
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

    // ===== v3 — flexible encoding (Kafka 3.6.1 generated sources) =====
    // Request: ClientSoftwareName + ClientSoftwareVersion as non-nullable compact
    // strings, followed by an empty tagged-field section (count varint 0).
    // Response: ErrorCode:int16, ApiKeys as a compact array of
    // {apiKey, minVersion, maxVersion, per-element tagged trailer (count varint 0)},
    // ThrottleTimeMs:int32 (fixed), then the tagged-field section with a LEADING
    // count varint (no trailing sentinel): tag 0 = SupportedFeatures
    // (implicit list: varint(n+1) header + elements {name compact string,
    // minVersion:int16, maxVersion:int16, element trailer varint 0}),
    // tag 1 = FinalizedFeaturesEpoch:int64, tag 2 = FinalizedFeatures (implicit
    // list: elements {name, maxVersionLevel:int16, minVersionLevel:int16, trailer}
    // — wire order is max before min per the 3.6.1 schema), tag 3 =
    // ZkMigrationReady:bool (1 byte).

    private static byte[] encodeRequestV3(ApiVersionsRequest req) {
        String name = req.clientSoftwareName() == null ? "" : req.clientSoftwareName();
        String version = req.clientSoftwareVersion() == null ? "" : req.clientSoftwareVersion();
        // upper bound: compact prefix is at most 9 bytes per string for our lengths
        int cap = 9 + name.getBytes(StandardCharsets.UTF_8).length
                + 9 + version.getBytes(StandardCharsets.UTF_8).length + 1;
        ByteBuffer buf = BufferPool.getBuffer(cap);
        KafkaCodecPrimitives.writeCompactStringNonNullable(buf, name);
        KafkaCodecPrimitives.writeCompactStringNonNullable(buf, version);
        KafkaCodecPrimitives.writeVarint(buf, 0); // empty tagged section
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ApiVersionsRequest decodeRequestV3(ByteBuffer buf) {
        String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
        String version = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
        KafkaCodecPrimitives.skipTaggedFields(buf); // request has no tagged fields; consume the count
        return new ApiVersionsRequest(name.isEmpty() ? null : name, version.isEmpty() ? null : version);
    }

    private static byte[] encodeResponseV3(ApiVersionsResponse resp) {
        int cap = 2 + 9 + 4 + 1; // errorCode + array marker + throttle + section count, baseline
        for (var ak : resp.apiKeys()) {
            cap += 3 * 2 + 1; // 3 int16 + per-element tagged count
        }
        // per-element: compact prefix (≤9) + name + 2 int16 + element count varint; +2 slack per element
        cap += resp.supportedFeatures().stream().mapToInt(f -> 9 + f.name().getBytes(StandardCharsets.UTF_8).length + 4 + 1 + 2).sum() + 2;
        cap += resp.finalizedFeatures().stream().mapToInt(f -> 9 + f.name().getBytes(StandardCharsets.UTF_8).length + 4 + 1 + 2).sum() + 2;
        cap += 2 + 8; // epochs tag + flag slack
        ByteBuffer buf = BufferPool.getBuffer(cap);
        buf.putShort(resp.errorCode());

        // ApiKeys: compact array, each element has its own (empty) tagged trailer
        KafkaCodecPrimitives.writeVarint(buf, resp.apiKeys().size() + 1);
        for (var ak : resp.apiKeys()) {
            buf.putShort(ak.apiKey());
            buf.putShort(ak.minVersion());
            buf.putShort(ak.maxVersion());
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-element tagged count
        }

        buf.putInt((int) resp.throttleTimeMs());

        // Tagged-field section: LEADING count varint, then the present fields in
        // tag order (0, 1, 2, 3); no trailing sentinel (3.6.1 generated write).
        int taggedCount = 0;
        if (!resp.supportedFeatures().isEmpty()) {
            taggedCount++;
        }
        if (resp.finalizedFeaturesEpoch() != ApiVersionsResponse.ABSENT_FINALIZED_EPOCH) {
            taggedCount++;
        }
        if (!resp.finalizedFeatures().isEmpty()) {
            taggedCount++;
        }
        if (resp.zkMigrationReady()) {
            taggedCount++;
        }
        KafkaCodecPrimitives.writeVarint(buf, taggedCount);

        if (!resp.supportedFeatures().isEmpty()) {
            KafkaCodecPrimitives.writeVarint(buf, 0); // tag 0 = SupportedFeatures
            // exact payload size = varint(n+1) header + per-element
            // {compact prefix varint + name bytes + 2 int16 + element count varint(0)}
            int size = KafkaCodecPrimitives.varintSize(resp.supportedFeatures().size() + 1)
                    + resp.supportedFeatures().stream()
                    .mapToInt(f -> {
                        int nameLen = f.name().getBytes(StandardCharsets.UTF_8).length;
                        return KafkaCodecPrimitives.varintSize(nameLen + 1) + nameLen + 4 + 1;
                    }).sum();
            KafkaCodecPrimitives.writeVarint(buf, size);
            KafkaCodecPrimitives.writeVarint(buf, resp.supportedFeatures().size() + 1);
            for (var f : resp.supportedFeatures()) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, f.name());
                buf.putShort(f.minVersion());
                buf.putShort(f.maxVersion());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-element tagged count
            }
        }
        if (resp.finalizedFeaturesEpoch() != ApiVersionsResponse.ABSENT_FINALIZED_EPOCH) {
            KafkaCodecPrimitives.writeVarint(buf, 1); // tag 1 = FinalizedFeaturesEpoch
            KafkaCodecPrimitives.writeVarint(buf, 8); // size
            buf.putLong(resp.finalizedFeaturesEpoch());
        }
        if (!resp.finalizedFeatures().isEmpty()) {
            KafkaCodecPrimitives.writeVarint(buf, 2); // tag 2 = FinalizedFeatures
            // exact payload size = varint(n+1) header + per-element
            // {compact prefix varint + name bytes + 2 int16 + element count varint(0)}
            int size = KafkaCodecPrimitives.varintSize(resp.finalizedFeatures().size() + 1)
                    + resp.finalizedFeatures().stream()
                    .mapToInt(f -> {
                        int nameLen = f.name().getBytes(StandardCharsets.UTF_8).length;
                        return KafkaCodecPrimitives.varintSize(nameLen + 1) + nameLen + 4 + 1;
                    }).sum();
            KafkaCodecPrimitives.writeVarint(buf, size);
            KafkaCodecPrimitives.writeVarint(buf, resp.finalizedFeatures().size() + 1);
            for (var f : resp.finalizedFeatures()) {
                KafkaCodecPrimitives.writeCompactStringNonNullable(buf, f.name());
                buf.putShort(f.maxVersionLevel()); // note: max before min in the spec
                buf.putShort(f.minVersionLevel());
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-element tagged count
            }
        }
        if (resp.zkMigrationReady()) {
            KafkaCodecPrimitives.writeVarint(buf, 3); // tag 3 = ZkMigrationReady
            KafkaCodecPrimitives.writeVarint(buf, 1); // size
            buf.put((byte) 1);
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static ApiVersionsResponse decodeResponseV3(ByteBuffer buf) {
        short errorCode = buf.getShort();
        int count = KafkaCodecPrimitives.readVarint(buf) - 1; // compact array
        List<ApiVersionsResponse.ApiVersion> keys = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            short apiKey = buf.getShort();
            short minVersion = buf.getShort();
            short maxVersion = buf.getShort();
            KafkaCodecPrimitives.skipTaggedFields(buf); // per-element tagged section
            keys.add(new ApiVersionsResponse.ApiVersion(apiKey, minVersion, maxVersion));
        }
        long throttleTimeMs = buf.getInt() & 0xFFFFFFFFL;

        List<ApiVersionsResponse.SupportedFeatureKey> supported = new ArrayList<>();
        List<ApiVersionsResponse.FinalizedFeatureKey> finalized = new ArrayList<>();
        long finalizedEpoch = ApiVersionsResponse.ABSENT_FINALIZED_EPOCH;
        boolean zkMigrationReady = false;

        // Tagged section: leading count varint, then count fields.
        int taggedCount = KafkaCodecPrimitives.readVarint(buf);
        for (int i = 0; i < taggedCount; i++) {
            int tag = KafkaCodecPrimitives.readVarint(buf);
            int size = KafkaCodecPrimitives.readVarint(buf);
            int end = buf.position() + size;
            switch (tag) {
                case 0: {
                    int n = KafkaCodecPrimitives.readVarint(buf) - 1;
                    for (int j = 0; j < n; j++) {
                        String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
                        short minV = buf.getShort();
                        short maxV = buf.getShort();
                        KafkaCodecPrimitives.skipTaggedFields(buf); // per-element
                        supported.add(new ApiVersionsResponse.SupportedFeatureKey(name, minV, maxV));
                    }
                    break;
                }
                case 1: {
                    finalizedEpoch = buf.getLong();
                    break;
                }
                case 2: {
                    int n = KafkaCodecPrimitives.readVarint(buf) - 1;
                    for (int j = 0; j < n; j++) {
                        String name = KafkaCodecPrimitives.readCompactStringNonNullable(buf);
                        short maxV = buf.getShort(); // wire order: max before min
                        short minV = buf.getShort();
                        KafkaCodecPrimitives.skipTaggedFields(buf); // per-element
                        finalized.add(new ApiVersionsResponse.FinalizedFeatureKey(name, maxV, minV));
                    }
                    break;
                }
                case 3: {
                    zkMigrationReady = buf.get() != 0;
                    break;
                }
                default:
                    buf.position(end); // unknown tag — skip payload
                    continue;
            }
            buf.position(end); // advance to the exact tag boundary (consumed less than size on partial reads)
        }

        return new ApiVersionsResponse(errorCode, keys, throttleTimeMs, supported, finalizedEpoch, finalized, zkMigrationReady);
    }
}
