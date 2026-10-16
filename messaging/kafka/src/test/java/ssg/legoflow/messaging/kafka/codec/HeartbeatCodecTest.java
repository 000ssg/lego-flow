package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import ssg.legoflow.messaging.kafka.protocol.HeartbeatRequest;
import ssg.legoflow.messaging.kafka.protocol.HeartbeatResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HeartbeatCodec}.
 *
 * <p>HeartbeatRequest: v0 GroupId + GenerationId + MemberId; v3+ nullable
 * GroupInstanceId (spec dflt null — in-house model does not expose it, codec
 * writes/reads the default). HeartbeatResponse: v0 ErrorCode; v1+ leading
 * ThrottleTimeMs (spec dflt 0); v4+ flexible.
 *
 * <p>The round-trip tests cover every supported version (0–4) in both directions.
 * Goldens are hand-built byte-for-byte references (v0 legacy, v3 legacy with
 * GroupInstanceId, v4 flexible).
 *
 * @since 0.1.0
 */
class HeartbeatCodecTest {

    private static final HeartbeatRequest REQUEST =
            new HeartbeatRequest("group-a", 5, "member-1");

    private static final short ERROR_OK = (short) 0;
    private static final short ERROR_REBALANCE = (short) 27;

    // ==================== REQUEST ====================

    @Nested
    class Request {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 4; v++) {
                byte[] encoded = HeartbeatCodec.encodeRequest(v, REQUEST);
                HeartbeatRequest decoded =
                        HeartbeatCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groupId()).as("request groupId v" + v)
                        .isEqualTo("group-a");
                assertThat(decoded.generationId()).as("request generationId v" + v)
                        .isEqualTo(5);
                assertThat(decoded.memberId()).as("request memberId v" + v)
                        .isEqualTo("member-1");
            }
        }

        @Test
        void v0_golden() {
            // GroupId(string16 "group-a") + GenerationId(int32 5) + MemberId(string16 "member-1")
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00000005" +             // generationId = 5
                    "00086d656d6265722d31"); // "member-1"
            assertThat(HeartbeatCodec.encodeRequest((short) 0, REQUEST))
                    .isEqualTo(golden);
            assertThat(HeartbeatCodec.decodeRequest((short) 0, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v3_golden_legacy() {
            // v0 fields + trailing nullable GroupInstanceId (int16 -1)
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00000005" +             // generationId = 5
                    "00086d656d6265722d31" + // "member-1"
                    "ffff");                 // GroupInstanceId null (int16 -1)
            assertThat(HeartbeatCodec.encodeRequest((short) 3, REQUEST))
                    .isEqualTo(golden);
            assertThat(HeartbeatCodec.decodeRequest((short) 3, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v4_golden_flexible_withNullGroupInstanceId() {
            // compact "group-a" (len 7 → prefix 8) + int32 5 + compact "member-1" (8 bytes → prefix 9)
            // + compact null GroupInstanceId (0x00) + top-level endTags (0x00)
            byte[] golden = fromHex(
                    "0867726f75702d61" +     // compact "group-a"
                    "00000005" +             // generationId = 5
                    "096d656d6265722d31" +   // compact "member-1"
                    "00" +                   // GroupInstanceId null
                    "00");                   // top-level endTags
            assertThat(HeartbeatCodec.encodeRequest((short) 4, REQUEST))
                    .isEqualTo(golden);
            assertThat(HeartbeatCodec.decodeRequest((short) 4, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v3_addsGroupInstanceIdTrailing() {
            // v3 request = v0 + trailing nullable GroupInstanceId (int16 -1). v3 is still legacy
            // encoding, so the added field is 2 bytes.
            byte[] v0 = HeartbeatCodec.encodeRequest((short) 0, REQUEST);
            byte[] v3 = HeartbeatCodec.encodeRequest((short) 3, REQUEST);
            assertThat(v3.length).isEqualTo(v0.length + 2);
            // trailing bytes = 0xFF 0xFF (int16 -1)
            assertThat(v3[v3.length - 2]).isEqualTo((byte) 0xff);
            assertThat(v3[v3.length - 1]).isEqualTo((byte) 0xff);
        }

        @Test
        void nullFieldsRoundTrip() {
            // GroupId/MemberId are spec-required (non-nullable). Legacy encoding (v0–v2)
            // throws NPE on a null field (s.getBytes); flexible (v4) writes a null
            // non-nullable string as the empty string, matching the defensive read
            // semantics (readCompactStringNonNullable: null → "").
            for (short v = 0; v <= 2; v++) {
                final short ver = v;
                assertThatThrownBy(() -> HeartbeatCodec.encodeRequest(ver,
                        new HeartbeatRequest(null, -1, "member-1")))
                        .as("null groupId v" + ver)
                        .isInstanceOf(NullPointerException.class);
                assertThatThrownBy(() -> HeartbeatCodec.encodeRequest(ver,
                        new HeartbeatRequest("group-a", -1, null)))
                        .as("null memberId v" + ver)
                        .isInstanceOf(NullPointerException.class);
            }
            // v4: null non-nullable field is written as the empty string
            byte[] encoded = HeartbeatCodec.encodeRequest((short) 4,
                    new HeartbeatRequest("group-a", -1, null));
            HeartbeatRequest decoded =
                    HeartbeatCodec.decodeRequest((short) 4, ByteBuffer.wrap(encoded));
            assertThat(decoded.memberId()).as("null memberId v4").isEmpty();
        }

        @Test
        void insufficientBuffer_throws() {
            ByteBuffer tooShort = ByteBuffer.wrap(new byte[]{0, 1, 0});
            assertThatThrownBy(() -> HeartbeatCodec.decodeRequest((short) 0, tooShort))
                    .isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        void unsupportedVersion_throws() {
            assertThatThrownBy(() -> HeartbeatCodec.encodeRequest((short) -1, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() -> HeartbeatCodec.encodeRequest((short) 5, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            HeartbeatCodec.decodeRequest((short) -1, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            HeartbeatCodec.decodeRequest((short) 5, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
        }
    }

    // ==================== RESPONSE ====================

    @Nested
    class Response {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 4; v++) {
                byte[] encoded =
                        HeartbeatCodec.encodeResponse(v, new HeartbeatResponse(ERROR_OK));
                HeartbeatResponse decoded =
                        HeartbeatCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.errorCode()).as("response errorCode v" + v)
                        .isEqualTo(ERROR_OK);
            }
        }

        @Test
        void v0_golden() {
            // ErrorCode(int16 0) only
            byte[] golden = fromHex("0000");
            assertThat(HeartbeatCodec.encodeResponse((short) 0, new HeartbeatResponse(ERROR_OK)))
                    .isEqualTo(golden);
        }

        @Test
        void v1_golden_withThrottle() {
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 27)
            byte[] golden = fromHex("00000000" + "001b");
            assertThat(HeartbeatCodec.encodeResponse(
                    (short) 1, new HeartbeatResponse(ERROR_REBALANCE)))
                    .isEqualTo(golden);
        }

        @Test
        void v4_golden_flexible() {
            // ThrottleTimeMs(int32 0) [flex] + ErrorCode(int16 0) [flex] + top-level endTags (0x00)
            byte[] golden = fromHex("00000000" + "0000" + "00");
            assertThat(HeartbeatCodec.encodeResponse(
                    (short) 4, new HeartbeatResponse(ERROR_OK)))
                    .isEqualTo(golden);
        }

        @Test
        void throttleDistinguishesFromError() {
            // A v1+ response with throttle=1 and error=0 must not decode as a v0 error=1.
            byte[] v1 = HeartbeatCodec.encodeResponse((short) 1, new HeartbeatResponse(ERROR_OK));
            // manually corrupt: set first byte of throttle to 1
            v1[0] = 1;
            HeartbeatResponse decoded = HeartbeatCodec.decodeResponse((short) 1, ByteBuffer.wrap(v1));
            // throttle read as 0x01000000 = 16777216, error still 0
            assertThat(decoded.errorCode()).isEqualTo(ERROR_OK);
            // v0 would read the same bytes as error=0x0100 = 256 → different
            HeartbeatResponse asV0 =
                    HeartbeatCodec.decodeResponse((short) 0, ByteBuffer.wrap(new byte[]{1, 0}));
            assertThat(asV0.errorCode()).isEqualTo((short) 256);
        }

        @Test
        void nullThrottleInV1Plus_decodesAsErrorOnly() {
            // A response body containing ONLY an int16 (no throttle) decoded at v1+
            // would read 8 bytes of throttle → insufficient. But a proper v1+ encode
            // always writes throttle. Verify v1+ never drops the leading throttle:
            byte[] v1 = HeartbeatCodec.encodeResponse((short) 1, new HeartbeatResponse(ERROR_OK));
            assertThat(v1.length).isEqualTo(6); // 4 (throttle) + 2 (error)
            byte[] v4 = HeartbeatCodec.encodeResponse((short) 4, new HeartbeatResponse(ERROR_OK));
            assertThat(v4.length).isEqualTo(7); // 4 (throttle) + 2 (error) + 1 (endTags)
        }
    }

    // ==================== FACADE DELEGATION ====================

    @Test
    void facade_delegatesToHeartbeatCodec() {
        byte[] reqFacade = KafkaCodec.encodeHeartbeatRequest((short) 3, REQUEST);
        byte[] reqCodec = HeartbeatCodec.encodeRequest((short) 3, REQUEST);
        assertThat(reqFacade).isEqualTo(reqCodec);

        HeartbeatRequest reqBack = KafkaCodec.decodeHeartbeatRequest(
                (short) 3, ByteBuffer.wrap(reqFacade));
        assertThat(reqBack).isEqualTo(REQUEST);

        byte[] respFacade = KafkaCodec.encodeHeartbeatResponse((short) 1, new HeartbeatResponse(ERROR_REBALANCE));
        byte[] respCodec = HeartbeatCodec.encodeResponse((short) 1, new HeartbeatResponse(ERROR_REBALANCE));
        assertThat(respFacade).isEqualTo(respCodec);

        HeartbeatResponse respBack = KafkaCodec.decodeHeartbeatResponse(
                (short) 1, ByteBuffer.wrap(respFacade));
        assertThat(respBack.errorCode()).isEqualTo(ERROR_REBALANCE);
    }

    // ==================== HELPERS ====================

    private static byte[] fromHex(String hex) {
        hex = hex.replaceAll("\\s", "");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
