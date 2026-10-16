package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import ssg.legoflow.messaging.kafka.protocol.LeaveGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.LeaveGroupResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LeaveGroupCodec}.
 *
 * <p>LeaveGroupRequest: v0–v2 singular MemberId; v3+ batch Members[] carrying
 * one entry per model member (KIP-343), with GroupInstanceId (and v5+ Reason)
 * at spec default null. LeaveGroupResponse: v0 ErrorCode; v1+ leading
 * ThrottleTimeMs; v3+ trailing Members[] (spec-default empty); v4+ flexible.
 *
 * <p>The round-trip tests cover every supported version (0–5) in both
 * directions. Goldens are hand-built byte-for-byte references (v0 legacy, v3
 * legacy batch, v4 flexible, v5 flexible + Reason).
 *
 * @since 0.1.0
 */
class LeaveGroupCodecTest {

    private static final LeaveGroupRequest REQUEST =
            new LeaveGroupRequest("group-a", "member-1");

    private static final short ERROR_OK = (short) 0;
    private static final short ERROR_UNKNOWN_MEMBER = (short) 22;

    // ==================== REQUEST ====================

    @Nested
    class Request {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = LeaveGroupCodec.encodeRequest(v, REQUEST);
                LeaveGroupRequest decoded =
                        LeaveGroupCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groupId()).as("request groupId v" + v)
                        .isEqualTo("group-a");
                assertThat(decoded.memberId()).as("request memberId v" + v)
                        .isEqualTo("member-1");
            }
        }

        @Test
        void v0_golden() {
            // GroupId(string16 "group-a") + MemberId(string16 "member-1")
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00086d656d6265722d31"); // "member-1"
            assertThat(LeaveGroupCodec.encodeRequest((short) 0, REQUEST))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeRequest((short) 0, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v3_golden_legacy_batch() {
            // GroupId(string16 "group-a") + Members count(int32 1) + MemberId
            // (string16 "member-1") + GroupInstanceId(nullable string16, -1)
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00000001" +             // Members count = 1
                    "00086d656d6265722d31" + // "member-1"
                    "ffff");                 // GroupInstanceId null (int16 -1)
            assertThat(LeaveGroupCodec.encodeRequest((short) 3, REQUEST))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeRequest((short) 3, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v4_golden_flexible() {
            // compact "group-a" (7 → prefix 8) + Members count varint(1 + 1 = 2)
            // + compact "member-1" (8 → prefix 9) + compact null GroupInstanceId (0x00)
            // + per-member endTags (0x00) + top-level endTags (0x00)
            byte[] golden = fromHex(
                    "0867726f75702d61" +     // compact "group-a"
                    "02" +                   // Members count = 1 (KIP-482: N+1)
                    "096d656d6265722d31" +   // compact "member-1"
                    "00" +                   // GroupInstanceId null
                    "00" +                   // per-member endTags
                    "00");                   // top-level endTags
            assertThat(LeaveGroupCodec.encodeRequest((short) 4, REQUEST))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeRequest((short) 4, ByteBuffer.wrap(golden)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v5_golden_flexible() {
            // v5 adds Reason (nullable, spec default null → 1 zero byte inside the
            // member entry) — everything else identical to v4.
            byte[] v4 = LeaveGroupCodec.encodeRequest((short) 4, REQUEST);
            byte[] v5 = LeaveGroupCodec.encodeRequest((short) 5, REQUEST);
            assertThat(v5.length).isEqualTo(v4.length + 1);
            // Reason null byte sits just before the per-member endTags
            assertThat(v5[v5.length - 3]).isZero();
            assertThat(LeaveGroupCodec.decodeRequest((short) 5, ByteBuffer.wrap(v5)))
                    .isEqualTo(REQUEST);
        }

        @Test
        void v3_addsBatchCountField() {
            // v3 replaces the singular MemberId (string16) with Members[]: int32
            // count(4) + MemberId(string16, 10 — same size here) + nullable
            // GroupInstanceId(string16, 2). So v3 = v0 + count(4) + groupInstanceId(2).
            byte[] v0 = LeaveGroupCodec.encodeRequest((short) 0, REQUEST);
            byte[] v3 = LeaveGroupCodec.encodeRequest((short) 3, REQUEST);
            assertThat(v3.length).isEqualTo(v0.length + 4 + 2);
        }

        @Test
        void v4_switchesToFlexibleEncoding() {
            // v4 = v3 layout re-encoded flexible: the two non-nullable strings
            // each lose 1 byte (int16 prefix → varint length+1), the nullable
            // GroupInstanceId loses 1 (2 → 1 zero byte), the int32 count becomes
            // a 1-byte varint (-3), plus per-member + top-level endTags (+2)
            // → v4 = v3 - 6 + 2.
            byte[] v3 = LeaveGroupCodec.encodeRequest((short) 3, REQUEST);
            byte[] v4 = LeaveGroupCodec.encodeRequest((short) 4, REQUEST);
            assertThat(v4.length).isEqualTo(v3.length - 6 + 2);
        }

        @Test
        void nullFieldsRoundTrip() {
            // GroupId/MemberId are spec-required (non-nullable). Legacy encoding
            // (v0–v3) throws NPE on a null field (s.getBytes); flexible (v4+)
            // writes a null non-nullable string as the empty string, matching the
            // defensive read semantics (readCompactStringNonNullable: null → "").
            for (short v = 0; v <= 3; v++) {
                final short ver = v;
                assertThatThrownBy(() -> LeaveGroupCodec.encodeRequest(ver,
                        new LeaveGroupRequest(null, "member-1")))
                        .as("null groupId v" + ver)
                        .isInstanceOf(NullPointerException.class);
                assertThatThrownBy(() -> LeaveGroupCodec.encodeRequest(ver,
                        new LeaveGroupRequest("group-a", null)))
                        .as("null memberId v" + ver)
                        .isInstanceOf(NullPointerException.class);
            }
            // v4/v5: null non-nullable field is written as the empty string
            for (short v = 4; v <= 5; v++) {
                byte[] encoded = LeaveGroupCodec.encodeRequest(v,
                        new LeaveGroupRequest("group-a", null));
                LeaveGroupRequest decoded =
                        LeaveGroupCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.memberId()).as("null memberId v" + v).isEmpty();
            }
        }

        @Test
        void insufficientBuffer_throws() {
            ByteBuffer tooShort = ByteBuffer.wrap(new byte[]{0, 1, 0});
            assertThatThrownBy(() -> LeaveGroupCodec.decodeRequest((short) 0, tooShort))
                    .isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        void unsupportedVersion_throws() {
            assertThatThrownBy(() -> LeaveGroupCodec.encodeRequest((short) -1, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() -> LeaveGroupCodec.encodeRequest((short) 6, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            LeaveGroupCodec.decodeRequest((short) -1, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            LeaveGroupCodec.decodeRequest((short) 6, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
        }
    }

    // ==================== RESPONSE ====================

    @Nested
    class Response {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded =
                        LeaveGroupCodec.encodeResponse(v, new LeaveGroupResponse(ERROR_OK));
                LeaveGroupResponse decoded =
                        LeaveGroupCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.errorCode()).as("response errorCode v" + v)
                        .isEqualTo(ERROR_OK);
            }
        }

        @Test
        void v0_golden() {
            // ErrorCode(int16 22) only
            byte[] golden = fromHex("0016");
            assertThat(LeaveGroupCodec.encodeResponse(
                    (short) 0, new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER)))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeResponse(
                    (short) 0, ByteBuffer.wrap(golden)))
                    .isEqualTo(new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER));
        }

        @Test
        void v1_golden_withThrottle() {
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 22)
            byte[] golden = fromHex("00000000" + "0016");
            assertThat(LeaveGroupCodec.encodeResponse(
                    (short) 1, new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER)))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeResponse(
                    (short) 1, ByteBuffer.wrap(golden)))
                    .isEqualTo(new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER));
        }

        @Test
        void v4_golden_flexible() {
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 0) + Members count
            // varint(0 + 1 = 1) + top-level endTags (0x00)
            byte[] golden = fromHex("00000000" + "0000" + "01" + "00");
            assertThat(LeaveGroupCodec.encodeResponse(
                    (short) 4, new LeaveGroupResponse(ERROR_OK)))
                    .isEqualTo(golden);
            assertThat(LeaveGroupCodec.decodeResponse(
                    (short) 4, ByteBuffer.wrap(golden)))
                    .isEqualTo(new LeaveGroupResponse(ERROR_OK));
        }

        @Test
        void v5_golden_flexible() {
            // v5 response layout = v4 (spec: "Version 5 is the same as version 4")
            byte[] v4 = LeaveGroupCodec.encodeResponse((short) 4, new LeaveGroupResponse(ERROR_OK));
            byte[] v5 = LeaveGroupCodec.encodeResponse((short) 5, new LeaveGroupResponse(ERROR_OK));
            assertThat(v5).isEqualTo(v4);
            assertThat(LeaveGroupCodec.decodeResponse(
                    (short) 5, ByteBuffer.wrap(v5)))
                    .isEqualTo(new LeaveGroupResponse(ERROR_OK));
        }

        @Test
        void shapeDiffsPerVersion() {
            LeaveGroupResponse resp = new LeaveGroupResponse(ERROR_OK);
            int v0 = LeaveGroupCodec.encodeResponse((short) 0, resp).length;
            int v1 = LeaveGroupCodec.encodeResponse((short) 1, resp).length;
            int v3 = LeaveGroupCodec.encodeResponse((short) 3, resp).length;
            int v4 = LeaveGroupCodec.encodeResponse((short) 4, resp).length;
            // v1 adds the leading throttle (4); v3 adds the batch count (int32 0);
            // v4 swaps the int32 count for a varint (1) + top-level endTags (1)
            assertThat(v1).isEqualTo(v0 + 4);
            assertThat(v3).isEqualTo(v1 + 4);
            assertThat(v4).isEqualTo(v3 - 4 + 1 + 1);
        }

        @Test
        void batchMembersDiscardedOnDecode() {
            // A v3 response carrying a non-empty Members list must decode to the
            // model's errorCode only (per-member entries discarded), and the list
            // must not desync the reader. Hand-built: throttle(0) + error(0) +
            // count(1) + member entry { MemberId("member-1"), GroupInstanceId(-1),
            // ErrorCode(0) } + top-level endTags (0x00) — flexible v4 read.
            String mid = "member-1";
            byte[] bytes = new byte[
                    4 /* throttle */ + 2 /* error */ + 1 /* count varint */
                    + 1 + mid.length()       // compact MemberId (len+1 prefix)
                    + 1                      // GroupInstanceId null
                    + 2 /* member error */
                    + 1 /* per-member endTags */
                    + 1 /* top-level endTags */];
            int i = 0;
            bytes[i++] = 0; bytes[i++] = 0; bytes[i++] = 0; bytes[i++] = 0; // throttle
            bytes[i++] = 0; bytes[i++] = 0;                               // error
            bytes[i++] = 2;                                             // count = 1 (N+1)
            bytes[i++] = (byte) (mid.length() + 1);                        // compact len
            for (byte b : mid.getBytes(java.nio.charset.StandardCharsets.UTF_8)) bytes[i++] = b;
            bytes[i++] = 0;      // GroupInstanceId null
            bytes[i++] = 0; bytes[i++] = 0;  // member ErrorCode
            bytes[i++] = 0;      // per-member endTags
            bytes[i++] = 0;      // top-level endTags
            LeaveGroupResponse decoded =
                    LeaveGroupCodec.decodeResponse((short) 4, ByteBuffer.wrap(bytes));
            assertThat(decoded.errorCode()).isEqualTo(ERROR_OK);
            // reader fully consumed — the entry was skipped cleanly
            ByteBuffer view = ByteBuffer.wrap(bytes);
            LeaveGroupCodec.decodeResponse((short) 4, view);
            assertThat(view.hasRemaining()).isFalse();
        }

        @Test
        void insufficientBuffer_throws() {
            ByteBuffer tooShort = ByteBuffer.wrap(new byte[]{0});
            assertThatThrownBy(() -> LeaveGroupCodec.decodeResponse((short) 0, tooShort))
                    .isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        void unsupportedVersion_throws() {
            assertThatThrownBy(() -> LeaveGroupCodec.encodeResponse((short) -1,
                    new LeaveGroupResponse(ERROR_OK)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() -> LeaveGroupCodec.encodeResponse((short) 6,
                    new LeaveGroupResponse(ERROR_OK)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            LeaveGroupCodec.decodeResponse((short) -1, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            LeaveGroupCodec.decodeResponse((short) 6, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
        }
    }

    // ==================== FACADE DELEGATION ====================

    @Test
    void facade_delegatesToLeaveGroupCodec() {
        byte[] reqFacade = KafkaCodec.encodeLeaveGroupRequest((short) 3, REQUEST);
        byte[] reqCodec = LeaveGroupCodec.encodeRequest((short) 3, REQUEST);
        assertThat(reqFacade).isEqualTo(reqCodec);

        LeaveGroupRequest reqBack = KafkaCodec.decodeLeaveGroupRequest(
                (short) 3, ByteBuffer.wrap(reqFacade));
        assertThat(reqBack).isEqualTo(REQUEST);

        byte[] respFacade = KafkaCodec.encodeLeaveGroupResponse(
                (short) 4, new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER));
        byte[] respCodec = LeaveGroupCodec.encodeResponse(
                (short) 4, new LeaveGroupResponse(ERROR_UNKNOWN_MEMBER));
        assertThat(respFacade).isEqualTo(respCodec);

        LeaveGroupResponse respBack = KafkaCodec.decodeLeaveGroupResponse(
                (short) 4, ByteBuffer.wrap(respFacade));
        assertThat(respBack.errorCode()).isEqualTo(ERROR_UNKNOWN_MEMBER);
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
