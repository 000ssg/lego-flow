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

    private static final byte[] AUTH_BYTES = {1, 2, 3};
    private static final byte[] RESPONSE_BYTES = {4, 5, 6};

    // ── Request round-trip tests ──────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(AUTH_BYTES);
        byte[] bytes = SaslAuthenticateCodec.encodeRequest((short) 0, req);
        SaslAuthenticateRequest decoded = SaslAuthenticateCodec.decodeRequest((short) 0, ByteBuffer.wrap(bytes));
        assertThat(decoded.authBytes()).isEqualTo(AUTH_BYTES);
    }

    @Test
    void requestRoundTrip_v1() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(AUTH_BYTES);
        byte[] bytes = SaslAuthenticateCodec.encodeRequest((short) 1, req);
        SaslAuthenticateRequest decoded = SaslAuthenticateCodec.decodeRequest((short) 1, ByteBuffer.wrap(bytes));
        assertThat(decoded.authBytes()).isEqualTo(AUTH_BYTES);
    }

    @Test
    void requestRoundTrip_v2() {
        SaslAuthenticateRequest req = new SaslAuthenticateRequest(AUTH_BYTES);
        byte[] bytes = SaslAuthenticateCodec.encodeRequest((short) 2, req);
        SaslAuthenticateRequest decoded = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(bytes));
        assertThat(decoded.authBytes()).isEqualTo(AUTH_BYTES);
    }

    // ── Response round-trip tests ─────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, RESPONSE_BYTES, 0L);
        byte[] bytes = SaslAuthenticateCodec.encodeResponse((short) 0, resp);
        SaslAuthenticateResponse decoded = SaslAuthenticateCodec.decodeResponse((short) 0, ByteBuffer.wrap(bytes));
        assertThat(decoded.errorCode()).isEqualTo((short) 0);
        assertThat(decoded.authBytes()).isEqualTo(RESPONSE_BYTES);
        assertThat(decoded.sessionLifetimeMs()).isEqualTo(0L);
    }

    @Test
    void responseRoundTrip_v1() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, RESPONSE_BYTES, 0L);
        byte[] bytes = SaslAuthenticateCodec.encodeResponse((short) 1, resp);
        SaslAuthenticateResponse decoded = SaslAuthenticateCodec.decodeResponse((short) 1, ByteBuffer.wrap(bytes));
        assertThat(decoded.errorCode()).isEqualTo((short) 0);
        assertThat(decoded.authBytes()).isEqualTo(RESPONSE_BYTES);
        assertThat(decoded.sessionLifetimeMs()).isEqualTo(0L);
    }

    @Test
    void responseRoundTrip_v2() {
        SaslAuthenticateResponse resp = new SaslAuthenticateResponse((short) 0, RESPONSE_BYTES, 0L);
        byte[] bytes = SaslAuthenticateCodec.encodeResponse((short) 2, resp);
        SaslAuthenticateResponse decoded = SaslAuthenticateCodec.decodeResponse((short) 2, ByteBuffer.wrap(bytes));
        assertThat(decoded.errorCode()).isEqualTo((short) 0);
        assertThat(decoded.authBytes()).isEqualTo(RESPONSE_BYTES);
        assertThat(decoded.sessionLifetimeMs()).isEqualTo(0L);
    }
}
