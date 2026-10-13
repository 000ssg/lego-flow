package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.JoinGroupRequest;
import ssg.legoflow.messaging.kafka.protocol.JoinGroupResponse;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for the Consumer Groups JoinGroup request (API key 11) and response, v0–v9.
 *
 * <p>Conventions (mirroring {@link OffsetCommitCodecTest}): round-trips encode at every version
 * then decode back and assert value equality. {@link JoinGroupRequest} / {@link JoinGroupResponse}
 * embed {@code byte[]} metadata, whose record {@code equals()} compares by reference — so the
 * round-trip assertions compare fields directly and use {@link #assertArrayEquals(byte[], byte[])}
 * for the metadata arrays.
 * <ul>
 *   <li>Request: v0 is the base ({@code GroupId + SessionTimeoutMs + MemberId + ProtocolType +
 *       Protocols}); v1+ adds {@code RebalanceTimeoutMs} (int32); v5+ adds nullable
 *       {@code GroupInstanceId} (spec default null, discarded); v6+ KIP-482 flexible (compact
 *       strings, N+1 varint counts, endTags); v8+ adds nullable {@code Reason} (default null,
 *       discarded — KIP-800). At the pinned v0 the request {@code RebalanceTimeoutMs} is ABSENT on
 *       the wire (introduced at v1), so a v0 decode reports the spec default {@code -1}; v1+
 *       round-trips the model value.</li>
 *   <li>Response: v0–v1 base ({@code ErrorCode + GenerationId + ProtocolName + Leader + MemberId +
 *       Members}); v2+ leading {@code ThrottleTimeMs} (default 0, discarded); v5+ per-member
 *       nullable {@code GroupInstanceId} (null, discarded); v6+ flexible; v7+ nullable
 *       {@code ProtocolType} (null, discarded — KIP-559) and a nullable {@code ProtocolName};
 *       v9+ {@code SkipAssignment} (bool, default false, discarded).</li>
 *   <li>Byte-for-byte goldens: request v0 (fixed) and v6 (flexible); response v7 and v9
 *       (flexible, each pinning its trailing v7/v9-only field), pinning the layouts.</li>
 * </ul>
 */
class JoinGroupCodecTest {

    private static JoinGroupRequest.Protocol proto(String name, byte[] metadata) {
        return new JoinGroupRequest.Protocol(name, metadata);
    }

    private static JoinGroupResponse.Member member(String id, byte[] metadata) {
        return new JoinGroupResponse.Member(id, metadata);
    }

    // Field-by-field equality: the models carry byte[] metadata, which record equals() compares
    // by reference — so round-trip assertions compare fields directly and the arrays by value.
    private static void assertRequestRoundTrip(JoinGroupRequest expected, JoinGroupRequest actual) {
        assertEquals(expected.groupId(), actual.groupId());
        assertEquals(expected.sessionTimeoutMs(), actual.sessionTimeoutMs());
        assertEquals(expected.rebalanceTimeoutMs(), actual.rebalanceTimeoutMs());
        assertEquals(expected.memberId(), actual.memberId());
        assertEquals(expected.protocolType(), actual.protocolType());
        assertEquals(expected.protocols().size(), actual.protocols().size());
        for (int i = 0; i < expected.protocols().size(); i++) {
            assertEquals(expected.protocols().get(i).name(), actual.protocols().get(i).name());
            assertArrayEquals(expected.protocols().get(i).metadata(), actual.protocols().get(i).metadata());
        }
    }

    private static void assertRequestRoundTrip(JoinGroupRequest expected, JoinGroupRequest actual, String msg) {
        assertRequestRoundTrip(expected, actual);
        // expose msg via a message-bearing scalar check
        assertEquals(expected.groupId(), actual.groupId(), msg);
    }

    private static void assertResponseRoundTrip(JoinGroupResponse expected, JoinGroupResponse actual) {
        assertEquals(expected.errorCode(), actual.errorCode());
        assertEquals(expected.generationId(), actual.generationId());
        assertEquals(expected.protocolName(), actual.protocolName());
        assertEquals(expected.leader(), actual.leader());
        assertEquals(expected.memberId(), actual.memberId());
        assertEquals(expected.members().size(), actual.members().size());
        for (int i = 0; i < expected.members().size(); i++) {
            assertEquals(expected.members().get(i).memberId(), actual.members().get(i).memberId());
            assertArrayEquals(expected.members().get(i).metadata(), actual.members().get(i).metadata());
        }
    }

    private static void assertResponseRoundTrip(JoinGroupResponse expected, JoinGroupResponse actual, String msg) {
        assertResponseRoundTrip(expected, actual);
        assertEquals(expected.errorCode(), actual.errorCode(), msg);
    }

    // ===================== Request round-trips =====================

    @Test
    void request_v0_roundTrip_rebalanceAbsentOnWire() {
        // v0 predates RebalanceTimeoutMs (introduced v1): the field is absent on the wire, so the
        // decoded value is the spec default -1 even when the model carries another value.
        var req = new JoinGroupRequest("grp1", 10000, 30000, "", "consumer",
                List.of(proto("range", new byte[]{1, 2})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 0, req);
        var dec = JoinGroupCodec.decodeRequest((short) 0, ByteBuffer.wrap(enc));
        assertEquals(-1, dec.rebalanceTimeoutMs(), "v0 carries no RebalanceTimeoutMs → spec default -1");
        assertRequestRoundTrip(new JoinGroupRequest("grp1", 10000, -1, "", "consumer",
                List.of(proto("range", new byte[]{1, 2}))), dec);
    }

    @Test
    void request_v1_roundTrip_withRebalanceTimeout() {
        var req = new JoinGroupRequest("grp1", 10000, 30000, "m1", "consumer",
                List.of(proto("range", new byte[]{1, 2}), proto("roundrobin", new byte[0])));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 1, req);
        var dec = JoinGroupCodec.decodeRequest((short) 1, ByteBuffer.wrap(enc));
        assertEquals(30000, dec.rebalanceTimeoutMs(), "v1+ round-trips RebalanceTimeoutMs");
        assertRequestRoundTrip(req, dec);
    }

    @Test
    void request_v2_roundTrip() {
        var req = new JoinGroupRequest("grp2", 5000, 15000, "m2", "consumer",
                List.of(proto("range", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 2, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v3_roundTrip() {
        var req = new JoinGroupRequest("grp3", 5000, 15000, "m3", "consumer",
                List.of(proto("range", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 3, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 3, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v4_roundTrip() {
        var req = new JoinGroupRequest("grp4", 5000, 15000, "m4", "consumer",
                List.of(proto("range", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 4, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 4, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v5_roundTrip_groupInstanceIdDiscarded() {
        // v5 adds nullable GroupInstanceId (legacy int16 nullable string, non-flexible); the
        // model does not expose it → written null, discarded on decode.
        var req = new JoinGroupRequest("grp5", 5000, 15000, "m5", "consumer",
                List.of(proto("range", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 5, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 5, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v6_roundTrip_flexible() {
        var req = new JoinGroupRequest("grp6", 5000, 15000, "m6", "consumer",
                List.of(proto("range", new byte[]{1, 2}), proto("rr", new byte[0])));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 6, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 6, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v7_roundTrip_flexible() {
        var req = new JoinGroupRequest("grp7", 5000, 15000, "m7", "consumer",
                List.of(proto("range", new byte[]{7})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 7, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 7, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v8_roundTrip_flexible_reasonDiscarded() {
        // v8 adds nullable Reason (KIP-800); the model does not expose it → written null,
        // discarded on decode.
        var req = new JoinGroupRequest("grp8", 5000, 15000, "m8", "consumer",
                List.of(proto("range", new byte[]{8})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 8, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 8, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v9_roundTrip_flexible() {
        var req = new JoinGroupRequest("grp9", 5000, 15000, "m9", "consumer",
                List.of(proto("range", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 9, req);
        assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest((short) 9, ByteBuffer.wrap(enc)));
    }

    @Test
    void request_v1_to_v9_roundTrip_allVersions() {
        var req = new JoinGroupRequest("grp", 10000, 30000, "member-1", "consumer",
                List.of(proto("range", new byte[]{1, 2}), proto("roundrobin", new byte[0])));
        for (short v = 1; v <= 9; v++) {
            byte[] enc = JoinGroupCodec.encodeRequest(v, req);
            assertRequestRoundTrip(req, JoinGroupCodec.decodeRequest(v, ByteBuffer.wrap(enc)), "v" + v);
        }
    }

    // ===================== Request version-shape =====================

    @Test
    void request_v1_adds_rebalanceTimeoutMs() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[]{1})));
        int v0 = JoinGroupCodec.encodeRequest((short) 0, req).length;
        int v1 = JoinGroupCodec.encodeRequest((short) 1, req).length;
        // v1 adds one int32 RebalanceTimeoutMs.
        assertEquals(v0 + 4, v1, "v1 adds int32 RebalanceTimeoutMs");
    }

    @Test
    void request_v5_adds_groupInstanceId() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[]{1})));
        int v4 = JoinGroupCodec.encodeRequest((short) 4, req).length;
        int v5 = JoinGroupCodec.encodeRequest((short) 5, req).length;
        // v5 adds nullable GroupInstanceId, written null (non-flexible: int16 -1 = 2 bytes).
        assertEquals(v4 + 2, v5, "v5 adds nullable GroupInstanceId (null = int16 -1)");
    }

    @Test
    void request_v7_is_wireIdentical_to_v6() {
        // v7 is the same layout as v6 (no new request field at v7).
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[]{1})));
        assertArrayEquals(
                JoinGroupCodec.encodeRequest((short) 6, req),
                JoinGroupCodec.encodeRequest((short) 7, req),
                "request v7 must be byte-identical to v6");
    }

    @Test
    void request_v8_adds_reason() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[]{1})));
        int v7 = JoinGroupCodec.encodeRequest((short) 7, req).length;
        int v8 = JoinGroupCodec.encodeRequest((short) 8, req).length;
        // v8 adds nullable Reason, written null (flexible: varint 0 = 1 byte).
        assertEquals(v7 + 1, v8, "v8 adds nullable Reason (null = varint 0)");
    }

    @Test
    void request_v9_is_wireIdentical_to_v8() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[]{1})));
        assertArrayEquals(
                JoinGroupCodec.encodeRequest((short) 8, req),
                JoinGroupCodec.encodeRequest((short) 9, req),
                "request v9 must be byte-identical to v8");
    }

    // ===================== Request byte-golden (v0, v6) =====================

    @Test
    void request_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding; NOTE: no RebalanceTimeoutMs at v0):
        //   GroupId          = int16 len 4 + "grp1"         -> 00 04 67 72 70 31
        //   SessionTimeoutMs = int32 10000                  -> 00 00 27 10
        //   MemberId         = int16 len 0 + ""             -> 00 00
        //   ProtocolType     = int16 len 5 + "range"        -> 00 05 72 61 6E 67 65
        //   Protocols.count  = int32 1                      -> 00 00 00 01
        //   Name             = int16 len 5 + "range"        -> 00 05 72 61 6E 67 65
        //   Metadata         = int32 2 + {1,2}              -> 00 00 00 02 01 02
        var req = new JoinGroupRequest("grp1", 10000, 30000, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        byte[] expected = {
                0x00, 0x04, 'g', 'r', 'p', '1',
                0x00, 0x00, 0x27, 0x10,
                0x00, 0x00,
                0x00, 0x05, 'r', 'a', 'n', 'g', 'e',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x05, 'r', 'a', 'n', 'g', 'e',
                0x00, 0x00, 0x00, 0x02, 0x01, 0x02
        };
        assertArrayEquals(expected, JoinGroupCodec.encodeRequest((short) 0, req));
    }

    @Test
    void request_v6_byteForByte_golden_flexible() {
        // Hand-built v6 wire layout (KIP-482 flexible encoding):
        //   GroupId          = varint(4+1) + "grp1"         -> 05 67 72 70 31
        //   SessionTimeoutMs = int32 10000                  -> 00 00 27 10
        //   RebalanceTimeout = int32 30000                  -> 00 00 75 30
        //   MemberId         = varint(0+1) + ""             -> 01
        //   GroupInstanceId  = null -> varint 0             -> 00
        //   ProtocolType     = varint(5+1) + "range"        -> 06 72 61 6E 67 65
        //   Protocols.count  = varint(1+1)                  -> 02
        //   Name             = varint(5+1) + "range"        -> 06 72 61 6E 67 65
        //   Metadata         = varint(2+1) + {1,2}          -> 03 01 02
        //   endTags x2 (protocol, top-level)                -> 00 00
        var req = new JoinGroupRequest("grp1", 10000, 30000, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        byte[] expected = {
                0x05, 'g', 'r', 'p', '1',
                0x00, 0x00, 0x27, 0x10,
                0x00, 0x00, 0x75, 0x30,
                0x01,
                0x00,
                0x06, 'r', 'a', 'n', 'g', 'e',
                0x02,
                0x06, 'r', 'a', 'n', 'g', 'e',
                0x03, 0x01, 0x02,
                0x00,
                0x00
        };
        assertArrayEquals(expected, JoinGroupCodec.encodeRequest((short) 6, req));
    }

    // ===================== Request edge cases =====================

    @Test
    void request_zeroProtocols_roundTrip_allVersions() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t", List.of());
        for (short v = 0; v <= 9; v++) {
            byte[] enc = JoinGroupCodec.encodeRequest(v, req);
            JoinGroupRequest expected = v == 0
                    ? new JoinGroupRequest("g", 10000, -1, "m", "t", List.of())
                    : req; // v0: RebalanceTimeoutMs absent on the wire → spec default -1
            assertRequestRoundTrip(expected, JoinGroupCodec.decodeRequest(v, ByteBuffer.wrap(enc)), "v" + v);
        }
    }

    @Test
    void request_emptyProtocolMetadata_roundTrip_allVersions() {
        // An empty metadata byte[] is a distinct, valid value (non-nullable bytes): fixed int32 0;
        // flexible varint(1) — must round-trip to an empty (not null) array.
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t",
                List.of(proto("range", new byte[0])));
        for (short v = 0; v <= 9; v++) {
            byte[] enc = JoinGroupCodec.encodeRequest(v, req);
            var dec = JoinGroupCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(1, dec.protocols().size(), "v" + v);
            assertArrayEquals(new byte[0], dec.protocols().get(0).metadata(), "v" + v + " empty metadata");
        }
    }

    @Test
    void request_invalidVersion_throws() {
        var req = new JoinGroupRequest("g", 10000, 30000, "m", "t", List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.encodeRequest((short) -1, req));
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.encodeRequest((short) 10, req));
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.decodeRequest((short) 10, ByteBuffer.allocate(0)));
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.decodeRequest((short) -1, ByteBuffer.allocate(0)));
    }

    @Test
    void request_truncatedFrame_throws() {
        var req = new JoinGroupRequest("grp1", 10000, 30000, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        byte[] enc = JoinGroupCodec.encodeRequest((short) 0, req);
        ByteBuffer partial = ByteBuffer.wrap(enc, 0, enc.length - 3);
        assertThrows(BufferUnderflowException.class,
                () -> JoinGroupCodec.decodeRequest((short) 0, partial));
    }

    // ===================== Response round-trips =====================

    @Test
    void response_v0_roundTrip() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 0, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 0, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v1_roundTrip() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 1, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 1, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v2_roundTrip_throttleTimeDiscarded() {
        // v2+ carries a leading ThrottleTimeMs (int32); the model does not expose it → written 0,
        // discarded on decode.
        var resp = new JoinGroupResponse((short) 0, 2, "range", "l2", "m2",
                List.of(member("m2", new byte[]{5})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 2, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 2, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v3_roundTrip() {
        var resp = new JoinGroupResponse((short) 0, 3, "range", "l3", "m3",
                List.of(member("m3", new byte[]{5})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 3, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 3, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v4_roundTrip() {
        var resp = new JoinGroupResponse((short) 0, 4, "range", "l4", "m4",
                List.of(member("m4", new byte[]{5})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 4, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 4, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v5_roundTrip_memberGroupInstanceIdDiscarded() {
        // v5+ adds per-member nullable GroupInstanceId (legacy int16 nullable string, v5);
        // written null, discarded on decode.
        var resp = new JoinGroupResponse((short) 0, 5, "range", "l5", "m5",
                List.of(member("m5", new byte[]{5}), member("m6", new byte[0])));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 5, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 5, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v6_roundTrip_flexible() {
        var resp = new JoinGroupResponse((short) 0, 6, "range", "l6", "m6",
                List.of(member("m6", new byte[]{6, 7}), member("m7", new byte[0])));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 6, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 6, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v7_roundTrip_flexible_protocolTypeDiscarded() {
        // v7+ adds nullable ProtocolType (KIP-559); the model does not expose it → written null,
        // discarded on decode. ProtocolName also becomes nullable from v7.
        var resp = new JoinGroupResponse((short) 0, 7, "range", "l7", "m7",
                List.of(member("m7", new byte[]{7})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 7, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 7, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v8_roundTrip_flexible() {
        var resp = new JoinGroupResponse((short) 0, 8, "range", "l8", "m8",
                List.of(member("m8", new byte[]{8})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 8, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 8, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_v9_roundTrip_flexible_skipAssignmentDiscarded() {
        // v9+ adds SkipAssignment (bool); the model does not expose it → written false,
        // discarded on decode.
        var resp = new JoinGroupResponse((short) 0, 9, "range", "l9", "m9",
                List.of(member("m9", new byte[]{9})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 9, resp);
        assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse((short) 9, ByteBuffer.wrap(enc)));
    }

    @Test
    void response_allVersions_roundTrip() {
        var resp = new JoinGroupResponse((short) 29, 100, "range", "leader-1", "member-1",
                List.of(member("member-1", new byte[]{1, 2}), member("member-2", new byte[0])));
        for (short v = 0; v <= 9; v++) {
            byte[] enc = JoinGroupCodec.encodeResponse(v, resp);
            assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse(v, ByteBuffer.wrap(enc)), "v" + v);
        }
    }

    // ===================== Response version-shape =====================

    @Test
    void response_v0_v1_byteIdentical() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{1})));
        assertArrayEquals(
                JoinGroupCodec.encodeResponse((short) 0, resp),
                JoinGroupCodec.encodeResponse((short) 1, resp),
                "response v0 == v1");
    }

    @Test
    void response_v2_adds_throttleTimeMs() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{1})));
        int v1 = JoinGroupCodec.encodeResponse((short) 1, resp).length;
        int v2 = JoinGroupCodec.encodeResponse((short) 2, resp).length;
        // v2 adds the leading int32 ThrottleTimeMs.
        assertEquals(v1 + 4, v2, "v2 adds int32 ThrottleTimeMs");
    }

    @Test
    void response_v5_adds_memberGroupInstanceId() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{1})));
        int v4 = JoinGroupCodec.encodeResponse((short) 4, resp).length;
        int v5 = JoinGroupCodec.encodeResponse((short) 5, resp).length;
        // v5 adds per-member nullable GroupInstanceId, written null (1 member: int16 -1 = 2 bytes).
        assertEquals(v4 + 2, v5, "v5 adds nullable GroupInstanceId per member");
    }

    @Test
    void response_v7_adds_protocolTypeAnd_nullableProtocolName() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        int v6 = JoinGroupCodec.encodeResponse((short) 6, resp).length;
        int v7 = JoinGroupCodec.encodeResponse((short) 7, resp).length;
        // v7 adds nullable ProtocolType (null = varint 0, +1). ProtocolName becomes nullable,
        // but a non-null value encodes identically either way (varint len+1 + bytes), so the
        // net delta is the new field alone.
        assertEquals(v6 + 1, v7, "v7 adds nullable ProtocolType");
    }

    @Test
    void response_v8_is_wireIdentical_to_v7() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        assertArrayEquals(
                JoinGroupCodec.encodeResponse((short) 7, resp),
                JoinGroupCodec.encodeResponse((short) 8, resp),
                "response v8 must be byte-identical to v7");
    }

    @Test
    void response_v9_adds_skipAssignment() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        int v8 = JoinGroupCodec.encodeResponse((short) 8, resp).length;
        int v9 = JoinGroupCodec.encodeResponse((short) 9, resp).length;
        // v9 adds the SkipAssignment bool (1 byte).
        assertEquals(v8 + 1, v9, "v9 adds bool SkipAssignment");
    }

    // ===================== Response byte-golden (v7, v9) =====================

    @Test
    void response_v7_byteForByte_golden_flexible() {
        // Hand-built v7 wire layout (KIP-482 flexible encoding):
        //   ThrottleTimeMs   = int32 0                      -> 00 00 00 00
        //   ErrorCode        = int16 0                      -> 00 00
        //   GenerationId     = int32 1                      -> 00 00 00 01
        //   ProtocolType     = null -> varint 0             -> 00
        //   ProtocolName     = varint(5+1) + "range"        -> 06 72 61 6E 67 65
        //   Leader           = varint(2+1) + "l1"           -> 03 6C 31
        //   MemberId         = varint(2+1) + "m1"           -> 03 6D 31
        //   Members.count    = varint(1+1)                  -> 02
        //   Member MemberId  = varint(2+1) + "m1"           -> 03 6D 31
        //   Member GroupInst = null -> varint 0             -> 00
        //   Member Metadata  = varint(2+1) + {3,4}          -> 03 03 04
        //   endTags x2 (member, top-level)                  -> 00 00
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00,
                0x06, 'r', 'a', 'n', 'g', 'e',
                0x03, 'l', '1',
                0x03, 'm', '1',
                0x02,
                0x03, 'm', '1',
                0x00,
                0x03, 0x03, 0x04,
                0x00,
                0x00
        };
        assertArrayEquals(expected, JoinGroupCodec.encodeResponse((short) 7, resp));
    }

    @Test
    void response_v9_byteForByte_golden_flexible() {
        // v9 = v7 layout + SkipAssignment (bool 0) between Leader and MemberId.
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00,
                0x06, 'r', 'a', 'n', 'g', 'e',
                0x03, 'l', '1',
                0x00,
                0x03, 'm', '1',
                0x02,
                0x03, 'm', '1',
                0x00,
                0x03, 0x03, 0x04,
                0x00,
                0x00
        };
        assertArrayEquals(expected, JoinGroupCodec.encodeResponse((short) 9, resp));
    }

    // ===================== Response edge cases =====================

    @Test
    void response_zeroMembers_roundTrip_allVersions() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1", List.of());
        for (short v = 0; v <= 9; v++) {
            byte[] enc = JoinGroupCodec.encodeResponse(v, resp);
            assertResponseRoundTrip(resp, JoinGroupCodec.decodeResponse(v, ByteBuffer.wrap(enc)), "v" + v);
        }
    }

    @Test
    void response_invalidVersion_throws() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1", List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.encodeResponse((short) -1, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.encodeResponse((short) 10, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> JoinGroupCodec.decodeResponse((short) -1, ByteBuffer.allocate(0)));
    }

    @Test
    void response_truncatedFrame_throws() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        byte[] enc = JoinGroupCodec.encodeResponse((short) 6, resp);
        ByteBuffer partial = ByteBuffer.wrap(enc, 0, enc.length - 4);
        assertThrows(BufferUnderflowException.class,
                () -> JoinGroupCodec.decodeResponse((short) 6, partial));
    }

    // ===================== Facade delegation =====================

    @Test
    void facade_request_encode_matches_dedicatedCodec() {
        var req = new JoinGroupRequest("grp-1", 10000, 30000, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        assertArrayEquals(
                JoinGroupCodec.encodeRequest(req),
                KafkaCodec.encodeJoinGroupRequest(req),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_response_encode_matches_dedicatedCodec() {
        var resp = new JoinGroupResponse((short) 0, 1, "range", "l1", "m1",
                List.of(member("m1", new byte[]{3, 4})));
        assertArrayEquals(
                JoinGroupCodec.encodeResponse(resp),
                KafkaCodec.encodeJoinGroupResponse(resp),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_decode_matches_dedicatedCodec() {
        var req = new JoinGroupRequest("grp-1", 10000, -1, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        byte[] enc = KafkaCodec.encodeJoinGroupRequest(req);
        var viaFacade = KafkaCodec.decodeJoinGroupRequest(ByteBuffer.wrap(enc));
        var viaCodec = JoinGroupCodec.decodeRequest(
                JoinGroupCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertRequestRoundTrip(viaCodec, viaFacade,
                "facade decode must forward to the dedicated codec at the pinned version");
    }

    @Test
    void facade_roundTrip() {
        var req = new JoinGroupRequest("grp-1", 10000, 30000, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        var dec = KafkaCodec.decodeJoinGroupRequest(
                ByteBuffer.wrap(KafkaCodec.encodeJoinGroupRequest(req)));
        // v0 round-trip: RebalanceTimeoutMs is absent on the wire → spec default -1.
        var expected = new JoinGroupRequest("grp-1", 10000, -1, "", "range",
                List.of(proto("range", new byte[]{1, 2})));
        assertRequestRoundTrip(expected, dec);
    }

    @Test
    void pinned_version_is_v0() {
        assertEquals((short) 0, JoinGroupCodec.PINNED_VERSION);
        assertEquals((short) 9, JoinGroupCodec.MAX_VERSION);
    }
}
