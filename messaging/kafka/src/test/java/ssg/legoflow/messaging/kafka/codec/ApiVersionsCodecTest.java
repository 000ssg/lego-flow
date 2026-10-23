package ssg.legoflow.messaging.kafka.codec;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.common.KafkaApiVersions;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsRequest;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * ApiVersions codec tests — all versions v0–v3.
 */
class ApiVersionsCodecTest {

    // ── Request round-trip tests ──────────────────────────────────────────────

    @Test
    void requestRoundTrip_v0() {
        ApiVersionsRequest req = new ApiVersionsRequest(List.of());
        assertRoundTrip(req, ApiVersionsCodec.encodeRequest((short) 0, req), (short) 0);
    }

    @Test
    void requestRoundTrip_v1() {
        ApiVersionsRequest req = new ApiVersionsRequest(List.of());
        assertRoundTrip(req, ApiVersionsCodec.encodeRequest((short) 1, req), (short) 1);
    }

    @Test
    void requestRoundTrip_v2() {
        ApiVersionsRequest req = new ApiVersionsRequest(List.of());
        assertRoundTrip(req, ApiVersionsCodec.encodeRequest((short) 2, req), (short) 2);
    }

    @Test
    void requestRoundTrip_v3() {
        ApiVersionsRequest req = new ApiVersionsRequest(List.of());
        assertRoundTrip(req, ApiVersionsCodec.encodeRequest((short) 3, req), (short) 3);
    }

    // ── Response round-trip tests ─────────────────────────────────────────────

    @Test
    void responseRoundTrip_v0() {
        ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, List.of());
        assertRoundTrip(resp, ApiVersionsCodec.encodeResponse((short) 0, resp), (short) 0);
    }

    @Test
    void responseRoundTrip_v1() {
        ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, List.of());
        assertRoundTrip(resp, ApiVersionsCodec.encodeResponse((short) 1, resp), (short) 1);
    }

    @Test
    void responseRoundTrip_v2() {
        ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, List.of());
        assertRoundTrip(resp, ApiVersionsCodec.encodeResponse((short) 2, resp), (short) 2);
    }

    @Test
    void responseRoundTrip_v3() {
        ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, List.of());
        assertRoundTrip(resp, ApiVersionsCodec.encodeResponse((short) 3, resp), (short) 3);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private static void assertRoundTrip(ApiVersionsRequest req, byte[] bytes, short version) {
        ApiVersionsRequest decoded = ApiVersionsCodec.decodeRequest(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(req);
    }

    private static void assertRoundTrip(ApiVersionsResponse resp, byte[] bytes, short version) {
        ApiVersionsResponse decoded = ApiVersionsCodec.decodeResponse(version, ByteBuffer.wrap(bytes));
        assertThat(decoded).isEqualTo(resp);
    }
}
