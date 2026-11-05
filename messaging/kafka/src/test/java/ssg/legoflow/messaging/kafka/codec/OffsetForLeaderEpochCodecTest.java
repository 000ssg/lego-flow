package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.OffsetForLeaderEpochRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetForLeaderEpochResponse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OffsetForLeaderEpoch codec tests (API key 23) — v0–v4.
 *
 * <p>Tests round-trip encode/decode for all versions with golden vectors
 * generated from the reference encoder in {@code doc/work/offsetforleaderepoch_golden.py}.
 *
 * @since 0.1.0
 */
class OffsetForLeaderEpochCodecTest {

    private static byte[] fromHex(String s) {
        return java.util.HexFormat.of().parseHex(s);
    }

    // ── Request golden vectors ───────────────────────────────────────────────

    /** v0: no clusterId, 1 topic, 3 partitions */
    private static final byte[] REQ_V0 = fromHex("ffff000000010006746f7069633100000003000000000000000100000001000000010000000200000001");

    /** v3: with clusterId, 2 topics */
    private static final byte[] REQ_V3 = fromHex("0007636c7573746572000000020006746f7069633100000002000000000000000100000001000000010006746f70696332000000010000000000000001");

    /** v4: flexible encoding with clusterId */
    private static final byte[] REQ_V4 = fromHex("08636c75737465720107746f70696331030000000000000001000000010000000100000002000000010000");

    // ── Response golden vectors ──────────────────────────────────────────────

    /** v0: errorCode=0, 1 topic, 2 partitions */
    private static final byte[] RESP_V0 = fromHex("0000000000010006746f706963310000000200000000000000000000006400000000000100000000000000c80000");

    /** v4: errorCode=1, 1 topic, 1 partition, flexible */
    private static final byte[] RESP_V4 = fromHex("00010107746f706963310100000000000000000000006400000000");

    // ── Request round-trip tests ─────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        var req = new OffsetForLeaderEpochRequest(null, List.of(
            new OffsetForLeaderEpochRequest.TopicData("topic1", List.of(
                new OffsetForLeaderEpochRequest.PartitionData(0, 1),
                new OffsetForLeaderEpochRequest.PartitionData(1, 1),
                new OffsetForLeaderEpochRequest.PartitionData(2, 1))
            )
        ));
        assertRoundTrip(req, REQ_V0, (short) 0);
    }

    @Test
    void requestRoundTrip_v3() {
        var req = new OffsetForLeaderEpochRequest("cluster", List.of(
            new OffsetForLeaderEpochRequest.TopicData("topic1", List.of(
                new OffsetForLeaderEpochRequest.PartitionData(0, 1),
                new OffsetForLeaderEpochRequest.PartitionData(1, 1))
            ),
            new OffsetForLeaderEpochRequest.TopicData("topic2", List.of(
                new OffsetForLeaderEpochRequest.PartitionData(0, 1))
            )
        ));
        assertRoundTrip(req, REQ_V3, (short) 3);
    }

    @Test
    void requestRoundTrip_v4() {
        var req = new OffsetForLeaderEpochRequest("cluster", List.of(
            new OffsetForLeaderEpochRequest.TopicData("topic1", List.of(
                new OffsetForLeaderEpochRequest.PartitionData(0, 1),
                new OffsetForLeaderEpochRequest.PartitionData(1, 1),
                new OffsetForLeaderEpochRequest.PartitionData(2, 1))
            )
        ));
        assertRoundTrip(req, REQ_V4, (short) 4);
    }

    // ── Response round-trip tests ────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        var resp = new OffsetForLeaderEpochResponse((short) 0, List.of(
            new OffsetForLeaderEpochResponse.TopicData("topic1", List.of(
                new OffsetForLeaderEpochResponse.EpochEndOffset(0, 100L, (short) 0),
                new OffsetForLeaderEpochResponse.EpochEndOffset(1, 200L, (short) 0))
            )
        ));
        assertRoundTrip(resp, RESP_V0, (short) 0);
    }

    @Test
    void responseRoundTrip_v4() {
        var resp = new OffsetForLeaderEpochResponse((short) 1, List.of(
            new OffsetForLeaderEpochResponse.TopicData("topic1", List.of(
                new OffsetForLeaderEpochResponse.EpochEndOffset(0, 100L, (short) 0))
            )
        ));
        assertRoundTrip(resp, RESP_V4, (short) 4);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertRoundTrip(OffsetForLeaderEpochRequest req, byte[] expected, short version) {
        byte[] encoded = OffsetForLeaderEpochCodec.encodeRequest(version, req);
        assertThat(encoded).isEqualTo(expected);
        var decoded = OffsetForLeaderEpochCodec.decodeRequest(version, ByteBuffer.wrap(encoded));
        assertThat(decoded).isEqualTo(req);
    }

    private void assertRoundTrip(OffsetForLeaderEpochResponse resp, byte[] expected, short version) {
        byte[] encoded = OffsetForLeaderEpochCodec.encodeResponse(version, resp);
        assertThat(encoded).isEqualTo(expected);
        var decoded = OffsetForLeaderEpochCodec.decodeResponse(version, ByteBuffer.wrap(encoded));
        assertThat(decoded).isEqualTo(resp);
    }
}