package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.WriteTxnMarkersRequest;
import ssg.legoflow.messaging.kafka.protocol.WriteTxnMarkersResponse;

/**
 * Exact-byte golden and round-trip tests for {@link WriteTxnMarkersCodec}.
 *
 * <p>Goldens are computed independently from the vendored spec JSON
 * ({@code WriteTxnMarkersRequest.json} / {@code WriteTxnMarkersResponse.json})
 * and the frozen order tables
 * ({@code doc/spec/order/WriteTxnMarkers.Request.txt} /
 * {@code WriteTxnMarkers.Response.txt}).
 *
 * <p>Request wire layout (v0 fixed, v1 flexible):
 * {@code Markers[] { ProducerId(int64), ProducerEpoch(int16), TransactionResult(bool),
 * Topics[]{ Name(string), PartitionIndexes[]int32 }, CoordinatorEpoch(int32) }} —
 * coordinator epoch is the <i>last</i> marker field; partitions are <i>grouped by
 * topic</i>. The in-house model's flat {@code partitions} list is grouped on encode
 * and flattened on decode.
 *
 * <p>Response wire layout (v0 fixed, v1 flexible):
 * {@code Markers[] { ProducerId(int64), Topics[]{ Name(string),
 * Partitions[]{ PartitionIndex(int32), ErrorCode(int16) } } }} — no leading
 * ThrottleTimeMs.
 */
class WriteTxnMarkersCodecTest {

    private static final short V0 = 0;
    private static final short V1 = 1; // flexible

    // ── Request: golden (one marker, topic "topic1" → partitions [0,1]) ─────

    /** v0 fixed: 43 bytes. */
    private static final int[] REQ_V0_GOLDEN = {
        0, 0, 0, 1, // numMarkers int32 = 1
        0, 0, 0, 0, 0, 0, 0, 100, // ProducerId int64 = 100
        0, 0, // ProducerEpoch int16 = 0
        1, // TransactionResult bool = true
        0, 0, 0, 1, // numTopics int32 = 1
        0, 6, 't', 'o', 'p', 'i', 'c', '1', // Name "topic1"
        0, 0, 0, 2, // PartitionIndexes count int32 = 2
        0, 0, 0, 0, // PartitionIndex[0] = 0
        0, 0, 0, 1, // PartitionIndex[1] = 1
        0, 0, 0, 1 // CoordinatorEpoch int32 = 1
    };

    /** v1 flexible: 38 bytes. */
    private static final int[] REQ_V1_GOLDEN = {
        2, // numMarkers varint (1+1) = 2
        0, 0, 0, 0, 0, 0, 0, 100, // ProducerId int64 = 100
        0, 0, // ProducerEpoch int16 = 0
        1, // TransactionResult bool = true
        2, // numTopics varint (1+1) = 2
        7, 't', 'o', 'p', 'i', 'c', '1', // Name compact "topic1" (len 6 → 7)
        3, // PartitionIndexes varint (2+1) = 3
        0, 0, 0, 0, 0, // PartitionIndex[0] = 0 + tags(0)
        0, 0, 0, 1, 0, // PartitionIndex[1] = 1 + tags(0)
        0, // topic-level tags(0)
        0, 0, 0, 1, // CoordinatorEpoch int32 = 1
        0, // marker-level tags(0)
        0 // top-level tags(0)
    };

    private WriteTxnMarkersRequest singleMarkerReq() {
        return new WriteTxnMarkersRequest(List.of(
            new WriteTxnMarkersRequest.TxnMarker(100L, (short) 0, 1, true, List.of(
                new WriteTxnMarkersRequest.TopicPartitionData("topic1", 0),
                new WriteTxnMarkersRequest.TopicPartitionData("topic1", 1)))));
    }

    @Test
    void requestGoldenV0Fixed() {
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V0, singleMarkerReq());
        assertThat(toInts(encoded)).isEqualTo(REQ_V0_GOLDEN);
    }

    @Test
    void requestGoldenV1Flexible() {
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V1, singleMarkerReq());
        assertThat(toInts(encoded)).isEqualTo(REQ_V1_GOLDEN);
    }

    // ── Request: round-trip ─────────────────────────────────────────────────

    @Test
    void requestRoundTripV0() {
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V0, singleMarkerReq());
        WriteTxnMarkersRequest decoded =
            WriteTxnMarkersCodec.decodeRequest(V0, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers()).hasSize(1);
        var m = decoded.markers().getFirst();
        assertThat(m.producerId()).isEqualTo(100L);
        assertThat(m.producerEpoch()).isEqualTo((short) 0);
        assertThat(m.coordinatorEpoch()).isEqualTo(1);
        assertThat(m.committed()).isTrue();
        // The flat model list is restored: two (topic1, 0/1) entries.
        assertThat(m.partitions()).hasSize(2);
        assertThat(m.partitions().get(0).topic()).isEqualTo("topic1");
        assertThat(m.partitions().get(0).partition()).isEqualTo(0);
        assertThat(m.partitions().get(1).topic()).isEqualTo("topic1");
        assertThat(m.partitions().get(1).partition()).isEqualTo(1);
    }

    @Test
    void requestRoundTripV1() {
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V1, singleMarkerReq());
        WriteTxnMarkersRequest decoded =
            WriteTxnMarkersCodec.decodeRequest(V1, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers().getFirst().producerId()).isEqualTo(100L);
        assertThat(decoded.markers().getFirst().committed()).isTrue();
        assertThat(decoded.markers().getFirst().coordinatorEpoch()).isEqualTo(1);
        assertThat(decoded.markers().getFirst().partitions()).hasSize(2);
    }

    /**
     * The in-house model interleaves topics in one flat list. The spec <b>groups by
     * topic</b>, so the codec re-groups on encode and re-flattens on decode in grouped
     * order. The round-trip preserves the (topic, partition) pairs and each topic's
     * per-topic partition order, but reorganizes cross-topic interleaving into grouped
     * order (here: input b,a,b → output b,b,a).
     */
    @Test
    void requestGroupsInterleavedTopics() {
        var req = new WriteTxnMarkersRequest(List.of(
            new WriteTxnMarkersRequest.TxnMarker(5L, (short) 2, 7, false, List.of(
                new WriteTxnMarkersRequest.TopicPartitionData("b", 1),
                new WriteTxnMarkersRequest.TopicPartitionData("a", 0),
                new WriteTxnMarkersRequest.TopicPartitionData("b", 2)))));
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V0, req);
        WriteTxnMarkersRequest decoded =
            WriteTxnMarkersCodec.decodeRequest(V0, ByteBuffer.wrap(encoded));
        var m = decoded.markers().getFirst();
        assertThat(m.producerId()).isEqualTo(5L);
        assertThat(m.producerEpoch()).isEqualTo((short) 2);
        assertThat(m.coordinatorEpoch()).isEqualTo(7);
        assertThat(m.committed()).isFalse();
        // Grouped by topic: "b" first (1, 2), then "a" (0). Cross-topic order reorganized.
        assertThat(m.partitions()).hasSize(3);
        assertThat(m.partitions().get(0).topic()).isEqualTo("b");
        assertThat(m.partitions().get(0).partition()).isEqualTo(1);
        assertThat(m.partitions().get(1).topic()).isEqualTo("b");
        assertThat(m.partitions().get(1).partition()).isEqualTo(2);
        assertThat(m.partitions().get(2).topic()).isEqualTo("a");
        assertThat(m.partitions().get(2).partition()).isEqualTo(0);
    }

    /** Empty request: 4 bytes (numMarkers=0) — fixed and flexible both empty. */
    @Test
    void requestEmpty() {
        var req = new WriteTxnMarkersRequest(List.of());
        byte[] v0 = WriteTxnMarkersCodec.encodeRequest(V0, req);
        assertThat(toInts(v0)).isEqualTo(new int[] {0, 0, 0, 0});
        byte[] v1 = WriteTxnMarkersCodec.encodeRequest(V1, req);
        // numMarkers varint(0+1)=1, then top-level tags(0)
        assertThat(toInts(v1)).isEqualTo(new int[] {1, 0});
        assertThat(WriteTxnMarkersCodec.decodeRequest(V0, ByteBuffer.wrap(v0)).markers()).isEmpty();
        assertThat(WriteTxnMarkersCodec.decodeRequest(V1, ByteBuffer.wrap(v1)).markers()).isEmpty();
    }

    /** Multiple markers are encoded and decoded in order. */
    @Test
    void requestMultipleMarkers() {
        var req = new WriteTxnMarkersRequest(List.of(
            new WriteTxnMarkersRequest.TxnMarker(1L, (short) 0, 1, true, List.of(
                new WriteTxnMarkersRequest.TopicPartitionData("t", 0))),
            new WriteTxnMarkersRequest.TxnMarker(2L, (short) 1, 2, false, List.of(
                new WriteTxnMarkersRequest.TopicPartitionData("t", 1)))));
        byte[] encoded = WriteTxnMarkersCodec.encodeRequest(V1, req);
        WriteTxnMarkersRequest decoded =
            WriteTxnMarkersCodec.decodeRequest(V1, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers()).hasSize(2);
        assertThat(decoded.markers().get(0).producerId()).isEqualTo(1L);
        assertThat(decoded.markers().get(0).committed()).isTrue();
        assertThat(decoded.markers().get(1).producerId()).isEqualTo(2L);
        assertThat(decoded.markers().get(1).coordinatorEpoch()).isEqualTo(2);
    }

    /** Invalid versions are rejected. */
    @Test
    void requestInvalidVersions() {
        assertThatThrownBy(() -> WriteTxnMarkersCodec.encodeRequest((short) -1, singleMarkerReq()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WriteTxnMarkersCodec.encodeRequest((short) 2, singleMarkerReq()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WriteTxnMarkersCodec.decodeRequest((short) 5, ByteBuffer.wrap(new byte[0])))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** A truncated request buffer propagates the raw underflow (sibling-codec convention). */
    @Test
    void requestTruncatedBuffer() {
        byte[] full = WriteTxnMarkersCodec.encodeRequest(V0, singleMarkerReq());
        ByteBuffer truncated = ByteBuffer.wrap(full, 0, full.length - 5);
        assertThatThrownBy(() -> WriteTxnMarkersCodec.decodeRequest(V0, truncated))
            .isInstanceOf(BufferUnderflowException.class);
    }

    // ── Response: golden (one marker, topic "topic1" → [(0,0),(1,0)]) ───────

    /** v0 fixed: 40 bytes. */
    private static final int[] RESP_V0_GOLDEN = {
        0, 0, 0, 1, // numMarkers int32 = 1
        0, 0, 0, 0, 0, 0, 0, 100, // ProducerId int64 = 100
        0, 0, 0, 1, // numTopics int32 = 1
        0, 6, 't', 'o', 'p', 'i', 'c', '1', // Name "topic1"
        0, 0, 0, 2, // Partitions count int32 = 2
        0, 0, 0, 0, // PartitionIndex[0] = 0
        0, 0, // ErrorCode[0] = 0
        0, 0, 0, 1, // PartitionIndex[1] = 1
        0, 0 // ErrorCode[1] = 0
    };

    /** v1 flexible: 35 bytes. */
    private static final int[] RESP_V1_GOLDEN = {
        2, // numMarkers varint (1+1) = 2
        0, 0, 0, 0, 0, 0, 0, 100, // ProducerId int64 = 100
        2, // numTopics varint (1+1) = 2
        7, 't', 'o', 'p', 'i', 'c', '1', // Name compact "topic1" (len 6 → 7)
        3, // Partitions varint (2+1) = 3
        0, 0, 0, 0, 0, 0, 0, // Partition[0]: PartitionIndex=0 + ErrorCode=0 + endTags=0
        0, 0, 0, 1, 0, 0, 0, // Partition[1]: PartitionIndex=1 + ErrorCode=0 + endTags=0
        0, // topic-level endTags
        0, // marker-level endTags
        0 // top-level endTags
    };

    private WriteTxnMarkersResponse singleMarkerResp() {
        return new WriteTxnMarkersResponse(List.of(
            new WriteTxnMarkersResponse.MarkerResult(100L, List.of(
                new WriteTxnMarkersResponse.TopicResult("topic1", List.of(
                    new WriteTxnMarkersResponse.PartitionResult(0, (short) 0),
                    new WriteTxnMarkersResponse.PartitionResult(1, (short) 0)))))));
    }

    @Test
    void responseGoldenV0Fixed() {
        byte[] encoded = WriteTxnMarkersCodec.encodeResponse(V0, singleMarkerResp());
        assertThat(toInts(encoded)).isEqualTo(RESP_V0_GOLDEN);
    }

    @Test
    void responseGoldenV1Flexible() {
        byte[] encoded = WriteTxnMarkersCodec.encodeResponse(V1, singleMarkerResp());
        assertThat(toInts(encoded)).isEqualTo(RESP_V1_GOLDEN);
    }

    @Test
    void responseRoundTripV0() {
        byte[] encoded = WriteTxnMarkersCodec.encodeResponse(V0, singleMarkerResp());
        WriteTxnMarkersResponse decoded =
            WriteTxnMarkersCodec.decodeResponse(V0, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers()).hasSize(1);
        var m = decoded.markers().getFirst();
        assertThat(m.producerId()).isEqualTo(100L);
        assertThat(m.topics()).hasSize(1);
        assertThat(m.topics().getFirst().topic()).isEqualTo("topic1");
        assertThat(m.topics().getFirst().partitions()).hasSize(2);
        assertThat(m.topics().getFirst().partitions().get(0).partition()).isEqualTo(0);
        assertThat(m.topics().getFirst().partitions().get(0).errorCode()).isEqualTo((short) 0);
        assertThat(m.topics().getFirst().partitions().get(1).partition()).isEqualTo(1);
    }

    @Test
    void responseRoundTripV1() {
        byte[] encoded = WriteTxnMarkersCodec.encodeResponse(V1, singleMarkerResp());
        WriteTxnMarkersResponse decoded =
            WriteTxnMarkersCodec.decodeResponse(V1, ByteBuffer.wrap(encoded));
        assertThat(decoded.markers().getFirst().producerId()).isEqualTo(100L);
        assertThat(decoded.markers().getFirst().topics().getFirst().partitions()).hasSize(2);
    }

    /** Non-zero error codes are preserved in both directions and versions. */
    @Test
    void responseErrorCodesPreserved() {
        var resp = new WriteTxnMarkersResponse(List.of(
            new WriteTxnMarkersResponse.MarkerResult(7L, List.of(
                new WriteTxnMarkersResponse.TopicResult("k", List.of(
                    new WriteTxnMarkersResponse.PartitionResult(3, (short) 17),
                    new WriteTxnMarkersResponse.PartitionResult(9, (short) 29)))))));
        for (short v : new short[] {V0, V1}) {
            byte[] encoded = WriteTxnMarkersCodec.encodeResponse(v, resp);
            WriteTxnMarkersResponse decoded =
                WriteTxnMarkersCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
            var parts = decoded.markers().getFirst().topics().getFirst().partitions();
            assertThat(parts.get(0).partition()).isEqualTo(3);
            assertThat(parts.get(0).errorCode()).isEqualTo((short) 17);
            assertThat(parts.get(1).partition()).isEqualTo(9);
            assertThat(parts.get(1).errorCode()).isEqualTo((short) 29);
        }
    }

    /** Empty response: 4 bytes (numMarkers=0) fixed; 2 bytes flexible (varint 1 + tags). */
    @Test
    void responseEmpty() {
        var resp = new WriteTxnMarkersResponse(List.of());
        byte[] v0 = WriteTxnMarkersCodec.encodeResponse(V0, resp);
        assertThat(toInts(v0)).isEqualTo(new int[] {0, 0, 0, 0});
        byte[] v1 = WriteTxnMarkersCodec.encodeResponse(V1, resp);
        assertThat(toInts(v1)).isEqualTo(new int[] {1, 0});
        assertThat(WriteTxnMarkersCodec.decodeResponse(V0, ByteBuffer.wrap(v0)).markers()).isEmpty();
        assertThat(WriteTxnMarkersCodec.decodeResponse(V1, ByteBuffer.wrap(v1)).markers()).isEmpty();
    }

    /** A truncated response buffer propagates the raw underflow (sibling-codec convention). */
    @Test
    void responseTruncatedBuffer() {
        byte[] full = WriteTxnMarkersCodec.encodeResponse(V0, singleMarkerResp());
        ByteBuffer truncated = ByteBuffer.wrap(full, 0, full.length - 4);
        assertThatThrownBy(() -> WriteTxnMarkersCodec.decodeResponse(V0, truncated))
            .isInstanceOf(BufferUnderflowException.class);
    }

    /** The v0 pin point: versioned calls at v0 reproduce the v0 golden bytes. */
    @Test
    void v0RoundTripMatchesGolden() {
        byte[] v0Req = WriteTxnMarkersCodec.encodeRequest(V0, singleMarkerReq());
        assertThat(toInts(v0Req)).isEqualTo(REQ_V0_GOLDEN);
        byte[] v0Resp = WriteTxnMarkersCodec.encodeResponse(V0, singleMarkerResp());
        assertThat(toInts(v0Resp)).isEqualTo(RESP_V0_GOLDEN);
    }

    private static int[] toInts(byte[] bytes) {
        int[] out = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = bytes[i] & 0xFF;
        }
        return out;
    }
}
