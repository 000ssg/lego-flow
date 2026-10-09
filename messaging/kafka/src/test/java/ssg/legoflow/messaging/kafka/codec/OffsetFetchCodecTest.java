package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.OffsetFetchRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetFetchResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for the Consumer Groups OffsetFetch request (API key 9) and response, v0–v8.
 *
 * <p>Conventions (mirroring {@link OffsetCommitCodecTest}):
 * <ul>
 *   <li>Round-trips encode at every version then decode back, asserting value equality.</li>
 *   <li>v0–v5 carry the fixed layout {@code GroupId + Topics[]{Name, PartitionIndexes[]}};
 *       v6 is KIP-482 flexible (compact strings, N+1 varint array counts, tagged-field
 *       trailers); v7 adds the request-only {@code RequireStable(bool)} (spec default false,
 *       discarded on decode — the model does not expose it); v8 switches the request to
 *       {@code Groups[]{groupId, Topics[]}} and the response to {@code Groups[]{groupId,
 *       Topics[], ErrorCode}} (multi-group fetch, KIP-848) — the codec synthesizes a
 *       single-element group array from the in-house single-group models.</li>
 *   <li>The response gains {@code ThrottleTimeMs(int32)} at v3 (spec default 0, discarded),
 *       a top-level {@code ErrorCode(int16)} at v2 (spec default 0, discarded), and the
 *       per-partition {@code CommittedLeaderEpoch(int32)} at v5 (spec default −1, discarded);
 *       the top-level error code is replaced by a per-group error code at v8.</li>
 *   <li>A v0 request/response and a v6 request/response carry byte-for-byte goldens, pinning
 *       the fixed and flexible wire layouts respectively.</li>
 * </ul>
 */
class OffsetFetchCodecTest {

    private static OffsetFetchRequest.TopicPartitions topic(String name, int... partitions) {
        return new OffsetFetchRequest.TopicPartitions(
                name, java.util.stream.IntStream.of(partitions).boxed().toList());
    }

    private static OffsetFetchResponse.PartitionResponse part(int idx, long offset,
            String meta, short errorCode) {
        return new OffsetFetchResponse.PartitionResponse(idx, offset, meta, errorCode);
    }

    private static OffsetFetchResponse.TopicResponse topicResp(String name,
            OffsetFetchResponse.PartitionResponse... parts) {
        return new OffsetFetchResponse.TopicResponse(name, Arrays.asList(parts));
    }

    // ===================== Request round-trips =====================

    @Test
    void request_v0_roundTrip_groupIdAndTopicsOnly() {
        var req = new OffsetFetchRequest("grp-1",
                List.of(topic("topic-a", 0, 1, 2)));
        byte[] enc = OffsetFetchCodec.encodeRequest((short) 0, req);
        var dec = OffsetFetchCodec.decodeRequest((short) 0, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v1_to_v5_roundTrip_fixedLayout() {
        var req = new OffsetFetchRequest("grp-2",
                List.of(
                        topic("t1", 0, 3),
                        topic("t2")));
        for (short v = 1; v <= 5; v++) {
            byte[] enc = OffsetFetchCodec.encodeRequest(v, req);
            var dec = OffsetFetchCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    @Test
    void request_v6_roundTrip_flexible() {
        var req = new OffsetFetchRequest("grp-6",
                List.of(
                        topic("topic-e", 0, 7),
                        topic("topic-f")));
        byte[] enc = OffsetFetchCodec.encodeRequest((short) 6, req);
        var dec = OffsetFetchCodec.decodeRequest((short) 6, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v7_roundTrip_requireStableDiscarded() {
        // v7 adds RequireStable(bool); the model does not expose it, so the codec writes the
        // spec default (false) and discards it on decode.
        var req = new OffsetFetchRequest("grp-7",
                List.of(topic("topic-d", 1, 2)));
        byte[] enc = OffsetFetchCodec.encodeRequest((short) 7, req);
        var dec = OffsetFetchCodec.decodeRequest((short) 7, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v8_roundTrip_groupsLayout() {
        // v8 wraps the model's single group into Groups[]{groupId, Topics[]}; decode
        // collapses the first group back into the model.
        var req = new OffsetFetchRequest("grp-8",
                List.of(
                        topic("topic-g", 4, 5),
                        topic("topic-h", 0)));
        byte[] enc = OffsetFetchCodec.encodeRequest((short) 8, req);
        var dec = OffsetFetchCodec.decodeRequest((short) 8, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v0_to_v8_roundTrip_allVersions() {
        var req = new OffsetFetchRequest("grp",
                List.of(
                        topic("t", 0, 1),
                        topic("u", 9)));
        for (short v = 0; v <= 8; v++) {
            byte[] enc = OffsetFetchCodec.encodeRequest(v, req);
            var dec = OffsetFetchCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    // ===================== Request version-shape =====================

    @Test
    void request_v7_adds_requireStable() {
        var req = new OffsetFetchRequest("g", List.of(topic("t", 0)));
        int v6 = OffsetFetchCodec.encodeRequest((short) 6, req).length;
        int v7 = OffsetFetchCodec.encodeRequest((short) 7, req).length;
        // v7 adds one byte RequireStable; v6 and v7 are both flexible, so the delta is exact.
        assertEquals(v6 + 1, v7, "v7 adds bool RequireStable");
    }

    @Test
    void request_v5_to_v6_switchesToFlexibleEncoding() {
        var req = new OffsetFetchRequest("g",
                List.of(topic("t", 0, 1, 2)));
        int v5 = OffsetFetchCodec.encodeRequest((short) 5, req).length;
        int v6 = OffsetFetchCodec.encodeRequest((short) 6, req).length;
        // v5 (fixed):  GroupId 00 01 "g"(2+1) + Topics count int32 1(4)
        //              + Name 00 01 "t"(2+1) + PI count int32 3(4) + 3*4 int32
        //              = 3 + 4 + 3 + 4 + 12 = 26
        // v6 (flex):   GroupId varint 2 "g"(1+1) + Topics varint 2(1)
        //              + Name varint 2 "t"(1+1) + PI varint 4(1) + 12 + endTags 1
        //              + PI varint 4(1) [inner struct] ... = 2 + 1 + 2 + 1 + 12 + 1 + 1 = 20
        // (varint(N+1) for N=3 is one byte 0x04; per-topic endTags 0x00; top-level 0x00)
        assertEquals(26, v5, "v5 fixed layout size");
        assertEquals(20, v6, "v6 flexible layout size");
    }

    @Test
    void request_v8_groupsWrap_singleGroup() {
        var req = new OffsetFetchRequest("g", List.of(topic("t", 0)));
        int v8 = OffsetFetchCodec.encodeRequest((short) 8, req).length;
        // v8 (flex): Groups count varint(2, N+1)(1) + groupId compact "g"(1+1=2)
        //            + Topics count varint(2)(1) + Name compact "t"(1+1=2)
        //            + PI count varint(2)(1) + PartitionIndex int32(4)
        //            + per-topic endTags(1) + per-group endTags(1)
        //            + RequireStable(1) + top-level endTags(1) = 15
        assertEquals(15, v8, "v8 groups layout size");
    }

    // ===================== Request byte-golden (v0, v6) =====================

    @Test
    void request_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding):
        //   GroupId          = int16 len 2 + "ab"     -> 00 02 61 62
        //   Topics.count     = int32 1                -> 00 00 00 01
        //   topic Name       = int16 len 2 + "to"     -> 00 02 74 6F
        //   PartitionIndexes = int32 1                -> 00 00 00 01
        //   PartitionIndex   = int32 5                -> 00 00 00 05
        var req = new OffsetFetchRequest("ab", List.of(topic("to", 5)));
        byte[] expected = {
                0x00, 0x02, 'a', 'b',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x02, 't', 'o',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x05
        };
        assertArrayEquals(expected, OffsetFetchCodec.encodeRequest((short) 0, req));
    }

    @Test
    void request_v6_byteForByte_golden_flexible() {
        // Hand-built v6 wire layout (KIP-482 flexible encoding):
        //   GroupId          = varint(2+1) + "ab"     -> 03 61 62
        //   Topics.count     = varint(1+1)            -> 02
        //   topic Name       = varint(2+1) + "to"     -> 03 74 6F
        //   PartitionIndexes = varint(1+1)            -> 02
        //   PartitionIndex   = int32 5                -> 00 00 00 05
        //   endTags (topic)  = varint 0               -> 00
        //   endTags (top)    = varint 0               -> 00
        var req = new OffsetFetchRequest("ab", List.of(topic("to", 5)));
        byte[] expected = {
                0x03, 'a', 'b',
                0x02,
                0x03, 't', 'o',
                0x02,
                0x00, 0x00, 0x00, 0x05,
                0x00,
                0x00
        };
        assertArrayEquals(expected, OffsetFetchCodec.encodeRequest((short) 6, req));
    }

    // ===================== Response round-trips =====================

    @Test
    void response_v0_roundTrip_topicsOnly() {
        var resp = new OffsetFetchResponse(List.of(
                topicResp("topic-a", part(0, 42L, "meta", (short) 0))));
        byte[] enc = OffsetFetchCodec.encodeResponse((short) 0, resp);
        var dec = OffsetFetchCodec.decodeResponse((short) 0, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_v1_to_v8_roundTrip_allVersions() {
        var resp = new OffsetFetchResponse(List.of(
                topicResp("t1", part(0, 1L, "m", (short) 0), part(2, -1L, null, (short) 14)),
                topicResp("t2", part(3, 99L, null, (short) 0))));
        for (short v = 1; v <= 8; v++) {
            byte[] enc = OffsetFetchCodec.encodeResponse(v, resp);
            var dec = OffsetFetchCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
        }
    }

    @Test
    void response_nullMetadata_roundTrip() {
        var resp = new OffsetFetchResponse(List.of(
                topicResp("t", part(0, 1L, null, (short) 0))));
        for (short v = 0; v <= 8; v++) {
            byte[] enc = OffsetFetchCodec.encodeResponse(v, resp);
            var dec = OffsetFetchCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
            assertNull(dec.topics().get(0).partitions().get(0).metadata(), "v" + v);
        }
    }

    // ===================== Response version-shape =====================

    @Test
    void response_v2_addsTopLevelErrorCode() {
        var resp = new OffsetFetchResponse(List.of(topicResp("t", part(0, 1L, "m", (short) 0))));
        int v1 = OffsetFetchCodec.encodeResponse((short) 1, resp).length;
        int v2 = OffsetFetchCodec.encodeResponse((short) 2, resp).length;
        // v2 adds one int16 top-level ErrorCode.
        assertEquals(v1 + 2, v2, "v2 adds int16 top-level ErrorCode");
    }

    @Test
    void response_v3_addsThrottleTimeMs() {
        var resp = new OffsetFetchResponse(List.of(topicResp("t", part(0, 1L, "m", (short) 0))));
        int v2 = OffsetFetchCodec.encodeResponse((short) 2, resp).length;
        int v3 = OffsetFetchCodec.encodeResponse((short) 3, resp).length;
        // v3 adds one int32 ThrottleTimeMs.
        assertEquals(v2 + 4, v3, "v3 adds int32 ThrottleTimeMs");
    }

    @Test
    void response_v5_addsCommittedLeaderEpoch() {
        var resp = new OffsetFetchResponse(List.of(
                topicResp("t", part(0, 1L, "m", (short) 0), part(1, 2L, null, (short) 0))));
        int v4 = OffsetFetchCodec.encodeResponse((short) 4, resp).length;
        int v5 = OffsetFetchCodec.encodeResponse((short) 5, resp).length;
        // v5 adds one int32 CommittedLeaderEpoch per partition (2 partitions here).
        assertEquals(v4 + 2 * 4, v5, "v5 adds int32 CommittedLeaderEpoch per partition");
    }

    @Test
    void response_v8_replacesTopLevelError_withGroupError() {
        var resp = new OffsetFetchResponse(List.of(topicResp("t", part(0, 1L, "m", (short) 0))));
        int v7 = OffsetFetchCodec.encodeResponse((short) 7, resp).length;
        int v8 = OffsetFetchCodec.encodeResponse((short) 8, resp).length;
        // v8 (vs v7): -top-level ErrorCode int16 (-2), +Groups count varint(1) (+1),
        // +groupId varint(1) for the spec-default "" (+1), +per-group ErrorCode int16 (+2),
        // +per-group endTags trailer (+1) => net +3.
        assertEquals(v7 + 3, v8, "v8 groups layout delta");
    }

    // ===================== Response byte-golden (v0, v6) =====================

    @Test
    void response_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding):
        //   Topics.count     = int32 1                -> 00 00 00 01
        //   topic Name       = int16 len 2 + "to"     -> 00 02 74 6F
        //   Partitions.count = int32 1                -> 00 00 00 01
        //   PartitionIndex   = int32 5                -> 00 00 00 05
        //   CommittedOffset  = int64 10               -> 00 00 00 00 00 00 00 0A
        //   Metadata         = int16 len 4 + "meta"   -> 00 04 6D 65 74 61
        //   ErrorCode        = int16 0                -> 00 00
        var resp = new OffsetFetchResponse(List.of(
                topicResp("to", part(5, 10L, "meta", (short) 0))));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x02, 't', 'o',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A,
                0x00, 0x04, 'm', 'e', 't', 'a',
                0x00, 0x00
        };
        assertArrayEquals(expected, OffsetFetchCodec.encodeResponse((short) 0, resp));
    }

    @Test
    void response_v6_byteForByte_golden_flexible() {
        // Hand-built v6 wire layout (KIP-482 flexible encoding):
        //   ThrottleTimeMs   = int32 0                -> 00 00 00 00
        //   Topics.count     = varint(1+1)            -> 02
        //   topic Name       = varint(2+1) + "to"     -> 03 74 6F
        //   Partitions.count = varint(1+1)            -> 02
        //   PartitionIndex   = int32 5                -> 00 00 00 05
        //   CommittedOffset  = int64 10               -> 00 00 00 00 00 00 00 0A
        //   CommittedLeaderEpoch = int32 -1 (default) -> FF FF FF FF
        //   Metadata         = varint(4+1) + "meta"   -> 05 6D 65 74 61
        //   ErrorCode        = int16 0                -> 00 00
        //   endTags x3 (partition, topic, top-level)  -> 00 00 00
        //   top-level ErrorCode = int16 0             -> 00 00
        var resp = new OffsetFetchResponse(List.of(
                topicResp("to", part(5, 10L, "meta", (short) 0))));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x02,
                0x03, 't', 'o',
                0x02,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x05, 'm', 'e', 't', 'a',
                0x00, 0x00,
                0x00, 0x00, 0x00,
                0x00, 0x00
        };
        assertArrayEquals(expected, OffsetFetchCodec.encodeResponse((short) 6, resp));
    }

    // ===================== Response edge cases =====================

    @Test
    void response_zeroTopics_roundTrip_allVersions() {
        var resp = new OffsetFetchResponse(List.of());
        for (short v = 0; v <= 8; v++) {
            byte[] enc = OffsetFetchCodec.encodeResponse(v, resp);
            var dec = OffsetFetchCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
        }
    }

    @Test
    void request_zeroTopics_roundTrip_allVersions() {
        var req = new OffsetFetchRequest("g", List.of());
        for (short v = 0; v <= 8; v++) {
            byte[] enc = OffsetFetchCodec.encodeRequest(v, req);
            var dec = OffsetFetchCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    @Test
    void invalidVersion_throws() {
        var req = new OffsetFetchRequest("g", List.of());
        var resp = new OffsetFetchResponse(List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.encodeRequest((short) -1, req));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.encodeRequest((short) 9, req));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.encodeResponse((short) -1, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.encodeResponse((short) 9, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.decodeRequest((short) -1, ByteBuffer.allocate(0)));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetFetchCodec.decodeResponse((short) 9, ByteBuffer.allocate(0)));
    }

    // ===================== Facade delegation =====================

    @Test
    void facade_request_encode_matches_dedicatedCodec() {
        var req = new OffsetFetchRequest("grp-1", List.of(topic("topic-a", 0, 1)));
        assertArrayEquals(
                OffsetFetchCodec.encodeRequest(req),
                KafkaCodec.encodeOffsetFetchRequest(req),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_response_encode_matches_dedicatedCodec() {
        var resp = new OffsetFetchResponse(List.of(
                topicResp("topic-a", part(0, 42L, "meta", (short) 0))));
        assertArrayEquals(
                OffsetFetchCodec.encodeResponse(resp),
                KafkaCodec.encodeOffsetFetchResponse(resp),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_decode_matches_dedicatedCodec() {
        var req = new OffsetFetchRequest("grp-1", List.of(topic("topic-a", 0, 1)));
        byte[] enc = KafkaCodec.encodeOffsetFetchRequest(req);
        var viaFacade = KafkaCodec.decodeOffsetFetchRequest(ByteBuffer.wrap(enc));
        var viaCodec = OffsetFetchCodec.decodeRequest(
                OffsetFetchCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(viaCodec, viaFacade,
                "facade decode must forward to the dedicated codec at the pinned version");
    }

    @Test
    void facade_roundTrip() {
        var req = new OffsetFetchRequest("grp-1", List.of(topic("topic-a", 0, 1)));
        var dec = KafkaCodec.decodeOffsetFetchRequest(
                ByteBuffer.wrap(KafkaCodec.encodeOffsetFetchRequest(req)));
        assertEquals(req, dec);
    }

    @Test
    void pinned_version_is_v0() {
        assertEquals((short) 0, OffsetFetchCodec.PINNED_VERSION);
        assertEquals((short) 8, OffsetFetchCodec.MAX_VERSION);
    }
}
