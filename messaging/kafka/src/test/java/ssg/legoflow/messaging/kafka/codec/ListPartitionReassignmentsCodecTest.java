package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.ListPartitionReassignmentsRequest;
import ssg.legoflow.messaging.kafka.protocol.ListPartitionReassignmentsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link ListPartitionReassignmentsCodec} — Kafka
 * ListPartitionReassignments request/response (API key 46).
 *
 * <p>Covers the single version v0. Byte-layout assertions verify the KIP-482
 * flexible encoding against Apache Kafka 3.6.1 generated code:
 * <ul>
 *   <li>{@code TimeoutMs} (int32, <em>fixed</em> width) leads the request body — it
 *       precedes the flexible KIP-482 schema; only the {@code Topics[]} array and
 *       string fields below it use compact encoding;</li>
 *   <li>compact array length varint = {@code size + 1} on write, {@code varint - 1} on read;</li>
 *   <li>nullable {@code Topics[]} array encodes null as {@code varint 0};</li>
 *   <li>response {@code ThrottleTimeMs(int32)} + {@code ErrorCode(int16)} + nullable
 *       {@code ErrorMessage} (varint 0 for null) lead the body before {@code Topics[]}.</li>
 * </ul>
 */
@DisplayName("ListPartitionReassignmentsCodec (API key 46)")
class ListPartitionReassignmentsCodecTest {

    // ── Fixture builders ────────────────────────────────────────────────

    private static ListPartitionReassignmentsRequest.TopicData topic(String name, List<Integer> partitions) {
        return new ListPartitionReassignmentsRequest.TopicData(name, partitions);
    }

    /** v0 request with one topic, three partitions. */
    private static ListPartitionReassignmentsRequest req() {
        return new ListPartitionReassignmentsRequest(30_000, List.of(topic("orders", List.of(0, 1, 2))));
    }

    /** v0 request with one topic, one partition, non-null partition list. */
    private static ListPartitionReassignmentsRequest reqSingle() {
        return new ListPartitionReassignmentsRequest(10_000, List.of(topic("t1", List.of(7))));
    }

    /** v0 request with null topics (list everything). */
    private static ListPartitionReassignmentsRequest reqNullTopics() {
        return new ListPartitionReassignmentsRequest(30_000, null);
    }

    private static ListPartitionReassignmentsRequest reqEmpty() {
        return new ListPartitionReassignmentsRequest(30_000, List.of());
    }

    private static ListPartitionReassignmentsResponse.PartitionResult presult(int id,
            List<Integer> replicas, List<Integer> adding, List<Integer> removing) {
        return new ListPartitionReassignmentsResponse.PartitionResult(id, replicas, adding, removing);
    }

    private static ListPartitionReassignmentsResponse.TopicResult tresult(String name,
            List<ListPartitionReassignmentsResponse.PartitionResult> parts) {
        return new ListPartitionReassignmentsResponse.TopicResult(name, parts);
    }

    /** v0 response with one topic, one partition — full replica arrays. */
    private static ListPartitionReassignmentsResponse resp() {
        return new ListPartitionReassignmentsResponse((short) 0, List.of(
                tresult("orders", List.of(
                        presult(0, List.of(1, 2, 3), List.of(4), List.of(1))))));
    }

    private static ListPartitionReassignmentsResponse respSingle(short err) {
        return new ListPartitionReassignmentsResponse(err, List.of(
                tresult("t1", List.of(presult(7, List.of(2, 5), List.of(), List.of())))));
    }

    private static ListPartitionReassignmentsResponse respEmpty() {
        return new ListPartitionReassignmentsResponse((short) 0, List.of());
    }

    private static ListPartitionReassignmentsRequest roundTripReq(short v, ListPartitionReassignmentsRequest r) {
        byte[] b = ListPartitionReassignmentsCodec.encodeRequest(v, r);
        return ListPartitionReassignmentsCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static ListPartitionReassignmentsResponse roundTripResp(short v, ListPartitionReassignmentsResponse r) {
        byte[] b = ListPartitionReassignmentsCodec.encodeResponse(v, r);
        return ListPartitionReassignmentsCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Version contract ────────────────────────────────────────────────

    @Test
    @DisplayName("PINNED_VERSION is v0 (KafkaAdminClient wire format)")
    void pinnedVersion() {
        assertEquals((short) 0, ListPartitionReassignmentsCodec.PINNED_VERSION);
    }

    @Test
    @DisplayName("single-arg encodeRequest pins to v0")
    void pinnedEncodeRequest() {
        byte[] pinned = ListPartitionReassignmentsCodec.encodeRequest((short) 0, req());
        byte[] oneArg = ListPartitionReassignmentsCodec.encodeRequest(req());
        assertArrayEquals(pinned, oneArg);
    }

    @Test
    @DisplayName("single-arg encodeResponse pins to v0")
    void pinnedEncodeResponse() {
        byte[] pinned = ListPartitionReassignmentsCodec.encodeResponse((short) 0, resp());
        byte[] oneArg = ListPartitionReassignmentsCodec.encodeResponse(resp());
        assertArrayEquals(pinned, oneArg);
    }

    @Test
    @DisplayName("version 1+ is not implemented (only v0 in spec)")
    void versionRange() {
        assertThrows(CodecNotImplementedException.class,
                () -> ListPartitionReassignmentsCodec.encodeRequest((short) 1, req()));
        assertThrows(CodecNotImplementedException.class,
                () -> ListPartitionReassignmentsCodec.decodeRequest((short) 1, ByteBuffer.allocate(0)));
        assertThrows(CodecNotImplementedException.class,
                () -> ListPartitionReassignmentsCodec.encodeResponse((short) 1, resp()));
    }

    // ── Request round-trips ─────────────────────────────────────────────

    @Test
    @DisplayName("v0 request round-trips: timeout + topic partition indexes")
    void roundTripRequest() {
        ListPartitionReassignmentsRequest r = roundTripReq((short) 0, req());
        assertEquals(30_000, r.timeoutMs());
        assertEquals(1, r.topics().size());
        var t = r.topics().get(0);
        assertEquals("orders", t.topic());
        assertEquals(List.of(0, 1, 2), t.partitions());
    }

    @Test
    @DisplayName("v0 request round-trips with null topics (list everything)")
    void roundTripRequestNullTopics() {
        ListPartitionReassignmentsRequest r = roundTripReq((short) 0, reqNullTopics());
        assertEquals(30_000, r.timeoutMs());
        assertNull(r.topics());
    }

    @Test
    @DisplayName("v0 empty request round-trips")
    void roundTripEmptyRequest() {
        ListPartitionReassignmentsRequest r = roundTripReq((short) 0, reqEmpty());
        assertEquals(30_000, r.timeoutMs());
        assertEquals(0, r.topics().size());
    }

    // ── Request byte layout (v0) ────────────────────────────────────────

    @Test
    @DisplayName("v0 request bytes: int32 timeout + compact topics + compact partition indexes")
    void bytesRequest() {
        // reqSingle: timeout 10_000; topics [1]: "t1" (2 bytes); partitionIndexes [1]: 7
        byte[] expected = new byte[]{
                0x00, 0x00, 0x27, 0x10,                     // timeoutMs = 10_000
                0x02,                                       // topics N+1 = 2
                0x03, 't', '1',                             // compact "t1" (N+1=3)
                0x02,                                       // partitionIndexes N+1 = 2
                0x00, 0x00, 0x00, 0x07,                     // partition 7
                0x00,                                       // topic endTags
                0x00};                                      // top endTags
        assertArrayEquals(expected, ListPartitionReassignmentsCodec.encodeRequest(reqSingle()));
    }

    @Test
    @DisplayName("v0 request bytes: null topics encoded as compact varint 0")
    void bytesRequestNullTopics() {
        // reqNullTopics: timeout 30_000; topics null (varint 0)
        byte[] expected = new byte[]{
                0x00, 0x00, 0x75, 0x30,                     // timeoutMs = 30_000
                0x00,                                       // topics null → varint 0
                0x00};                                      // top endTags
        assertArrayEquals(expected, ListPartitionReassignmentsCodec.encodeRequest(reqNullTopics()));
    }

    // ── Response round-trips ────────────────────────────────────────────

    @Test
    @DisplayName("v0 response round-trips: errorCode + nested topic/partition + three replica arrays")
    void roundTripResponse() {
        ListPartitionReassignmentsResponse r = roundTripResp((short) 0, resp());
        assertEquals(0, r.errorCode());
        assertEquals(1, r.topics().size());
        var t = r.topics().get(0);
        assertEquals("orders", t.topic());
        assertEquals(1, t.partitions().size());
        var p = t.partitions().get(0);
        assertEquals(0, p.partition());
        assertEquals(List.of(1, 2, 3), p.replicas());
        assertEquals(List.of(4), p.addingReplicas());
        assertEquals(List.of(1), p.removingReplicas());
    }

    @Test
    @DisplayName("v0 response round-trips with non-zero top-level errorCode")
    void roundTripResponseWithTopLevelError() {
        ListPartitionReassignmentsResponse r = roundTripResp((short) 0, respSingle((short) 22));
        assertEquals(22, r.errorCode());
        assertEquals(1, r.topics().size());
        assertEquals("t1", r.topics().get(0).topic());
        var p = r.topics().get(0).partitions().get(0);
        assertEquals(7, p.partition());
        assertEquals(List.of(2, 5), p.replicas());
    }

    @Test
    @DisplayName("v0 empty response round-trips")
    void roundTripEmptyResponse() {
        ListPartitionReassignmentsResponse r = roundTripResp((short) 0, respEmpty());
        assertEquals(0, r.errorCode());
        assertEquals(0, r.topics().size());
    }

    // ── Response byte layout (v0) ───────────────────────────────────────

    @Test
    @DisplayName("v0 response bytes: throttle + errorCode + null error message + compact topics")
    void bytesResponse() {
        // respSingle((short) 22): throttle 0, errorCode 22, errorMessage null (varint 0),
        // topics [1]: "t1"; partitions [1]: 7, replicas [2,5], adding [], removing []
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0
                0x00, 0x16,                                 // ErrorCode = 22
                0x00,                                       // ErrorMessage null → varint 0
                0x02,                                       // topics N+1 = 2
                0x03, 't', '1',                             // compact "t1"
                0x02,                                       // partitions N+1 = 2
                0x00, 0x00, 0x00, 0x07,                     // partition 7
                0x03,                                       // replicas N+1 = 3
                0x00, 0x00, 0x00, 0x02,                     // 2
                0x00, 0x00, 0x00, 0x05,                     // 5
                0x01,                                       // adding N+1 = 1 (empty)
                0x01,                                       // removing N+1 = 1 (empty)
                0x00,                                       // partition endTags
                0x00,                                       // topic endTags
                0x00};                                      // top endTags
        assertArrayEquals(expected, ListPartitionReassignmentsCodec.encodeResponse(respSingle((short) 22)));
    }

    // ── Encoding properties ─────────────────────────────────────────────

    @Test
    @DisplayName("encoding is deterministic (two encodes → identical bytes)")
    void deterministic() {
        assertArrayEquals(
                ListPartitionReassignmentsCodec.encodeRequest(req()),
                ListPartitionReassignmentsCodec.encodeRequest(req()));
        assertArrayEquals(
                ListPartitionReassignmentsCodec.encodeResponse(resp()),
                ListPartitionReassignmentsCodec.encodeResponse(resp()));
    }

    // ── Facade wiring ───────────────────────────────────────────────────

    @Test
    @DisplayName("KafkaCodec facade delegates to ListPartitionReassignmentsCodec")
    void facadeDelegation() {
        byte[] reqFacade = KafkaCodec.encodeListPartitionReassignmentsRequest(req());
        byte[] reqDirect = ListPartitionReassignmentsCodec.encodeRequest(req());
        assertArrayEquals(reqDirect, reqFacade);

        ListPartitionReassignmentsRequest decodedFacade =
                KafkaCodec.decodeListPartitionReassignmentsRequest(ByteBuffer.wrap(reqDirect));
        assertEquals(30_000, decodedFacade.timeoutMs());

        byte[] respFacade = KafkaCodec.encodeListPartitionReassignmentsResponse(resp());
        byte[] respDirect = ListPartitionReassignmentsCodec.encodeResponse(resp());
        assertArrayEquals(respDirect, respFacade);

        ListPartitionReassignmentsResponse decodedResp =
                KafkaCodec.decodeListPartitionReassignmentsResponse(ByteBuffer.wrap(respDirect));
        assertEquals(1, decodedResp.topics().size());
        assertEquals(List.of(4), decodedResp.topics().get(0).partitions().get(0).addingReplicas());
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
                new ListPartitionReassignmentsRequest(5_000, List.of(topic("no-parts", List.of()))));
        assertEquals(1, r.topics().size());
        assertEquals(0, r.topics().get(0).partitions().size());
    }
}
