package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.DeleteRecordsRequest;
import ssg.legoflow.messaging.kafka.protocol.DeleteRecordsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link DeleteRecordsCodec} — Kafka DeleteRecords request/response (API key 21).
 *
 * <p>Covers the pinned version (v0, the shape {@code KafkaAdminClient} sends) and the full
 * implemented range v0–v2. Byte-layout assertions in the flexible (KIP-482) v2 verify the
 * compact-array count encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read
 *       (arrays of structs, including the nested Partitions array);</li>
 *   <li>compact strings for the topic name;</li>
 *   <li>fixed-width fields ({@code PartitionIndex} int32, {@code Offset}/{@code LowWatermark}
 *       int64, {@code ErrorCode} int16, {@code TimeoutMs} int32) keep fixed width even in
 *       flexible layout; {@code ThrottleTimeMs} (int32) is present from v0 and always leads
 *       the response.</li>
 * </ul>
 */
@DisplayName("DeleteRecordsCodec (API key 21)")
class DeleteRecordsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static DeleteRecordsRequest req(int timeoutMs) {
        return new DeleteRecordsRequest(List.of(new DeleteRecordsRequest.TopicData(
                "orders", List.of(
                        new DeleteRecordsRequest.PartitionData(0, 1000L),
                        new DeleteRecordsRequest.PartitionData(1, 2000L)))), timeoutMs);
    }

    private static DeleteRecordsRequest reqMulti() {
        return new DeleteRecordsRequest(List.of(
                new DeleteRecordsRequest.TopicData("orders", List.of(
                        new DeleteRecordsRequest.PartitionData(0, 1000L))),
                new DeleteRecordsRequest.TopicData("events", List.of(
                        new DeleteRecordsRequest.PartitionData(0, 500L),
                        new DeleteRecordsRequest.PartitionData(1, 700L),
                        new DeleteRecordsRequest.PartitionData(3, 900L)))), 30000);
    }

    private static DeleteRecordsResponse resp(short err) {
        return new DeleteRecordsResponse(List.of(new DeleteRecordsResponse.TopicData(
                "orders", List.of(
                        new DeleteRecordsResponse.PartitionData(0, 999L, err),
                        new DeleteRecordsResponse.PartitionData(1, 1999L, err)))));
    }

    private static DeleteRecordsResponse respMulti(short err) {
        return new DeleteRecordsResponse(List.of(
                new DeleteRecordsResponse.TopicData("orders", List.of(
                        new DeleteRecordsResponse.PartitionData(0, 999L, err))),
                new DeleteRecordsResponse.TopicData("events", List.of(
                        new DeleteRecordsResponse.PartitionData(0, 499L, err),
                        new DeleteRecordsResponse.PartitionData(1, 699L, err),
                        new DeleteRecordsResponse.PartitionData(3, 899L, err)))));
    }

    private static DeleteRecordsRequest roundTripReq(short v, DeleteRecordsRequest r) {
        byte[] b = DeleteRecordsCodec.encodeRequest(v, r);
        return DeleteRecordsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static DeleteRecordsResponse roundTripResp(short v, DeleteRecordsResponse r) {
        byte[] b = DeleteRecordsCodec.encodeResponse(v, r);
        return DeleteRecordsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Pinned version ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pinned version (v0)")
    class Pinned {

        @Test
        @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
        void pinnedVersion() {
            assertEquals((short) 0, DeleteRecordsCodec.PINNED_VERSION);
        }

        @Test
        @DisplayName("single-arg encodeRequest pins to v0")
        void pinnedEncodeRequest() {
            byte[] pinned = DeleteRecordsCodec.encodeRequest((short) 0, req(30000));
            byte[] oneArg = DeleteRecordsCodec.encodeRequest(req(30000));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("single-arg encodeResponse pins to v0")
        void pinnedEncodeResponse() {
            byte[] pinned = DeleteRecordsCodec.encodeResponse((short) 0, resp((short) 0));
            byte[] oneArg = DeleteRecordsCodec.encodeResponse(resp((short) 0));
            assertArrayEquals(pinned, oneArg);
        }

        @Test
        @DisplayName("v0 request round-trip preserves topics, offsets + timeout")
        void requestRoundTrip() {
            DeleteRecordsRequest decoded = roundTripReq((short) 0, req(30000));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(2, decoded.topics().get(0).partitions().size());
            assertEquals(0, decoded.topics().get(0).partitions().get(0).partitionIndex());
            assertEquals(1000L, decoded.topics().get(0).partitions().get(0).offset());
            assertEquals(1, decoded.topics().get(0).partitions().get(1).partitionIndex());
            assertEquals(2000L, decoded.topics().get(0).partitions().get(1).offset());
            assertEquals(30000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("v0 response round-trip preserves lowWatermark + errorCode")
        void responseRoundTrip() {
            DeleteRecordsResponse decoded = roundTripResp((short) 0, resp((short) 0));
            assertEquals(1, decoded.topics().size());
            assertEquals("orders", decoded.topics().get(0).name());
            assertEquals(999L, decoded.topics().get(0).partitions().get(0).lowWatermark());
            assertEquals((short) 0, decoded.topics().get(0).partitions().get(0).errorCode());
            assertEquals(1999L, decoded.topics().get(0).partitions().get(1).lowWatermark());
        }
    }

    // ── Fixed-width request (v0–v1) ─────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width request (v0–v1)")
    class RequestLayout {

        @Test
        @DisplayName("v0 = int32 numTopics + [int16 name, int32 numParts, (int32 idx, int64 offset)*] + int32 timeoutMs (byte-for-byte)")
        void v0() {
            byte[] b = DeleteRecordsCodec.encodeRequest((short) 0, req(30000));
            // 4 numTopics + 8 name + 4 numParts + 2*(4+8) parts + 4 timeout = 44
            assertEquals(44, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 1,                          // numTopics = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',  // "orders"
                    0, 0, 0, 2,                          // numPartitions = 2
                    0, 0, 0, 0,                           // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 232,      // Offset = 1000 (0x3E8)
                    0, 0, 0, 1,                           // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 7, (byte) 208,      // Offset = 2000 (0x7D0)
                    0, 0, 117, 48,                        // timeoutMs = 30000 (0x7530)
            });
        }

        @Test
        @DisplayName("v1 request is byte-identical to v0 (spec: 'Version 1 is the same as version 0')")
        void v1Identical() {
            byte[] v0 = DeleteRecordsCodec.encodeRequest((short) 0, req(30000));
            assertArrayEquals(v0, DeleteRecordsCodec.encodeRequest((short) 1, req(30000)),
                    "request v1 must match v0");
        }

        @Test
        @DisplayName("empty topic list round-trip")
        void emptyTopics() {
            DeleteRecordsRequest decoded = roundTripReq((short) 0, new DeleteRecordsRequest(List.of(), 5000));
            assertEquals(List.of(), decoded.topics());
            assertEquals(5000, decoded.timeoutMs());
        }

        @Test
        @DisplayName("topic with zero partitions round-trip")
        void emptyPartitions() {
            DeleteRecordsRequest r = new DeleteRecordsRequest(
                    List.of(new DeleteRecordsRequest.TopicData("orders", List.of())), 5000);
            DeleteRecordsRequest decoded = roundTripReq((short) 0, r);
            assertEquals(1, decoded.topics().size());
            assertEquals(List.of(), decoded.topics().get(0).partitions());
        }
    }

    // ── Flexible request (KIP-482, v2) ──────────────────────────────────

    @Nested
    @DisplayName("Flexible request (KIP-482)")
    class FlexibleRequest {

        @Test
        @DisplayName("v2 count varint = size + 1 at every array level (1 topic / 2 partitions → varints 2 and 3)")
        void v2CountIsPlusOne() {
            byte[] b = DeleteRecordsCodec.encodeRequest((short) 2, req(30000));
            assertEquals(2, b[0]);  // numTopics varint = 1 + 1
            assertEquals(3, b[8]);  // numPartitions varint = 2 + 1 (after compact "orders")
        }

        @Test
        @DisplayName("v2 layout byte-for-byte (compact name, N+1 counts, fixed-width fields, endTags=0)")
        void v2Layout() {
            byte[] b = DeleteRecordsCodec.encodeRequest((short) 2, req(30000));
            // 1 numTopics + 7 name + 1 numParts + 2*(12+1) parts + 1 topic endTags + 4 timeout + 1 endTags = 41
            assertEquals(41, b.length);
            assertArrayEquals(b, new byte[]{
                    2,                                     // numTopics varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    3,                                     // numPartitions varint = 2 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 232,        // Offset = 1000 (0x3E8)
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 1,                             // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 7, (byte) 208,        // Offset = 2000 (0x7D0)
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    0, 0, 117, 48,                          // timeoutMs = 30000 (0x7530)
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 multi-topic/multi-partition layout (1 + 3 partitions: varints 3 and 4)")
        void v2LayoutMulti() {
            byte[] b = DeleteRecordsCodec.encodeRequest((short) 2, reqMulti());
            // 1 numTopics + (7 name + 1 numParts + 13 part + 1 part end + 1 topic end)
            //            + (7 name + 1 numParts + 3*13 parts + 3 part ends + 1 topic end)
            //            + 4 timeout + 1 endTags = 1+20+60+5 = 76
            assertEquals(76, b.length);
            assertArrayEquals(b, new byte[]{
                    3,                                     // numTopics varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    2,                                     // numPartitions varint = 1 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 232,        // Offset = 1000 (0x3E8)
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    4,                                     // numPartitions varint = 3 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 1, (byte) 244,        // Offset = 500 (0x1F4)
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 1,                             // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 2, (byte) 188,        // Offset = 700 (0x2BC)
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 3,                             // PartitionIndex = 3
                    0, 0, 0, 0, 0, 0, 3, (byte) 132,        // Offset = 900 (0x384)
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    0, 0, 117, 48,                          // timeoutMs = 30000 (0x7530)
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = DeleteRecordsCodec.encodeRequest((short) 1, req(30000));
            byte[] v2 = DeleteRecordsCodec.encodeRequest((short) 2, req(30000));
            assertEquals(44, v1.length);
            assertEquals(41, v2.length);
        }

        @Test
        @DisplayName("v2 round-trip with uneven partition counts per topic")
        void roundTripUneven() {
            DeleteRecordsRequest decoded = roundTripReq((short) 2, reqMulti());
            assertEquals(2, decoded.topics().size());
            assertEquals(1, decoded.topics().get(0).partitions().size());
            assertEquals(3, decoded.topics().get(1).partitions().size());
            assertEquals(3, decoded.topics().get(1).partitions().get(2).partitionIndex());
            assertEquals(900L, decoded.topics().get(1).partitions().get(2).offset());
            assertEquals(30000, decoded.timeoutMs());
        }
    }

    // ── Fixed-width response (v0–v1) ────────────────────────────────────

    @Nested
    @DisplayName("Fixed-width response (v0–v1)")
    class ResponseLayout {

        @Test
        @DisplayName("v0 = int32 ThrottleTimeMs(0 default) + int32 numTopics + [int16 name, int32 numParts, (int32 idx, int64 lwm, int16 err)*] (byte-for-byte)")
        void v0() {
            byte[] b = DeleteRecordsCodec.encodeResponse((short) 0, resp((short) 0));
            // 4 throttle + 4 numTopics + (8 + 4 + 2*14) = 48
            assertEquals(48, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0 (spec default)
                    0, 0, 0, 1,                             // numTopics = 1
                    0, 6, 'o', 'r', 'd', 'e', 'r', 's',     // "orders"
                    0, 0, 0, 2,                             // numPartitions = 2
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 231,        // LowWatermark = 999 (0x3E7)
                    0, 0,                                   // ErrorCode = 0
                    0, 0, 0, 1,                             // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 7, (byte) 207,        // LowWatermark = 1999 (0x7CF)
                    0, 0                                    // ErrorCode = 0
            });
        }

        @Test
        @DisplayName("v1 response is byte-identical to v0 (spec: 'Version 1 is the same as version 0')")
        void v1Identical() {
            byte[] v0 = DeleteRecordsCodec.encodeResponse((short) 0, resp((short) 0));
            assertArrayEquals(v0, DeleteRecordsCodec.encodeResponse((short) 1, resp((short) 0)),
                    "response v1 must match v0");
        }

        @Test
        @DisplayName("non-zero errorCode round-trips")
        void nonZeroError() {
            DeleteRecordsResponse decoded = roundTripResp((short) 1, resp((short) 3));
            assertEquals((short) 3, decoded.topics().get(0).partitions().get(0).errorCode());
        }
    }

    // ── Flexible response (KIP-482, v2) ─────────────────────────────────

    @Nested
    @DisplayName("Flexible response (KIP-482)")
    class FlexibleResponse {

        @Test
        @DisplayName("v2 layout byte-for-byte (ThrottleTimeMs + N+1 counts + compact name + fixed-width fields + endTags=0)")
        void v2Layout() {
            byte[] b = DeleteRecordsCodec.encodeResponse((short) 2, resp((short) 0));
            // 4 throttle + 1 numTopics + 7 name + 1 numParts + 2*(14+1) + 1 topic end + 1 endTags = 45
            assertEquals(45, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0
                    2,                                     // numTopics varint = 1 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    3,                                     // numPartitions varint = 2 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 231,        // LowWatermark = 999 (0x3E7)
                    0, 0,                                   // ErrorCode = 0
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 1,                             // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 7, (byte) 207,        // LowWatermark = 1999 (0x7CF)
                    0, 0,                                   // ErrorCode = 0
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 multi-topic layout (1 + 3 partitions: varints 3 and 4)")
        void v2LayoutMulti() {
            byte[] b = DeleteRecordsCodec.encodeResponse((short) 2, respMulti((short) 1));
            // 4 + 1 + (7+1+15+1+1) + (7+1+3*15+3+1) + 1 = 4+1+20+54+1 = 84
            assertEquals(84, b.length);
            assertArrayEquals(b, new byte[]{
                    0, 0, 0, 0,                             // ThrottleTimeMs = 0
                    3,                                     // numTopics varint = 2 + 1
                    7, 'o', 'r', 'd', 'e', 'r', 's',       // "orders" varint(6+1)=7
                    2,                                     // numPartitions varint = 1 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 3, (byte) 231,        // LowWatermark = 999 (0x3E7)
                    0, 1,                                   // ErrorCode = 1
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    7, 'e', 'v', 'e', 'n', 't', 's',       // "events" varint(6+1)=7
                    4,                                     // numPartitions varint = 3 + 1
                    0, 0, 0, 0,                             // PartitionIndex = 0
                    0, 0, 0, 0, 0, 0, 1, (byte) 243,        // LowWatermark = 499 (0x1F3)
                    0, 1,                                   // ErrorCode = 1
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 1,                             // PartitionIndex = 1
                    0, 0, 0, 0, 0, 0, 2, (byte) 187,        // LowWatermark = 699 (0x2BB)
                    0, 1,                                   // ErrorCode = 1
                    0,                                     // per-partition endTags = 0
                    0, 0, 0, 3,                             // PartitionIndex = 3
                    0, 0, 0, 0, 0, 0, 3, (byte) 131,        // LowWatermark = 899 (0x383)
                    0, 1,                                   // ErrorCode = 1
                    0,                                     // per-partition endTags = 0
                    0,                                     // per-topic endTags = 0
                    0                                      // top-level endTags = 0
            });
        }

        @Test
        @DisplayName("v2 differs from v1 layout (flexible — silent fallback forbidden)")
        void v2DiffersFromV1() {
            byte[] v1 = DeleteRecordsCodec.encodeResponse((short) 1, resp((short) 0));
            byte[] v2 = DeleteRecordsCodec.encodeResponse((short) 2, resp((short) 0));
            assertEquals(48, v1.length);
            assertEquals(45, v2.length);
        }
    }

    // ── Full-range round-trips (v0–v2) ──────────────────────────────────

    @Nested
    @DisplayName("Full-range round-trips (v0–v2)")
    class RoundTrips {

        @Test
        @DisplayName("request round-trips at every version 0–2 (single topic)")
        void requestAllVersions() {
            for (short v = 0; v <= 2; v++) {
                DeleteRecordsRequest decoded = roundTripReq(v, req(30000));
                assertEquals(1, decoded.topics().size(), "v" + v);
                assertEquals("orders", decoded.topics().get(0).name(), "v" + v);
                assertEquals(2, decoded.topics().get(0).partitions().size(), "v" + v);
                assertEquals(1000L, decoded.topics().get(0).partitions().get(0).offset(), "v" + v);
                assertEquals(2000L, decoded.topics().get(0).partitions().get(1).offset(), "v" + v);
                assertEquals(30000, decoded.timeoutMs(), "v" + v);
            }
        }

        @Test
        @DisplayName("request round-trips at every version 0–2 (multi-topic, uneven partitions)")
        void requestMultiAllVersions() {
            for (short v = 0; v <= 2; v++) {
                DeleteRecordsRequest decoded = roundTripReq(v, reqMulti());
                assertEquals(2, decoded.topics().size(), "v" + v);
                assertEquals(1, decoded.topics().get(0).partitions().size(), "v" + v);
                assertEquals(3, decoded.topics().get(1).partitions().size(), "v" + v);
            }
        }

        @Test
        @DisplayName("response round-trips at every version 0–2")
        void responseAllVersions() {
            for (short v = 0; v <= 2; v++) {
                DeleteRecordsResponse decoded = roundTripResp(v, resp((short) 0));
                assertEquals(1, decoded.topics().size(), "v" + v);
                assertEquals("orders", decoded.topics().get(0).name(), "v" + v);
                assertEquals(999L, decoded.topics().get(0).partitions().get(0).lowWatermark(), "v" + v);
                assertEquals(1999L, decoded.topics().get(0).partitions().get(1).lowWatermark(), "v" + v);
                assertEquals((short) 0, decoded.topics().get(0).partitions().get(1).errorCode(), "v" + v);
            }
        }

        @Test
        @DisplayName("empty response round-trip at v2 (flexible, zero topics)")
        void emptyResponseV2() {
            DeleteRecordsResponse decoded = roundTripResp((short) 2, new DeleteRecordsResponse(List.of()));
            assertEquals(List.of(), decoded.topics());
        }

        @Test
        @DisplayName("empty request round-trip at v2 (flexible, zero topics)")
        void emptyRequestV2() {
            DeleteRecordsRequest decoded = roundTripReq((short) 2, new DeleteRecordsRequest(List.of(), 5000));
            assertEquals(List.of(), decoded.topics());
            assertEquals(5000, decoded.timeoutMs());
        }
    }

    // ── Version guard ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Version guard")
    class VersionGuard {

        @Test
        @DisplayName("request encode/decode reject versions outside 0–2")
        void requestVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.encodeRequest((short) 3, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.encodeRequest((short) -1, req(30000)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.decodeRequest((short) 3, ByteBuffer.wrap(new byte[0])));
        }

        @Test
        @DisplayName("response encode/decode reject versions outside 0–2")
        void responseVersionValidation() {
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.encodeResponse((short) 3, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.encodeResponse((short) -1, resp((short) 0)));
            assertThrows(CodecNotImplementedException.class,
                    () -> DeleteRecordsCodec.decodeResponse((short) 3, ByteBuffer.wrap(new byte[0])));
        }
    }
}
