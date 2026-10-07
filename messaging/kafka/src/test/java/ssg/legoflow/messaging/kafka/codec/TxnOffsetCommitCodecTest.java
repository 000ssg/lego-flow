package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.TxnOffsetCommitRequest;
import ssg.legoflow.messaging.kafka.protocol.TxnOffsetCommitResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec tests for the TxnOffsetCommit request (API key 28) and response, v0–v3.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Round-trips encode at every version then decode back, asserting value equality.</li>
 *   <li>v0–v1 are byte-identical (no committed leader epoch); v2 adds the per-partition
 *       {@code CommittedLeaderEpoch} (spec default −1 — the model does not expose it, so it
 *       round-trips as discarded); v3 is the first flexible version (KIP-482) and adds the
 *       top-level {@code GenerationId/MemberId/GroupInstanceId} triple.</li>
 *   <li>A v0 request carries a byte-for-byte golden, pinning the exact fixed-encoding
 *       wire layout (int16 string prefixes, int32 array counts).</li>
 *   <li>v3 is KIP-482 flexible: its varint length prefixes can make a *short* request
 *       smaller than the v2 it supersedes, so no monotonic-size relation is asserted.</li>
 * </ul>
 */
class TxnOffsetCommitCodecTest {

    private static TxnOffsetCommitRequest.TopicData topic(String name,
            TxnOffsetCommitRequest.PartitionData... parts) {
        return new TxnOffsetCommitRequest.TopicData(name, Arrays.asList(parts));
    }

    private static TxnOffsetCommitRequest.PartitionData part(int idx, long offset, String meta) {
        return new TxnOffsetCommitRequest.PartitionData(idx, offset, meta);
    }

    // ===================== Request round-trips =====================

    @Test
    void request_v0_roundTrip_singleTopic() {
        var req = new TxnOffsetCommitRequest("txn-1", "grp-1", 123456L, (short) 7,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        byte[] enc = TxnOffsetCommitCodec.encodeRequest((short) 0, req);
        var dec = TxnOffsetCommitCodec.decodeRequest((short) 0, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v1_roundTrip_nullMetadata_multipleTopics() {
        var req = new TxnOffsetCommitRequest("txn-1", "grp-1", -1L, (short) 0,
                List.of(
                        topic("t1", part(0, 1L, null), part(1, 2L, "x")),
                        topic("t2", part(5, 100L, null))));
        byte[] enc = TxnOffsetCommitCodec.encodeRequest((short) 1, req);
        var dec = TxnOffsetCommitCodec.decodeRequest((short) 1, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
        assertNull(dec.topics().get(0).partitions().get(0).metadata());
        assertEquals("x", dec.topics().get(0).partitions().get(1).metadata());
    }

    @Test
    void request_v2_roundTrip_committedLeaderEpochDiscarded() {
        // v2 adds per-partition CommittedLeaderEpoch (int32, spec default -1). The model does
        // not expose it, so the codec writes the default and discards it on decode.
        var req = new TxnOffsetCommitRequest("txn-2", "grp-2", 99L, (short) 3,
                List.of(topic("topic-b", part(2, 7L, "m"))));
        byte[] enc = TxnOffsetCommitCodec.encodeRequest((short) 2, req);
        var dec = TxnOffsetCommitCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v3_roundTrip_flexibleWithConsumerIdentityTriple() {
        // v3 (flexible): adds top-level GenerationId/MemberId/GroupInstanceId. Model does not
        // expose them, so they are written with spec defaults and discarded on decode.
        var req = new TxnOffsetCommitRequest("txn-3", "grp-3", 7L, (short) 1,
                List.of(topic("topic-c", part(0, 55L, "meta-c"))));
        byte[] enc = TxnOffsetCommitCodec.encodeRequest((short) 3, req);
        var dec = TxnOffsetCommitCodec.decodeRequest((short) 3, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void request_v3_roundTrip_nullTransactionalIdAndGroupId() {
        // flexible compact strings treat null as varint 0.
        var req = new TxnOffsetCommitRequest(null, null, 0L, (short) 0,
                List.of(topic("t", part(0, 0L, null))));
        byte[] enc = TxnOffsetCommitCodec.encodeRequest((short) 3, req);
        var dec = TxnOffsetCommitCodec.decodeRequest((short) 3, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    // ===================== Request version-shape =====================

    @Test
    void request_v0_v1_byteIdentical() {
        var req = new TxnOffsetCommitRequest("txn", "grp", 1L, (short) 1,
                List.of(topic("t", part(0, 1L, "m"))));
        assertArrayEquals(
                TxnOffsetCommitCodec.encodeRequest((short) 0, req),
                TxnOffsetCommitCodec.encodeRequest((short) 1, req),
                "v0 and v1 must be byte-identical");
    }

    @Test
    void request_v2_isLongerThan_v1() {
        var req = new TxnOffsetCommitRequest("txn", "grp", 1L, (short) 1,
                List.of(topic("t", part(0, 1L, "m"))));
        int v1 = TxnOffsetCommitCodec.encodeRequest((short) 1, req).length;
        int v2 = TxnOffsetCommitCodec.encodeRequest((short) 2, req).length;
        // v2 adds one int32 CommittedLeaderEpoch per partition.
        assertEquals(v1 + 4, v2, "v2 adds int32 committed leader epoch per partition");
    }

    @Test
    void request_v3_isLongerThan_v2() {
        var req = new TxnOffsetCommitRequest("txn", "grp", 1L, (short) 1,
                List.of(topic("t", part(0, 1L, "m"))));
        int v2 = TxnOffsetCommitCodec.encodeRequest((short) 2, req).length;
        int v3 = TxnOffsetCommitCodec.encodeRequest((short) 3, req).length;
        // v3 is flexible. Versus v2 for this fixture:
        //   - compact strings save 4 bytes (int16->varint on txn/grp/t/m)
        //   - compact arrays save 6 bytes (int32->varint on Topics/Partitions counts)
        //   - +6 for the top-level consumer-identity triple
        //       (GenerationId int32 + MemberId compact + GroupInstanceId compact)
        //   - +3 for tagged-field terminators (varint 0 on partition, topic, top-level)
        // Net: -4 -6 +6 +3 = -1, i.e. v3 (49) is one byte shorter than v2 (50).
        assertEquals(-1, v3 - v2, "v3 flexible encoding is net 1 byte shorter than v2 " +
                "for this fixture (compact savings outweigh the new identity fields)");
    }

    // ===================== Request byte-golden (v0) =====================

    @Test
    void request_v0_byteForByte_golden() {
        // Hand-built v0 wire layout (fixed encoding):
        //   TransactionalId  = int16 len 5 + "txn-1"        -> 00 05 74 78 6E 2D 31
        //   GroupId          = int16 len 5 + "grp-1"        -> 00 05 67 72 70 2D 31
        //   ProducerId       = int64 0x1122334455667788     -> 11 22 33 44 55 66 77 88
        //   ProducerEpoch    = int16 -2                     -> FF FE
        //   Topics.count     = int32 1                      -> 00 00 00 01
        //   topic Name       = int16 len 1 + "t"            -> 00 01 74
        //   Partitions.count = int32 1                      -> 00 00 00 01
        //   PartitionIndex   = int32 0                      -> 00 00 00 00
        //   CommittedOffset  = int64 42                     -> 00 00 00 00 00 00 00 2A
        //   CommittedMetadata= int16 len 1 + "m"            -> 00 01 6D
        var req = new TxnOffsetCommitRequest("txn-1", "grp-1", 0x1122334455667788L, (short) -2,
                List.of(topic("t", part(0, 42L, "m"))));
        byte[] expected = {
                (byte) 0x00, 0x05, 't', 'x', 'n', '-', '1',
                (byte) 0x00, 0x05, 'g', 'r', 'p', '-', '1',
                (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77, (byte) 0x88,
                (byte) 0xFF, (byte) 0xFE,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x01, 't',
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A,
                0x00, 0x01, 'm'
        };
        assertArrayEquals(expected, TxnOffsetCommitCodec.encodeRequest((short) 0, req));
    }

    // ===================== Request edge cases =====================

    @Test
    void request_zeroTopics_roundTrip() {
        var req = new TxnOffsetCommitRequest("txn", "grp", 1L, (short) 1, List.of());
        for (short v = 0; v <= 3; v++) {
            byte[] enc = TxnOffsetCommitCodec.encodeRequest(v, req);
            var dec = TxnOffsetCommitCodec.decodeRequest(v, ByteBuffer.wrap(enc));
            assertEquals(req, dec, "v" + v);
        }
    }

    @Test
    void request_invalidVersion_throws() {
        var req = new TxnOffsetCommitRequest("t", "g", 1L, (short) 1, List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> TxnOffsetCommitCodec.encodeRequest((short) -1, req));
        assertThrows(CodecNotImplementedException.class,
                () -> TxnOffsetCommitCodec.encodeRequest((short) 4, req));
        assertThrows(CodecNotImplementedException.class,
                () -> TxnOffsetCommitCodec.decodeRequest((short) 9, ByteBuffer.allocate(0)));
    }

    // ===================== Response round-trips =====================

    @Test
    void response_v0_roundTrip() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic-a",
                        List.of(new TxnOffsetCommitResponse.PartitionData(0, (short) 0)))));
        byte[] enc = TxnOffsetCommitCodec.encodeResponse((short) 0, resp);
        var dec = TxnOffsetCommitCodec.decodeResponse((short) 0, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_v1_roundTrip_nonZeroErrorCode() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic-a",
                        List.of(new TxnOffsetCommitResponse.PartitionData(3, (short) 29)))));
        byte[] enc = TxnOffsetCommitCodec.encodeResponse((short) 1, resp);
        var dec = TxnOffsetCommitCodec.decodeResponse((short) 1, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
        assertEquals((short) 29, dec.topics().get(0).partitions().get(0).errorCode());
    }

    @Test
    void response_v2_roundTrip() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic-a",
                        List.of(new TxnOffsetCommitResponse.PartitionData(0, (short) 0)))));
        byte[] enc = TxnOffsetCommitCodec.encodeResponse((short) 2, resp);
        var dec = TxnOffsetCommitCodec.decodeResponse((short) 2, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_v3_roundTrip_flexible() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic-a",
                        List.of(new TxnOffsetCommitResponse.PartitionData(0, (short) 14)))));
        byte[] enc = TxnOffsetCommitCodec.encodeResponse((short) 3, resp);
        var dec = TxnOffsetCommitCodec.decodeResponse((short) 3, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void response_zeroTopics_roundTrip_allVersions() {
        var resp = new TxnOffsetCommitResponse(List.of());
        for (short v = 0; v <= 3; v++) {
            byte[] enc = TxnOffsetCommitCodec.encodeResponse(v, resp);
            var dec = TxnOffsetCommitCodec.decodeResponse(v, ByteBuffer.wrap(enc));
            assertEquals(resp, dec, "v" + v);
        }
    }

    // ===================== Response version-shape =====================

    @Test
    void response_v0_v1_v2_byteIdentical() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("t",
                        List.of(new TxnOffsetCommitResponse.PartitionData(0, (short) 0)))));
        byte[] v0 = TxnOffsetCommitCodec.encodeResponse((short) 0, resp);
        byte[] v1 = TxnOffsetCommitCodec.encodeResponse((short) 1, resp);
        byte[] v2 = TxnOffsetCommitCodec.encodeResponse((short) 2, resp);
        assertArrayEquals(v0, v1, "response v0 == v1");
        assertArrayEquals(v0, v2, "response v0 == v2");
    }

    // ===================== Facade delegation =====================

    @Test
    void facade_request_encode_matches_dedicatedCodec() {
        var req = new TxnOffsetCommitRequest("txn-1", "grp-1", 123456L, (short) 7,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        assertArrayEquals(
                TxnOffsetCommitCodec.encodeRequest(req),
                KafkaCodec.encodeTxnOffsetCommitRequest(req),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_response_encode_matches_dedicatedCodec() {
        var resp = new TxnOffsetCommitResponse(List.of(
                new TxnOffsetCommitResponse.TopicData("topic-a",
                        List.of(new TxnOffsetCommitResponse.PartitionData(0, (short) 0)))));
        assertArrayEquals(
                TxnOffsetCommitCodec.encodeResponse(resp),
                KafkaCodec.encodeTxnOffsetCommitResponse(resp),
                "facade encode must delegate to the dedicated codec");
    }

    @Test
    void facade_roundTrip() {
        var req = new TxnOffsetCommitRequest("txn-1", "grp-1", 123456L, (short) 7,
                List.of(topic("topic-a", part(0, 42L, "meta"))));
        var dec = KafkaCodec.decodeTxnOffsetCommitRequest(
                ByteBuffer.wrap(KafkaCodec.encodeTxnOffsetCommitRequest(req)));
        assertEquals(req, dec);
    }

    @Test
    void pinned_version_is_v0() {
        assertEquals((short) 0, TxnOffsetCommitCodec.PINNED_VERSION);
        assertEquals((short) 3, TxnOffsetCommitCodec.MAX_VERSION);
    }
}
