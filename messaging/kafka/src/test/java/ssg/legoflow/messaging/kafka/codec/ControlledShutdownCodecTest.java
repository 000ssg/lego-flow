package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.ControlledShutdownRequest;
import ssg.legoflow.messaging.kafka.protocol.ControlledShutdownResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ControlledShutdown codec tests (API key 7) — v0–v1.
 *
 * <p>Tests encode/decode round-trips for all versions with golden vectors.
 *
 * @since 0.1.0
 */
class ControlledShutdownCodecTest {

    // ── Request golden vectors ───────────────────────────────────────────────

    /** v0: brokerId=2 */
    private static final byte[] REQ_V0 = fromHex("00000002");

    /** v1: brokerId=0 */
    private static final byte[] REQ_V1 = fromHex("00000000");

    // ── Response golden vectors ──────────────────────────────────────────────

    /** v0: errorCode=0, no partitions */
    private static final byte[] RESP_V0 = fromHex("000000000000");

    /** v1: errorCode=1, one partition */
    private static final byte[] RESP_V1 = fromHex("0001000000010006746f7069633100000000");

    // ── Request round-trip tests ─────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        var req = new ControlledShutdownRequest(2);
        assertRoundTrip(req, REQ_V0, (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        var req = new ControlledShutdownRequest(0);
        assertRoundTrip(req, REQ_V1, (short) 1);
    }

    // ── Response round-trip tests ────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        var resp = new ControlledShutdownResponse((short) 0, List.of());
        assertRoundTrip(resp, RESP_V0, (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        var resp = new ControlledShutdownResponse((short) 1, List.of(
                new ControlledShutdownResponse.TopicPartitionData("topic1", 0)));
        assertRoundTrip(resp, RESP_V1, (short) 1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertRoundTrip(ControlledShutdownRequest req, byte[] golden, short version) {
        byte[] encoded = ControlledShutdownCodec.encodeRequest(version, req);
        assertThat(encoded).as("encode v%d", version).isEqualTo(golden);

        var decoded = ControlledShutdownCodec.decodeRequest(version, ByteBuffer.wrap(encoded));
        assertThat(decoded).as("decode v%d", version).isEqualTo(req);
    }

    private void assertRoundTrip(ControlledShutdownResponse resp, byte[] golden, short version) {
        byte[] encoded = ControlledShutdownCodec.encodeResponse(version, resp);
        assertThat(encoded).as("encode v%d", version).isEqualTo(golden);

        var decoded = ControlledShutdownCodec.decodeResponse(version, ByteBuffer.wrap(encoded));
        assertThat(decoded).as("decode v%d", version).isEqualTo(resp);
    }

    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) (Character.digit(hex.charAt(i), 16) << 4
                    | Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }
}