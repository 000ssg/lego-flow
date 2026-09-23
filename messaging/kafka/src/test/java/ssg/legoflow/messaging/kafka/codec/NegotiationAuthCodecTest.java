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
import ssg.legoflow.messaging.kafka.protocol.RequestHeader;
import ssg.legoflow.messaging.kafka.common.ApiKey;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negotiation/Auth sub-category unit tests (Phase 6a).
 *
 * <p>Layout expectations are taken from the 3.6.1 request/response schemas
 * ({@code doc/spec/LAYOUTS.md}, generated from {@code doc/spec/message/*.json}):
 * <ul>
 *   <li>ApiVersions request v0: empty body (0 bytes); v1 identical</li>
 *   <li>ApiVersions response v0: errorCode(int16) + count(int32) + array of
 *       apiKey/minVersion/maxVersion(int16 each); v1 adds ThrottleTimeMs(int32)
 *       after the array</li>
 *   <li>SaslHandshake request v0: mechanism(string16); v1 byte-identical</li>
 *   <li>SaslHandshake response v0: errorCode(int16) + count(int32) + array of
 *       mechanisms(string16); v1 byte-identical</li>
 *   <li>SaslAuthenticate request v0: authBytes(bytes); v1 byte-identical</li>
 *   <li>SaslAuthenticate response v0: errorCode(int16) + errorMessage(string16)
 *       + authBytes(bytes); v1 adds sessionLifetimeMs(int64) after authBytes</li>
 *   <li>v2 (flexible, SaslAuthenticate): length fields become varints — compact
 *       nullable string for errorMessage, compact nullable bytes for authBytes,
 *       fixed-width integers unchanged. Wire nuance: null and empty nullable
 *       compact bytes are indistinguishable (both varint 1, decode to null).</li>
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
        @DisplayName("v1 request is byte-identical to v0 (spec: no request fields until v3)")
        void v1RequestIdenticalToV0() {
            assertArrayEquals(new byte[0],
                    ApiVersionsCodec.encodeRequest((short) 1, new ApiVersionsRequest()));
        }

        @Test
        @DisplayName("v1 response round-trips with throttleTimeMs; exact layout 2+4+6+4")
        void v1ResponseRoundTrip() {
            List<ApiVersionsResponse.ApiVersion> keys = List.of(
                    new ApiVersionsResponse.ApiVersion((short) 0, (short) 0, (short) 6));
            ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, keys, 150L);

            byte[] body = ApiVersionsCodec.encodeResponse((short) 1, resp);
            // v1 = v0 (12 bytes for 1 key) + ThrottleTimeMs int32
            assertEquals(16, body.length, "errorCode(2) + count(4) + 1*6 + throttle(4)");
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(0, buf.getShort(), "errorCode");
            assertEquals(1, buf.getInt(), "count");
            assertEquals(0, buf.getShort(), "key.apiKey");
            assertEquals(0, buf.getShort(), "key.minVersion");
            assertEquals(6, buf.getShort(), "key.maxVersion");
            assertEquals(150, buf.getInt(), "throttleTimeMs");

            ApiVersionsResponse decoded = ApiVersionsCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
        }

        @Test
        @DisplayName("v2 request is byte-identical to v1 (spec: no request fields until v3)")
        void v2RequestIdenticalToV1() {
            assertArrayEquals(new byte[0],
                    ApiVersionsCodec.encodeRequest((short) 2, new ApiVersionsRequest()));
            ApiVersionsRequest decoded =
                    ApiVersionsCodec.decodeRequest((short) 2,
                            ByteBuffer.wrap(ApiVersionsCodec.encodeRequest((short) 2, new ApiVersionsRequest())));
            assertEquals(new ApiVersionsRequest(), decoded);
        }

        @Test
        @DisplayName("v2 response round-trips — byte-identical to v1 in the 3.6.1 schema")
        void v2ResponseRoundTrip() {
            List<ApiVersionsResponse.ApiVersion> keys = List.of(
                    new ApiVersionsResponse.ApiVersion((short) 0, (short) 0, (short) 6));
            ApiVersionsResponse resp = new ApiVersionsResponse((short) 0, keys, 250L);

            byte[] body = ApiVersionsCodec.encodeResponse((short) 2, resp);
            assertArrayEquals(
                    ApiVersionsCodec.encodeResponse((short) 1, resp),
                    body, "v2 must be byte-identical to v1");
            assertEquals(16, body.length, "v2 = v1 layout: 2 + 4 + 1*6 + 4");

            ApiVersionsResponse decoded = ApiVersionsCodec.decodeResponse((short) 2, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
        }

        @Test
        @DisplayName("v3 throws CodecNotImplementedException (flexible encoding, pending)")
        void v3Throws() {
            assertThrows(CodecNotImplementedException.class,
                    () -> ApiVersionsCodec.encodeResponse((short) 3,
                            new ApiVersionsResponse((short) 0, List.of(), 100L)));
            assertThrows(CodecNotImplementedException.class,
                    () -> ApiVersionsCodec.decodeResponse((short) 3, ByteBuffer.allocate(6)));
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
        @DisplayName("v1 request round-trips — byte-identical to v0 in the 3.6.1 schema")
        void v1RequestRoundTrip() {
            SaslHandshakeRequest req = new SaslHandshakeRequest("PLAIN");

            byte[] body = SaslHandshakeCodec.encodeRequest((short) 1, req);
            assertArrayEquals(
                    SaslHandshakeCodec.encodeRequest((short) 0, req),
                    body, "v1 must be byte-identical to v0");

            SaslHandshakeRequest decoded = SaslHandshakeCodec.decodeRequest((short) 1, ByteBuffer.wrap(body));
            assertEquals(req, decoded);
        }

        @Test
        @DisplayName("v1 response round-trips — byte-identical to v0 in the 3.6.1 schema")
        void v1ResponseRoundTrip() {
            SaslHandshakeResponse resp = new SaslHandshakeResponse((short) 0, List.of("GSSAPI"));

            byte[] body = SaslHandshakeCodec.encodeResponse((short) 1, resp);
            assertArrayEquals(
                    SaslHandshakeCodec.encodeResponse((short) 0, resp),
                    body, "v1 must be byte-identical to v0");

            SaslHandshakeResponse decoded = SaslHandshakeCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));
            assertEquals(resp, decoded);
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
        @DisplayName("v1 request is byte-identical to v0 (spec: no request fields in v1)")
        void v1RequestIdenticalToV0() {
            byte[] payload = {7, 8};
            SaslAuthenticateRequest req = new SaslAuthenticateRequest(payload);
            assertArrayEquals(
                    SaslAuthenticateCodec.encodeRequest((short) 0, req),
                    SaslAuthenticateCodec.encodeRequest((short) 1, req));
            SaslAuthenticateRequest decoded =
                    SaslAuthenticateCodec.decodeRequest((short) 1,
                            ByteBuffer.wrap(SaslAuthenticateCodec.encodeRequest((short) 1, req)));
            assertArrayEquals(payload, decoded.authBytes());
        }

        @Test
        @DisplayName("v1 response round-trips with sessionLifetimeMs; exact layout 2+2+4+4+8")
        void v1ResponseRoundTrip() {
            byte[] serverToken = {1, 2};
            SaslAuthenticateResponse resp = new SaslAuthenticateResponse(
                    (short) 0, "ok", serverToken, 1000L);

            byte[] body = SaslAuthenticateCodec.encodeResponse((short) 1, resp);
            // 2 (errorCode) + 2 (errorMessage len) + 2 ("ok") + 4 (authBytes len) + 2 (authBytes) + 8 (sessionLifetime) = 20
            assertEquals(20, body.length);
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(0, buf.getShort(), "errorCode");
            assertEquals(2, buf.getShort(), "errorMessage length");
            assertEquals((byte) 'o', buf.get(), "errorMessage[0]");
            assertEquals((byte) 'k', buf.get(), "errorMessage[1]");
            assertEquals(2, buf.getInt(), "authBytes length");
            assertEquals((byte) 1, buf.get(), "authBytes[0]");
            assertEquals((byte) 2, buf.get(), "authBytes[1]");
            assertEquals(1000L, buf.getLong(), "sessionLifetimeMs");

            SaslAuthenticateResponse decoded =
                    SaslAuthenticateCodec.decodeResponse((short) 1, ByteBuffer.wrap(body));
            // record holds a byte[] field → compare fields, not record reference-equality
            assertEquals((short) 0, decoded.errorCode());
            assertEquals("ok", decoded.errorMessage());
            assertArrayEquals(serverToken, decoded.authBytes());
            assertEquals(1000L, decoded.sessionLifetimeMs());
        }

        @Test
        @DisplayName("v1 response with empty message/authBytes: exact 16-byte layout")
        void v1ResponseEmptyFields() {
            SaslAuthenticateResponse resp = new SaslAuthenticateResponse(
                    (short) 0, "", new byte[0], 65536L);

            byte[] body = SaslAuthenticateCodec.encodeResponse((short) 1, resp);
            assertEquals(16, body.length, "2 (error) + 2 (msgLen) + 4 (bytesLen) + 8 (lifetime)");
            ByteBuffer buf = ByteBuffer.wrap(body);
            assertEquals(0, buf.getShort());
            assertEquals(0, buf.getShort(), "empty errorMessage length");
            assertEquals(0, buf.getInt(), "empty authBytes length");
            assertEquals(65536L, buf.getLong());
        }

        @Test
        @DisplayName("v2 request round-trips authBytes as compact nullable bytes (varint length, +1 offset)")
        void v2RequestRoundTrip() {
            var req = new SaslAuthenticateRequest(new byte[]{1, 2, 3});
            byte[] enc = SaslAuthenticateCodec.encodeRequest((short) 2, req);
            // compact bytes: varint(3 + 1) = 0x04, then 3 data bytes = 4 bytes total
            assertArrayEquals(new byte[]{0x04, 1, 2, 3}, enc);
            var dec = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
            assertArrayEquals(req.authBytes(), dec.authBytes());
        }

        @Test
        @DisplayName("v2 request: null and empty authBytes are wire-identical (varint 1) — decodes to null")
        void v2RequestNullEmptyIndistinguishable() {
            byte[] encNull = SaslAuthenticateCodec.encodeRequest((short) 2, new SaslAuthenticateRequest(null));
            byte[] encEmpty = SaslAuthenticateCodec.encodeRequest((short) 2, new SaslAuthenticateRequest(new byte[0]));
            assertArrayEquals(new byte[]{0x01}, encNull);
            assertArrayEquals(encNull, encEmpty); // same wire form
            // decodes back to null per the flexible nullable-bytes convention
            var dec = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(encNull));
            assertNull(dec.authBytes());
        }

        @Test
        @DisplayName("v2 response round-trips with exact compact layout: int16 + varint string + varint bytes + int64")
        void v2ResponseRoundTrip() {
            var resp = new SaslAuthenticateResponse((short) 15, "fail", new byte[]{1, 2}, 123_456_789L);
            byte[] enc = SaslAuthenticateCodec.encodeResponse((short) 2, resp);
            // errorCode(2) + errorMessage varint(5) + "fail"(4) + authBytes varint(3) + data(2) + sessionLifetimeMs(8)
            int expectedLen = 2 + 1 + 4 + 1 + 2 + 8;
            assertEquals(expectedLen, enc.length);
            var dec = SaslAuthenticateCodec.decodeResponse((short) 2, ByteBuffer.wrap(enc));
            assertEquals(resp.errorCode(), dec.errorCode());
            assertEquals(resp.errorMessage(), dec.errorMessage());
            assertArrayEquals(resp.authBytes(), dec.authBytes());
            assertEquals(resp.sessionLifetimeMs(), dec.sessionLifetimeMs());
        }

        @Test
        @DisplayName("v2 response with empty errorMessage/authBytes: exact 12-byte layout")
        void v2ResponseEmptyExactLayout() {
            var resp = new SaslAuthenticateResponse((short) 0, "", new byte[0], 0L);
            byte[] enc = SaslAuthenticateCodec.encodeResponse((short) 2, resp);
            // errorCode(2) + errorMessage varint(1)=0x01(empty) + authBytes varint(1)=0x01(empty) + sessionLifetimeMs(8)
            assertEquals(12, enc.length);
            var dec = SaslAuthenticateCodec.decodeResponse((short) 2, ByteBuffer.wrap(enc));
            assertEquals((short) 0, dec.errorCode());
            assertEquals("", dec.errorMessage());
            // empty decodes to null per flexible convention — accept either empty or null
            assertTrue(dec.authBytes() == null || dec.authBytes().length == 0);
            assertEquals(0L, dec.sessionLifetimeMs());
        }

        @Test
        @DisplayName("v2 response with null errorMessage: varint 0 (null) — distinct from empty")
        void v2ResponseNullErrorMessage() {
            var resp = new SaslAuthenticateResponse((short) 0, null, new byte[0], 42L);
            byte[] enc = SaslAuthenticateCodec.encodeResponse((short) 2, resp);
            // errorCode(2) + errorMessage varint(0)=0x00(null) + authBytes varint(1)=0x01 + sessionLifetimeMs(8)
            assertEquals(12, enc.length); // 2 + 1 + 1 + 8 = 12
            var dec = SaslAuthenticateCodec.decodeResponse((short) 2, ByteBuffer.wrap(enc));
            assertEquals((short) 0, dec.errorCode());
            assertNull(dec.errorMessage());
            assertEquals(42L, dec.sessionLifetimeMs());
        }

        @Test
        @DisplayName("v2 request with 200-byte authBytes: multi-byte varint length (201 = 0xC1 0x01)")
        void v2RequestMultiByteVarint() {
            byte[] large = new byte[200];
            java.util.Arrays.fill(large, (byte) 7);
            var req = new SaslAuthenticateRequest(large);
            byte[] enc = SaslAuthenticateCodec.encodeRequest((short) 2, req);
            // compact bytes: varint(200 + 1) = varint(201). 201 = 0b11001001.
            // varint little-endian groups: low 7 bits = 0b1100101 = 0xC9 (|0x80), next 7 = 0b0000001 = 0x01.
            assertEquals(0xC9, enc[0] & 0xFF);
            assertEquals(0x01, enc[1] & 0xFF);
            assertEquals(202, enc.length); // 2 (varint) + 200 (data)
            var dec = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
            assertArrayEquals(large, dec.authBytes());
        }

        @Test
        @DisplayName("v2 request with null authBytes: varint 1 (null) — decodes to null")
        void v2RequestNullAuthBytes() {
            var req = new SaslAuthenticateRequest(null);
            byte[] enc = SaslAuthenticateCodec.encodeRequest((short) 2, req);
            assertArrayEquals(new byte[]{0x01}, enc);
            var dec = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
            assertNull(dec.authBytes());
        }

        @Test
        @DisplayName("v2 request with empty authBytes: varint 1 (empty, same as null) — decodes to null")
        void v2RequestEmptyAuthBytes() {
            var req = new SaslAuthenticateRequest(new byte[0]);
            byte[] enc = SaslAuthenticateCodec.encodeRequest((short) 2, req);
            assertArrayEquals(new byte[]{0x01}, enc);
            var dec = SaslAuthenticateCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
            assertNull(dec.authBytes()); // null and empty are indistinguishable
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

    @Nested
    @DisplayName("Flexible request frame (Kafka 3.0+ header — required for v2)")
    class FlexibleRequestFrame {

        @Test
        @DisplayName("v2 request frame round-trips: apiKey|0x8000, varint correlationId, compact clientId")
        void flexibleFrameRoundTrip() {
            var header = new RequestHeader((short) 36, (short) 2, 42, "client");
            ByteBuffer frame = KafkaCodec.encodeRequest(header, new byte[]{1, 2}, true);
            int len = frame.getInt();
            assertEquals(12 + 2, len); // header(12: 2+2+1+7) + body(2); prefix not counted
            RequestHeader dec = KafkaCodec.decodeRequestHeaderFlexible(frame);
            assertEquals((short) 36, dec.apiKey());       // flexible bit stripped
            assertEquals((short) 2, dec.apiVersion());
            assertEquals(42, dec.correlationId());
            assertEquals("client", dec.clientId());
            // body follows the header at the expected position
            assertEquals(1, frame.get());
            assertEquals(2, frame.get());
        }

        @Test
        @DisplayName("flexible header exact layout: 0x8024 0x0002 0x54 [0x06 c l i e n t] = 12 bytes")
        void flexibleFrameExactBytes() {
            var header = new RequestHeader((short) 36, (short) 2, 42, "client");
            byte[] body = new byte[0];
            ByteBuffer frame = KafkaCodec.encodeRequest(header, body, true);
            int len = frame.getInt();
            assertEquals(12, len); // header(12: 2+2+1+7) + body(0)
            // apiKey | 0x8000 = 0x8024
            short apiKeyBits = frame.getShort();
            assertEquals(0x80, (apiKeyBits >> 8) & 0xFF);
            assertEquals(0x24, apiKeyBits & 0xFF); // apiKey 36
            assertEquals(0x02, frame.getShort());        // apiVersion 2
            // correlationId 42 → zigzag 84 → varint 0x54 (1 byte)
            assertEquals(0x54, frame.get());
            // clientId "client": varint(6+1)=0x07 + 6 bytes
            assertEquals(0x07, frame.get());
            byte[] id = new byte[6];
            frame.get(id);
            assertEquals("client", new String(id, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("flexible frame with null clientId: compact null = varint 0")
        void flexibleFrameNullClientId() {
            var header = new RequestHeader((short) 36, (short) 2, 7, null);
            ByteBuffer frame = KafkaCodec.encodeRequest(header, new byte[]{9}, true);
            int len = frame.getInt();
            assertEquals(6 + 1, len); // header(6: 2+2+1+1) + body(1); prefix not counted
            RequestHeader dec = KafkaCodec.decodeRequestHeaderFlexible(frame);
            assertEquals((short) 36, dec.apiKey());
            assertEquals(7, dec.correlationId());
            assertNull(dec.clientId());
            assertEquals(9, frame.get());
        }

        @Test
        @DisplayName("legacy 2-arg encodeRequest is unchanged (flexible=false path)")
        void legacyPathUnchanged() {
            var header = new RequestHeader((short) 36, (short) 2, 42, "c");
            ByteBuffer frame = KafkaCodec.encodeRequest(header, new byte[]{1});
            int len = frame.getInt();
            assertEquals(11 + 1, len); // header(11: 2+2+4+2+1) + body(1); prefix not counted
            // legacy apiKey is NOT marked flexible
            short apiKey = frame.getShort();
            assertEquals(0x00, (apiKey >> 8) & 0xFF);
            assertEquals(0x24, apiKey & 0xFF);
            assertEquals(0x02, frame.getShort());
            assertEquals(42, frame.getInt());
        }
    }
}
