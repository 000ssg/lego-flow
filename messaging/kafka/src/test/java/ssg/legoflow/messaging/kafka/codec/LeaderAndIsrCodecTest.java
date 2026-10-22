package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.LeaderAndIsrRequest;
import ssg.legoflow.messaging.kafka.protocol.LeaderAndIsrResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * LeaderAndIsr codec tests - all versions v0-v7.
 *
 * Tests use golden vectors from leaderisr_golden.py as the source of truth.
 */
class LeaderAndIsrCodecTest {

    /** UUID constant for tests (Java 8 compatible). */
    private static final UUID UUID_CONST = new UUID(0, 0);

    // ── Golden vectors (from leaderisr_golden.py) ──────────────────────────────

    // Request golden vectors
    private static final byte[] REQ_V0 = fromHex("0000000000000001000000010006746f7069633100000000000000010000000000000001000000030000000000000001000000020000000000000002000000000000000100000000");
    private static final byte[] REQ_V1 = fromHex("0000000000000001000000010006746f706963310000000000000001000000000000000100000003000000000000000100000002000000000000000200000000000000010100000000");
    private static final byte[] REQ_V2 = fromHex("00000000000000010000000000003039000000010006746f70696331000000010000000000000001000000000000000100000003000000000000000100000002000000000000000200000000000000010000000000");
    private static final byte[] REQ_V3 = fromHex("00000000000000010000000000003039000000010006746f7069633100000001000000000000000100000000000000010000000300000000000000010000000200000000000000020000000000000001000000000100000003000000010000000400000000");
    private static final byte[] REQ_V4 = fromHex("0000000000000001ffffffffffffffff000000010006746f706963310000000100000000000000010000000000000001000000030000000000000001000000020000000000000002000000000000000100ffffffffffffffff00000000");
    private static final byte[] REQ_V5 = fromHex("0000000000000001ffffffffffffffff00000000010006746f70696331000000000000000000000000000000000000000100000000000000010000000000000001000000030000000000000001000000020000000000000002000000000000000100ffffffffffffffff00000000");
    private static final byte[] REQ_V6 = fromHex("0000000000000001ffffffffffffffff00000000010006746f70696331000000000000000000000000000000000000000100000000000000010000000000000001000000030000000000000001000000020000000000000002000000000000000100ffffffffffffffff0100000000");
    private static final byte[] REQ_V7 = fromHex("000000010100000001ffffffffffffffff00000000010006746f70696331000000000000000000000000000000000000000100000000000000010000000000000001000000030000000000000001000000020000000000000002000000000000000100ffffffffffffffff0000000000");

    // Response golden vectors
    private static final byte[] RESP_V0 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V1 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V2 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V3 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V4 = fromHex("0000000000010006746f70696331000000000000");
    private static final byte[] RESP_V5 = fromHex("0000000000010000000000000000000000000000000000000003000000000000000000010001000000020001");
    private static final byte[] RESP_V6 = fromHex("0000000000010000000000000000000000000000000000000003000000000000000000010001000000020001");
    private static final byte[] RESP_V7 = fromHex("0000000000010000000000000000000000000000000000000003000000000000000000010001000000020001");

    // ── Request round-trip tests ───────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        var req = new LeaderAndIsrRequest(
            0, 1, LeaderAndIsrRequest.DEFAULT_BROKER_EPOCH,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(new LeaderAndIsrRequest.PartitionState(
                "topic1", 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)),
            List.of(), List.of()
        );
        assertRoundTrip(req, REQ_V0, (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        var req = new LeaderAndIsrRequest(
            0, 1, LeaderAndIsrRequest.DEFAULT_BROKER_EPOCH,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(new LeaderAndIsrRequest.PartitionState(
                "topic1", 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                true, null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)),
            List.of(), List.of()
        );
        assertRoundTrip(req, REQ_V1, (short) 1);
    }

    @Test
    void requestRoundTrip_v2() {
        var req = new LeaderAndIsrRequest(
            0, 1, 0x3039L,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", null,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V2, (short) 2);
    }

    @Test
    void requestRoundTrip_v3() {
        var req = new LeaderAndIsrRequest(
            0, 1, 0x3039L,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", null,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    List.of(3), List.of(4), LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V3, (short) 3);
    }

    @Test
    void requestRoundTrip_v4() {
        var req = new LeaderAndIsrRequest(
            0, 1, -1L,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", null,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V4, (short) 4);
    }

    @Test
    void requestRoundTrip_v5() {
        var req = new LeaderAndIsrRequest(
            0, 1, -1L,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", UUID_CONST,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V5, (short) 5);
    }

    @Test
    void requestRoundTrip_v6() {
        var req = new LeaderAndIsrRequest(
            0, 1, -1L,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", UUID_CONST,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    null, null, 1)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V6, (short) 6);
    }

    @Test
    void requestRoundTrip_v7() {
        var req = new LeaderAndIsrRequest(
            1, 1, -1L,
            true,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                "topic1", UUID_CONST,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1, 2), 0, List.of(0, 1),
                    LeaderAndIsrRequest.PartitionState.DEFAULT_IS_NEW,
                    null, null, LeaderAndIsrRequest.PartitionState.DEFAULT_LEADER_RECOVERY_STATE)))),
            List.of()
        );
        assertRoundTrip(req, REQ_V7, (short) 7);
    }

    // ── Response round-trip tests ──────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0)),
            List.of());
        assertRoundTrip(resp, RESP_V0, (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0)),
            List.of());
        assertRoundTrip(resp, RESP_V1, (short) 1);
    }

    @Test
    void responseRoundTrip_v2() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0)),
            List.of());
        assertRoundTrip(resp, RESP_V2, (short) 2);
    }

    @Test
    void responseRoundTrip_v3() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0)),
            List.of());
        assertRoundTrip(resp, RESP_V3, (short) 3);
    }

    @Test
    void responseRoundTrip_v4() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(new LeaderAndIsrResponse.PartitionError("topic1", 0, (short) 0)),
            List.of());
        assertRoundTrip(resp, RESP_V4, (short) 4);
    }

    @Test
    void responseRoundTrip_v5() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(),
            List.of(new LeaderAndIsrResponse.TopicError(UUID_CONST,
                List.of(new LeaderAndIsrResponse.PartitionError(null, 0, (short) 0),
                        new LeaderAndIsrResponse.PartitionError(null, 1, (short) 1),
                        new LeaderAndIsrResponse.PartitionError(null, 2, (short) 1)))));
        assertRoundTrip(resp, RESP_V5, (short) 5);
    }

    @Test
    void responseRoundTrip_v6() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(),
            List.of(new LeaderAndIsrResponse.TopicError(UUID_CONST,
                List.of(new LeaderAndIsrResponse.PartitionError(null, 0, (short) 0),
                        new LeaderAndIsrResponse.PartitionError(null, 1, (short) 1),
                        new LeaderAndIsrResponse.PartitionError(null, 2, (short) 1)))));
        assertRoundTrip(resp, RESP_V6, (short) 6);
    }

    @Test
    void responseRoundTrip_v7() {
        var resp = new LeaderAndIsrResponse((short) 0,
            List.of(),
            List.of(new LeaderAndIsrResponse.TopicError(UUID_CONST,
                List.of(new LeaderAndIsrResponse.PartitionError(null, 0, (short) 0),
                        new LeaderAndIsrResponse.PartitionError(null, 1, (short) 1),
                        new LeaderAndIsrResponse.PartitionError(null, 2, (short) 1)))));
        assertRoundTrip(resp, RESP_V7, (short) 7);
    }

    // ── Version constraint tests ───────────────────────────────────────────────

    @Test
    void unsupportedVersion_throws() {
        var req = new LeaderAndIsrRequest(
            0, 1, LeaderAndIsrRequest.DEFAULT_BROKER_EPOCH,
            LeaderAndIsrRequest.DEFAULT_IS_KRAFT_CONTROLLER,
            LeaderAndIsrRequest.DEFAULT_TYPE,
            List.of(), List.of(), List.of());
        var resp = new LeaderAndIsrResponse((short) 0, List.of(), List.of());

        assertThatThrownBy(() -> LeaderAndIsrCodec.encodeRequest((short) -1, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported LeaderAndIsr version: -1");
        assertThatThrownBy(() -> LeaderAndIsrCodec.encodeRequest((short) 8, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported LeaderAndIsr version: 8");
        assertThatThrownBy(() -> LeaderAndIsrCodec.encodeResponse((short) -1, resp))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported LeaderAndIsr version: -1");
        assertThatThrownBy(() -> LeaderAndIsrCodec.encodeResponse((short) 8, resp))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported LeaderAndIsr version: 8");
    }

    // ── Edge case tests ────────────────────────────────────────────────────────

    @Test
    void emptyArrays_roundTrip() {
        var req = new LeaderAndIsrRequest(
            0, 1, -1L,
            false, 0,
            List.of(), List.of(), List.of());
        var encoded = LeaderAndIsrCodec.encodeRequest((short) 4, req);
        var decoded = LeaderAndIsrCodec.decodeRequest((short) 4, ByteBuffer.wrap(encoded));
        assertThat(decoded.controllerId()).isZero();
        assertThat(decoded.controllerEpoch()).isOne();
        assertThat(decoded.partitionStates()).isEmpty();
        assertThat(decoded.topicStates()).isEmpty();
        assertThat(decoded.liveLeaders()).isEmpty();
    }

    @Test
    void nullTopics_roundTrip() {
        var req = new LeaderAndIsrRequest(
            0, 1, -1L,
            false, 0,
            List.of(),
            List.of(new LeaderAndIsrRequest.TopicState(
                null, UUID_CONST,
                List.of(new LeaderAndIsrRequest.PartitionState(
                    null, 0, 1, 0, 1, List.of(0, 1), 0, List.of(0),
                    false, null, null, 0)))),
            List.of());
        var encoded = LeaderAndIsrCodec.encodeRequest((short) 5, req);
        var decoded = LeaderAndIsrCodec.decodeRequest((short) 5, ByteBuffer.wrap(encoded));
        assertThat(decoded.topicStates()).hasSize(1);
        assertThat(decoded.topicStates().get(0).topicName()).isNull();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void assertRoundTrip(Object obj, byte[] golden, short version) {
        // Encode
        byte[] encoded;
        if (obj instanceof LeaderAndIsrRequest) {
            encoded = LeaderAndIsrCodec.encodeRequest(version, (LeaderAndIsrRequest) obj);
        } else {
            encoded = LeaderAndIsrCodec.encodeResponse(version, (LeaderAndIsrResponse) obj);
        }

        // Verify golden matches
        assertThat(encoded).as("encoded bytes match golden").isEqualTo(golden);

        // Decode and verify round-trip
        ByteBuffer decodeBuf = ByteBuffer.wrap(encoded);
        if (obj instanceof LeaderAndIsrRequest) {
            var decoded = LeaderAndIsrCodec.decodeRequest(version, decodeBuf);
            assertThat(decoded).isEqualTo(obj);
        } else {
            var decoded = LeaderAndIsrCodec.decodeResponse(version, decodeBuf);
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
