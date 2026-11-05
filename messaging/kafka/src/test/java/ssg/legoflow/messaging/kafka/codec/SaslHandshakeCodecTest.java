package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * SaslHandshake codec tests — all versions v0–v1.
 */
class SaslHandshakeCodecTest {

    // ── Request round-trip tests ──────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        SaslHandshakeRequest req = new SaslHandshakeRequest("PLAIN");
        assertRoundTrip(req, SaslHandshakeCodec.encodeRequest((short) 0, req), (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        SaslHandshakeRequest req = new SaslHandshakeRequest("PLAIN");
        assertRoundTrip(req, SaslHandshakeCodec.encodeRequest((short) 1, req), (short) 1);
    }

    // ── Response round-trip tests ─────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        SaslHandshakeResponse resp = new SaslHandshakeResponse((short) 0, List.of("PLAIN"));
        assertRoundTrip(resp, SaslHandshakeCodec.encodeResponse((short) 0, resp), (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        SaslHandshakeResponse resp = new SaslHandshakeResponse((short) 0, List.of("PLAIN"));
        assertRoundTrip(resp, SaslHandshakeCodec.encodeResponse((short) 1, resp), (short) 1);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private static void assertRoundTrip(SaslHandshakeRequest req, byte[] bytes, short version) {
        SaslHandshakeRequest decoded = SaslHandshakeCodec.decodeRequest(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(req);
    }

    private static void assertRoundTrip(SaslHandshakeResponse resp, byte[] bytes, short version) {
        SaslHandshakeResponse decoded = SaslHandshakeCodec.decodeResponse(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(resp);
    }
}
