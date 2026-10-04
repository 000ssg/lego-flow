package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.DeleteTopicsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteTopicsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link DeleteTopicsCodec} — Kafka DeleteTopics request/response (API key 20).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v6. Byte-layout assertions in the flexible (KIP-482) versions verify
 * the compact-array count encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>nullable compact strings encode {@code varint(0)} when null;</li>
 *   <li>uuid fields are fixed 16 bytes, absent default = all-zero.</li>
 * </ul>
 */
@DisplayName("DeleteTopicsCodec (API key 20)")
class DeleteTopicsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static DeleteTopicsRequest req(int timeoutMs) {
        return new DeleteTopicsRequest(List.of("orders", "events"), timeoutMs);
    }

    private static DeleteTopicsResponse resp(short err) {
        return new DeleteTopicsResponse(List.of(
                new DeleteTopicsResponse.TopicResult("orders", err),
                new DeleteTopicsResponse.TopicResult("events", err)));
    }

    private static DeleteTopicsRequest roundTripReq(short v, DeleteTopicsRequest r) {
        byte[] b = DeleteTopicsCodec.encodeRequest(v, r);
        return DeleteTopicsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static DeleteTopicsResponse roundTripResp(short v, DeleteTopicsResponse r) {
        byte[] b = DeleteTopicsCodec.encodeResponse(v, r);
        return DeleteTopicsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, DeleteTopicsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = DeleteTopicsCodec.encodeRequest((short) 0, req(30000));
            byte[] oneArg = DeleteTopicsCodec.encodeRequest(req(30000));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = DeleteTopicsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] oneArg = DeleteTopicsCodec.encodeResponse(resp((short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("v0 request round-trip preserves names + timeout")
        void requestRoundTrip() {
            DeleteTopicsRequest decoded = roundTripReq((short) 0, req(30000));
            assertEquals(List.of("orders", "events"), decoded.topicNames());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("v0 response round-trip preserves name + errorCode")
        void responseRoundTrip() {
            DeleteTopicsResponse decoded = roundTripResp((short) 0, resp((short) 0));
            assertEquals(2, decoded.responses().size());
            assertEquals("orders", decoded.responses().get(0).name());
            assertEquals((short) 0, decoded.responses().get(0).errorCode());
            assertEquals("events", decoded.responses().get(1).name());
        }
    }

    // ── Fixed-width request (v0–v3) ─────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width request (v0–v3)")
    class RequestLayout {

        @Test
        @DisplayName("v0 = int32 numNames + [int16 name]* + int32 timeoutMs (byte-for-byte)")
        void v0() {
            byte[] b = DeleteTopicsCodec.encodeRequest((short) 0, req(30000));
            // 4 numNames + (2+6) + (2+6) + 4 timeout = 24
            assertEquals(24, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 2,                 // numNames = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',  // "events"
                    0, 0, 117, 48,              // timeoutMs = 30000 (0x7530)
            });
        }

        @Test
        @DisplayName("v1–v3 requests are byte-identical to v0 (spec: 0,1,2,3 same)")
        void v1ToV3Identical() {
            byte[] v0 = DeleteTopicsCodec.encodeRequest((short) 0, req(30000));
            for (short v = 1; v <= 3; v++) {
                assertArrayEquals(v0, DeleteTopicsCodec.encodeRequest(v, req(30000)),
                        "request v" + v + " must match v0");
            }
        }

        @Test
        @DisplayName("empty name list round-trip")
        void emptyNames() {
            DeleteTopicsRequest decoded = roundTripReq((short) 0, new DeleteTopicsRequest(List.of(), 5000));
            assertEquals(List.of(), decoded.topicNames());
            assertEquals(5000, decoded.timeoutMs());
        }
    }

    // ── Flexible request (KIP-482, v4–v6) ───────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v4 count varint = size + 1 (2 names → varint 3)")
        void v4CountIsPlusOne() {
            byte[] b = DeleteTopicsCodec.encodeRequest((short) 4, req(30000));
            assertEquals(3, b[0]); // numNames varint = 2 + 1
        }

        @Test
        @DisplayName("v4–v5 layout byte-for-byte (compact strings, N+1 count, endTags=0)")
        void v4v5Layout() {
            for (short v : new short[]{4, 5}) {
                byte[] b = DeleteTopicsCodec.encodeRequest(v, req(30000));
                // 1 count + (7+7) names + 4 timeout + 1 endTags = 20
                assertEquals(20, b.length, "v" + v);
                assertArrayEquals(b, new byte[]{
                        3,                                    // numNames varint = 2 + 1
                        7, 'o', 'r', 'd', 'e', 'r', 's',      // "orders" varint(6+1)=7
                        7, 'e', 'v', 'e', 'n', 't', 's',      // "events" varint(6+1)=7
                        0, 0, 117, 48,                         // timeoutMs = 30000 (0x7530)
                        0,                                    // top-level endTags = 0
                }, "v" + v);
            }
        }

        @Test
        @DisplayName("v6 request reorganizes into Topics[]DeleteTopicState: name(compact) + TopicId(16) + endTags per entry")
        void v6Layout() {
            byte[] b = DeleteTopicsCodec.encodeRequest((short) 6, req(30000));
            // 1 count + (7+16+1)*2 + 4 timeout + 1 endTags = 54
            assertEquals(54, b.length);
            assertArrayEquals(b, new byte[]{
                    3,                                    // numTopics varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',      // Name "orders" varint(6+1)=7
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // TopicId all-zero
                    0,                                    // per-entry endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',      // Name "events" varint(6+1)=7
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // TopicId all-zero
                    0,                                    // per-entry endTags = 0
                    0, 0, 117, 48,                         // timeoutMs = 30000 (0x7530)
                    0,                                    // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v6 differs from v5 layout (TopicNames vs Topics[] — silent fallback forbidden)")
        void v6DiffersFromV5() {
            byte[] v5 = DeleteTopicsCodec.encodeRequest((short) 5, req(30000));
            byte[] v6 = DeleteTopicsCodec.encodeRequest((short) 6, req(30000));
            assertEquals(20, v5.length);
            assertEquals(54, v6.length);
        }
    }

    // ── Fixed-width response (v0–v3) ────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width response (v0–v3)")
    class ResponseLayout {

        @Test
        @DisplayName("v0 = int32 numResults + [int16 name, int16 errorCode]* (byte-for-byte)")
        void v0() {
            byte[] b = DeleteTopicsCodec.encodeResponse((short) 0, resp((short) 0));
            // 4 numResults + (8+2) + (8+2) = 24
            assertEquals(24, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 2,                 // numResults = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 0,                       // ErrorCode = 0
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',  // "events"
                    0, 0,                       // ErrorCode = 0
            });
        }

        @Test
        @DisplayName("v1 adds ThrottleTimeMs=int32 (spec default 0) before results; v2–v3 identical")
        void v1ToV3() {
            byte[] b1 = DeleteTopicsCodec.encodeResponse((short) 1, resp((short) 0));
            assertEquals(28, b1.length); // v0 (24) + 4 throttle
            assertArrayEquals(b1, new byte[]{
                    0, 0, 0, 0,                 // ThrottleTimeMs = 0
                    0, 0, 0, 2,                 // numResults = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',
                    0, 0,
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',
                    0, 0,
            });
            for (short v : new short[]{2, 3}) {
                assertArrayEquals(b1, DeleteTopicsCodec.encodeResponse(v, resp((short) 0)), "v" + v);
            }
        }
    }

    // ── Flexible response (KIP-482, v4–v6) ──────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v4 layout byte-for-byte (ThrottleTimeMs + N+1 count + [compact name, errorCode, endTags] + endTags)")
        void v4Layout() {
            byte[] b = DeleteTopicsCodec.encodeResponse((short) 4, resp((short) 0));
            // 4 throttle + 1 count + (7+2+1) + (7+2+1) + 1 endTags = 26
            assertEquals(26, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                           // ThrottleTimeMs = 0
                    3,                                    // numResults varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',      // "orders" varint(6+1)=7
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // per-entry endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',      // "events" varint(6+1)=7
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // per-entry endTags = 0
                    0,                                    // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v5 adds per-result ErrorMessage (compact-nullable, absent = varint 0)")
        void v5Layout() {
            byte[] b = DeleteTopicsCodec.encodeResponse((short) 5, resp((short) 0));
            assertEquals(28, b.length); // v4 (26) + 2 absent ErrorMessage varints
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                           // ThrottleTimeMs = 0
                    3,                                    // numResults varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // ErrorMessage absent (null)
                    0,                                    // per-entry endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // ErrorMessage absent (null)
                    0,                                    // per-entry endTags = 0
                    0,                                    // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v6 adds per-result TopicId (uuid 16 bytes, all-zero absent default) between Name and ErrorCode")
        void v6Layout() {
            byte[] b = DeleteTopicsCodec.encodeResponse((short) 6, resp((short) 0));
            // v5 (28) + 2*16 TopicId = 60
            assertEquals(60, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                           // ThrottleTimeMs = 0
                    3,                                    // numResults varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // TopicId all-zero
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // ErrorMessage absent (null)
                    0,                                    // per-entry endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // TopicId all-zero
                    0, 0,                                 // ErrorCode = 0
                    0,                                    // ErrorMessage absent (null)
                    0,                                    // per-entry endTags = 0
                    0,                                    // top-level endTags = 0
            });
        }
    }

    // ── Full-range round-trips (v0–v6) ──────────────────────────────────

    @Nested
    @DisplayName("Full-range round-trips (v0–v6)")
    class RoundTrips {

        @Test
        @DisplayName("request round-trips at every version 0–6")
        void requestAllVersions() {
            for (short v = 0; v <= 6; v++) {
                DeleteTopicsRequest decoded = roundTripReq(v, req(30000));
                assertEquals(List.of("orders", "events"), decoded.topicNames(), "v" + v);
                assertEquals(30000, decoded.timeoutMs(), "v" + v);
            }
        }

        @Test
        @DisplayName("response round-trips at every version 0–6")
        void responseAllVersions() {
            for (short v = 0; v <= 6; v++) {
                DeleteTopicsResponse decoded = roundTripResp(v, resp((short) 0));
                assertEquals(2, decoded.responses().size(), "v" + v);
                assertEquals("orders", decoded.responses().get(0).name(), "v" + v);
                assertEquals((short) 0, decoded.responses().get(0).errorCode(), "v" + v);
                assertEquals("events", decoded.responses().get(1).name(), "v" + v);
            }
        }

        @Test
        @DisplayName("empty response round-trip at v4 (flexible, zero results)")
        void emptyResponseV4() {
            DeleteTopicsResponse decoded = roundTripResp((short) 4,
                    new DeleteTopicsResponse(List.of()));
            assertEquals(List.of(), decoded.responses());
        }
    }

    // ── Version guard ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version guard")
    class VersionGuard {

        @Test
        @DisplayName("request encode/decode reject versions outside 0–6")
        void requestVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.encodeRequest((short) 7, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.encodeRequest((short) -1, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.decodeRequest((short) 7, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response encode/decode reject versions outside 0–6")
        void responseVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.encodeResponse((short) 7, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.encodeResponse((short) -1, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteTopicsCodec.decodeResponse((short) 7, ByteBuffer.wrap(new byte[0])));
        }
    }
}
