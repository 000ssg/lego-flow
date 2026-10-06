package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.AlterConfigsRequest;
import ssg.legoflow.messaging.kafka.protocol.AlterConfigsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka AlterConfigs request (API key 33) and response — alter the
 * configuration of resources (topics, brokers).
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v2 is encoded so a frame
 * carrying any AlterConfigs version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/AlterConfigsRequest.json} and
 * {@code AlterConfigsResponse.json}):
 * <ul>
 *   <li>v0 (request): baseline. {@code Resources[] { ResourceType(int8), ResourceName(string),
 *       Configs[] { Name(string), Value(nullable string) } }} + trailing {@code ValidateOnly(bool)}.</li>
 *   <li>v1 (request): byte-identical to v0 (spec: "Version 1 is the same as version 0").</li>
 *   <li>v2 (request): flexible encoding (KIP-482): compact arrays/strings + tagged fields on
 *       every struct level (config, resource, top).</li>
 *   <li>v0 (response): baseline. Leading {@code ThrottleTimeMs(int32)} +
 *       {@code Responses[] { ErrorCode(int16), ErrorMessage(nullable string),
 *       ResourceType(int8), ResourceName(string) }}.</li>
 *   <li>v1 (response): byte-identical to v0 (quota-violation responses before throttling;
 *       wire layout unchanged).</li>
 *   <li>v2 (response): flexible encoding (KIP-482): compact arrays/strings + tagged fields on
 *       every struct level (resource, top).</li>
 * </ul>
 * Response fields not exposed by the in-house model ({@code ThrottleTimeMs},
 * {@code ErrorMessage}, {@code ResourceType}) are written with spec defaults and read +
 * discarded.
 *
 * @since 0.1.0
 */
public final class AlterConfigsCodec {

    /** Pinned version for client/broker AlterConfigs interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v2+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 2;

    /** Highest API version with a defined wire format in the spec (v2). */
    private static final short MAX_VERSION = 2;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    /** Response {@code ResourceType} default — 2 (TOPIC; model does not expose it). */
    private static final byte DEFAULT_RESOURCE_TYPE = 2;

    private AlterConfigsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "AlterConfigs v" + version + " not implemented (range 0–2)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode an {@link AlterConfigsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(AlterConfigsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode an AlterConfigs request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, AlterConfigsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(req);
        }
        return flexibleRequest(req);
    }

    /**
     * Decode an AlterConfigs request body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the request body
     * @return the decoded request
     */
    public static AlterConfigsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(buf);
        }
        return flexibleRequest(buf);
    }

    // ── Fixed-width request (v0–v1) ─────────────────────────────────────
    // Order: numResources(int32),
    //   [ ResourceType(int8), ResourceName(string),
    //     numConfigs(int32) [ Name(string), Value(nullable string) ] ],
    //   ValidateOnly(bool)

    private static byte[] fixedRequest(AlterConfigsRequest req) {
        int size = 4; // numResources
        for (AlterConfigsRequest.ResourceConfig r : req.resources()) {
            size += 1; // ResourceType
            size += 2 + r.resourceName().getBytes(StandardCharsets.UTF_8).length; // ResourceName
            size += 4; // numConfigs
            for (AlterConfigsRequest.ConfigEntry c : r.configs()) {
                size += 2 + c.name().getBytes(StandardCharsets.UTF_8).length; // Name
                size += 2; // Value length prefix (0 if null)
                if (c.value() != null) {
                    size += c.value().getBytes(StandardCharsets.UTF_8).length;
                }
            }
        }
        size += 1; // ValidateOnly

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.resources().size());
        for (AlterConfigsRequest.ResourceConfig r : req.resources()) {
            buf.put(r.resourceType());
            KafkaCodecPrimitives.writeString(buf, r.resourceName());
            buf.putInt(r.configs().size());
            for (AlterConfigsRequest.ConfigEntry c : r.configs()) {
                KafkaCodecPrimitives.writeString(buf, c.name());
                KafkaCodecPrimitives.writeNullableString(buf, c.value());
            }
        }
        buf.put((byte) (req.validateOnly() ? 1 : 0));
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterConfigsRequest fixedRequest(ByteBuffer b) {
        int count = b.getInt();
        List<AlterConfigsRequest.ResourceConfig> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte resourceType = b.get();
            String resourceName = KafkaCodecPrimitives.readString(b);
            int configCount = b.getInt();
            List<AlterConfigsRequest.ConfigEntry> configs = new ArrayList<>(configCount);
            for (int j = 0; j < configCount; j++) {
                configs.add(new AlterConfigsRequest.ConfigEntry(
                        KafkaCodecPrimitives.readString(b),
                        KafkaCodecPrimitives.readNullableString(b)));
            }
            resources.add(new AlterConfigsRequest.ResourceConfig(resourceType, resourceName, configs));
        }
        boolean validateOnly = b.get() == 1;
        return new AlterConfigsRequest(resources, validateOnly);
    }

    // ── Flexible request (KIP-482, v2) ──────────────────────────────────
    // Order: numResources(varint, KIP-482 N+1),
    //   [ ResourceType(int8), ResourceName(compact string),
    //     numConfigs(varint, N+1) [ Name(compact string),
    //       Value(nullable compact string; null = varint 0) ], endTags(config) ],
    //   endTags(resource), ValidateOnly(bool), endTags(top)

    private static byte[] flexibleRequest(AlterConfigsRequest req) {
        int n = req.resources().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numResources (KIP-482: N+1)
        for (AlterConfigsRequest.ResourceConfig r : req.resources()) {
            size += 1; // ResourceType
            size += compactStringSize(r.resourceName()); // ResourceName
            size += KafkaCodecPrimitives.varintSize(r.configs().size() + 1); // numConfigs (KIP-482: N+1)
            for (AlterConfigsRequest.ConfigEntry c : r.configs()) {
                size += compactStringSize(c.name()); // Name
                size += c.value() == null
                        ? KafkaCodecPrimitives.varintSize(0) // Value null (KIP-482: varint 0)
                        : compactStringSize(c.value()); // Value (nullable compact string)
                size += 1; // per-config endTags (0)
            }
            size += 1; // per-resource endTags (0)
        }
        size += 1; // ValidateOnly
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (AlterConfigsRequest.ResourceConfig r : req.resources()) {
            buf.put(r.resourceType());
            KafkaCodecPrimitives.writeCompactString(buf, r.resourceName());
            KafkaCodecPrimitives.writeVarint(buf, r.configs().size() + 1); // KIP-482: N+1
            for (AlterConfigsRequest.ConfigEntry c : r.configs()) {
                KafkaCodecPrimitives.writeCompactString(buf, c.name());
                writeCompactNullableString(buf, c.value()); // nullable compact (KIP-482)
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-config endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-resource endTags
        }
        buf.put((byte) (req.validateOnly() ? 1 : 0));
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterConfigsRequest flexibleRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<AlterConfigsRequest.ResourceConfig> resources = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            byte resourceType = b.get();
            String resourceName = KafkaCodecPrimitives.readCompactString(b);
            int configCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<AlterConfigsRequest.ConfigEntry> configs = new ArrayList<>(configCount);
            for (int j = 0; j < configCount; j++) {
                String name = KafkaCodecPrimitives.readCompactString(b);
                String value = readCompactNullable(b); // nullable compact (KIP-482: null = varint 0)
                configs.add(new AlterConfigsRequest.ConfigEntry(name, value));
                KafkaCodecPrimitives.skipTaggedFields(b); // per-config
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-resource
            resources.add(new AlterConfigsRequest.ResourceConfig(resourceType, resourceName, configs));
        }
        boolean validateOnly = b.get() == 1;
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new AlterConfigsRequest(resources, validateOnly);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode an {@link AlterConfigsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(AlterConfigsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode an AlterConfigs response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, AlterConfigsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode an AlterConfigs response body at the given API version.
     *
     * @param version the API version (0–2)
     * @param buf     the response body
     * @return the decoded response
     */
    public static AlterConfigsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v1) ────────────────────────────────────
    // Order: ThrottleTimeMs(int32), numResults(int32),
    //   [ ErrorCode(int16), ErrorMessage(nullable string), ResourceType(int8),
    //     ResourceName(string) ]

    private static byte[] fixedResponse(AlterConfigsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 4; // numResults
        for (AlterConfigsResponse.ResourceResponse r : resp.resources()) {
            size += 2; // ErrorCode
            size += 2; // ErrorMessage length prefix (0 if null)
            size += 1; // ResourceType
            size += 2 + r.resourceName().getBytes(StandardCharsets.UTF_8).length; // ResourceName
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putInt(resp.resources().size());
        for (AlterConfigsResponse.ResourceResponse r : resp.resources()) {
            buf.putShort(r.errorCode());
            KafkaCodecPrimitives.writeNullableString(buf, null); // ErrorMessage (spec default)
            buf.put(DEFAULT_RESOURCE_TYPE); // ResourceType (spec default)
            KafkaCodecPrimitives.writeString(buf, r.resourceName());
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterConfigsResponse fixedResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int count = b.getInt();
        List<AlterConfigsResponse.ResourceResponse> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            short errorCode = b.getShort();
            String errorMessage = KafkaCodecPrimitives.readNullableString(b); // nullable; discarded
            b.get(); // ResourceType, read + discarded (model does not expose it)
            String resourceName = KafkaCodecPrimitives.readString(b);
            resources.add(new AlterConfigsResponse.ResourceResponse(errorCode, resourceName));
        }
        return new AlterConfigsResponse(resources);
    }

    // ── Flexible response (KIP-482, v2) ─────────────────────────────────
    // Order: ThrottleTimeMs(int32), numResults(varint, KIP-482 N+1),
    //   [ ErrorCode(int16), ErrorMessage(nullable compact string; null = varint 0),
    //     ResourceType(int8), ResourceName(compact string), endTags(resource) ],
    //   endTags(top)

    private static byte[] flexibleResponse(AlterConfigsResponse resp) {
        int n = resp.resources().size();
        int size = 4; // ThrottleTimeMs
        size += KafkaCodecPrimitives.varintSize(n + 1); // numResults (KIP-482: N+1)
        for (AlterConfigsResponse.ResourceResponse r : resp.resources()) {
            size += 2; // ErrorCode
            size += KafkaCodecPrimitives.varintSize(0); // ErrorMessage null (KIP-482: varint 0)
            size += 1; // ResourceType
            size += compactStringSize(r.resourceName()); // ResourceName
            size += 1; // per-resource endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (AlterConfigsResponse.ResourceResponse r : resp.resources()) {
            buf.putShort(r.errorCode());
            KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage null (KIP-482)
            buf.put(DEFAULT_RESOURCE_TYPE); // ResourceType (spec default)
            KafkaCodecPrimitives.writeCompactString(buf, r.resourceName());
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-resource endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static AlterConfigsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<AlterConfigsResponse.ResourceResponse> resources = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            short errorCode = b.getShort();
            skipCompactNullable(b); // ErrorMessage, read + discarded
            b.get(); // ResourceType, read + discarded (model does not expose it)
            String resourceName = KafkaCodecPrimitives.readCompactString(b);
            KafkaCodecPrimitives.skipTaggedFields(b); // per-resource
            resources.add(new AlterConfigsResponse.ResourceResponse(errorCode, resourceName));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new AlterConfigsResponse(resources);
    }

    private static int compactStringSize(String s) {
        // KIP-482: length varint is length+1 (null-safe); the varint may span multiple
        // bytes, and the data is UTF-8 — size on the byte length, not the char count.
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }

    private static int compactNullableStringSize(String s) {
        if (s == null) {
            return 1; // varint 0
        }
        return compactStringSize(s);
    }

    private static void writeCompactNullableString(ByteBuffer buf, String s) {
        if (s == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0);
            return;
        }
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        KafkaCodecPrimitives.writeVarint(buf, len + 1);
        buf.put(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String readCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len == 0) {
            return null;
        }
        byte[] data = new byte[len - 1];
        b.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void skipCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len != 0) {
            b.position(b.position() + len - 1);
        }
    }
}
