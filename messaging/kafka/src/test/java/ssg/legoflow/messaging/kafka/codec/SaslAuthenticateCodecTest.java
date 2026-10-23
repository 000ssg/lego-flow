package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * SaslAuthenticate codec tests — all versions v0–v2.
 */
class SaslAuthenticateCodecTest {

    // ── Request round-trip tests ──────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(new byte[]{1, 2, 3});
        assertRoundTrip(req, SaslAuthenticateCodec.encodeRequest((short) 0, req), (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(new byte[]{1, 2, 3});
        assertRoundTrip(req, SaslAuthenticateCodec.encodeRequest((short) 1, req), (short) 1);
    }

    @Test
    void requestRoundTrip_v2() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(new byte[]{1, 2, 3});
        assertRoundTrip(req, SaslAuthenticateCodec.encodeRequest((short) 2, req), (short) 2);
    }

    // ── Response round-trip tests ─────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, new byte[]{4, 5, 6}, 0L);
        assertRoundTrip(resp, SaslAuthenticateCodec.encodeResponse((short) 0, resp), (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, new byte[]{4, 5, 6}, 0L);
        assertRoundTrip(resp, SaslAuthenticateCodec.encodeResponse((short) 1, resp), (short) 1);
    }

    @Test
    void responseRoundTrip_v2() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, new byte[]{4, 5, 6}, 0L);
        assertRoundTrip(resp, SaslAuthenticateCodec.encodeResponse((short) 2, resp), (short) 2);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private static void assertRoundTrip(SaslAuthenticateRequest req, byte[] bytes, short version) {
        SaslAuthenticateRequest decoded = SaslAuthenticateCodec.decodeRequest(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(req);
    }

    private static void assertRoundTrip(SaslAuthenticateResponse resp, byte[] bytes, short version) {
        SaslAuthenticateResponse decoded = SaslAuthenticateCodec.decodeResponse(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(resp);
    }
}
