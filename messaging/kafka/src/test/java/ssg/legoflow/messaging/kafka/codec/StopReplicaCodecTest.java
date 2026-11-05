package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.StopReplicaRequest;
import ssg.legoflow.messaging.kafka.protocol.StopReplicaResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * StopReplica codec tests - all versions v0-v4.
 *
 * Tests use golden vectors from doc/work/stopreplica_golden.py as the source of truth.
 */
class StopReplicaCodecTest {

    // ── Golden vectors (from stopreplica_golden.py) ───────────────────────────

    // Request golden vectors
    private static final byte[] REQ_V0 = fromHex("000000000000000101000000020006746f70696331000000000006746f7069633100000001");
    private static final byte[] REQ_V1 = fromHex("0000000000000001000000000000303900000000010006746f70696331000000020000000000000001");
    private static final byte[] REQ_V2 = fromHex("00000000000000010000000000003039010207746f70696331030000000000000001");
    private static final byte[] REQ_V3 = fromHex("000000000000000100000000000030390207746f7069633103000000000000000100000000010000000101");
    private static final byte[] REQ_V4 = fromHex("00000001000000010000000000003039010207746f7069633102000000000000000100");

    // Response golden vectors
    private static final byte[] RESP_V0 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V1 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V2 = fromHex("0000020006746f70696331000000000000");
    private static final byte[] RESP_V3 = fromHex("0000020006746f70696331000000000000");
    private static final byte[] RESP_V4 = fromHex("0000020006746f70696331000000000000");

    // ── Request round-trip tests ─────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        var req = new StopReplicaRequest(
            0, 1, StopReplicaRequest.DEFAULT_BROKER_EPOCH,
            StopReplicaRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            true,
            List.of(
                new StopReplicaRequest.TopicPartitionData("topic1", 0),
                new StopReplicaRequest.TopicPartitionData("topic1", 1)),
            List.of(), List.of());
        assertRoundTrip(req, REQ_V0, (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        var req = new StopReplicaRequest(
            0, 1, 0x3039L,
            StopReplicaRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            false,
            List.of(),
            List.of(new StopReplicaRequest.TopicData("topic1", List.of(0, 1))),
            List.of());
        assertRoundTrip(req, REQ_V1, (short) 1);
    }

    @Test
    void requestRoundTrip_v2() {
        var req = new StopReplicaRequest(
            0, 1, 0x3039L,
            StopReplicaRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            true,
            List.of(),
            List.of(new StopReplicaRequest.TopicData("topic1", List.of(0, 1))),
            List.of());
        assertRoundTrip(req, REQ_V2, (short) 2);
    }

    @Test
    void requestRoundTrip_v3() {
        var req = new StopReplicaRequest(
            0, 1, 0x3039L,
            StopReplicaRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            false,
            List.of(),
            List.of(),
            List.of(new StopReplicaRequest.TopicState("topic1", List.of(
                new StopReplicaRequest.PartitionState(0, 1, false),
                new StopReplicaRequest.PartitionState(1, 1, true)))));
        assertRoundTrip(req, REQ_V3, (short) 3);
    }

    @Test
    void requestRoundTrip_v4() {
        var req = new StopReplicaRequest(
            1, 1, 0x3039L,
            true,
            false,
            List.of(),
            List.of(),
            List.of(new StopReplicaRequest.TopicState("topic1", List.of(
                new StopReplicaRequest.PartitionState(0, 1, false)))));
        assertRoundTrip(req, REQ_V4, (short) 4);
    }

    // ── Response round-trip tests ────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        var resp = new StopReplicaResponse((short) 0, List.of(
            new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        assertRoundTrip(resp, RESP_V0, (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        var resp = new StopReplicaResponse((short) 0, List.of(
            new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        assertRoundTrip(resp, RESP_V1, (short) 1);
    }

    @Test
    void responseRoundTrip_v2() {
        var resp = new StopReplicaResponse((short) 0, List.of(
            new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        assertRoundTrip(resp, RESP_V2, (short) 2);
    }

    @Test
    void responseRoundTrip_v3() {
        var resp = new StopReplicaResponse((short) 0, List.of(
            new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        assertRoundTrip(resp, RESP_V3, (short) 3);
    }

    @Test
    void responseRoundTrip_v4() {
        var resp = new StopReplicaResponse((short) 0, List.of(
            new StopReplicaResponse.PartitionResult("topic1", 0, (short) 0)));
        assertRoundTrip(resp, RESP_V4, (short) 4);
    }

    // ── Version constraint tests ─────────────────────────────────────────────

    @Test
    void unsupportedVersion() {
        assertThatThrownBy(() -> StopReplicaCodec.encodeRequest((short) -1,
            new StopReplicaRequest(0, 0, -1L, false, false, List.of(), List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported StopReplica version: -1");

        assertThatThrownBy(() -> StopReplicaCodec.encodeRequest((short) 5,
            new StopReplicaRequest(0, 0, -1L, false, false, List.of(), List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported StopReplica version: 5");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertRoundTrip(Object obj, byte[] golden, short version) {
        // Encode
        byte[] encoded;
        if (obj instanceof StopReplicaRequest) {
            encoded = StopReplicaCodec.encodeRequest(version, (StopReplicaRequest) obj);
        } else {
            encoded = StopReplicaCodec.encodeResponse(version, (StopReplicaResponse) obj);
        }

        // Verify golden matches
        assertThat(encoded).as("encoded bytes match golden").isEqualTo(golden);

        // Decode and verify round-trip
        ByteBuffer decodeBuf = ByteBuffer.wrap(encoded);
        if (obj instanceof StopReplicaRequest) {
            var decoded = StopReplicaCodec.decodeRequest(version, decodeBuf);
            assertThat(decoded).isEqualTo(obj);
        } else {
            var decoded = StopReplicaCodec.decodeResponse(version, decodeBuf);
            assertThat(decoded).isEqualTo(obj);
        }
    }

    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
