package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.CreatePartitionsRequest;
import ssg.legoflow.messaging.kafka.protocol.CreatePartitionsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link CreatePartitionsCodec} — Kafka CreatePartitions request/response
 * (API key 37).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v3. Byte-layout assertions in the flexible (KIP-482) v2/v3 verify the
 * compact-array count encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;
 *       nullable arrays use varint 0 for null;</li>
 *   <li>compact strings for the topic names;</li>
 *   <li>fixed-width fields ({@code Count} int32, {@code ErrorCode} int16, {@code TimeoutMs}
 *       int32, {@code ThrottleTimeMs} int32) keep fixed width even in flexible layout;
 *       {@code ThrottleTimeMs} is present from v0 and always leads the response.</li>
 * </ul>
 * The in-house models do not expose {@code Assignments} / {@code ValidateOnly} (request) or
 * {@code ThrottleTimeMs} / {@code ErrorMessage} (response): the spec defaults (null, false,
 * 0, null) are written and the values are read + discarded.
 */
@DisplayName("CreatePartitionsCodec (API key 37)")
class CreatePartitionsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static CreatePartitionsRequest req(int timeoutMs) {
        return new CreatePartitionsRequest(
                List.of(new CreatePartitionsRequest.TopicNewPartitions("orders", 5)), timeoutMs);
    }

    private static CreatePartitionsRequest reqMulti() {
        return new CreatePartitionsRequest(List.of(
                new CreatePartitionsRequest.TopicNewPartitions("orders", 5),
                new CreatePartitionsRequest.TopicNewPartitions("events", 10)), 30000);
    }

    private static CreatePartitionsResponse resp(short err) {
        return new CreatePartitionsResponse(
                List.of(new CreatePartitionsResponse.TopicResult("orders", err)));
    }

    private static CreatePartitionsResponse respMulti() {
        return new CreatePartitionsResponse(List.of(
                new CreatePartitionsResponse.TopicResult("orders", (short) 0),
                new CreatePartitionsResponse.TopicResult("events", (short) 37)));
    }

    private static CreatePartitionsRequest roundTripReq(short v, CreatePartitionsRequest r) {
        byte[] b = CreatePartitionsCodec.encodeRequest(v, r);
        return CreatePartitionsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static CreatePartitionsResponse roundTripResp(short v, CreatePartitionsResponse r) {
        byte[] b = CreatePartitionsCodec.encodeResponse(v, r);
        return CreatePartitionsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, CreatePartitionsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = CreatePartitionsCodec.encodeRequest((short) 0, req(30000));
            byte[] oneArg = CreatePartitionsCodec.encodeRequest(req(30000));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = CreatePartitionsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] oneArg = CreatePartitionsCodec.encodeResponse(resp((short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("v0 request round-trip preserves name, count + timeout")
        void requestRoundTrip() {
            CreatePartitionsRequest decoded = roundTripReq((short) 0, req(30000));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(5, decoded.topics().get(0).newCount());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("v0 response round-trip preserves name + errorCode")
        void responseRoundTrip() {
            CreatePartitionsResponse decoded = roundTripResp((short) 0, resp((short) 0));
            assertEquals(1, decoded.results().size());
            assertEquals("orders", decoded.results().get(0).name());
            assertEquals((short) 0, decoded.results().get(0).errorCode());
        }
    }

    // ── Fixed-width request (v0–v1) ─────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width request (v0–v1)")
    class RequestLayout {

        @Test
        @DisplayName("v0 = int32 numTopics + [int16 name, int32 count, int32 assignments(-1 null)] + int32 timeout + bool validateOnly (byte-for-byte)")
        void v0() {
            byte[] b = CreatePartitionsCodec.encodeRequest((short) 0, req(30000));
            // 4 numTopics + 8 name + 4 count + 4 assignments + 4 timeout + 1 validateOnly = 25
            assertEquals(25, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,                          // numTopics = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 0, 0, 5,                           // Count = 5
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // Assignments = null
                    0, 0, 117, 48,                        // timeoutMs = 30000 (0x7530)
                    0                                    // ValidateOnly = false (spec default)
            });
        }

        @Test
        @DisplayName("v0 multi-topic layout (2 topics, byte-for-byte)")
        void v0Multi() {
            byte[] b = CreatePartitionsCodec.encodeRequest((short) 0, reqMulti());
            // 4 numTopics + 2*(8 name + 4 count + 4 assignments) + 4 timeout + 1 validateOnly = 41
            assertEquals(41, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 2,                          // numTopics = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 0, 0, 5,                           // Count = 5
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // Assignments = null
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',  // "events"
                    0, 0, 0, 10,                          // Count = 10
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // Assignments = null
                    0, 0, 117, 48,                        // timeoutMs = 30000 (0x7530)
                    0                                    // ValidateOnly = false (spec default)
            });
        }

        @Test
        @DisplayName("v1 request is byte-identical to v0 (spec: 'Version 1 is the same as version 0')")
        void v1Identical() {
            byte[] v0 = CreatePartitionsCodec.encodeRequest((short) 0, req(30000));
            assertArrayEquals(v0, CreatePartitionsCodec.encodeRequest((short) 1, req(30000)),
                    "request v1 must match v0");
        }

        @Test
        @DisplayName("empty topic list round-trip")
        void emptyTopics() {
            CreatePartitionsRequest decoded = roundTripReq((short) 0, new CreatePartitionsRequest(List.of(), 5000));
            assertEquals(List.of(), decoded.topics());
            assertEquals(5000, decoded.timeoutMs());
        }
    }

    // ── Flexible request (KIP-482, v2–v3) ───────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v2 layout byte-for-byte (compact name, fixed-width count, Assignments null varint 0, endTags=0)")
        void v2Layout() {
            byte[] b = CreatePartitionsCodec.encodeRequest((short) 2, req(30000));
            // 1 numTopics + 7 name + 4 count + 1 assignments + 1 topic endTags
            // + 4 timeout + 1 validateOnly + 1 top endTags = 20
            assertEquals(20, b.length);
            assertArrayEquals(b, new byte[]{
                    2,                                     // numTopics varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    0, 0, 0, 5,                             // Count = 5 (fixed width)
                    0,                                     // Assignments null (varint 0)
                    0,                                     // per-topic endTags = 0
                    0, 0, 117, 48,                          // timeoutMs = 30000 (0x7530)
                    0,                                     // ValidateOnly = false (spec default)
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 multi-topic layout byte-for-byte (numTopics varint = 2 + 1)")
        void v2LayoutMulti() {
            byte[] b = CreatePartitionsCodec.encodeRequest((short) 2, reqMulti());
            // 1 numTopics + 2*(7 name + 4 count + 1 assignments + 1 topic endTags) + 4 timeout + 1 validateOnly + 1 top endTags = 33
            assertEquals(33, b.length);
            assertArrayEquals(b, new byte[]{
                    3,                                     // numTopics varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    0, 0, 0, 5,                             // Count = 5
                    0,                                     // Assignments null (varint 0)
                    0,                                     // per-topic endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    0, 0, 0, 10,                            // Count = 10
                    0,                                     // Assignments null (varint 0)
                    0,                                     // per-topic endTags = 0
                    0, 0, 117, 48,                          // timeoutMs = 30000 (0x7530)
                    0,                                     // ValidateOnly = false (spec default)
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = CreatePartitionsCodec.encodeRequest((short) 1, req(30000));
            byte[] v2 = CreatePartitionsCodec.encodeRequest((short) 2, req(30000));
            assertEquals(25, v1.length);
            assertEquals(20, v2.length);
        }

        @Test
        @DisplayName("v3 request is byte-identical to v2 (KIP-599 affects only response errors)")
        void v3Identical() {
            byte[] v2 = CreatePartitionsCodec.encodeRequest((short) 2, req(30000));
            assertArrayEquals(v2, CreatePartitionsCodec.encodeRequest((short) 3, req(30000)),
                    "request v3 must match v2");
        }

        @Test
        @DisplayName("v2 round-trip with multi-topic request")
        void roundTripMulti() {
            CreatePartitionsRequest decoded = roundTripReq((short) 2, reqMulti());
            assertEquals(2, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(5, decoded.topics().get(0).newCount());
            assertEquals("events", decoded.topics().get(1).name());
            assertEquals(10, decoded.topics().get(1).newCount());
            assertEquals(30000, decoded.timeoutMs());
        }
    }

    // ── Fixed-width response (v0–v1) ────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width response (v0–v1)")
    class ResponseLayout {

        @Test
        @DisplayName("v0 = int32 ThrottleTimeMs(0 default) + int32 numResults + [int16 name, int16 err, int16 -1 errorMessage null] (byte-for-byte)")
        void v0() {
            byte[] b = CreatePartitionsCodec.encodeResponse((short) 0, resp((short) 0));
            // 4 throttle + 4 numResults + 8 name + 2 err + 2 errorMessage = 20
            assertEquals(20, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    0, 0, 0, 1,                             // numResults = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 0,                                   // ErrorCode = 0
                    (byte) 0xFF, (byte) 0xFF                // ErrorMessage = null (int16 -1)
            });
        }

        @Test
        @DisplayName("v0 multi-result layout (2 results, byte-for-byte)")
        void v0Multi() {
            byte[] b = CreatePartitionsCodec.encodeResponse((short) 0, respMulti());
            // 4 throttle + 4 numResults + 2*(8 name + 2 err + 2 errorMessage) = 32
            assertEquals(32, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    0, 0, 0, 2,                             // numResults = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 0,                                   // ErrorCode = 0
                    (byte) 0xFF, (byte) 0xFF,               // ErrorMessage = null
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',     // "events"
                    0, 37,                                  // ErrorCode = 37 (INVALID_PARTITIONS)
                    (byte) 0xFF, (byte) 0xFF                // ErrorMessage = null
            });
        }

        @Test
        @DisplayName("v1 response is byte-identical to v0 (spec: 'Version 1 is the same as version 0')")
        void v1Identical() {
            byte[] v0 = CreatePartitionsCodec.encodeResponse((short) 0, resp((short) 0));
            assertArrayEquals(v0, CreatePartitionsCodec.encodeResponse((short) 1, resp((short) 0)),
                    "response v1 must match v0");
        }

        @Test
        @DisplayName("non-zero errorCode round-trips")
        void nonZeroError() {
            CreatePartitionsResponse decoded = roundTripResp((short) 1, resp((short) 3));
            assertEquals((short) 3, decoded.results().get(0).errorCode());
        }
    }

    // ── Flexible response (KIP-482, v2–v3) ──────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v2 layout byte-for-byte (ThrottleTimeMs + N+1 count + compact name + fixed-width err + null varint + endTags=0)")
        void v2Layout() {
            byte[] b = CreatePartitionsCodec.encodeResponse((short) 2, resp((short) 0));
            // 4 throttle + 1 numResults + 7 name + 2 err + 1 errorMessage + 1 endTags + 1 top endTags = 17
            assertEquals(17, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (fixed width)
                    2,                                     // numResults varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    0, 0,                                   // ErrorCode = 0 (fixed width)
                    0,                                     // ErrorMessage null (varint 0)
                    0,                                     // per-result endTags = 0
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 multi-result layout byte-for-byte (numResults varint = 2 + 1)")
        void v2LayoutMulti() {
            byte[] b = CreatePartitionsCodec.encodeResponse((short) 2, respMulti());
            // 4 throttle + 1 numResults + 2*(7 name + 2 err + 1 errorMessage + 1 endTags) + 1 top endTags = 28
            assertEquals(28, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (fixed width)
                    3,                                     // numResults varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    0, 0,                                   // ErrorCode = 0
                    0,                                     // ErrorMessage null (varint 0)
                    0,                                     // per-result endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    0, 37,                                  // ErrorCode = 37 (INVALID_PARTITIONS)
                    0,                                     // ErrorMessage null (varint 0)
                    0,                                     // per-result endTags = 0
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = CreatePartitionsCodec.encodeResponse((short) 1, resp((short) 0));
            byte[] v2 = CreatePartitionsCodec.encodeResponse((short) 2, resp((short) 0));
            assertEquals(20, v1.length);
            assertEquals(17, v2.length);
        }

        @Test
        @DisplayName("v3 response is byte-identical to v2 (KIP-599 affects only possible errors)")
        void v3Identical() {
            byte[] v2 = CreatePartitionsCodec.encodeResponse((short) 2, resp((short) 0));
            assertArrayEquals(v2, CreatePartitionsCodec.encodeResponse((short) 3, resp((short) 0)),
                    "response v3 must match v2");
        }
    }

    // ── Full-range round-trips (v0–v3) ──────────────────────────────────

    @Nested
    @DisplayName("Full-range round-trips (v0–v3)")
    class RoundTrips {

        @Test
        @DisplayName("request round-trips at every version 0–3 (single topic)")
        void requestAllVersions() {
            for (short v = 0; v <= 3; v++) {
                CreatePartitionsRequest decoded = roundTripReq(v, req(30000));
                assertEquals(1, decoded.topics().size(), "v" + v);
                assertEquals("orders", decoded.topics().get(0).name(), "v" + v);
                assertEquals(5, decoded.topics().get(0).newCount(), "v" + v);
                assertEquals(30000, decoded.timeoutMs(), "v" + v);
            }
        }

        @Test
        @DisplayName("request round-trips at every version 0–3 (multi-topic)")
        void requestMultiAllVersions() {
            for (short v = 0; v <= 3; v++) {
                CreatePartitionsRequest decoded = roundTripReq(v, reqMulti());
                assertEquals(2, decoded.topics().size(), "v" + v);
                assertEquals(5, decoded.topics().get(0).newCount(), "v" + v);
                assertEquals(10, decoded.topics().get(1).newCount(), "v" + v);
            }
        }

        @Test
        @DisplayName("response round-trips at every version 0–3")
        void responseAllVersions() {
            for (short v = 0; v <= 3; v++) {
                CreatePartitionsResponse decoded = roundTripResp(v, resp((short) 0));
                assertEquals(1, decoded.results().size(), "v" + v);
                assertEquals("orders", decoded.results().get(0).name(), "v" + v);
                assertEquals((short) 0, decoded.results().get(0).errorCode(), "v" + v);
            }
        }

        @Test
        @DisplayName("empty response round-trip at v3 (flexible, zero results)")
        void emptyResponseV3() {
            CreatePartitionsResponse decoded = roundTripResp((short) 3, new CreatePartitionsResponse(List.of()));
            assertEquals(List.of(), decoded.results());
        }

        @Test
        @DisplayName("empty request round-trip at v3 (flexible, zero topics)")
        void emptyRequestV3() {
            CreatePartitionsRequest decoded = roundTripReq((short) 3, new CreatePartitionsRequest(List.of(), 5000));
            assertEquals(List.of(), decoded.topics());
            assertEquals(5000, decoded.timeoutMs());
        }
    }

    // ── Version guard ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version guard")
    class VersionGuard {

        @Test
        @DisplayName("request encode/decode reject versions outside 0–3")
        void requestVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.encodeRequest((short) 4, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.encodeRequest((short) -1, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.decodeRequest((short) 4, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response encode/decode reject versions outside 0–3")
        void responseVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.encodeResponse((short) 4, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.encodeResponse((short) -1, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> CreatePartitionsCodec.decodeResponse((short) 4, ByteBuffer.wrap(new byte[0])));
        }
    }
}
