package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.DescribeConfigsRequest;
import ssg.legoflow.messaging.kafka.protocol.DescribeConfigsResponse;
import ssg.legoflow.service.util.BufferPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the Kafka DescribeConfigs request (API key 32) and response — describe the
 * configuration of resources (topics, brokers).
 * <p>
 * Client/broker interaction is pinned at {@link #PINNED_VERSION} (v0: the baseline that
 * {@code KafkaAdminClient} sends), while the full version range v0–v4 is encoded so a frame
 * carrying any DescribeConfigs version can be produced or parsed.
 * <p>
 * Version differences (mirrors {@code doc/spec/message/DescribeConfigsRequest.json} and
 * {@code DescribeConfigsResponse.json}):
 * <ul>
 *   <li>v0 (request): baseline. {@code Resources[] { ResourceType(int8), ResourceName(string),
 *       ConfigurationKeys([]string, nullable) }}.</li>
 *   <li>v1 (request): adds trailing {@code IncludeSynonyms(bool)}.</li>
 *   <li>v3 (request): adds trailing {@code IncludeDocumentation(bool)}.</li>
 *   <li>v4 (request): flexible encoding (KIP-482): compact arrays/strings + tagged fields on
 *       every struct level (resource, top).</li>
 *   <li>v0 (response): baseline. Leading {@code ThrottleTimeMs(int32)} +
 *       {@code Results[] { ErrorCode(int16), ErrorMessage(nullable string),
 *       ResourceType(int8), ResourceName(string),
 *       Configs[] { Name, Value(nullable), ReadOnly(bool), ConfigSource(int8),
 *       IsSensitive(bool) } }}.</li>
 *   <li>v1 (response): adds {@code Synonyms[] { Name, Value(nullable), Source(int8) }} to
 *       each config entry.</li>
 *   <li>v3 (response): adds {@code ConfigType(int8)} + {@code Documentation(nullable string)}
 *       to each config entry.</li>
 *   <li>v4 (response): flexible encoding (KIP-482): compact arrays/strings + tagged fields on
 *       every struct level (config, result, top).</li>
 * </ul>
 * Response fields not exposed by the in-house model ({@code ThrottleTimeMs},
 * {@code ErrorMessage}, {@code ResourceType}, {@code ConfigSource}, {@code Synonyms},
 * {@code ConfigType}, {@code Documentation}) are written with spec defaults and read +
 * discarded.
 *
 * @since 0.1.0
 */
public final class DescribeConfigsCodec {

    /** Pinned version for client/broker DescribeConfigs interaction (v0). */
    public static final short PINNED_VERSION = 0;

    /** Flexible-encoding versions (v4+): compact arrays/strings + tagged fields. */
    private static final short FLEXIBLE_START = 4;

    /** Highest API version with a defined wire format in the spec (v4). */
    private static final short MAX_VERSION = 4;

    /** {@code IncludeSynonyms} default — false (spec default). */
    private static final boolean DEFAULT_INCLUDE_SYNONYMS = false;

    /** {@code IncludeDocumentation} default — false (spec default). */
    private static final boolean DEFAULT_INCLUDE_DOCUMENTATION = false;

    /** Response {@code ThrottleTimeMs} default — 0 (no throttling). */
    private static final int DEFAULT_THROTTLE_TIME_MS = 0;

    /** Response {@code ResourceType} default — 2 (TOPIC; model does not expose it). */
    private static final byte DEFAULT_RESOURCE_TYPE = 2;

    /** Response {@code ConfigSource} default — 1 (DEFAULT_CONFIG; model does not expose it). */
    private static final byte DEFAULT_CONFIG_SOURCE = 1;

    private DescribeConfigsCodec() {
    }

    private static void checkVersion(short version) {
        if (version < 0 || version > MAX_VERSION) {
            throw new CodecNotImplementedException(
                    "DescribeConfigs v" + version + " not implemented (range 0–4)");
        }
    }

    // ── Request ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DescribeConfigsRequest} at the pinned version.
     *
     * @param req the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(DescribeConfigsRequest req) {
        return encodeRequest(PINNED_VERSION, req);
    }

    /**
     * Encode a DescribeConfigs request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param req     the request
     * @return encoded body bytes
     */
    public static byte[] encodeRequest(short version, DescribeConfigsRequest req) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(version, req);
        }
        return flexibleRequest(req);
    }

    /**
     * Decode a DescribeConfigs request body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the request body
     * @return the decoded request
     */
    public static DescribeConfigsRequest decodeRequest(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedRequest(version, buf);
        }
        return flexibleRequest(buf);
    }

    // ── Fixed-width request (v0–v3) ─────────────────────────────────────
    // Order: numResources(int32),
    //   [ ResourceType(int8), ResourceName(string), ConfigurationKeys(int32 count,
    //     nullable -1 = null) [ string ] ],
    //   IncludeSynonyms(bool) v1+, IncludeDocumentation(bool) v3+

    private static byte[] fixedRequest(short version, DescribeConfigsRequest req) {
        int size = 4; // numResources
        for (DescribeConfigsRequest.ResourceRequest r : req.resources()) {
            size += 1; // ResourceType
            size += 2 + r.resourceName().getBytes(StandardCharsets.UTF_8).length; // ResourceName
            size += 4; // ConfigurationKeys count (null = -1)
            if (r.configNames() != null) {
                for (String name : r.configNames()) {
                    size += 2 + name.getBytes(StandardCharsets.UTF_8).length;
                }
            }
        }
        if (version >= 1) {
            size += 1; // IncludeSynonyms
        }
        if (version >= 3) {
            size += 1; // IncludeDocumentation
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(req.resources().size());
        for (DescribeConfigsRequest.ResourceRequest r : req.resources()) {
            buf.put(r.resourceType());
            KafkaCodecPrimitives.writeString(buf, r.resourceName());
            if (r.configNames() == null) {
                buf.putInt(-1); // ConfigurationKeys null
            } else {
                buf.putInt(r.configNames().size());
                for (String name : r.configNames()) {
                    KafkaCodecPrimitives.writeString(buf, name);
                }
            }
        }
        if (version >= 1) {
            buf.put((byte) (DEFAULT_INCLUDE_SYNONYMS ? 1 : 0));
        }
        if (version >= 3) {
            buf.put((byte) (DEFAULT_INCLUDE_DOCUMENTATION ? 1 : 0));
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DescribeConfigsRequest fixedRequest(short version, ByteBuffer b) {
        int count = b.getInt();
        List<DescribeConfigsRequest.ResourceRequest> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte resourceType = b.get();
            String resourceName = KafkaCodecPrimitives.readString(b);
            int nameCount = b.getInt();
            List<String> configNames = null;
            if (nameCount >= 0) {
                configNames = new ArrayList<>(nameCount);
                for (int j = 0; j < nameCount; j++) {
                    configNames.add(KafkaCodecPrimitives.readString(b));
                }
            }
            resources.add(new DescribeConfigsRequest.ResourceRequest(resourceType, resourceName, configNames));
        }
        if (version >= 1) {
            b.get(); // IncludeSynonyms, read + discarded (model does not expose it)
        }
        if (version >= 3) {
            b.get(); // IncludeDocumentation, read + discarded (model does not expose it)
        }
        return new DescribeConfigsRequest(resources);
    }

    // ── Flexible request (v4) ───────────────────────────────────────────
    // Order: numResources(varint, KIP-482 N+1),
    //   [ ResourceType(int8), ResourceName(compact string),
    //     ConfigurationKeys(varint, N+1; null = 0) [ compact string ], endTags ],
    //   IncludeSynonyms(bool) v1+, IncludeDocumentation(bool) v3+, endTags

    private static byte[] flexibleRequest(DescribeConfigsRequest req) {
        int n = req.resources().size();
        int size = KafkaCodecPrimitives.varintSize(n + 1); // numResources (KIP-482: N+1)
        for (DescribeConfigsRequest.ResourceRequest r : req.resources()) {
            size += 1; // ResourceType
            size += compactStringSize(r.resourceName()); // ResourceName
            size += KafkaCodecPrimitives.varintSize(r.configNames() == null ? 0 : r.configNames().size() + 1); // ConfigurationKeys
            if (r.configNames() != null) {
                for (String name : r.configNames()) {
                    size += compactStringSize(name);
                }
            }
            size += 1; // per-resource endTags (0)
        }
        size += 1; // IncludeSynonyms (v1+)
        size += 1; // IncludeDocumentation (v3+)
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DescribeConfigsRequest.ResourceRequest r : req.resources()) {
            buf.put(r.resourceType());
            KafkaCodecPrimitives.writeCompactString(buf, r.resourceName());
            if (r.configNames() == null) {
                KafkaCodecPrimitives.writeVarint(buf, 0); // ConfigurationKeys null
            } else {
                KafkaCodecPrimitives.writeVarint(buf, r.configNames().size() + 1); // KIP-482: N+1
                for (String name : r.configNames()) {
                    KafkaCodecPrimitives.writeCompactString(buf, name);
                }
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-resource endTags
        }
        buf.put((byte) (DEFAULT_INCLUDE_SYNONYMS ? 1 : 0)); // IncludeSynonyms (v1+)
        buf.put((byte) (DEFAULT_INCLUDE_DOCUMENTATION ? 1 : 0)); // IncludeDocumentation (v3+)
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DescribeConfigsRequest flexibleRequest(ByteBuffer b) {
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DescribeConfigsRequest.ResourceRequest> resources = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            byte resourceType = b.get();
            String resourceName = KafkaCodecPrimitives.readCompactString(b);
            int nameCount = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
            List<String> configNames = null;
            if (nameCount != 0) {
                int count = nameCount - 1;
                configNames = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    configNames.add(KafkaCodecPrimitives.readCompactString(b));
                }
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-resource
            resources.add(new DescribeConfigsRequest.ResourceRequest(resourceType, resourceName, configNames));
        }
        b.get(); // IncludeSynonyms, read + discarded (model does not expose it)
        b.get(); // IncludeDocumentation, read + discarded (model does not expose it)
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DescribeConfigsRequest(resources);
    }

    // ── Response ─────────────────────────────────────────────────────────

    /**
     * Encode a {@link DescribeConfigsResponse} at the pinned version.
     *
     * @param resp the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(DescribeConfigsResponse resp) {
        return encodeResponse(PINNED_VERSION, resp);
    }

    /**
     * Encode a DescribeConfigs response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param resp    the response
     * @return encoded body bytes
     */
    public static byte[] encodeResponse(short version, DescribeConfigsResponse resp) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(version, resp);
        }
        return flexibleResponse(resp);
    }

    /**
     * Decode a DescribeConfigs response body at the given API version.
     *
     * @param version the API version (0–4)
     * @param buf     the response body
     * @return the decoded response
     */
    public static DescribeConfigsResponse decodeResponse(short version, ByteBuffer buf) {
        checkVersion(version);
        if (version < FLEXIBLE_START) {
            return fixedResponse(version, buf);
        }
        return flexibleResponse(buf);
    }

    // ── Fixed-width response (v0–v3) ────────────────────────────────────
    // Order: ThrottleTimeMs(int32), numResults(int32),
    //   [ ErrorCode(int16), ErrorMessage(nullable string), ResourceType(int8),
    //     ResourceName(string), numConfigs(int32),
    //     [ Name(string), Value(nullable string), ReadOnly(bool), ConfigSource(int8),
    //       IsSensitive(bool),
    //       Synonyms(int32 count, v1+; null = -1) [ Name(string), Value(nullable string),
    //         Source(int8) ],
    //       ConfigType(int8) v3+, Documentation(nullable string) v3+ ] ] ]

    private static byte[] fixedResponse(short version, DescribeConfigsResponse resp) {
        int size = 4; // ThrottleTimeMs
        size += 4; // numResults
        for (DescribeConfigsResponse.ResourceResponse r : resp.resources()) {
            size += 2; // ErrorCode
            size += 2; // ErrorMessage length prefix (0 if null)
            size += 1; // ResourceType
            size += 2 + r.resourceName().getBytes(StandardCharsets.UTF_8).length; // ResourceName
            size += 4; // numConfigs
            for (DescribeConfigsResponse.ConfigEntry c : r.configs()) {
                size += 2 + c.name().getBytes(StandardCharsets.UTF_8).length; // Name
                size += 2; // Value length prefix (0 if null)
                size += 1; // ReadOnly
                size += 1; // ConfigSource
                size += 1; // IsSensitive
                if (version >= 1) {
                    size += 4; // Synonyms count (null = -1; model does not expose it)
                }
                if (version >= 3) {
                    size += 1; // ConfigType
                    size += 2; // Documentation length prefix (0 if null)
                }
            }
        }

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        buf.putInt(resp.resources().size());
        for (DescribeConfigsResponse.ResourceResponse r : resp.resources()) {
            buf.putShort(r.errorCode());
            KafkaCodecPrimitives.writeNullableString(buf, null); // ErrorMessage (spec default)
            buf.put(DEFAULT_RESOURCE_TYPE); // ResourceType (spec default)
            KafkaCodecPrimitives.writeString(buf, r.resourceName());
            buf.putInt(r.configs().size());
            for (DescribeConfigsResponse.ConfigEntry c : r.configs()) {
                KafkaCodecPrimitives.writeString(buf, c.name());
                KafkaCodecPrimitives.writeNullableString(buf, c.value());
                buf.put((byte) (c.readOnly() ? 1 : 0));
                buf.put(DEFAULT_CONFIG_SOURCE); // ConfigSource (spec default)
                buf.put((byte) (c.isSensitive() ? 1 : 0));
                if (version >= 1) {
                    buf.putInt(-1); // Synonyms null (spec default; model does not expose it)
                }
                if (version >= 3) {
                    buf.put((byte) 0); // ConfigType (spec default)
                    KafkaCodecPrimitives.writeNullableString(buf, null); // Documentation (spec default)
                }
            }
        }
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DescribeConfigsResponse fixedResponse(short version, ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int count = b.getInt();
        List<DescribeConfigsResponse.ResourceResponse> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            short errorCode = b.getShort();
            b.position(b.position() + 2); // ErrorMessage length prefix (nullable)
            b.get(); // ResourceType, read + discarded (model does not expose it)
            String resourceName = KafkaCodecPrimitives.readString(b);
            int configCount = b.getInt();
            List<DescribeConfigsResponse.ConfigEntry> configs = new ArrayList<>(configCount);
            for (int j = 0; j < configCount; j++) {
                String name = KafkaCodecPrimitives.readString(b);
                String value = KafkaCodecPrimitives.readNullableString(b);
                boolean readOnly = b.get() == 1;
                b.get(); // ConfigSource, read + discarded (model does not expose it)
                boolean isSensitive = b.get() == 1;
                if (version >= 1) {
                    skipFixedSynonyms(b);
                }
                if (version >= 3) {
                    b.get(); // ConfigType, read + discarded (model does not expose it)
                    b.position(b.position() + 2); // Documentation length prefix (nullable)
                }
                configs.add(new DescribeConfigsResponse.ConfigEntry(name, value, readOnly, isSensitive));
            }
            resources.add(new DescribeConfigsResponse.ResourceResponse(errorCode, resourceName, configs));
        }
        return new DescribeConfigsResponse(resources);
    }

    private static void skipFixedSynonyms(ByteBuffer b) {
        int count = b.getInt(); // Synonyms (null = -1)
        if (count < 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            b.position(b.position() + 2); // Name length prefix
            int valLen = b.getShort() & 0xFFFF; // Value (nullable) length prefix
            b.position(b.position() + valLen);
            b.get(); // Source
        }
    }

    // ── Flexible response (v4) ──────────────────────────────────────────
    // Order: ThrottleTimeMs(int32), numResults(varint, KIP-482 N+1),
    //   [ ErrorCode(int16), ErrorMessage(nullable compact), ResourceType(int8),
    //     ResourceName(compact string), numConfigs(varint, N+1; never null),
    //     [ Name(compact string), Value(nullable compact), ReadOnly(bool),
    //       ConfigSource(int8), IsSensitive(bool),
    //       Synonyms(varint, N+1; null = 0) [ Name(compact string), Value(nullable compact),
    //         Source(int8), endTags ] , endTags,
    //       ConfigType(int8) v3+, Documentation(nullable compact) v3+, endTags ] , endTags ] ],
    //   endTags

    private static byte[] flexibleResponse(DescribeConfigsResponse resp) {
        int n = resp.resources().size();
        int size = 4; // ThrottleTimeMs (fixed-width even in flexible)
        size += KafkaCodecPrimitives.varintSize(n + 1); // numResults (KIP-482: N+1)
        for (DescribeConfigsResponse.ResourceResponse r : resp.resources()) {
            size += 2; // ErrorCode
            size += 1; // ErrorMessage null (varint 0)
            size += 1; // ResourceType
            size += compactStringSize(r.resourceName()); // ResourceName
            size += KafkaCodecPrimitives.varintSize(r.configs().size() + 1); // numConfigs (KIP-482: N+1)
            for (DescribeConfigsResponse.ConfigEntry c : r.configs()) {
                size += compactStringSize(c.name()); // Name
                size += compactNullableStringSize(c.value()); // Value (nullable)
                size += 1; // ReadOnly
                size += 1; // ConfigSource
                size += 1; // IsSensitive
                size += 1; // Synonyms null (varint 0)
                size += 1; // ConfigType (v3+)
                size += 1; // Documentation null (varint 0)
                size += 1; // per-config endTags (0)
            }
            size += 1; // per-result endTags (0)
        }
        size += 1; // top-level endTags (0)

        ByteBuffer buf = BufferPool.getBuffer(size);
        buf.putInt(DEFAULT_THROTTLE_TIME_MS);
        KafkaCodecPrimitives.writeVarint(buf, n + 1); // KIP-482: N+1
        for (DescribeConfigsResponse.ResourceResponse r : resp.resources()) {
            buf.putShort(r.errorCode());
            KafkaCodecPrimitives.writeVarint(buf, 0); // ErrorMessage null
            buf.put(DEFAULT_RESOURCE_TYPE); // ResourceType (spec default)
            KafkaCodecPrimitives.writeCompactString(buf, r.resourceName());
            KafkaCodecPrimitives.writeVarint(buf, r.configs().size() + 1); // KIP-482: N+1
            for (DescribeConfigsResponse.ConfigEntry c : r.configs()) {
                KafkaCodecPrimitives.writeCompactString(buf, c.name());
                writeCompactNullableString(buf, c.value());
                buf.put((byte) (c.readOnly() ? 1 : 0));
                buf.put(DEFAULT_CONFIG_SOURCE); // ConfigSource (spec default)
                buf.put((byte) (c.isSensitive() ? 1 : 0));
                KafkaCodecPrimitives.writeVarint(buf, 0); // Synonyms null (spec default)
                buf.put((byte) 0); // ConfigType (v3+, spec default)
                KafkaCodecPrimitives.writeVarint(buf, 0); // Documentation null (v3+, spec default)
                KafkaCodecPrimitives.writeVarint(buf, 0); // per-config endTags
            }
            KafkaCodecPrimitives.writeVarint(buf, 0); // per-result endTags
        }
        KafkaCodecPrimitives.writeVarint(buf, 0); // top-level endTags
        buf.flip();
        return KafkaCodecPrimitives.toBytes(buf);
    }

    private static DescribeConfigsResponse flexibleResponse(ByteBuffer b) {
        b.getInt(); // ThrottleTimeMs, read + discarded (model does not expose it)
        int n = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
        List<DescribeConfigsResponse.ResourceResponse> resources = new ArrayList<>(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            short errorCode = b.getShort();
            skipCompactNullable(b); // ErrorMessage (nullable compact)
            b.get(); // ResourceType, read + discarded (model does not expose it)
            String resourceName = KafkaCodecPrimitives.readCompactString(b);
            int configCount = KafkaCodecPrimitives.readVarint(b) - 1; // KIP-482: N+1
            List<DescribeConfigsResponse.ConfigEntry> configs = new ArrayList<>(Math.max(configCount, 0));
            for (int j = 0; j < configCount; j++) {
                String name = KafkaCodecPrimitives.readCompactString(b);
                String value = readCompactNullable(b);
                boolean readOnly = b.get() == 1;
                b.get(); // ConfigSource, read + discarded (model does not expose it)
                boolean isSensitive = b.get() == 1;
                skipCompactSynonyms(b); // Synonyms (v1+)
                b.get(); // ConfigType, read + discarded (v3+, model does not expose it)
                skipCompactNullable(b); // Documentation (v3+)
                KafkaCodecPrimitives.skipTaggedFields(b); // per-config
                configs.add(new DescribeConfigsResponse.ConfigEntry(name, value, readOnly, isSensitive));
            }
            KafkaCodecPrimitives.skipTaggedFields(b); // per-result
            resources.add(new DescribeConfigsResponse.ResourceResponse(errorCode, resourceName, configs));
        }
        KafkaCodecPrimitives.skipTaggedFields(b); // top-level
        return new DescribeConfigsResponse(resources);
    }

    private static void skipCompactSynonyms(ByteBuffer b) {
        int count = KafkaCodecPrimitives.readVarint(b); // KIP-482: 0 = null, otherwise N+1
        if (count == 0) {
            return;
        }
        for (int i = 0; i < count - 1; i++) {
            int nameLen = KafkaCodecPrimitives.readVarint(b);
            b.position(b.position() + nameLen - 1); // Name
            skipCompactNullable(b); // Value
            b.get(); // Source
            KafkaCodecPrimitives.skipTaggedFields(b); // per-synonym
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Size of a non-null string in flexible (compact) encoding. */
    private static int compactStringSize(String s) {
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        return KafkaCodecPrimitives.varintSize(len + 1) + len;
    }

    /** Size of a nullable compact string (varint 0 if null). */
    private static int compactNullableStringSize(String s) {
        if (s == null) {
            return 1; // varint 0
        }
        return compactStringSize(s);
    }

    /** Write a nullable compact string (varint 0 if null). */
    private static void writeCompactNullableString(ByteBuffer buf, String s) {
        if (s == null) {
            KafkaCodecPrimitives.writeVarint(buf, 0);
            return;
        }
        int len = s.getBytes(StandardCharsets.UTF_8).length;
        KafkaCodecPrimitives.writeVarint(buf, len + 1);
        buf.put(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Read a nullable compact string. */
    private static String readCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len == 0) {
            return null;
        }
        byte[] data = new byte[len - 1];
        b.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Skip a nullable compact string. */
    private static void skipCompactNullable(ByteBuffer b) {
        int len = KafkaCodecPrimitives.readVarint(b); // 0 = null, otherwise len+1
        if (len != 0) {
            b.position(b.position() + len - 1);
        }
    }
}
