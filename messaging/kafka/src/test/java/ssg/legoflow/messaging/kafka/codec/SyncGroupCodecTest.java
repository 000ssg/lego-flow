package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ssg.legoflow.messaging.kafka.protocol.SyncGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.SyncGroupResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SyncGroupCodec}.
 *
 * <p>SyncGroupRequest: v0–v2 GroupId/GenerationId/MemberId/Assignments[]; v3+
 * nullable GroupInstanceId; v5+ nullable ProtocolType/ProtocolName (KIP-559).
 * SyncGroupResponse: v0 ErrorCode+Assignment; v1+ leading ThrottleTimeMs; v5+
 * nullable ProtocolType/ProtocolName (KIP-559); v4+ flexible. v5 response is
 * NOT wire-identical to v4 — the two protocol fields add 2 bytes.
 *
 * <p>The round-trip tests cover every supported version (0–5) in both
 * directions. Goldens are hand-built byte-for-byte references (v0 legacy, v3
 * legacy + groupInstanceId, v4 flexible, v5 flexible + protocol fields).
 *
 * @since 0.1.0
 */
class SyncGroupCodecTest {

    private static final byte[] ASSIGNMENT_DATA = {1, 2, 3};

    private static final SyncGroupRequest REQUEST = new SyncGroupRequest(
            "group-a", 1, "member-1",
            List.of(new SyncGroupRequest.Assignment("member-1", ASSIGNMENT_DATA)));

    private static final short ERROR_OK = (short) 0;
    private static final short ERROR_UNKNOWN_MEMBER = (short) 22;

    // ==================== REQUEST ====================

    @Nested
    class Request {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = SyncGroupCodec.encodeRequest(v, REQUEST);
                SyncGroupRequest decoded =
                        SyncGroupCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groupId()).as("request groupId v" + v)
                        .isEqualTo("group-a");
                assertThat(decoded.generationId()).as("request generationId v" + v)
                        .isEqualTo(1);
                assertThat(decoded.memberId()).as("request memberId v" + v)
                        .isEqualTo("member-1");
                assertThat(decoded.assignments()).as("request assignments v" + v)
                        .hasSize(1);
                assertThat(decoded.assignments().getFirst().memberId()).isEqualTo("member-1");
                assertThat(decoded.assignments().getFirst().assignment())
                        .isEqualTo(ASSIGNMENT_DATA);
            }
        }

        @Test
        void v0_golden() {
            // GroupId(string16 "group-a") + GenerationId(int32 1) + MemberId
            // (string16 "member-1") + Assignments count(int32 1) + entry:
            // MemberId(string16 "member-1") + Assignment(bytes3 {1,2,3})
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00000001" +             // GenerationId
                    "00086d656d6265722d31" + // "member-1"
                    "00000001" +             // Assignments count = 1
                    "00086d656d6265722d31" + // entry MemberId "member-1"
                    "00000003010203");       // Assignment bytes {1,2,3}
            assertThat(SyncGroupCodec.encodeRequest((short) 0, REQUEST))
                    .isEqualTo(golden);
            assertRequestEquals(REQUEST,
                    SyncGroupCodec.decodeRequest((short) 0, ByteBuffer.wrap(golden)));
        }

        @Test
        void v3_golden_legacy_withGroupInstanceId() {
            // v3 adds nullable GroupInstanceId (string16, -1 null) after MemberId
            byte[] golden = fromHex(
                    "000767726f75702d61" +   // "group-a"
                    "00000001" +             // GenerationId
                    "00086d656d6265722d31" + // "member-1"
                    "ffff" +                 // GroupInstanceId null (int16 -1)
                    "00000001" +             // Assignments count = 1
                    "00086d656d6265722d31" + // entry MemberId "member-1"
                    "00000003010203");       // Assignment bytes {1,2,3}
            assertThat(SyncGroupCodec.encodeRequest((short) 3, REQUEST))
                    .isEqualTo(golden);
            assertRequestEquals(REQUEST,
                    SyncGroupCodec.decodeRequest((short) 3, ByteBuffer.wrap(golden)));
        }

        @Test
        void v4_golden_flexible() {
            // compact "group-a" (7 → prefix 8) + GenerationId(int32 1) +
            // compact "member-1" (8 → prefix 9) + compact null GroupInstanceId
            // (0x00) + Assignments count varint(1 + 1 = 2) + entry { compact
            // "member-1" (9), compact bytes {1,2,3} (3 → prefix 4: 04 01 02 03) }
            // + per-entry endTags (0x00) + top-level endTags (0x00)
            byte[] golden = fromHex(
                    "0867726f75702d61" +     // compact "group-a"
                    "00000001" +             // GenerationId
                    "096d656d6265722d31" +   // compact "member-1"
                    "00" +                   // GroupInstanceId null
                    "02" +                   // Assignments count = 1 (KIP-482: N+1)
                    "096d656d6265722d31" +   // entry MemberId
                    "04010203" +             // entry Assignment bytes
                    "00" +                   // per-entry endTags
                    "00");                   // top-level endTags
            assertThat(SyncGroupCodec.encodeRequest((short) 4, REQUEST))
                    .isEqualTo(golden);
            assertRequestEquals(REQUEST,
                    SyncGroupCodec.decodeRequest((short) 4, ByteBuffer.wrap(golden)));
        }

        @Test
        void v5_golden_flexible() {
            // v5 adds nullable ProtocolType + ProtocolName (KIP-559) after
            // GroupInstanceId — both at spec default null, one zero byte each.
            // NOTE: not appended after the endTags; field order per spec is
            // GroupId, GenerationId, MemberId, GroupInstanceId, ProtocolType,
            // ProtocolName, Assignments.
            byte[] v5 = SyncGroupCodec.encodeRequest((short) 5, REQUEST);
            byte[] golden = fromHex(
                    "0867726f75702d61" +     // compact "group-a"
                    "00000001" +             // GenerationId (int32)
                    "096d656d6265722d31" +   // compact "member-1"
                    "00" +                   // GroupInstanceId null
                    "00" +                   // ProtocolType null
                    "00" +                   // ProtocolName null
                    "02" +                   // Assignments count = 1 (KIP-482: N+1)
                    "096d656d6265722d31" +   // entry MemberId
                    "04010203" +             // entry Assignment bytes
                    "00" +                   // per-entry endTags
                    "00");                   // top-level endTags
            assertThat(v5).isEqualTo(golden);
            assertRequestEquals(REQUEST,
                    SyncGroupCodec.decodeRequest((short) 5, ByteBuffer.wrap(golden)));
        }

        @Test
        void shapeDiffsPerVersion() {
            int v0 = SyncGroupCodec.encodeRequest((short) 0, REQUEST).length;
            int v3 = SyncGroupCodec.encodeRequest((short) 3, REQUEST).length;
            int v4 = SyncGroupCodec.encodeRequest((short) 4, REQUEST).length;
            int v5 = SyncGroupCodec.encodeRequest((short) 5, REQUEST).length;
            // v3 adds nullable GroupInstanceId as int16 -1 (+2).
            assertThat(v3).isEqualTo(v0 + 2);
            // v4 = v3 re-encoded flexible: each of the four string length prefixes
            // int16 → 1-byte varint (-1 × 4, including nullable GroupInstanceId
            // 2 → 1); the int32 array count → 1-byte varint (-3); the bytes length
            // prefix int32 → 1-byte varint (-3); per-entry + top-level endTags (+2)
            // → v4 = v3 - 8. GenerationId stays int32 (0).
            assertThat(v4).isEqualTo(v3 - 8);
            // v5 = v4 + two null compact protocol strings (1 byte each).
            assertThat(v5).isEqualTo(v4 + 2);
        }

        @Test
        void nullFieldsRoundTrip() {
            // GroupId/MemberId are spec-required (non-nullable). Legacy encoding
            // (v0–v3) throws NPE on a null field (s.getBytes); flexible (v4+)
            // writes a null non-nullable string as the empty string, matching the
            // defensive read semantics (readCompactStringNonNullable: null → "").
            for (short v = 0; v <= 3; v++) {
                final short ver = v;
                assertThatThrownBy(() -> SyncGroupCodec.encodeRequest(ver,
                        new SyncGroupRequest(null, 1, "member-1",
                                List.of(new SyncGroupRequest.Assignment("m", new byte[0])))))
                        .as("null groupId v" + ver)
                        .isInstanceOf(NullPointerException.class);
            }
            // v4/v5: null non-nullable groupId is written as the empty string;
            // a null assignment is written as empty bytes
            for (short v = 4; v <= 5; v++) {
                var req = new SyncGroupRequest(null, 1, "member-1",
                        List.of(new SyncGroupRequest.Assignment("member-1", null)));
                byte[] encoded = SyncGroupCodec.encodeRequest(v, req);
                SyncGroupRequest decoded =
                        SyncGroupCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.groupId()).as("null groupId v" + v).isEmpty();
                assertThat(decoded.assignments().getFirst().assignment()).isEmpty();
            }
        }

        @Test
        void multipleAssignmentsRoundTrip() {
            var req = new SyncGroupRequest("group-a", 1, "member-1", List.of(
                    new SyncGroupRequest.Assignment("m-1", new byte[]{1, 2, 3}),
                    new SyncGroupRequest.Assignment("m-2", new byte[]{4, 5})));
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = SyncGroupCodec.encodeRequest(v, req);
                SyncGroupRequest decoded =
                        SyncGroupCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.assignments()).as("assignments v" + v).hasSize(2);
                assertThat(decoded.assignments().get(0).memberId()).isEqualTo("m-1");
                assertThat(decoded.assignments().get(0).assignment()).isEqualTo(new byte[]{1, 2, 3});
                assertThat(decoded.assignments().get(1).memberId()).isEqualTo("m-2");
                assertThat(decoded.assignments().get(1).assignment()).isEqualTo(new byte[]{4, 5});
            }
        }

        @Test
        void insufficientBuffer_throws() {
            ByteBuffer tooShort = ByteBuffer.wrap(new byte[]{0, 1, 0});
            assertThatThrownBy(() -> SyncGroupCodec.decodeRequest((short) 0, tooShort))
                    .isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        void unsupportedVersion_throws() {
            assertThatThrownBy(() -> SyncGroupCodec.encodeRequest((short) -1, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() -> SyncGroupCodec.encodeRequest((short) 6, REQUEST))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            SyncGroupCodec.decodeRequest((short) -1, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            SyncGroupCodec.decodeRequest((short) 6, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
        }
    }

    // ==================== RESPONSE ====================

    @Nested
    class Response {

        @Test
        void encodeDecode_roundTrip() {
            for (short v = 0; v <= 5; v++) {
                byte[] encoded = SyncGroupCodec.encodeResponse(v,
                        new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA));
                SyncGroupResponse decoded =
                        SyncGroupCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.errorCode()).as("response errorCode v" + v)
                        .isEqualTo(ERROR_OK);
                assertThat(decoded.assignment()).as("response assignment v" + v)
                        .isEqualTo(ASSIGNMENT_DATA);
            }
        }

        @Test
        void v0_golden() {
            // ErrorCode(int16 22) + Assignment(bytes3 {1,2,3})
            byte[] golden = fromHex("0016" + "00000003010203");
            assertThat(SyncGroupCodec.encodeResponse(
                    (short) 0, new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA)))
                    .isEqualTo(golden);
            assertResponseEquals(new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA),
                    SyncGroupCodec.decodeResponse((short) 0, ByteBuffer.wrap(golden)));
        }

        @Test
        void v1_golden_withThrottle() {
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 22) + Assignment
            byte[] golden = fromHex("00000000" + "0016" + "00000003010203");
            assertThat(SyncGroupCodec.encodeResponse(
                    (short) 1, new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA)))
                    .isEqualTo(golden);
            assertResponseEquals(new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA),
                    SyncGroupCodec.decodeResponse((short) 1, ByteBuffer.wrap(golden)));
        }

        @Test
        void v4_golden_flexible() {
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 0) + compact bytes
            // {1,2,3} (3 → prefix 4) + top-level endTags (0x00)
            byte[] golden = fromHex("00000000" + "0000" + "04010203" + "00");
            assertThat(SyncGroupCodec.encodeResponse(
                    (short) 4, new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA)))
                    .isEqualTo(golden);
            assertResponseEquals(new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA),
                    SyncGroupCodec.decodeResponse((short) 4, ByteBuffer.wrap(golden)));
        }

        @Test
        void v5_golden_flexible() {
            // v5 = v4 flexible + nullable ProtocolType + ProtocolName between
            // ErrorCode and Assignment (KIP-559). The model exposes no protocol
            // fields, so both are at their spec default null — one zero byte each.
            // ThrottleTimeMs(int32 0) + ErrorCode(int16 0) + ProtocolType null (varint 0)
            // + ProtocolName null (varint 0) + compact bytes {1,2,3} (3 → prefix 4)
            // + top-level endTags (0x00)
            byte[] golden = fromHex(
                    "00000000" + "0000" + "00" + "00" + "04010203" + "00");
            assertThat(SyncGroupCodec.encodeResponse(
                    (short) 5, new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA)))
                    .isEqualTo(golden);
            assertResponseEquals(new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA),
                    SyncGroupCodec.decodeResponse((short) 5, ByteBuffer.wrap(golden)));
        }

        @Test
        void shapeDiffsPerVersion() {
            SyncGroupResponse resp = new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA);
            int v0 = SyncGroupCodec.encodeResponse((short) 0, resp).length;
            int v1 = SyncGroupCodec.encodeResponse((short) 1, resp).length;
            int v3 = SyncGroupCodec.encodeResponse((short) 3, resp).length;
            int v4 = SyncGroupCodec.encodeResponse((short) 4, resp).length;
            int v5 = SyncGroupCodec.encodeResponse((short) 5, resp).length;
            // v1 adds the leading throttle (4); v3 = v1; v4 = throttle (4) + error (2)
            // + compact bytes {1,2,3} (prefix 4 = 3 bytes + 1 prefix) + endTags (1) = 11
            // v5 = v4 + nullable ProtocolType/ProtocolName (KIP-559) = 2 bytes
            assertThat(v1).isEqualTo(v0 + 4);
            assertThat(v3).isEqualTo(v1);
            assertThat(v4).isEqualTo(11);
            assertThat(v5).isEqualTo(v4 + 2);
        }

        @Test
        void v3_encodesArrayAssignment() {
            // v3 is still legacy: Assignment is an int32-count bytes array
            byte[] v3 = SyncGroupCodec.encodeResponse(
                    (short) 3, new SyncGroupResponse(ERROR_OK, ASSIGNMENT_DATA));
            byte[] golden = fromHex("00000000" + "0000" + "00000003010203");
            assertThat(v3).isEqualTo(golden);
        }

        @Test
        void nullFieldsRoundTrip() {
            // ErrorCode is non-nullable; a null assignment encodes as empty bytes
            // at every version
            for (short v = 0; v <= 5; v++) {
                byte[] encoded =
                        SyncGroupCodec.encodeResponse(v, new SyncGroupResponse(ERROR_OK, null));
                SyncGroupResponse decoded =
                        SyncGroupCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.assignment()).as("null assignment v" + v).isEmpty();
            }
        }

        @Test
        void insufficientBuffer_throws() {
            ByteBuffer tooShort = ByteBuffer.wrap(new byte[]{0});
            assertThatThrownBy(() -> SyncGroupCodec.decodeResponse((short) 0, tooShort))
                    .isInstanceOf(BufferUnderflowException.class);
        }

        @Test
        void unsupportedVersion_throws() {
            assertThatThrownBy(() -> SyncGroupCodec.encodeResponse((short) -1,
                    new SyncGroupResponse(ERROR_OK, null)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() -> SyncGroupCodec.encodeResponse((short) 6,
                    new SyncGroupResponse(ERROR_OK, null)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            SyncGroupCodec.decodeResponse((short) -1, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
            assertThatThrownBy(() ->
                            SyncGroupCodec.decodeResponse((short) 6, ByteBuffer.allocate(0)))
                    .isInstanceOf(CodecNotImplementedException.class);
        }
    }

    // ==================== FACADE DELEGATION ====================

    @Test
    void facade_delegatesToSyncGroupCodec() {
        byte[] reqFacade = KafkaCodec.encodeSyncGroupRequest((short) 3, REQUEST);
        byte[] reqCodec = SyncGroupCodec.encodeRequest((short) 3, REQUEST);
        assertThat(reqFacade).isEqualTo(reqCodec);

        SyncGroupRequest reqBack = KafkaCodec.decodeSyncGroupRequest(
                (short) 3, ByteBuffer.wrap(reqFacade));
        assertRequestEquals(REQUEST, reqBack); // component-wise: byte[] fields compare by value

        byte[] respFacade = KafkaCodec.encodeSyncGroupResponse(
                (short) 4, new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA));
        byte[] respCodec = SyncGroupCodec.encodeResponse(
                (short) 4, new SyncGroupResponse(ERROR_UNKNOWN_MEMBER, ASSIGNMENT_DATA));
        assertThat(respFacade).isEqualTo(respCodec);

        SyncGroupResponse respBack = KafkaCodec.decodeSyncGroupResponse(
                (short) 4, ByteBuffer.wrap(respFacade));
        assertThat(respBack.errorCode()).isEqualTo(ERROR_UNKNOWN_MEMBER);
        assertThat(respBack.assignment()).isEqualTo(ASSIGNMENT_DATA);
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

    /**
     * Field-by-field request comparison: the record's byte[] assignment fields
     * compare by reference, so arrays must be checked element-wise.
     */
    private static void assertRequestEquals(SyncGroupRequest expected, SyncGroupRequest actual) {
        assertThat(actual.groupId()).isEqualTo(expected.groupId());
        assertThat(actual.generationId()).isEqualTo(expected.generationId());
        assertThat(actual.memberId()).isEqualTo(expected.memberId());
        assertThat(actual.assignments()).hasSameSizeAs(expected.assignments());
        for (int i = 0; i < expected.assignments().size(); i++) {
            assertThat(actual.assignments().get(i).memberId())
                    .isEqualTo(expected.assignments().get(i).memberId());
            assertThat(actual.assignments().get(i).assignment())
                    .isEqualTo(expected.assignments().get(i).assignment());
        }
    }

    private static void assertResponseEquals(SyncGroupResponse expected, SyncGroupResponse actual) {
        assertThat(actual.errorCode()).isEqualTo(expected.errorCode());
        assertThat(actual.assignment()).isEqualTo(expected.assignment());
    }
}
