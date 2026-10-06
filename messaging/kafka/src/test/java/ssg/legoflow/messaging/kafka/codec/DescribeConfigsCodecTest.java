package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.DescribeConfigsRequest;
import ssg.legoflow.messaging.kafka.protocol.DescribeConfigsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link DescribeConfigsCodec} — Kafka DescribeConfigs request/response
 * (API key 32).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v4. Byte-layout assertions in the flexible (KIP-482) v4 verify the
 * compact-array/compact-string encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>nullable compact arrays (ConfigurationKeys, Value, Documentation, ErrorMessage)
 *       encode null as {@code varint 0} (KIP-482);</li>
 *   <li>{@code ThrottleTimeMs} (int32) is present from v0 and always leads the response;</li>
 *   <li>version-gated response fields (ErrorMessage, ResourceType, ConfigSource,
 *       ConfigType, Documentation) not exposed by the model are written with spec defaults.</li>
 * </ul>
 */
@DisplayName("DescribeConfigsCodec (API key 32)")
class DescribeConfigsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static DescribeConfigsRequest.ResourceRequest resource(byte type, String name, List<String> keys) {
        return new DescribeConfigsRequest.ResourceRequest(type, name, keys);
    }

    private static DescribeConfigsRequest req() {
        return new DescribeConfigsRequest(List.of(
                resource((byte) 2, "orders", List.of("a", "b")),
                resource((byte) 4, "broker", null)));
    }

    private static DescribeConfigsRequest reqSingle() {
        return new DescribeConfigsRequest(List.of(resource((byte) 2, "orders", List.of("a", "b"))));
    }

    private static DescribeConfigsResponse.ResourceResponse respResource(short err, String name,
            DescribeConfigsResponse.ConfigEntry... configs) {
        return new DescribeConfigsResponse.ResourceResponse(err, name, List.of(configs));
    }

    private static DescribeConfigsResponse resp(short err) {
        return new DescribeConfigsResponse(List.of(
                respResource(err, "orders",
                        new DescribeConfigsResponse.ConfigEntry("a", "v2", false, false)),
                respResource(err, "broker",
                        new DescribeConfigsResponse.ConfigEntry("b", null, true, true))));
    }

    private static DescribeConfigsResponse respSingle(short err) {
        return new DescribeConfigsResponse(List.of(
                respResource(err, "orders",
                        new DescribeConfigsResponse.ConfigEntry("a", "v2", false, false))));
    }

    private static DescribeConfigsRequest roundTripReq(short v, DescribeConfigsRequest r) {
        byte[] b = DescribeConfigsCodec.encodeRequest(v, r);
        return DescribeConfigsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static DescribeConfigsResponse roundTripResp(short v, DescribeConfigsResponse r) {
        byte[] b = DescribeConfigsCodec.encodeResponse(v, r);
        return DescribeConfigsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, DescribeConfigsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = DescribeConfigsCodec.encodeRequest((short) 0, req());
            byte[] oneArg = DescribeConfigsCodec.encodeRequest(req());
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = DescribeConfigsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] oneArg = DescribeConfigsCodec.encodeResponse(resp((short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("v0 request round-trip preserves resourceType + name + configNames")
        void requestRoundTrip() {
            DescribeConfigsRequest decoded = roundTripReq((short) 0, req());
            assertEquals(2, decoded.resources().size());
            DescribeConfigsRequest.ResourceRequest first = decoded.resources().get(0);
            assertEquals((byte) 2, first.resourceType());
            assertEquals("orders", first.resourceName());
            assertEquals(List.of("a", "b"), first.configNames());
            DescribeConfigsRequest.ResourceRequest second = decoded.resources().get(1);
            assertEquals((byte) 4, second.resourceType());
            assertEquals("broker", second.resourceName());
            assertEquals(null, second.configNames()); // null configNames preserved
        }

        @Test
        @DisplayName("v0 response round-trip preserves errorCode + name + configs")
        void responseRoundTrip() {
            DescribeConfigsResponse decoded = roundTripResp((short) 0, resp((short) 0));
            assertEquals(2, decoded.resources().size());
            DescribeConfigsResponse.ResourceResponse first = decoded.resources().get(0);
            assertEquals("orders", first.resourceName());
            assertEquals(1, first.configs().size());
            DescribeConfigsResponse.ConfigEntry entry = first.configs().get(0);
            assertEquals("a", entry.name());
            assertEquals("v2", entry.value());
            DescribeConfigsResponse.ResourceResponse second = decoded.resources().get(1);
            DescribeConfigsResponse.ConfigEntry entry2 = second.configs().get(0);
            assertEquals(null, entry2.value()); // null value preserved
            assertEquals(true, entry2.readOnly());
            assertEquals(true, entry2.isSensitive());
        }

        @Test
        @DisplayName("v0 request round-trip preserves empty resource list")
        void requestRoundTripEmpty() {
            DescribeConfigsRequest decoded = roundTripReq((short) 0, new DescribeConfigsRequest(List.of()));
            assertEquals(List.of(), decoded.resources());
        }

        @Test
        @DisplayName("v0 response round-trip preserves empty results")
        void responseRoundTripEmpty() {
            DescribeConfigsResponse decoded = roundTripResp((short) 0, new DescribeConfigsResponse(List.of()));
            assertEquals(List.of(), decoded.resources());
        }
    }

    // ── Fixed-width request (v0–v3) ─────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width request (v0–v3)")
    class RequestLayout {

        @Test
        @DisplayName("v0 layout byte-for-byte (int32 count + [int8 type, int16 name, int32 keys count, keys])")
        void v0() {
            byte[] b = DescribeConfigsCodec.encodeRequest((short) 0, reqSingle());
            // 4 numResources
            // 1 type + 2+6 "orders" + 4 keys count(2) + 2+1 "a" + 2+1 "b"
            // = 4 + 1 + 8 + 4 + 3 + 3 = 23
            assertEquals(23, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,                          // numResources = 1
                    2,                                    // ResourceType = TOPIC
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 0, 0, 2,                           // ConfigurationKeys count = 2
                    0, 1, 'a',                            // "a"
                    0, 1, 'b'                             // "b"
            });
        }

        @Test
        @DisplayName("v0 layout: null ConfigurationKeys encoded as int32 -1")
        void v0NullKeys() {
            byte[] b = DescribeConfigsCodec.encodeRequest((short) 0,
                    new DescribeConfigsRequest(List.of(resource((byte) 4, "broker", null))));
            // 4 + 1 + (2+6) + 4 = 17
            assertEquals(17, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,                          // numResources = 1
                    4,                                    // ResourceType = BROKER
                    0, 6, 'b', 'r', 'o', 'k', 'e', 'r',  // "broker"
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF  // ConfigurationKeys = null (-1)
            });
        }

        @Test
        @DisplayName("v1 adds trailing IncludeSynonyms(bool) after the resource array")
        void v1AddsIncludeSynonyms() {
            byte[] v0 = DescribeConfigsCodec.encodeRequest((short) 0, reqSingle());
            byte[] v1 = DescribeConfigsCodec.encodeRequest((short) 1, reqSingle());
            assertEquals(v0.length + 1, v1.length);
            // v0 body is a prefix of v1; v1 appends IncludeSynonyms=false (0)
            for (int i = 0; i < v0.length; i++) {
                assertEquals(v0[i], v1[i]);
            }
            assertEquals(0, v1[v0.length]); // IncludeSynonyms = false
        }

        @Test
        @DisplayName("v2 = v1 byte-identical (no new field in v2)")
        void v2EqualsV1() {
            byte[] v1 = DescribeConfigsCodec.encodeRequest((short) 1, reqSingle());
            byte[] v2 = DescribeConfigsCodec.encodeRequest((short) 2, reqSingle());
            assertArrayEquals(v1, v2);
        }

        @Test
        @DisplayName("v3 adds trailing IncludeDocumentation(bool) after IncludeSynonyms")
        void v3AddsIncludeDocumentation() {
            byte[] v2 = DescribeConfigsCodec.encodeRequest((short) 2, reqSingle());
            byte[] v3 = DescribeConfigsCodec.encodeRequest((short) 3, reqSingle());
            assertEquals(v2.length + 1, v3.length);
            for (int i = 0; i < v2.length; i++) {
                assertEquals(v2[i], v3[i]);
            }
            assertEquals(0, v3[v3.length - 1]); // IncludeDocumentation = false
        }

        @Test
        @DisplayName("v3 request round-trip (trailing bools read + discarded)")
        void v3RoundTrip() {
            DescribeConfigsRequest decoded = roundTripReq((short) 3, req());
            assertEquals(2, decoded.resources().size());
            assertEquals("orders", decoded.resources().get(0).resourceName());
        }
    }

    // ── Flexible request (KIP-482, v4) ──────────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v4 count varint = size + 1 (2 resources → varint 3)")
        void v4CountIsPlusOne() {
            byte[] b = DescribeConfigsCodec.encodeRequest((short) 4, req());
            assertEquals(3, b[0]); // numResources varint = 2 + 1
        }

        @Test
        @DisplayName("v4 layout byte-for-byte (compact names/keys, N+1 count, per-resource + top endTags)")
        void v4Layout() {
            byte[] b = DescribeConfigsCodec.encodeRequest((short) 4, req());
            // 1 numRes(3)
            // + 1 type(2) + 7 "orders" + 1 keys(3) + 2 "a" + 2 "b" + 1 endRes = 14
            // + 1 type(4) + 7 "broker" + 1 keys(null=0) + 1 endRes = 10
            // + 1 IncludeSynonyms + 1 IncludeDocumentation + 1 top endTags = 3
            // = 28
            assertEquals(28, b.length);
            assertArrayEquals(b, new byte[]{
                    3,                                    // numResources varint = 2+1
                    2,                                    // type TOPIC
                    7, 'o', 'r', 'd', 'e', 'r', 's',      // "orders"
                    3,                                    // keys varint = 2+1
                    2, 'a',                                // "a" varint(1+1)=2
                    2, 'b',                                // "b"
                    0,                                    // per-resource endTags = 0
                    4,                                    // type BROKER
                    7, 'b', 'r', 'o', 'k', 'e', 'r',      // "broker"
                    0,                                    // keys null = varint 0
                    0,                                    // per-resource endTags = 0
                    0,                                    // IncludeSynonyms = false
                    0,                                    // IncludeDocumentation = false
                    0                                     // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v4 layout with empty resource list (varint 1 = 0+1, top endTags=0)")
        void v4LayoutEmpty() {
            byte[] b = DescribeConfigsCodec.encodeRequest((short) 4, new DescribeConfigsRequest(List.of()));
            assertArrayEquals(b, new byte[]{1, 0, 0, 0}); // 1 numRes(0+1), 0 synonyms, 0 doc, 0 top end
        }

        @Test
        @DisplayName("v4 differs from v3 layout (flexible — silent fallback forbidden)")
        void v4DiffersFromV3() {
            byte[] v3 = DescribeConfigsCodec.encodeRequest((short) 3, req());
            byte[] v4 = DescribeConfigsCodec.encodeRequest((short) 4, req());
            assertEquals(38, v3.length); // 4 (num) + 19 (res1) + 13 (res2) + 2 (bools) = 38 (v3 fixed width)
            assertEquals(28, v4.length);
        }

        @Test
        @DisplayName("v4 round-trip preserves resources + configNames (incl. null)")
        void roundTrip() {
            DescribeConfigsRequest decoded = roundTripReq((short) 4, req());
            assertEquals(2, decoded.resources().size());
            assertEquals(List.of("a", "b"), decoded.resources().get(0).configNames());
            assertEquals(null, decoded.resources().get(1).configNames());
        }

        @Test
        @DisplayName("v4 round-trip preserves empty resource list")
        void roundTripEmpty() {
            DescribeConfigsRequest decoded = roundTripReq((short) 4, new DescribeConfigsRequest(List.of()));
            assertEquals(List.of(), decoded.resources());
        }
    }

    // ── Fixed-width response (v0–v3) ────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width response (v0–v3)")
    class ResponseLayout {

        @Test
        @DisplayName("v0 layout byte-for-byte (ThrottleTimeMs + [err, errMsg, resType, name, [name, val, ro, cfgSrc, sens]])")
        void v0() {
            byte[] b = DescribeConfigsCodec.encodeResponse((short) 0, respSingle((short) 16));
            // 4 throttle + 4 numResults(1)
            // + 2 err + 2 errMsg(len 0) + 1 resType + (2+6) "orders" + 4 numConfigs(1)
            // + (2+1) "a" + (2+2) "v2" + 1 ro + 1 cfgSrc + 1 sens = 3+4+1+1+1 = 10
            // = 4 + 4 + 2 + 2 + 1 + 8 + 4 + 10 = 35
            assertEquals(35, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    0, 0, 0, 1,                             // numResults = 1
                    0, 16,                                   // ErrorCode = 16
                    (byte) 0xFF, (byte) 0xFF,               // ErrorMessage = null (-1 = 0xFFFF)
                    2,                                      // ResourceType = TOPIC (default)
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 0, 0, 1,                             // numConfigs = 1
                    0, 1, 'a',                              // Name "a"
                    0, 2, 'v', '2',                         // Value "v2"
                    0,                                      // ReadOnly = false
                    1,                                      // ConfigSource = DEFAULT_CONFIG
                    0                                      // IsSensitive = false
            });
        }

        @Test
        @DisplayName("v1 adds trailing Synonyms(int32 count, null = -1) per config entry")
        void v1AddsSynonyms() {
            byte[] v0 = DescribeConfigsCodec.encodeResponse((short) 0, respSingle((short) 0));
            byte[] v1 = DescribeConfigsCodec.encodeResponse((short) 1, respSingle((short) 0));
            // 1 config entry → +4 bytes (Synonyms count null = -1)
            assertEquals(v0.length + 4, v1.length);
            // Synonyms count (-1) is the last 4 bytes; each byte is 0xFF → sign-extends to -1
            assertEquals(-1, v1[v1.length - 4]);
            assertEquals(-1, v1[v1.length - 3]);
            assertEquals(-1, v1[v1.length - 2]);
            assertEquals(-1, v1[v1.length - 1]);
        }

        @Test
        @DisplayName("v2 = v1 byte-identical (no new field in v2)")
        void v2EqualsV1() {
            byte[] v1 = DescribeConfigsCodec.encodeResponse((short) 1, respSingle((short) 0));
            byte[] v2 = DescribeConfigsCodec.encodeResponse((short) 2, respSingle((short) 0));
            assertArrayEquals(v1, v2);
        }

        @Test
        @DisplayName("v3 adds trailing ConfigType(int8) + Documentation(nullable string) per config entry")
        void v3AddsConfigTypeDocumentation() {
            byte[] v2 = DescribeConfigsCodec.encodeResponse((short) 2, respSingle((short) 0));
            byte[] v3 = DescribeConfigsCodec.encodeResponse((short) 3, respSingle((short) 0));
            // 1 config entry → +1 (ConfigType) + 2 (Documentation null = -1) = 3 bytes
            assertEquals(v2.length + 3, v3.length);
            // last 3 bytes: ConfigType(0), Documentation null (-1 → 0xFF 0xFF)
            assertEquals(0, v3[v3.length - 3]); // ConfigType = 0
            assertEquals(-1, v3[v3.length - 2]); // Documentation len = -1 (null)
            assertEquals(-1, v3[v3.length - 1]);
        }

        @Test
        @DisplayName("v0 response round-trip preserves configs (null value, flags)")
        void v0RoundTrip() {
            DescribeConfigsResponse decoded = roundTripResp((short) 0, resp((short) 0));
            assertEquals(2, decoded.resources().size());
            DescribeConfigsResponse.ConfigEntry e = decoded.resources().get(0).configs().get(0);
            assertEquals("a", e.name());
            assertEquals("v2", e.value());
            assertEquals(false, e.readOnly());
            assertEquals(false, e.isSensitive());
            DescribeConfigsResponse.ConfigEntry e2 = decoded.resources().get(1).configs().get(0);
            assertEquals(null, e2.value());
            assertEquals(true, e2.readOnly());
            assertEquals(true, e2.isSensitive());
        }
    }

    // ── Flexible response (KIP-482, v4) ─────────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v4 layout byte-for-byte (compact names/vals, N+1 counts, per-config + per-result + top endTags)")
        void v4Layout() {
            byte[] b = DescribeConfigsCodec.encodeResponse((short) 4, respSingle((short) 16));
            // 4 throttle + 1 numRes(2=1+1)
            // + 2 err + 1 errMsg(null=0) + 1 resType + 7 "orders" + 1 numCfg(2=1+1)
            // + 2 "a" + 3 "v2" + 1 ro + 1 cfgSrc + 1 sens + 1 syn(null=0) + 1 cfgType + 1 doc(null=0) + 1 endCfg = 14
            // + 1 endRes = 1
            // + 1 top endTags = 1
            // = 4 + 1 + 2 + 1 + 1 + 7 + 1 + 12 + 1 + 1 = 31
            assertEquals(31, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0
                    2,                                    // numResults varint = 1+1
                    0, 16,                                   // ErrorCode = 16
                    0,                                    // ErrorMessage null = varint 0
                    2,                                    // ResourceType = TOPIC
                    7, 'o', 'r', 'd', 'e', 'r', 's',      // "orders"
                    2,                                    // numConfigs varint = 1+1
                    2, 'a',                                // Name "a" varint(1+1)=2
                    3, 'v', '2',                            // Value "v2" varint(2+1)=3
                    0,                                    // ReadOnly = false
                    1,                                    // ConfigSource = DEFAULT_CONFIG
                    0,                                    // IsSensitive = false
                    0,                                    // Synonyms null = varint 0
                    0,                                    // ConfigType = 0
                    0,                                    // Documentation null = varint 0
                    0,                                    // per-config endTags = 0
                    0,                                    // per-result endTags = 0
                    0                                     // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v4 count varint = size + 1 at the results array (2 results → varint 3)")
        void v4CountIsPlusOne() {
            byte[] b = DescribeConfigsCodec.encodeResponse((short) 4, resp((short) 0));
            assertEquals(3, b[4]); // numResults varint = 2 + 1 (after 4-byte throttle)
        }

        @Test
        @DisplayName("v4 differs from v3 layout (flexible — silent fallback forbidden)")
        void v4DiffersFromV3() {
            byte[] v3 = DescribeConfigsCodec.encodeResponse((short) 3, respSingle((short) 0));
            byte[] v4 = DescribeConfigsCodec.encodeResponse((short) 4, respSingle((short) 0));
            assertEquals(42, v3.length); // 35 (v0) + 4 (synonyms) + 3 (cfgType+doc)
            assertEquals(31, v4.length);
        }

        @Test
        @DisplayName("v4 round-trip preserves configs (incl. null value + flags)")
        void roundTrip() {
            DescribeConfigsResponse decoded = roundTripResp((short) 4, resp((short) 0));
            assertEquals(2, decoded.resources().size());
            DescribeConfigsResponse.ConfigEntry e = decoded.resources().get(0).configs().get(0);
            assertEquals("a", e.name());
            assertEquals("v2", e.value());
            DescribeConfigsResponse.ConfigEntry e2 = decoded.resources().get(1).configs().get(0);
            assertEquals(null, e2.value());
            assertEquals(true, e2.readOnly());
            assertEquals(true, e2.isSensitive());
        }

        @Test
        @DisplayName("v4 round-trip preserves empty results")
        void roundTripEmpty() {
            DescribeConfigsResponse decoded = roundTripResp((short) 4, new DescribeConfigsResponse(List.of()));
            assertEquals(List.of(), decoded.resources());
        }
    }

    // ── Version range ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version range")
    class Versions {

        @Test
        @DisplayName("encodeRequest rejects v5+ (spec range 0–4)")
        void requestRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DescribeConfigsCodec.encodeRequest((short) 5, req()));
        }

        @Test
        @DisplayName("encodeResponse rejects v5+ (spec range 0–4)")
        void responseRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DescribeConfigsCodec.encodeResponse((short) 5, resp((short) 0)));
        }

        @Test
        @DisplayName("decodeRequest rejects v5+ (spec range 0–4)")
        void decodeRequestRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DescribeConfigsCodec.decodeRequest((short) 5, ByteBuffer.allocate(4)));
        }

        @Test
        @DisplayName("decodeResponse rejects v5+ (spec range 0–4)")
        void decodeResponseRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DescribeConfigsCodec.decodeResponse((short) 5, ByteBuffer.allocate(4)));
        }

        @Test
        @DisplayName("encodeRequest rejects negative version")
        void requestRejectsNegative() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DescribeConfigsCodec.encodeRequest((short) -1, req()));
        }
    }
}