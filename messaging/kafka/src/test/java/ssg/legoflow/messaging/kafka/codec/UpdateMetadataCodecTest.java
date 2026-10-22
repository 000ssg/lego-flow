package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.UpdateMetadataRequest;
import ssg.legoflow.messaging.kafka.protocol.UpdateMetadataResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * UpdateMetadata codec tests — all versions v0–v8.
 *
 * Tests use golden vectors from doc/work/updatemetadata_golden.py as the source of truth.
 */
class UpdateMetadataCodecTest {

    // ── Golden vectors (from updatemetadata_golden.py) ───────────────────────

    // Request golden vectors
    private static final byte[] REQ_V0 = fromHex("0000000000000001000000020006746f706963310000000000000001000000000000000100000003000000000000000100000002000000000000000200000000000000010006746f70696331000000010000000100000001000000010000000200000000000000010000000000000002000000000000000100000001000000000005686f73743100002384");
    private static final byte[] REQ_V4 = fromHex("0000000000000001000000010006746f70696331000000000000000100000000000000010000000300000000000000010000000200000000000000020000000000000001000000010000000200000001000000000005686f73743100002384");
    private static final byte[] REQ_V5 = fromHex("00000000000000010000000000003039000000010006746f7069633100000001000000000000000100000000000000010000000300000000000000010000000200000000000000020000000000000001ffffffff00000001000000000005686f73743100002384");
    private static final byte[] REQ_V8 = fromHex("000000000101000000010000000000003039000000010006746f7069633100000001000000000000000100000000000000010000000300000000000000010000000200000000000000020000000000000001ffffffff00000001000000000005686f73743100002384");

    // Response golden vectors
    private static final byte[] RESP_V0 = fromHex("0000");
    private static final byte[] RESP_V1 = fromHex("0000");
    private static final byte[] RESP_V2 = fromHex("0000");
    private static final byte[] RESP_V3 = fromHex("0000");
    private static final byte[] RESP_V4 = fromHex("0000");
    private static final byte[] RESP_V5 = fromHex("0000");
    private static final byte[] RESP_V6 = fromHex("0000");
    private static final byte[] RESP_V7 = fromHex("0000");
    private static final byte[] RESP_V8 = fromHex("0000");

    // ── Request round-trip tests ─────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        var req = new UpdateMetadataRequest(
            0, 1, UpdateMetadataRequest.DEFAULT_BROKER_EPOCH,
            UpdateMetadataRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            UpdateMetadataRequest.DEFAULT_TYPE,
            List.of(
                new UpdateMetadataRequest.PartitionState("topic1", 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1), null),
                new UpdateMetadataRequest.PartitionState("topic1", 1, 1, 1, 1, List.of(0, 1), 0, List.of(0, 1), null)),
            List.of(),
            List.of(new UpdateMetadataRequest.BrokerState(0, "host1", 9092)));
        assertRoundTrip(req, REQ_V0, (short) 0);
    }

    @Test
    void requestRoundTrip_v4() {
        var req = new UpdateMetadataRequest(
            0, 1, UpdateMetadataRequest.DEFAULT_BROKER_EPOCH,
            UpdateMetadataRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            UpdateMetadataRequest.DEFAULT_TYPE,
            List.of(
                new UpdateMetadataRequest.PartitionState("topic1", 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1), List.of(2))),
            List.of(),
            List.of(new UpdateMetadataRequest.BrokerState(0, "host1", 9092)));
        assertRoundTrip(req, REQ_V4, (short) 4);
    }

    @Test
    void requestRoundTrip_v5() {
        var req = new UpdateMetadataRequest(
            0, 1, 0x3039L,
            UpdateMetadataRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            UpdateMetadataRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new UpdateMetadataRequest.TopicState("topic1",
                List.of(new UpdateMetadataRequest.PartitionState(null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1), null)))),
            List.of(new UpdateMetadataRequest.BrokerState(0, "host1", 9092)));
        assertRoundTrip(req, REQ_V5, (short) 5);
    }

    @Test
    void requestRoundTrip_v8() {
        var req = new UpdateMetadataRequest(
            0, 1, 0x3039L,
            true, 1,
            List.of(),
            List.of(new UpdateMetadataRequest.TopicState("topic1",
                List.of(new UpdateMetadataRequest.PartitionState(null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1), null)))),
            List.of(new UpdateMetadataRequest.BrokerState(0, "host1", 9092)));
        assertRoundTrip(req, REQ_V8, (short) 8);
    }

    // ── Response round-trip tests ────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        var resp = new UpdateMetadataResponse((short) 0);
        assertRoundTrip(resp, RESP_V0, (short) 0);
    }

    @Test
    void responseRoundTrip_v4() {
        var resp = new UpdateMetadataResponse((short) 0);
        assertRoundTrip(resp, RESP_V4, (short) 4);
    }

    @Test
    void responseRoundTrip_v5() {
        var resp = new UpdateMetadataResponse((short) 0);
        assertRoundTrip(resp, RESP_V5, (short) 5);
    }

    @Test
    void responseRoundTrip_v8() {
        var resp = new UpdateMetadataResponse((short) 0);
        assertRoundTrip(resp, RESP_V8, (short) 8);
    }

    // ── Version constraint tests ─────────────────────────────────────────────

    @Test
    void unsupportedVersion() {
        assertThatThrownBy(() -> UpdateMetadataCodec.encodeRequest((short) -1,
                new UpdateMetadataRequest(0, 0, -1L, false, 0, List.of(), List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported UpdateMetadata version: -1");

        assertThatThrownBy(() -> UpdateMetadataCodec.encodeRequest((short) 9,
                new UpdateMetadataRequest(0, 0, -1L, false, 0, List.of(), List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported UpdateMetadata version: 9");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertRoundTrip(Object obj, byte[] golden, short version) {
        // Encode
        byte[] encoded;
        if (obj instanceof UpdateMetadataRequest) {
            encoded = UpdateMetadataCodec.encodeRequest(version, (UpdateMetadataRequest) obj);
        } else {
            encoded = UpdateMetadataCodec.encodeResponse(version, (UpdateMetadataResponse) obj);
        }

        // Verify golden matches
        assertThat(encoded).as("encoded bytes match golden").isEqualTo(golden);

        // Decode and verify round-trip
        ByteBuffer decodeBuf = ByteBuffer.wrap(encoded);
        if (obj instanceof UpdateMetadataRequest) {
            var decoded = UpdateMetadataCodec.decodeRequest(version, decodeBuf);
            assertThat(decoded).isEqualTo(obj);
        } else {
            var decoded = UpdateMetadataCodec.decodeResponse(version, decodeBuf);
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