package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.AlterPartitionReassignmentsRequest;
import ssg.legoflow.messaging.kafka.protocol.AlterPartitionReassignmentsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link AlterPartitionReassignmentsCodec} — Kafka
 * AlterPartitionReassignments request/response (API key 45).
 *
 * <p>Covers the single version v0. Byte-layout assertions verify the KIP-482
 * flexible encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>{@code TimeoutMs} (int32, <em>fixed</em> width) leads the request body — it
 *       precedes the flexible KIP-482 schema; only the {@code Topics[]} array and
 *       string fields below it use compact encoding;</li>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>nullable compact array (Replicas) encodes null as {@code varint 0};</li>
 *   <li>response {@code ThrottleTimeMs(int32)} + {@code ErrorCode(int16)} + nullable
 *       {@code ErrorMessage} (varint 0 for null) lead the body before {@code Topics[]}.</li>
 * </ul>
 */
@DisplayName("AlterPartitionReassignmentsCodec (API key 45)")
class AlterPartitionReassignmentsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static AlterPartitionReassignmentsRequest.PartitionReassignment part(int id, List<Integer> replicas) {
        return new AlterPartitionReassignmentsRequest.PartitionReassignment(id, replicas);
    }

    private static AlterPartitionReassignmentsRequest.TopicReassignment topic(String name,
            List<AlterPartitionReassignmentsRequest.PartitionReassignment> parts) {
        return new AlterPartitionReassignmentsRequest.TopicReassignment(name, parts);
    }

    /** v0 request with one topic, two partitions — one with replicas, one without (null). */
    private static AlterPartitionReassignmentsRequest req() {
        return new AlterPartitionReassignmentsRequest(30_000, List.of(
                topic("orders", List.of(
                        part(0, List.of(1, 2, 3)),
                        part(1, null)))));
    }

    /** v0 request with one topic, one partition, two replicas. */
    private static AlterPartitionReassignmentsRequest reqSingle() {
        return new AlterPartitionReassignmentsRequest(10_000, List.of(
                topic("t1", List.of(part(7, List.of(2, 5))))));
    }

    private static AlterPartitionReassignmentsRequest reqEmpty() {
        return new AlterPartitionReassignmentsRequest(30_000, List.of());
    }

    private static AlterPartitionReassignmentsResponse.PartitionResult presult(int id, short err) {
        return new AlterPartitionReassignmentsResponse.PartitionResult(id, err);
    }

    private static AlterPartitionReassignmentsResponse.TopicResult tresult(String name,
            List<AlterPartitionReassignmentsResponse.PartitionResult> parts) {
        return new AlterPartitionReassignmentsResponse.TopicResult(name, parts);
    }

    private static AlterPartitionReassignmentsResponse resp() {
        return new AlterPartitionReassignmentsResponse((short) 0, List.of(
                tresult("orders", List.of(presult(0, (short) 0), presult(1, (short) 14)))));
    }

    private static AlterPartitionReassignmentsResponse respSingle(short err) {
        return new AlterPartitionReassignmentsResponse(err, List.of(
                tresult("t1", List.of(presult(7, (short) 0)))));
    }

    private static AlterPartitionReassignmentsRequest roundTripReq(short v, AlterPartitionReassignmentsRequest r) {
        byte[] b = AlterPartitionReassignmentsCodec.encodeRequest(v, r);
        return AlterPartitionReassignmentsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static AlterPartitionReassignmentsResponse roundTripResp(short v, AlterPartitionReassignmentsResponse r) {
        byte[] b = AlterPartitionReassignmentsCodec.encodeResponse(v, r);
        return AlterPartitionReassignmentsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Version contract ────────────────────────────────────────────────

    @Test
    @DisplayName("minimum version is v0 (KafkaAdminClient wire format)")
    void pinnedVersion() {
        assertEquals((short) 0, AlterPartitionReassignmentsCodec.MIN_VERSION);
    }

    @Test
    @DisplayName("v0 encodeRequest/encodeResponse are versioned calls (unpinned facade)")
    void pinnedEncodeRequest() {
        byte[] b = AlterPartitionReassignmentsCodec.encodeRequest((short) 0, req());
        AlterPartitionReassignmentsRequest decoded = AlterPartitionReassignmentsCodec.decodeRequest(
                (short) 0, ByteBuffer.wrap(b));
        assertEquals(30_000, decoded.timeoutMs());
        assertEquals(1, decoded.topics().size());
        assertEquals("orders", decoded.topics().get(0).topic());
    }

    @Test
    @DisplayName("v0 encodeResponse round-trips at the pinned version")
    void pinnedEncodeResponse() {
        byte[] b = AlterPartitionReassignmentsCodec.encodeResponse((short) 0, resp());
        AlterPartitionReassignmentsResponse decoded = AlterPartitionReassignmentsCodec.decodeResponse(
                (short) 0, ByteBuffer.wrap(b));
        assertEquals(0, decoded.errorCode());
        assertEquals(1, decoded.topics().size());
        assertEquals(2, decoded.topics().get(0).partitions().size());
    }

    @Test
    @DisplayName("version 1+ is not implemented (only v0 in spec)")
    void versionRange() {
        assertThrows(CodecNotImplementedException.class,
                () -> AlterPartitionReassignmentsCodec.encodeRequest((short) 1, req()));
        assertThrows(CodecNotImplementedException.class,
                () -> AlterPartitionReassignmentsCodec.decodeRequest((short) 1, ByteBuffer.allocate(0)));
        assertThrows(CodecNotImplementedException.class,
                () -> AlterPartitionReassignmentsCodec.encodeResponse((short) 1, resp()));
    }

    // ── Request round-trips ─────────────────────────────────────────────

    @Test
    @DisplayName("v0 request round-trips: timeout, topics, partitions, null/non-null replicas")
    void roundTripRequest() {
        AlterPartitionReassignmentsRequest r = roundTripReq((short) 0, req());
        assertEquals(30_000, r.timeoutMs());
        assertEquals(1, r.topics().size());
        var t = r.topics().get(0);
        assertEquals("orders", t.topic());
        assertEquals(2, t.partitions().size());
        assertEquals(0, t.partitions().get(0).partition());
        assertEquals(List.of(1, 2, 3), t.partitions().get(0).replicas());
        assertEquals(1, t.partitions().get(1).partition());
        assertNull(t.partitions().get(1).replicas());
    }

    @Test
    @DisplayName("v0 empty request round-trips")
    void roundTripEmptyRequest() {
        AlterPartitionReassignmentsRequest r = roundTripReq((short) 0, reqEmpty());
        assertEquals(30_000, r.timeoutMs());
        assertEquals(0, r.topics().size());
    }

    // ── Request byte layout (v0) ────────────────────────────────────────

    @Test
    @DisplayName("v0 request bytes: int32 timeout + compact topics, non-null replicas array")
    void bytesRequest() {
        // reqSingle: timeout 10_000; topics [1]: "t1" (2 bytes); partitions [1]: 7, [2]: 2, 5
        byte[] expected = new byte[]{
                0x00, 0x00, 0x27, 0x10,                     // timeoutMs = 10_000
                0x02,                                       // topics N+1 = 2
                0x03, 't', '1',                             // compact "t1" (N+1=3)
                0x02,                                       // partitions N+1 = 2
                0x00, 0x00, 0x00, 0x07,                     // partition 7
                0x03, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x05, // N+1=2: 2, 5
                0x00,                                               // partition endTags
                0x00,                                               // topic endTags
                0x00};                                              // top endTags
        assertArrayEquals(expected, AlterPartitionReassignmentsCodec.encodeRequest((short) 0, reqSingle()));
    }

    @Test
    @DisplayName("v0 request bytes: null replicas encoded as compact varint 0")
    void bytesRequestNullReplicas() {
        // req(): timeout 30_000; topics [1]: "orders" (6 bytes); partitions [2]:
        //   0: replicas [1,2,3]; 1: replicas null (varint 0)
        byte[] expected = new byte[]{
                0x00, 0x00, 0x75, 0x30,                     // timeoutMs = 30_000
                0x02,                                       // topics N+1 = 2
                0x07, 'o', 'r', 'd', 'e', 'r', 's',         // compact "orders" (N+1=7)
                0x03,                                       // partitions N+1 = 3
                0x00, 0x00, 0x00, 0x00,                     // partition 0
                0x04,                                       // replicas N+1 = 4
                0x00, 0x00, 0x00, 0x01,                     // 1
                0x00, 0x00, 0x00, 0x02,                     // 2
                0x00, 0x00, 0x00, 0x03,                     // 3
                0x00,                                       // partition endTags
                0x00, 0x00, 0x00, 0x01,                     // partition 1
                0x00,                                       // replicas null → varint 0
                0x00,                                       // partition endTags
                0x00,                                       // topic endTags
                0x00};                                      // top endTags
        assertArrayEquals(expected, AlterPartitionReassignmentsCodec.encodeRequest((short) 0, req()));
    }

    // ── Response round-trips ────────────────────────────────────────────

    @Test
    @DisplayName("v0 response round-trips: errorCode + nested topic/partition results")
    void roundTripResponse() {
        AlterPartitionReassignmentsResponse r = roundTripResp((short) 0, resp());
        assertEquals(0, r.errorCode());
        assertEquals(1, r.topics().size());
        var t = r.topics().get(0);
        assertEquals("orders", t.topic());
        assertEquals(2, t.partitions().size());
        assertEquals(0, t.partitions().get(0).partition());
        assertEquals(0, t.partitions().get(0).errorCode());
        assertEquals(1, t.partitions().get(1).partition());
        assertEquals(14, t.partitions().get(1).errorCode());
    }

    @Test
    @DisplayName("v0 response round-trips with non-zero top-level errorCode")
    void roundTripResponseWithTopLevelError() {
        AlterPartitionReassignmentsResponse r = roundTripResp((short) 0, respSingle((short) 22));
        assertEquals(22, r.errorCode());
        assertEquals(1, r.topics().size());
        assertEquals("t1", r.topics().get(0).topic());
    }

    @Test
    @DisplayName("v0 empty response round-trips")
    void roundTripEmptyResponse() {
        AlterPartitionReassignmentsResponse r = roundTripResp((short) 0,
                new AlterPartitionReassignmentsResponse((short) 0, List.of()));
        assertEquals(0, r.errorCode());
        assertEquals(0, r.topics().size());
    }

    // ── Response byte layout (v0) ───────────────────────────────────────

    @Test
    @DisplayName("v0 response bytes: throttle + errorCode + null error message + compact topics")
    void bytesResponse() {
        // respSingle((short) 22): throttle 0, errorCode 22, errorMessage null (varint 0),
        // topics [1]: "t1"; partitions [1]: 7, errorCode 0
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0
                0x00, 0x16,                                 // ErrorCode = 22
                0x00,                                       // ErrorMessage null → varint 0
                0x02,                                       // topics N+1 = 2
                0x03, 't', '1',                             // compact "t1"
                0x02,                                       // partitions N+1 = 2
                0x00, 0x00, 0x00, 0x07,                     // partition 7
                0x00, 0x00,                                 // errorCode 0
                0x00,                                       // partition ErrorMessage null → varint 0
                0x00,                                       // partition endTags
                0x00,                                       // topic endTags
                0x00};                                      // top endTags
        assertArrayEquals(expected, AlterPartitionReassignmentsCodec.encodeResponse((short) 0, respSingle((short) 22)));
    }

    // ── Encoding properties ─────────────────────────────────────────────

    @Test
    @DisplayName("encoding is deterministic (two encodes → identical bytes)")
    void deterministic() {
        assertArrayEquals(
                AlterPartitionReassignmentsCodec.encodeRequest((short) 0, req()),
                AlterPartitionReassignmentsCodec.encodeRequest((short) 0, req()));
        assertArrayEquals(
                AlterPartitionReassignmentsCodec.encodeResponse((short) 0, resp()),
                AlterPartitionReassignmentsCodec.encodeResponse((short) 0, resp()));
    }

    // ── Facade wiring ───────────────────────────────────────────────────

    @Test
    @DisplayName("KafkaCodec facade delegates to AlterPartitionReassignmentsCodec")
    void facadeDelegation() {
        byte[] reqFacade = KafkaCodec.encodeAlterPartitionReassignmentsRequest((short) 0, req());
        byte[] reqDirect = AlterPartitionReassignmentsCodec.encodeRequest((short) 0, req());
        assertArrayEquals(reqDirect, reqFacade);

        AlterPartitionReassignmentsRequest decodedFacade =
                KafkaCodec.decodeAlterPartitionReassignmentsRequest((short) 0, ByteBuffer.wrap(reqDirect));
        assertEquals(30_000, decodedFacade.timeoutMs());

        byte[] respFacade = KafkaCodec.encodeAlterPartitionReassignmentsResponse((short) 0, resp());
        byte[] respDirect = AlterPartitionReassignmentsCodec.encodeResponse((short) 0, resp());
        assertArrayEquals(respDirect, respFacade);

        AlterPartitionReassignmentsResponse decodedResp =
                KafkaCodec.decodeAlterPartitionReassignmentsResponse((short) 0, ByteBuffer.wrap(respDirect));
        assertEquals(1, decodedResp.topics().size());
        assertEquals(14, decodedResp.topics().get(0).partitions().get(1).errorCode());
    }

    // ── Pinned-version integration ──────────────────────────────────────

    @Test
    @DisplayName("pinned v0 round-trip through encode/decode of the full shapes")
    void pinnedRoundTrip() {
        var r = roundTripReq((short) 0, req());
        assertEquals(req().topics().get(0).topic(), r.topics().get(0).topic());

        var s = roundTripResp((short) 0, resp());
        assertEquals(resp().topics().get(0).topic(), s.topics().get(0).topic());
    }

    @Test
    @DisplayName("empty partition list within a non-empty topic round-trips")
    void emptyPartitionList() {
        var r = roundTripReq((short) 0,
                new AlterPartitionReassignmentsRequest(5_000, List.of(
                        topic("no-parts", List.of()))));
        assertEquals(1, r.topics().size());
        assertEquals(0, r.topics().get(0).partitions().size());
    }
}
