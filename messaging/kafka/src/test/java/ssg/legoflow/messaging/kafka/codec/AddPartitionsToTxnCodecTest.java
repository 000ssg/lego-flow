package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.AddPartitionsToTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.AddPartitionsToTxnResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for AddPartitionsToTxn (API 24) — v0–v4, flexible from v3.
 *
 * <p>Request v0–v3 (byte-identical v0–v2; v3 flexible): {@code V3AndBelowTransactionalId (string) +
 * V3AndBelowProducerId (int64) + V3AndBelowProducerEpoch (int16) + V3AndBelowTopics[] { Name (string),
 * Partitions[] int32 }}. v4 reorganizes into a batched {@code Transactions[] { TransactionalId,
 * ProducerId, ProducerEpoch, VerifyOnly (bool, spec default false), Topics[] }} with compact
 * encoding; the in-house model carries exactly one transaction, which is the whole payload.
 *
 * <p>Response v0–v3 (byte-identical v0–v2; v3 flexible): leading {@code ThrottleTimeMs (int32, spec
 * default 0, written + discarded)} + {@code ResultsByTopicV3AndBelow[] { Name (string),
 * ResultsByPartition[] { PartitionIndex (int32), PartitionErrorCode (int16) }}}. v4 adds an ignorable
 * top-level {@code ErrorCode (int16)} before the batched {@code ResultsByTransaction[]}. The old inline
 * facade response omitted the mandatory leading ThrottleTimeMs — malformed on the wire.
 *
 * <p>Models unchanged: {@code AddPartitionsToTxnRequest(transactionalId, producerId, producerEpoch,
 * topics)} / {@code AddPartitionsToTxnResponse(topics)}.
 */
class AddPartitionsToTxnCodecTest {

    private static final short VERSION = AddPartitionsToTxnCodec.PINNED_VERSION;

    // ── Fixtures ─────────────────────────────────────────────────────────

    private static AddPartitionsToTxnRequest req() {
        return new AddPartitionsToTxnRequest(
                "txn-1", 123L, (short) 7,
                List.of(new AddPartitionsToTxnRequest.TopicPartitions("topicA", List.of(3, 5))));
    }

    private static AddPartitionsToTxnRequest reqMulti() {
        return new AddPartitionsToTxnRequest(
                "txn-1", 123L, (short) 7,
                List.of(new AddPartitionsToTxnRequest.TopicPartitions("topicA", List.of(3, 5)),
                        new AddPartitionsToTxnRequest.TopicPartitions("topicB", List.of(0))));
    }

    private static AddPartitionsToTxnRequest reqEmptyTopics() {
        return new AddPartitionsToTxnRequest("txn-1", 123L, (short) 7, List.of());
    }

    private static AddPartitionsToTxnResponse resp() {
        return new AddPartitionsToTxnResponse(
                List.of(new AddPartitionsToTxnResponse.TopicResponse(
                        "topicA",
                        List.of(new AddPartitionsToTxnResponse.PartitionResponse(3, (short) 0),
                                new AddPartitionsToTxnResponse.PartitionResponse(5, (short) 3)))));
    }

    private static ByteBuffer bb(byte[] b) {
        return ByteBuffer.wrap(b);
    }

    // ── Pinned version ───────────────────────────────────────────────────

    @Test
    @DisplayName("PINNED_VERSION is v0")
    void pinnedVersionIsV0() {
        assertEquals(0, VERSION);
    }

    // ── Request byte layout ──────────────────────────────────────────────

    @Test
    @DisplayName("v0/v1/v2 request bytes: int16 txnId + int64 producerId + int16 epoch + topics")
    void bytesRequestFixedV0() {
        byte[] expected = new byte[]{
                0x00, 0x05, 't', 'x', 'n', '-', '1',   // V3AndBelowTransactionalId = "txn-1"
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7B,  // ProducerId = 123
                0x00, 0x07,                                 // ProducerEpoch = 7
                0x00, 0x00, 0x00, 0x01,                     // numTopics = 1
                0x00, 0x06, 't', 'o', 'p', 'i', 'c', 'A',  // topic name "topicA"
                0x00, 0x00, 0x00, 0x02,                     // numPartitions = 2
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00, 0x00, 0x00, 0x05,                     // partition 5
        };
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeRequest((short) 0, req()));
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeRequest((short) 1, req()));
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeRequest((short) 2, req()));
    }

    @Test
    @DisplayName("v3 request bytes: flexible (compact txnId/topics/partitions + endTags, no top-level trailer)")
    void bytesRequestFlexibleV3() {
        //  3:  num?  — v3 top-level: txnId (compact), producerId, producerEpoch, numTopics (varint N+1), topic, endTags? none
        byte[] expected = new byte[]{
                0x06,                                       // txnId compact count = 5+1 ("txn-1" is 5 bytes)
                't', 'x', 'n', '-', '1',                    // "txn-1"
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7B,  // ProducerId = 123
                0x00, 0x07,                                 // ProducerEpoch = 7
                0x02,                                       // numTopics varint = 1+1
                0x07,                                       // name compact count = 6+1
                't', 'o', 'p', 'i', 'c', 'A',              // "topicA"
                0x03,                                       // numPartitions varint = 2+1
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00,                                       // per-partition endTags
                0x00, 0x00, 0x00, 0x05,                     // partition 5
                0x00,                                       // per-partition endTags
                0x00,                                       // per-topic endTags
                // v3: no top-level endTags (spec: top-level fields = txnId, producerId, producerEpoch, topics — no tagged section at top? actually v3+ IS flexible → top-level endTags present)
        };
        // NOTE: v3 is flexible from v3 → the top message carries a tagged-field sentinel (varint 0)
        // after the topics array. Corrected expectation includes it:
        byte[] withTopEndTags = new byte[expected.length + 1];
        System.arraycopy(expected, 0, withTopEndTags, 0, expected.length);
        withTopEndTags[expected.length] = 0x00; // top-level endTags
        assertArrayEquals(withTopEndTags, AddPartitionsToTxnCodec.encodeRequest((short) 3, req()));
    }

    @Test
    @DisplayName("v4 request bytes: batched flexible (transactions[] + VerifyOnly false + endTags)")
    void bytesRequestBatchV4() {
        byte[] expected = new byte[]{
                0x02,                                       // numTransactions varint = 1+1
                0x06,                                       // txnId compact count = 5+1 ("txn-1" is 5 bytes)
                't', 'x', 'n', '-', '1',                    // "txn-1"
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7B,  // ProducerId = 123
                0x00, 0x07,                                 // ProducerEpoch = 7
                0x00,                                       // VerifyOnly = false (spec default)
                0x02,                                       // numTopics varint = 1+1
                0x07,                                       // name compact count = 6+1
                't', 'o', 'p', 'i', 'c', 'A',              // "topicA"
                0x03,                                       // numPartitions varint = 2+1
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00,                                       // per-partition endTags
                0x00, 0x00, 0x00, 0x05,                     // partition 5
                0x00,                                       // per-partition endTags
                0x00,                                       // per-topic endTags
                0x00,                                       // per-transaction endTags
                0x00,                                       // top-level endTags
        };
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeRequest((short) 4, req()));
    }

    // ── Response byte layout ─────────────────────────────────────────────

    @Test
    @DisplayName("v0/v1/v2 response bytes: leading ThrottleTimeMs (0) + ResultsByTopicV3AndBelow")
    void bytesResponseFixedV0() {
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0 (spec default, written + discarded)
                0x00, 0x00, 0x00, 0x01,                     // numTopics = 1
                0x00, 0x06, 't', 'o', 'p', 'i', 'c', 'A',  // "topicA"
                0x00, 0x00, 0x00, 0x02,                     // numPartitions = 2
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00, 0x00,                                 // errorCode = 0
                0x00, 0x00, 0x00, 0x05,                     // partition 5
                0x00, 0x03,                                 // errorCode = 3 (UNKNOWN_TOPIC_OR_PARTITION)
        };
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeResponse((short) 0, resp()));
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeResponse((short) 1, resp()));
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeResponse((short) 2, resp()));
    }

    @Test
    @DisplayName("v3 response bytes: flexible (compact name + per-partition/topic/top endTags)")
    void bytesResponseFlexibleV3() {
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0 (fixed-width)
                0x02,                                       // numTopics varint = 1+1
                0x07,                                       // name compact count = 6+1
                't', 'o', 'p', 'i', 'c', 'A',              // "topicA"
                0x03,                                       // numPartitions varint = 2+1
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00, 0x00,                                 // errorCode = 0
                0x00,                                       // per-partition endTags
                0x00, 0x00, 0x00, 0x05,                     // partition 5
                0x00, 0x03,                                 // errorCode = 3
                0x00,                                       // per-partition endTags
                0x00,                                       // per-topic endTags
                0x00,                                       // top-level endTags
        };
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeResponse((short) 3, resp()));
    }

    @Test
    @DisplayName("v4 response bytes: leading ThrottleTimeMs + ignorable ErrorCode (0) + batched ResultsByTransaction")
    void bytesResponseBatchV4() {
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0
                0x00, 0x00,                                 // top-level ErrorCode = 0 (ignorable, spec default)
                0x02,                                       // numResultsByTransaction varint = 1+1
                0x04,                                       // placeholder txnId compact count = 3+1 ("txn" is 3 bytes)
                't', 'x', 'n',                              // "txn" (single-transaction batch placeholder)
                0x02,                                       // numTopicResults varint = 1+1
                0x07,                                       // name compact count = 6+1
                't', 'o', 'p', 'i', 'c', 'A',              // "topicA"
                0x03,                                       // numPartitions varint = 2+1
                0x00, 0x00, 0x00, 0x03,                     // partition 3
                0x00, 0x00,                                 // errorCode = 0
                0x00,                                       // per-partition endTags
                0x00, 0x00, 0x00, 0x05,                     // partition 5
                0x00, 0x03,                                 // errorCode = 3
                0x00,                                       // per-partition endTags
                0x00,                                       // per-topic endTags
                0x00,                                       // per-transaction endTags
                0x00,                                       // top-level endTags
        };
        assertArrayEquals(expected, AddPartitionsToTxnCodec.encodeResponse((short) 4, resp()));
    }

    // ── Round-trips ──────────────────────────────────────────────────────

    @Test
    @DisplayName("request round-trips v0–v4 (incl. v3 flexible + v4 batched single transaction)")
    void roundTripRequestAllVersions() {
        AddPartitionsToTxnRequest original = req();
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnRequest decoded =
                    AddPartitionsToTxnCodec.decodeRequest(v, bb(AddPartitionsToTxnCodec.encodeRequest(v, original)));
            assertEquals(original, decoded, "request round-trip failed at v" + v);
        }
    }

    @Test
    @DisplayName("response round-trips v0–v4 (incl. v3 flexible + v4 batched)")
    void roundTripResponseAllVersions() {
        AddPartitionsToTxnResponse original = resp();
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnResponse decoded =
                    AddPartitionsToTxnCodec.decodeResponse(v, bb(AddPartitionsToTxnCodec.encodeResponse(v, original)));
            assertEquals(original, decoded, "response round-trip failed at v" + v);
        }
    }

    @Test
    @DisplayName("multi-topic request round-trips v0–v4")
    void roundTripMultiTopic() {
        AddPartitionsToTxnRequest original = reqMulti();
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnRequest decoded =
                    AddPartitionsToTxnCodec.decodeRequest(v, bb(AddPartitionsToTxnCodec.encodeRequest(v, original)));
            assertEquals(original, decoded, "multi-topic round-trip failed at v" + v);
        }
    }

    @Test
    @DisplayName("empty topics round-trips v0–v4")
    void roundTripEmptyTopics() {
        AddPartitionsToTxnRequest req = reqEmptyTopics();
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnRequest decoded =
                    AddPartitionsToTxnCodec.decodeRequest(v, bb(AddPartitionsToTxnCodec.encodeRequest(v, req)));
            assertEquals(req, decoded, "empty-topics round-trip failed at v" + v);
        }
        AddPartitionsToTxnResponse resp = new AddPartitionsToTxnResponse(List.of());
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnResponse decoded =
                    AddPartitionsToTxnCodec.decodeResponse(v, bb(AddPartitionsToTxnCodec.encodeResponse(v, resp)));
            assertEquals(resp, decoded, "empty-response round-trip failed at v" + v);
        }
    }

    @Test
    @DisplayName("null transactionalId round-trips v0–v4 (fixed null + flexible varint 0)")
    void roundTripNullTransactionalId() {
        AddPartitionsToTxnRequest req =
                new AddPartitionsToTxnRequest(null, 123L, (short) 7,
                        List.of(new AddPartitionsToTxnRequest.TopicPartitions("topicA", List.of(3))));
        for (short v = 0; v <= 4; v++) {
            AddPartitionsToTxnRequest decoded =
                    AddPartitionsToTxnCodec.decodeRequest(v, bb(AddPartitionsToTxnCodec.encodeRequest(v, req)));
            assertEquals(req, decoded, "null-txnId round-trip failed at v" + v);
        }
    }

    // ── Facade delegation ────────────────────────────────────────────────

    @Test
    @DisplayName("KafkaCodec facade delegates to AddPartitionsToTxnCodec (pinned v0)")
    void facadeDelegates() {
        byte[] reqBytes = KafkaCodec.encodeAddPartitionsToTxnRequest(req());
        assertArrayEquals(AddPartitionsToTxnCodec.encodeRequest((short) 0, req()), reqBytes);
        AddPartitionsToTxnRequest decodedReq = KafkaCodec.decodeAddPartitionsToTxnRequest(bb(reqBytes));
        assertEquals(req(), decodedReq);

        byte[] respBytes = KafkaCodec.encodeAddPartitionsToTxnResponse(resp());
        assertArrayEquals(AddPartitionsToTxnCodec.encodeResponse((short) 0, resp()), respBytes);
        AddPartitionsToTxnResponse decodedResp = KafkaCodec.decodeAddPartitionsToTxnResponse(bb(respBytes));
        assertEquals(resp(), decodedResp);
    }

    // ── Version guards ───────────────────────────────────────────────────

    @Test
    @DisplayName("encodeRequest/decodeRequest/encodeResponse/decodeResponse throw CodecNotImplementedException above v4")
    void versionGuardAboveMax() {
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.encodeRequest((short) 5, req()));
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.decodeRequest((short) 5, bb(new byte[0])));
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.encodeResponse((short) 5, resp()));
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.decodeResponse((short) 5, bb(new byte[0])));
    }

    @Test
    @DisplayName("negative version throws CodecNotImplementedException")
    void versionGuardNegative() {
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.encodeRequest((short) -1, req()));
        assertThrows(CodecNotImplementedException.class,
                () -> AddPartitionsToTxnCodec.decodeResponse((short) -1, bb(new byte[0])));
    }

    // ── Structural checks ────────────────────────────────────────────────

    @Test
    @DisplayName("v3 request carries the top-level endTags sentinel (flexible top message)")
    void v3RequestHasTopEndTags() {
        byte[] bytes = AddPartitionsToTxnCodec.encodeRequest((short) 3, req());
        assertEquals(0, bytes[bytes.length - 1], "top-level endTags must be the final byte");
    }

    @Test
    @DisplayName("v4 request writes VerifyOnly = false (spec default) after ProducerEpoch")
    void v4RequestVerifyOnlyFalse() {
        byte[] bytes = AddPartitionsToTxnCodec.encodeRequest((short) 4, req());
        // layout: 1 (numTxns) + 1+5 (txnId compact) + 8 (producerId) + 2 (epoch) + ...
        int verifyOnlyIdx = 1 + 1 + 5 + 8 + 2; // = 17
        assertEquals(0, bytes[verifyOnlyIdx], "VerifyOnly must be varint 0 (false)");
    }

    @Test
    @DisplayName("v3 response decodes the fixed-width ThrottleTimeMs first (discarded) and then the flexible topics")
    void v3ResponseReadsThrottleFirst() {
        // Build a v3 response with a non-zero ThrottleTimeMs to prove it is read + discarded.
        byte[] body = new byte[]{
                0x00, 0x00, 0x01, 0x2C,                     // ThrottleTimeMs = 300 (non-zero, discarded)
                0x01,                                       // numTopics varint = 0+1
                0x00,                                       // top-level endTags
        };
        AddPartitionsToTxnResponse decoded =
                AddPartitionsToTxnCodec.decodeResponse((short) 3, bb(body));
        assertEquals(List.of(), decoded.topics(), "empty topics must decode cleanly past ThrottleTimeMs");
    }

    @Test
    @DisplayName("v4 response decodes the ignorable top-level ErrorCode (discarded) before the batched results")
    void v4ResponseReadsErrorCodeFirst() {
        byte[] body = new byte[]{
                0x00, 0x00, 0x00, 0x00,                     // ThrottleTimeMs = 0
                0x00, 0x01,                                 // top-level ErrorCode = 1 (ignored)
                0x01,                                       // numResultsByTransaction varint = 0+1
                0x00,                                       // top-level endTags
        };
        AddPartitionsToTxnResponse decoded =
                AddPartitionsToTxnCodec.decodeResponse((short) 4, bb(body));
        assertEquals(List.of(), decoded.topics(), "empty results must decode cleanly past the ignorable ErrorCode");
    }
}
