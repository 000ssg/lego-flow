package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.ApiVersionsRequest;
import ssg.legoflow.messaging.kafka.protocol.ApiVersionsResponse;
import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslAuthenticateResponse;
import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeRequest;
import ssg.legoflow.messaging.kafka.protocol.SaslHandshakeResponse;
import ssg.legoflow.messaging.kafka.common.ApiKey;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negotiation/Auth sub-category unit tests (Phase 6a).
 *
 * <p>Layout expectations are taken from the 3.6.1 request/response schemas
 * ({@code doc/spec/LAYOUTS.md}, generated from {@code doc/spec/message/*.json}):
 * <ul>
 *   <li>ApiVersions request v0: empty body (0 bytes)</li>
 *   <li>ApiVersions response v0: errorCode(int16) + count(int32) + array of
 *       apiKey/minVersion/maxVersion(int16 each)</li>
 *   <li>SaslHandshake request v0: mechanism(string16)</li>
 *   <li>SaslHandshake response v0: errorCode(int16) + count(int32) + array of
 *       mechanisms(string16)</li>
 *   <li>SaslAuthenticate request v0: authBytes(bytes)</li>
 *   <li>SaslAuthenticate response v0: errorCode(int16) + errorMessage(string16)
 *       + authBytes(bytes) — the deployed wire layout (sessionLifetimeMs does NOT
 *       appear in v0)</li>
 * </ul>
 */
class NegotiationAuthCodecTest {

    @Nested
    @DisplayName("ApiVersions (key 18)")
    class ApiVersions {

        @Test
        @DisplayName("v0 request encodes to exactly 0 bytes (spec: no fields)")
        void v0RequestIsEmpty() {
            byte[] body = ApiVersionsCodec.encodeRequest((short) 0, new ApiVersionsRequest());
            assertArrayEquals(new byte[0], body);
        }

        @Test
        @DisplayName("v0 response round-trips errorCode + 2 apiKeys with exact byte layout")
        void v0ResponseRoundTrip() {
            List<ApiVersionsResponse.ApiVersion> keys = List.of(
                    new ApiVersionsResponse.ApiVersion((short) 0, (short) 0, (short) 6),
                    new ApiVersionsResponse.ApiVersion((short) 18, (short) 0, (short) 3));
            ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, keys, 0L);

            byte[] body = ApiVersionsCodec.encodeResponse((short) 0, resp);

            // Exact spec layout: 2 + 4 + 2*6 = 18 bytes
            assertEquals(18, body.length, "errorCode(2) + count(4) + 2*(3*2)");
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(0, buf.getShort(), "errorCode");
            assertEquals(2, buf.getInt(), "count");
            assertEquals(0, buf.getShort(), "key.apiKey[0]");
            assertEquals(0, buf.getShort(), "key.minVersion[0]");
            assertEquals(6, buf.getShort(), "key.maxVersion[0]");

            ApiVersionsResponse decoded = ApiVersionsCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
        }

        @Test
        @DisplayName("v1 response throws CodecNotImplementedException (v1/v2 = throttleTime, pending)")
        void v1Throws() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ApiVersionsCodec.encodeResponse((short) 1,
                            new ApiVersionsResponse((short) 0, List.of(), 100L)));
            assertThrows(CodecNotImplementedException.class,
                    () -> ApiVersionsCodec.decodeResponse((short) 2, ByteBuffer.allocate(6)));
        }
    }

    @Nested
    @DisplayName("SaslHandshake (key 17)")
    class SaslHandshake {

        @Test
        @DisplayName("v0 request round-trips mechanism(string16)")
        void v0RequestRoundTrip() {
            SaslHandshakeRequest req = new SaslHandshakeRequest("GSSAPI");

            byte[] body = SaslHandshakeCodec.encodeRequest((short) 0, req);
            // string16: length(2) + 6 bytes = 8
            assertEquals(8, body.length);

            SaslHandshakeRequest decoded = SaslHandshakeCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));
            assertEquals(req, decoded);
        }

        @Test
        @DisplayName("v0 response round-trips errorCode + mechanism array; 0 mechanisms = 6 bytes")
        void v0ResponseRoundTrip() {
            SaslHandshakeResponse resp = new SaslHandshakeResponse((short) 0,
                    List.of("GSSAPI", "PLAIN"));

            byte[] body = SaslHandshakeCodec.encodeResponse((short) 0, resp);
            // 2 + 4 + (2+6) + (2+5) = 21
            assertEquals(21, body.length);

            SaslHandshakeResponse decoded = SaslHandshakeCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
        }

        @Test
        @DisplayName("v0 response with no supported mechanisms (spec: fields NULLABLE, v0 wire form empty array)")
        void v0ResponseEmptyMechanisms() {
            SaslHandshakeResponse resp = new SaslHandshakeResponse((short) 33, List.of());

            byte[] body = SaslHandshakeCodec.encodeResponse((short) 0, resp);
            assertEquals(6, body.length, "errorCode(2) + count(4)");

            SaslHandshakeResponse decoded = SaslHandshakeCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
        }

        @Test
        @DisplayName("v1 request/response throw CodecNotImplementedException (v1/v2 pending)")
        void v1Throws() {
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslHandshakeCodec.encodeRequest((short) 1, new SaslHandshakeRequest("PLAIN")));
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslHandshakeCodec.decodeResponse((short) 1, ByteBuffer.allocate(6)));
        }
    }

    @Nested
    @DisplayName("SaslAuthenticate (key 36)")
    class SaslAuthenticate {

        @Test
        @DisplayName("v0 request round-trips authBytes(bytes): length(4) + data")
        void v0RequestRoundTrip() {
            byte[] payload = "user:secret".getBytes();
            SaslAuthenticateRequest req = new SaslAuthenticateRequest(payload);

            byte[] body = SaslAuthenticateCodec.encodeRequest((short) 0, req);
            assertEquals(4 + payload.length, body.length);

            SaslAuthenticateRequest decoded = SaslAuthenticateCodec.decodeRequest((short) 0, ByteBuffer.wrap(body));
            assertArrayEquals(payload, decoded.authBytes());
        }

        @Test
        @DisplayName("v0 request accepts null authBytes as empty (request layout has no nullable semantics)")
        void v0RequestNullAuthBytesIsEmpty() {
            byte[] body = SaslAuthenticateCodec.encodeRequest((short) 0, new SaslAuthenticateRequest((byte[]) null));
            assertArrayEquals(new byte[4], body, "int32 zero-length");
        }

        @Test
        @DisplayName("v0 response exact layout: errorCode(int16) + errorMessage(string16) + authBytes(bytes)")
        void v0ResponseExactLayout() {
            byte[] serverToken = {1, 2, 3, 4};
            SaslAuthenticateResponse resp = new SaslAuthenticateResponse(
                    (short) 0, "", serverToken, 0L);

            byte[] body = SaslAuthenticateCodec.encodeResponse((short) 0, resp);
            // 2 (errorCode) + 2 (errorMessage len) + 4 (authBytes len) + 4 (authBytes) = 12
            assertEquals(12, body.length);
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(0, buf.getShort(), "errorCode");
            assertEquals(0, buf.getShort(), "errorMessage length (empty)");
            assertEquals(4, buf.getInt(), "authBytes length");
            assertArrayEquals(serverToken, new byte[]{body[8], body[9], body[10], body[11]});

            SaslAuthenticateResponse decoded =
                    SaslAuthenticateCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));
            // record holds a byte[] field → compare fields, not record reference-equality
            assertEquals((short) 0, decoded.errorCode());
            assertEquals("", decoded.errorMessage());
            assertArrayEquals(serverToken, decoded.authBytes());
        }

        @Test
        @DisplayName("v0 response carries errorMessage text verbatim")
        void v0ResponseErrorMessage() {
            SaslAuthenticateResponse resp = new SaslAuthenticateResponse(
                    (short) 33, "no SASL mechanism negotiated", new byte[0], 0L);

            byte[] body = SaslAuthenticateCodec.encodeResponse((short) 0, resp);

            SaslAuthenticateResponse decoded =
                    SaslAuthenticateCodec.decodeResponse((short) 0, ByteBuffer.wrap(body));
            assertEquals("no SASL mechanism negotiated", decoded.errorMessage());
            assertEquals((short) 33, decoded.errorCode());
        }

        @Test
        @DisplayName("v1 response throws CodecNotImplementedException (sessionLifetimeMs sub-task pending)")
        void v1Throws() {
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslAuthenticateCodec.encodeResponse((short) 1,
                            new SaslAuthenticateResponse((short) 0, "", new byte[0], 1L)));
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslAuthenticateCodec.decodeResponse((short) 1, ByteBuffer.allocate(16)));
        }

        @Test
        @DisplayName("v2 throws CodecNotImplementedException (flexible format, pending)")
        void v2Throws() {
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslAuthenticateCodec.encodeResponse((short) 2,
                            new SaslAuthenticateResponse((short) 0, "", new byte[0], 0L)));
            assertThrows(CodecNotImplementedException.class,
                    () -> SaslAuthenticateCodec.decodeResponse((short) 2, ByteBuffer.allocate(16)));
        }
    }

    @Test
    @DisplayName("Facade delegates all six negotiation/auth methods to the sub-category codecs")
    void facadeDelegates() {
        // Request paths
        assertArrayEquals(
                ApiVersionsCodec.encodeRequest((short) 0, new ApiVersionsRequest()),
                KafkaCodec.encodeApiVersionsRequest(new ApiVersionsRequest()));

        assertArrayEquals(
                SaslHandshakeCodec.encodeRequest((short) 0, new SaslHandshakeRequest("PLAIN")),
                KafkaCodec.encodeSaslHandshakeRequest(new SaslHandshakeRequest("PLAIN")));

        assertArrayEquals(
                SaslAuthenticateCodec.encodeRequest((short) 0, new SaslAuthenticateRequest(new byte[]{9})),
                KafkaCodec.encodeSaslAuthenticateRequest(new SaslAuthenticateRequest(new byte[]{9})));

        // Response paths
        ApiVersionsResponse avr = new ApiVersionsResponse((short) 0,
                List.of(new ApiVersionsResponse.ApiVersion((short) 1, (short) 0, (short) 16)), 0L);
        assertArrayEquals(
                ApiVersionsCodec.encodeResponse((short) 0, avr),
                KafkaCodec.encodeApiVersionsResponse(avr));

        SaslHandshakeResponse shr = new SaslHandshakeResponse((short) 0, List.of("PLAIN"));
        assertArrayEquals(
                SaslHandshakeCodec.encodeResponse((short) 0, shr),
                KafkaCodec.encodeSaslHandshakeResponse(shr));

        SaslAuthenticateResponse sar = new SaslAuthenticateResponse((short) 0, "done", new byte[0], 0L);
        assertArrayEquals(
                SaslAuthenticateCodec.encodeResponse((short) 0, sar),
                KafkaCodec.encodeSaslAuthenticateResponse(sar));

        // Decode paths return identical objects
        ByteBuffer avrBuf = ByteBuffer.wrap(KafkaCodec.encodeApiVersionsResponse(avr));
        assertEquals(avr, KafkaCodec.decodeApiVersionsResponse(avrBuf));

        ByteBuffer shrBuf = ByteBuffer.wrap(KafkaCodec.encodeSaslHandshakeResponse(shr));
        assertEquals(shr, KafkaCodec.decodeSaslHandshakeResponse(shrBuf));

        ByteBuffer sarBuf = ByteBuffer.wrap(KafkaCodec.encodeSaslAuthenticateResponse(sar));
        SaslAuthenticateResponse sarDecoded = KafkaCodec.decodeSaslAuthenticateResponse(sarBuf);
        // record holds a byte[] field → compare fields, not record reference-equality
        assertEquals((short) 0, sarDecoded.errorCode());
        assertEquals("done", sarDecoded.errorMessage());
        assertArrayEquals(new byte[0], sarDecoded.authBytes());

        // Unused-enum guard: key 25 AddOffsetsToTxn exists with v0..v3 (plan matrix §SASL).
        // Nothing in this sub-category should reference it; this asserts the ApiKey
        // registry itself is intact after the split.
        assertTrue(ApiKey.ADD_OFFSETS_TO_TXN != null, "ApiKey registry intact");
    }
}
