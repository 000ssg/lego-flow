package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.DeleteGroupsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteGroupsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link DeleteGroupsCodec} — Kafka DeleteGroups request/response (API key 42).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v2. Byte-layout assertions in the flexible (KIP-482) v2 verify the
 * compact-array/compact-string encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>compact non-nullable strings for the group IDs (varint(length + 1) + UTF-8 bytes);</li>
 *   <li>{@code ErrorCode} (int16) keeps fixed width even in flexible layout;
 *       {@code ThrottleTimeMs} (int32) is present from v0 and always leads the response.</li>
 * </ul>
 */
@DisplayName("DeleteGroupsCodec (API key 42)")
class DeleteGroupsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static DeleteGroupsRequest req() {
        return new DeleteGroupsRequest(List.of("orders", "events"));
    }

    private static DeleteGroupsRequest reqSingle() {
        return new DeleteGroupsRequest(List.of("orders"));
    }

    private static DeleteGroupsResponse resp(short err) {
        return new DeleteGroupsResponse(List.of(
                new DeleteGroupsResponse.GroupResult("orders", err),
                new DeleteGroupsResponse.GroupResult("events", err)));
    }

    private static DeleteGroupsResponse respSingle(short err) {
        return new DeleteGroupsResponse(List.of(
                new DeleteGroupsResponse.GroupResult("orders", err)));
    }

    private static DeleteGroupsRequest roundTripReq(short v, DeleteGroupsRequest r) {
        byte[] b = DeleteGroupsCodec.encodeRequest(v, r);
        return DeleteGroupsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static DeleteGroupsResponse roundTripResp(short v, DeleteGroupsResponse r) {
        byte[] b = DeleteGroupsCodec.encodeResponse(v, r);
        return DeleteGroupsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, DeleteGroupsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = DeleteGroupsCodec.encodeRequest((short) 0, req());
            byte[] oneArg = DeleteGroupsCodec.encodeRequest(req());
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = DeleteGroupsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] oneArg = DeleteGroupsCodec.encodeResponse(resp((short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("v0 request round-trip preserves group IDs")
        void requestRoundTrip() {
            DeleteGroupsRequest decoded = roundTripReq((short) 0, req());
            assertEquals(List.of("orders", "events"), decoded.groups());
        }

        @Test
        @DisplayName("v0 response round-trip preserves groupId + errorCode")
        void responseRoundTrip() {
            DeleteGroupsResponse decoded = roundTripResp((short) 0, resp((short) 16));
            assertEquals(2, decoded.results().size());
            assertEquals("orders", decoded.results().get(0).groupId());
            assertEquals((short) 16, decoded.results().get(0).errorCode());
            assertEquals("events", decoded.results().get(1).groupId());
            assertEquals((short) 16, decoded.results().get(1).errorCode());
        }

        @Test
        @DisplayName("v0 request round-trip preserves empty group list")
        void requestRoundTripEmpty() {
            DeleteGroupsRequest decoded = roundTripReq((short) 0, new DeleteGroupsRequest(List.of()));
            assertEquals(List.of(), decoded.groups());
        }

        @Test
        @DisplayName("v0 response round-trip preserves empty results")
        void responseRoundTripEmpty() {
            DeleteGroupsResponse decoded = roundTripResp((short) 0, new DeleteGroupsResponse(List.of()));
            assertEquals(List.of(), decoded.results());
        }
    }

    // ── Fixed-width request (v0–v1) ─────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width request (v0–v1)")
    class RequestLayout {

        @Test
        @DisplayName("v0 layout byte-for-byte (int32 count + [int16 string]*): 2 groups")
        void v0() {
            byte[] b = DeleteGroupsCodec.encodeRequest((short) 0, req());
            // 4 numGroups + 8 "orders" + 8 "events" = 20
            assertEquals(20, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 2,                             // numGroups = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 6, 'e', 'v', 'e', 'n', 't', 's'      // "events"
            });
        }

        @Test
        @DisplayName("v1 = v0 byte-identical (spec: 'Version 1 is the same as version 0')")
        void v1EqualsV0() {
            byte[] v0 = DeleteGroupsCodec.encodeRequest((short) 0, req());
            byte[] v1 = DeleteGroupsCodec.encodeRequest((short) 1, req());
            assertArrayEquals(v0, v1);
        }

        @Test
        @DisplayName("v0 layout with empty group list")
        void v0Empty() {
            byte[] b = DeleteGroupsCodec.encodeRequest((short) 0, new DeleteGroupsRequest(List.of()));
            assertArrayEquals(b, new byte[]{0, 0, 0, 0});
        }
    }

    // ── Flexible request (KIP-482, v2) ──────────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v2 count varint = size + 1 (2 groups → varint 3)")
        void v2CountIsPlusOne() {
            byte[] b = DeleteGroupsCodec.encodeRequest((short) 2, req());
            assertEquals(3, b[0]); // numGroups varint = 2 + 1
        }

        @Test
        @DisplayName("v2 layout byte-for-byte (compact strings, N+1 count, endTags=0)")
        void v2Layout() {
            byte[] b = DeleteGroupsCodec.encodeRequest((short) 2, req());
            // 1 numGroups + 7 "orders" + 7 "events" + 1 endTags = 16
            assertEquals(16, b.length);
            assertArrayEquals(b, new byte[]{
                    3,                                     // numGroups varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 layout with empty group list (varint 1 = 0+1, endTags=0)")
        void v2LayoutEmpty() {
            byte[] b = DeleteGroupsCodec.encodeRequest((short) 2, new DeleteGroupsRequest(List.of()));
            assertArrayEquals(b, new byte[]{1, 0});
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = DeleteGroupsCodec.encodeRequest((short) 1, req());
            byte[] v2 = DeleteGroupsCodec.encodeRequest((short) 2, req());
            assertEquals(20, v1.length);
            assertEquals(16, v2.length);
        }

        @Test
        @DisplayName("v2 round-trip preserves group IDs")
        void roundTrip() {
            DeleteGroupsRequest decoded = roundTripReq((short) 2, req());
            assertEquals(List.of("orders", "events"), decoded.groups());
        }

        @Test
        @DisplayName("v2 round-trip preserves empty group list")
        void roundTripEmpty() {
            DeleteGroupsRequest decoded = roundTripReq((short) 2, new DeleteGroupsRequest(List.of()));
            assertEquals(List.of(), decoded.groups());
        }
    }

    // ── Fixed-width response (v0–v1) ────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width response (v0–v1)")
    class ResponseLayout {

        @Test
        @DisplayName("v0 = int32 ThrottleTimeMs(0 default) + int32 numResults + [int16 string, int16 err]* (byte-for-byte)")
        void v0() {
            byte[] b = DeleteGroupsCodec.encodeResponse((short) 0, resp((short) 16));
            // 4 throttle + 4 numResults + 8 + 2 + 8 + 2 = 28
            assertEquals(28, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    0, 0, 0, 2,                             // numResults = 2
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 16,                                   // ErrorCode = 16 (UNKNOWN MemberId)
                    0, 6, 'e', 'v', 'e', 'n', 't', 's',     // "events"
                    0, 16                                    // ErrorCode = 16
            });
        }

        @Test
        @DisplayName("v1 = v0 byte-identical")
        void v1EqualsV0() {
            byte[] v0 = DeleteGroupsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] v1 = DeleteGroupsCodec.encodeResponse((short) 1, resp((short) 0));
            assertArrayEquals(v0, v1);
        }

        @Test
        @DisplayName("v0 response round-trip (single group, non-zero error code)")
        void v0RoundTrip() {
            DeleteGroupsResponse decoded = roundTripResp((short) 0, respSingle((short) 22));
            assertEquals(1, decoded.results().size());
            assertEquals("orders", decoded.results().get(0).groupId());
            assertEquals((short) 22, decoded.results().get(0).errorCode());
        }
    }

    // ── Flexible response (KIP-482, v2) ─────────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v2 layout byte-for-byte (throttle, N+1 count, compact groupId, int16 err, per-result + top endTags=0)")
        void v2Layout() {
            byte[] b = DeleteGroupsCodec.encodeResponse((short) 2, resp((short) 16));
            // 4 throttle + 1 count + (7 + 2 + 1) + (7 + 2 + 1) + 1 endTags = 26
            assertEquals(26, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    3,                                     // numResults varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    0, 16,                                   // ErrorCode = 16
                    0,                                     // per-result endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    0, 16,                                   // ErrorCode = 16
                    0,                                     // per-result endTags = 0
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 count varint = size + 1 at the results array (1 result → varint 2)")
        void v2CountIsPlusOne() {
            byte[] b = DeleteGroupsCodec.encodeResponse((short) 2, respSingle((short) 0));
            assertEquals(2, b[4]); // numResults varint = 1 + 1 (after 4-byte throttle)
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = DeleteGroupsCodec.encodeResponse((short) 1, resp((short) 0));
            byte[] v2 = DeleteGroupsCodec.encodeResponse((short) 2, resp((short) 0));
            assertEquals(28, v1.length);
            assertEquals(26, v2.length);
        }

        @Test
        @DisplayName("v2 round-trip preserves groupId + errorCode")
        void roundTrip() {
            DeleteGroupsResponse decoded = roundTripResp((short) 2, resp((short) 16));
            assertEquals(2, decoded.results().size());
            assertEquals("orders", decoded.results().get(0).groupId());
            assertEquals((short) 16, decoded.results().get(0).errorCode());
            assertEquals("events", decoded.results().get(1).groupId());
            assertEquals((short) 16, decoded.results().get(1).errorCode());
        }

        @Test
        @DisplayName("v2 round-trip preserves empty results")
        void roundTripEmpty() {
            DeleteGroupsResponse decoded = roundTripResp((short) 2, new DeleteGroupsResponse(List.of()));
            assertEquals(List.of(), decoded.results());
        }
    }

    // ── Version range ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version range")
    class Versions {

        @Test
        @DisplayName("encodeRequest rejects v3+ (spec range 0–2)")
        void requestRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteGroupsCodec.encodeRequest((short) 3, req()));
        }

        @Test
        @DisplayName("encodeResponse rejects v3+ (spec range 0–2)")
        void responseRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteGroupsCodec.encodeResponse((short) 3, resp((short) 0)));
        }

        @Test
        @DisplayName("decodeRequest rejects v3+ (spec range 0–2)")
        void decodeRequestRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteGroupsCodec.decodeRequest((short) 3, ByteBuffer.allocate(4)));
        }

        @Test
        @DisplayName("decodeResponse rejects v3+ (spec range 0–2)")
        void decodeResponseRejectsOutOfRange() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteGroupsCodec.decodeResponse((short) 3, ByteBuffer.allocate(4)));
        }
    }
}
