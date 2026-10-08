package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.OffsetCommitRequest;
import ssg.legoflow.messaging.kafka.protocol.OffsetCommitResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for the Consumer Groups OffsetCommit request (API key 8) and response, v0–v9.
 *
 * <p>Conventions (mirroring {@link TxnOffsetCommitCodecTest}):
 * <ul>
 *   <li>Round-trips encode at every version then decode back, asserting value equality.</li>
 *   <li>v0 carries only GroupId + Topics (the model's generationId/memberId must be at their
 *       decode defaults, 0/null, for round-trip equality); v1 adds GenerationId/MemberId;
 *       v2–v4 add the top-level RetentionTimeMs (spec default −1, discarded on decode — the
 *       model does not expose it); v5 drops it again; v6 adds the per-partition
 *       CommittedLeaderEpoch (default −1, discarded); v7 adds GroupInstanceId (nullable,
 *       spec default null, discarded); v8–v9 are KIP-482 flexible (compact strings, N+1
 *       varint array counts, tagged-field trailers).</li>
 *   <li>v8 and v9 are wire-identical (KIP-848 changed only the response error-code surface).</li>
 *   <li>A v0 request/response and a v8 request/response carry byte-for-byte goldens, pinning
 *       the fixed and flexible wire layouts respectively.</li>
 * </ul>
 */
class OffsetCommitCodecTest {

    private static OffsetCommitRequest.TopicOffsets topic(String name,
            OffsetCommitRequest.PartitionOffset... parts) {
        return new OffsetCommitRequest.TopicOffsets(name, Arrays.asList(parts));
    }

    private static OffsetCommitRequest.PartitionOffset part(int idx, long offset, String meta) {
        return new OffsetCommitRequest.PartitionOffset(idx, offset, meta);
    }

    private static OffsetCommitResponse.TopicResponse topicResp(String name,
            OffsetCommitResponse.PartitionResponse... parts) {
        return new OffsetCommitResponse.TopicResponse(name, Arrays.asList(parts));
    }

    // ===================== Request round-trips =====================

    @Test
    void request_v0_roundTrip_groupIdAndTopicsOnly() {
        // v0 predates generation/member identity: those fields are not on the wire, so the
        // model values must equal the decode defaults (generationId 0, memberId null).
        var req = new OffsetCommitRequest("grp-1", 0, null,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 0, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 0, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v1_roundTrip_withGenerationAndMemberId() {
        var req = new OffsetCommitRequest("grp-1", 5, "member-1",
                List.of(
                        topic("t1", part(0, 1L, null), part(1, 2L, "x")),
                        topic("t2", part(5, 100L, null))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 1, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 1, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
        assertNull(dec.topics().get(0).partitions().get(0).metadata());
        assertEquals("x", dec.topics().get(0).partitions().get(1).metadata());
    }

    @Test
    void request_v2_roundTrip_retentionTimeDiscarded() {
        // v2–v4 add top-level RetentionTimeMs (int64, spec default -1); the model does not
        // expose it, so the codec writes the default and discards it on decode.
        var req = new OffsetCommitRequest("grp-2", 5, "member-2",
                List.of(topic("topic-b", part(2, 7L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 2, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v3_roundTrip() {
        var req = new OffsetCommitRequest("grp-3", 5, "member-3",
                List.of(topic("topic-b", part(2, 7L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 3, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 3, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v4_roundTrip() {
        var req = new OffsetCommitRequest("grp-4", 5, "member-4",
                List.of(topic("topic-b", part(2, 7L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 4, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 4, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v5_roundTrip_retentionDroppedAgain() {
        // v5 removes RetentionTimeMs (now a broker-side configuration).
        var req = new OffsetCommitRequest("grp-5", 5, "member-5",
                List.of(topic("topic-b", part(2, 7L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 5, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 5, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v6_roundTrip_committedLeaderEpochDiscarded() {
        // v6 adds per-partition CommittedLeaderEpoch (int32, spec default -1); the model does
        // not expose it, so the codec writes the default and discards it on decode.
        var req = new OffsetCommitRequest("grp-6", 5, "member-6",
                List.of(topic("topic-c", part(0, 55L, "meta-c"), part(3, 56L, null))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 6, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 6, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v7_roundTrip_groupInstanceIdDiscarded() {
        // v7 adds GroupInstanceId (nullable, spec default null); the model does not expose it,
        // so the codec writes null and discards it on decode. Non-flexible v7: legacy int16
        // nullable string.
        var req = new OffsetCommitRequest("grp-7", 5, "member-7",
                List.of(topic("topic-d", part(1, 60L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 7, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 7, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v8_roundTrip_flexible() {
        var req = new OffsetCommitRequest("grp-8", 5, "member-8",
                List.of(
                        topic("topic-e", part(0, 1L, null), part(1, 2L, "x")),
                        topic("topic-f", part(9, 100L, "meta-f"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 8, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 8, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v9_roundTrip_flexible() {
        var req = new OffsetCommitRequest("grp-9", 5, "member-9",
                List.of(topic("topic-g", part(4, 90L, "m"))));
        byte[] enc = OffsetCommitCodec.encodeRequest((short) 9, req);
        var dec = OffsetCommitCodec.decodeRequest((short) 9, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v1_to_v9_roundTrip_allVersions() {
        var req = new OffsetCommitRequest("grp", 5, "member-1",
                List.of(
                        topic("t", part(0, 1L, "m"), part(1, 2L, null)),
                        topic("u", part(7, 8L, null))));
        for (short v = 1; v <= 9; v++) {
            byte[] enc = OffsetCommitCodec.encodeRequest(v, req);
            var dec = OffsetCommitCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    // ===================== Request version-shape =====================

    @Test
    void request_v2_adds_retentionTimeMs() {
        var req = new OffsetCommitRequest("g", 5, "m", List.of(topic("t", part(0, 1L, "m"))));
        int v5 = OffsetCommitCodec.encodeRequest((short) 5, req).length;
        int v2 = OffsetCommitCodec.encodeRequest((short) 2, req).length;
        // v2 adds one top-level int64 RetentionTimeMs; v5 is the same layout without it.
        assertEquals(v5 + 8, v2, "v2 adds int64 RetentionTimeMs (top-level)");
    }

    @Test
    void request_v1_to_v2_drops_perPartition_commitTimestamp() {
        var req = new OffsetCommitRequest("g", 5, "m",
                List.of(topic("t", part(0, 1L, "m"), part(1, 2L, null))));
        int v1 = OffsetCommitCodec.encodeRequest((short) 1, req).length;
        int v2 = OffsetCommitCodec.encodeRequest((short) 2, req).length;
        // v2 adds top-level RetentionTimeMs (+8) but drops v1's per-partition
        // CommitTimestamp (−8 per partition; 2 partitions here) → net −8.
        assertEquals(v1 - 8, v2, "v1 carries per-partition CommitTimestamp; v2 does not");
    }

    @Test
    void request_v5_drops_retentionTimeMs() {
        var req = new OffsetCommitRequest("g", 5, "m", List.of(topic("t", part(0, 1L, "m"))));
        int v2 = OffsetCommitCodec.encodeRequest((short) 2, req).length;
        int v5 = OffsetCommitCodec.encodeRequest((short) 5, req).length;
        // v5 removes the int64 RetentionTimeMs that v2–v4 carry.
        assertEquals(v2 - 8, v5, "v5 drops int64 RetentionTimeMs");
    }

    @Test
    void request_v6_adds_committedLeaderEpoch() {
        var req = new OffsetCommitRequest("g", 5, "m",
                List.of(topic("t", part(0, 1L, "m"), part(1, 2L, null))));
        int v5 = OffsetCommitCodec.encodeRequest((short) 5, req).length;
        int v6 = OffsetCommitCodec.encodeRequest((short) 6, req).length;
        // v6 adds one int32 CommittedLeaderEpoch per partition (2 partitions here).
        assertEquals(v5 + 2 * 4, v6, "v6 adds int32 CommittedLeaderEpoch per partition");
    }

    @Test
    void request_v7_adds_groupInstanceId() {
        var req = new OffsetCommitRequest("g", 5, "m", List.of(topic("t", part(0, 1L, "m"))));
        int v6 = OffsetCommitCodec.encodeRequest((short) 6, req).length;
        int v7 = OffsetCommitCodec.encodeRequest((short) 7, req).length;
        // v7 adds GroupInstanceId, written as null (non-flexible: int16 -1 = 2 bytes).
        assertEquals(v6 + 2, v7, "v7 adds nullable GroupInstanceId (null = int16 -1)");
    }

    @Test
    void request_v8_v9_byteIdentical() {
        // KIP-848 (v9) is wire-identical to v8; only the response error-code surface changes.
        var req = new OffsetCommitRequest("g", 5, "m",
                List.of(topic("t", part(0, 1L, "m"))));
        assertArrayEquals(
                OffsetCommitCodec.encodeRequest((short) 8, req),
                OffsetCommitCodec.encodeRequest((short) 9, req),
                "request v8 and v9 must be byte-identical");
    }

    // ===================== Request byte-golden (v0, v8) =====================

    @Test
    void request_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding):
        //   GroupId          = int16 len 2 + "ab"           -> 00 02 61 62
        //   Topics.count     = int32 1                      -> 00 00 00 01
        //   topic Name       = int16 len 2 + "to"           -> 00 02 74 6F
        //   Partitions.count = int32 1                      -> 00 00 00 01
        //   PartitionIndex   = int32 5                      -> 00 00 00 05
        //   CommittedOffset  = int64 10                     -> 00 00 00 00 00 00 00 0A
        //   CommittedMetadata= int16 len 4 + "meta"         -> 00 04 6D 65 74 61
        var req = new OffsetCommitRequest("ab", 0, null,
                List.of(topic("to", part(5, 10L, "meta"))));
        byte[] expected = {
                0x00, 0x02, 'a', 'b',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x02, 't', 'o',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A,
                0x00, 0x04, 'm', 'e', 't', 'a'
        };
        assertArrayEquals(expected, OffsetCommitCodec.encodeRequest((short) 0, req));
    }

    @Test
    void request_v8_byteForByte_golden_flexible() {
        // Hand-built v8 wire layout (KIP-482 flexible encoding):
        //   GroupId          = varint(2+1) + "ab"           -> 03 61 62
        //   GenerationId     = int32 1                      -> 00 00 00 01
        //   MemberId         = varint(2+1) + "m1"           -> 03 6D 31
        //   GroupInstanceId  = null -> varint 0             -> 00
        //   Topics.count     = varint(1+1)                  -> 02
        //   topic Name       = varint(2+1) + "to"           -> 03 74 6F
        //   Partitions.count = varint(1+1)                  -> 02
        //   PartitionIndex   = int32 5                      -> 00 00 00 05
        //   CommittedOffset  = int64 10                     -> 00 00 00 00 00 00 00 0A
        //   CommittedLeaderEpoch = int32 -1                 -> FF FF FF FF
        //   CommittedMetadata= varint(4+1) + "meta"         -> 05 6D 65 74 61
        //   endTags x3 (partition, topic, top-level)        -> 00 00 00
        var req = new OffsetCommitRequest("ab", 1, "m1",
                List.of(topic("to", part(5, 10L, "meta"))));
        byte[] expected = {
                0x03, 'a', 'b',
                0x00, 0x00, 0x00, 0x01,
                0x03, 'm', '1',
                0x00,
                0x02,
                0x03, 't', 'o',
                0x02,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x05, 'm', 'e', 't', 'a',
                0x00,
                0x00,
                0x00
        };
        assertArrayEquals(expected, OffsetCommitCodec.encodeRequest((short) 8, req));
    }

    // ===================== Request edge cases =====================

    @Test
    void request_zeroTopics_roundTrip_allVersions() {
        var req = new OffsetCommitRequest("g", 5, "m", List.of());
        for (short v = 1; v <= 9; v++) {
            byte[] enc = OffsetCommitCodec.encodeRequest(v, req);
            var dec = OffsetCommitCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    @Test
    void request_invalidVersion_throws() {
        var req = new OffsetCommitRequest("t", 0, null, List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.encodeRequest((short) -1, req));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.encodeRequest((short) 10, req));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.decodeRequest((short) 10, ByteBuffer.allocate(0)));
    }

    // ===================== Response round-trips =====================

    @Test
    void response_v0_roundTrip() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("topic-a",
                        new OffsetCommitResponse.PartitionResponse(0, (short) 0))));
        byte[] enc = OffsetCommitCodec.encodeResponse((short) 0, resp);
        var dec = OffsetCommitCodec.decodeResponse((short) 0, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_v3_roundTrip_nonZeroErrorCode() {
        // v3+ carries a leading ThrottleTimeMs (int32); the model does not expose it, so the
        // codec writes the spec default 0 and discards it on decode.
        var resp = new OffsetCommitResponse(List.of(
                topicResp("topic-a",
                        new OffsetCommitResponse.PartitionResponse(3, (short) 29))));
        byte[] enc = OffsetCommitCodec.encodeResponse((short) 3, resp);
        var dec = OffsetCommitCodec.decodeResponse((short) 3, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
        assertEquals((short) 29, dec.topics().get(0).partitions().get(0).errorCode());
    }

    @Test
    void response_v8_roundTrip_flexible() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("topic-a",
                        new OffsetCommitResponse.PartitionResponse(0, (short) 14),
                        new OffsetCommitResponse.PartitionResponse(2, (short) 0))));
        byte[] enc = OffsetCommitCodec.encodeResponse((short) 8, resp);
        var dec = OffsetCommitCodec.decodeResponse((short) 8, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_v9_roundTrip_flexible() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("topic-b",
                        new OffsetCommitResponse.PartitionResponse(1, (short) 30))));
        byte[] enc = OffsetCommitCodec.encodeResponse((short) 9, resp);
        var dec = OffsetCommitCodec.decodeResponse((short) 9, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_allVersions_roundTrip() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("t",
                        new OffsetCommitResponse.PartitionResponse(0, (short) 0),
                        new OffsetCommitResponse.PartitionResponse(1, (short) 29)),
                topicResp("u",
                        new OffsetCommitResponse.PartitionResponse(7, (short) 14))));
        for (short v = 0; v <= 9; v++) {
            byte[] enc = OffsetCommitCodec.encodeResponse(v, resp);
            var dec = OffsetCommitCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
        }
    }

    // ===================== Response version-shape =====================

    @Test
    void response_v0_v1_v2_byteIdentical() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("t", new OffsetCommitResponse.PartitionResponse(0, (short) 0))));
        byte[] v0 = OffsetCommitCodec.encodeResponse((short) 0, resp);
        byte[] v1 = OffsetCommitCodec.encodeResponse((short) 1, resp);
        byte[] v2 = OffsetCommitCodec.encodeResponse((short) 2, resp);
        assertArrayEquals(v0, v1, "response v0 == v1");
        assertArrayEquals(v0, v2, "response v0 == v2");
    }

    @Test
    void response_v3_adds_throttleTimeMs() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("t", new OffsetCommitResponse.PartitionResponse(0, (short) 0))));
        int v2 = OffsetCommitCodec.encodeResponse((short) 2, resp).length;
        int v3 = OffsetCommitCodec.encodeResponse((short) 3, resp).length;
        // v3 adds the leading int32 ThrottleTimeMs.
        assertEquals(v2 + 4, v3, "v3 adds int32 ThrottleTimeMs");
    }

    @Test
    void response_v8_v9_byteIdentical() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("t", new OffsetCommitResponse.PartitionResponse(0, (short) 0))));
        assertArrayEquals(
                OffsetCommitCodec.encodeResponse((short) 8, resp),
                OffsetCommitCodec.encodeResponse((short) 9, resp),
                "response v8 and v9 must be byte-identical");
    }

    // ===================== Response byte-golden (v0, v8) =====================

    @Test
    void response_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding):
        //   Topics.count     = int32 1                      -> 00 00 00 01
        //   topic Name       = int16 len 2 + "to"           -> 00 02 74 6F
        //   Partitions.count = int32 1                      -> 00 00 00 01
        //   PartitionIndex   = int32 5                      -> 00 00 00 05
        //   ErrorCode        = int16 0                      -> 00 00
        var resp = new OffsetCommitResponse(List.of(
                topicResp("to", new OffsetCommitResponse.PartitionResponse(5, (short) 0))));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x02, 't', 'o',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00
        };
        assertArrayEquals(expected, OffsetCommitCodec.encodeResponse((short) 0, resp));
    }

    @Test
    void response_v8_byteForByte_golden_flexible() {
        // Hand-built v8 wire layout (KIP-482 flexible encoding):
        //   ThrottleTimeMs   = int32 0                      -> 00 00 00 00
        //   Topics.count     = varint(1+1)                  -> 02
        //   topic Name       = varint(2+1) + "to"           -> 03 74 6F
        //   Partitions.count = varint(1+1)                  -> 02
        //   PartitionIndex   = int32 5                      -> 00 00 00 05
        //   ErrorCode        = int16 0                      -> 00 00
        //   endTags x3 (partition, topic, top-level)        -> 00 00 00
        var resp = new OffsetCommitResponse(List.of(
                topicResp("to", new OffsetCommitResponse.PartitionResponse(5, (short) 0))));
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x02,
                0x03, 't', 'o',
                0x02,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00,
                0x00,
                0x00,
                0x00
        };
        assertArrayEquals(expected, OffsetCommitCodec.encodeResponse((short) 8, resp));
    }

    // ===================== Response edge cases =====================

    @Test
    void response_zeroTopics_roundTrip_allVersions() {
        var resp = new OffsetCommitResponse(List.of());
        for (short v = 0; v <= 9; v++) {
            byte[] enc = OffsetCommitCodec.encodeResponse(v, resp);
            var dec = OffsetCommitCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
        }
    }

    @Test
    void response_invalidVersion_throws() {
        var resp = new OffsetCommitResponse(List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.encodeResponse((short) -1, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.encodeResponse((short) 10, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> OffsetCommitCodec.decodeResponse((short) -1, ByteBuffer.allocate(0)));
    }

    // ===================== Facade delegation =====================

    @Test
    void facade_request_encode_matches_dedicatedCodec() {
        var req = new OffsetCommitRequest("grp-1", 5, "member-1",
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        assertArrayEquals(
                OffsetCommitCodec.encodeRequest(req),
                KafkaCodec.encodeOffsetCommitRequest(req),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_response_encode_matches_dedicatedCodec() {
        var resp = new OffsetCommitResponse(List.of(
                topicResp("topic-a", new OffsetCommitResponse.PartitionResponse(0, (short) 0))));
        assertArrayEquals(
                OffsetCommitCodec.encodeResponse(resp),
                KafkaCodec.encodeOffsetCommitResponse(resp),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_decode_matches_dedicatedCodec() {
        var req = new OffsetCommitRequest("grp-1", 0, null,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        byte[] enc = KafkaCodec.encodeOffsetCommitRequest(req);
        var viaFacade = KafkaCodec.decodeOffsetCommitRequest(ByteBuffer.wrap(enc));
        var viaCodec = OffsetCommitCodec.decodeRequest(
                OffsetCommitCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(viaCodec, viaFacade,
                "facade decode must forward to the dedicated codec at the pinned version");
    }

    @Test
    void facade_roundTrip() {
        var req = new OffsetCommitRequest("grp-1", 0, null,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        var dec = KafkaCodec.decodeOffsetCommitRequest(
                ByteBuffer.wrap(KafkaCodec.encodeOffsetCommitRequest(req)));
        assertEquals(req, dec);
    }

    @Test
    void pinned_version_is_v0() {
        assertEquals((short) 0, OffsetCommitCodec.PINNED_VERSION);
        assertEquals((short) 9, OffsetCommitCodec.MAX_VERSION);
    }
}
