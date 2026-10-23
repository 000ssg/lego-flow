package ssg.legoflow.messaging.kafka.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ssg.legoflow.messaging.kafka.protocol.InitProducerIdRequest;
import ssg.legoflow.messaging.kafka.protocol.InitProducerIdResponse;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link InitProducerIdCodec} — Kafka InitProducerId
 * request/response (API key 22).
 *
 * <p>Covers the full version range v0–v4 (KIP-482 flexible from v2).
 * Byte-layout assertions verify the wire format against the spec:
 * <ul>
 *   <li>v0–v1 request: {@code TransactionalId(nullable string), TransactionTimeoutMs(int32)} —
 *       v3+ appends {@code ProducerId(int64), ProducerEpoch(int16)} (spec-default -1/-1,
 *       read + discarded by the model);</li>
 *   <li>v2–v4 request: nullable <em>compact</em> string + tagged end marker;</li>
 *   <li>response (all versions): {@code ThrottleTimeMs(int32), ErrorCode(int16),
 *       ProducerId(int64), ProducerEpoch(int16)}; v2+ flexible (tagged end marker).</li>
 * </ul>
 */
@DisplayName("InitProducerIdCodec (API key 22)")
class InitProducerIdCodecTest {

    // ── Fixtures ────────────────────────────────────────────────────────

    private static InitProducerIdRequest req() {
        return new InitProducerIdRequest("prod-txn", 60_000);
    }

    private static InitProducerIdRequest reqNullTxnId() {
        return new InitProducerIdRequest(null, 60_000);
    }

    private static InitProducerIdResponse resp() {
        return new InitProducerIdResponse((short) 0, 123_456, (short) 1);
    }

    private static InitProducerIdResponse respErr(short err) {
        return new InitProducerIdResponse(err, -1, (short) -1);
    }

    private static InitProducerIdRequest roundTripReq(short v, InitProducerIdRequest r) {
        byte[] b = InitProducerIdCodec.encodeRequest(v, r);
        return InitProducerIdCodec.decodeRequest(v, ByteBuffer.wrap(b));
    }

    private static InitProducerIdResponse roundTripResp(short v, InitProducerIdResponse r) {
        byte[] b = InitProducerIdCodec.encodeResponse(v, r);
        return InitProducerIdCodec.decodeResponse(v, ByteBuffer.wrap(b));
    }

    // ── Version contract ────────────────────────────────────────────────

    @Test
    @DisplayName("minimum version is v0 (KafkaProducer wire format)")
    void pinnedVersion() {
        assertEquals((short) 0, InitProducerIdCodec.MIN_VERSION);
    }

    @Test
    @DisplayName("v0 encodeRequest/decodeRequest round-trips at the pinned version")
    void pinnedEncodeRequest() {
        byte[] b = InitProducerIdCodec.encodeRequest((short) 0, req());
        InitProducerIdRequest decoded = InitProducerIdCodec.decodeRequest(
                (short) 0, ByteBuffer.wrap(b));
        assertEquals("prod-txn", decoded.transactionalId());
        assertEquals(60_000, decoded.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v0 encodeResponse/decodeResponse round-trips at the pinned version")
    void pinnedEncodeResponse() {
        byte[] b = InitProducerIdCodec.encodeResponse((short) 0, resp());
        InitProducerIdResponse decoded = InitProducerIdCodec.decodeResponse(
                (short) 0, ByteBuffer.wrap(b));
        assertEquals(0, decoded.errorCode());
        assertEquals(123_456, decoded.producerId());
        assertEquals(1, decoded.producerEpoch());
    }

    @Test
    @DisplayName("version 5+ is not implemented (range 0–4); v3/v4 encode fine")
    void versionRange() {
        assertThrows(CodecNotImplementedException.class,
                () -> InitProducerIdCodec.encodeRequest((short) 5, req()));
        assertThrows(CodecNotImplementedException.class,
                () -> InitProducerIdCodec.decodeRequest((short) 5, ByteBuffer.allocate(0)));
        assertThrows(CodecNotImplementedException.class,
                () -> InitProducerIdCodec.encodeResponse((short) 5, resp()));
        // v3/v4 are within range and must encode without error
        InitProducerIdCodec.encodeRequest((short) 4, req());
        InitProducerIdCodec.encodeResponse((short) 3, resp());
    }

    // ── Request round-trips ─────────────────────────────────────────────

    @Test
    @DisplayName("v0 request round-trips: transactionalId + timeout")
    void roundTripRequestV0() {
        InitProducerIdRequest r = roundTripReq((short) 0, req());
        assertEquals("prod-txn", r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v0 request round-trips with null transactionalId (idempotent-only)")
    void roundTripRequestNullTxnId() {
        InitProducerIdRequest r = roundTripReq((short) 0, reqNullTxnId());
        assertNull(r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v1 request round-trips: identical to v0 (no field change)")
    void roundTripRequestV1() {
        InitProducerIdRequest r = roundTripReq((short) 1, req());
        assertEquals("prod-txn", r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v3 request round-trips: v3+ ProducerId/ProducerEpoch read + discarded")
    void roundTripRequestV3() {
        InitProducerIdRequest r = roundTripReq((short) 3, req());
        assertEquals("prod-txn", r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v2 flexible request round-trips")
    void roundTripRequestV2Flexible() {
        InitProducerIdRequest r = roundTripReq((short) 2, req());
        assertEquals("prod-txn", r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    @Test
    @DisplayName("v4 flexible request round-trips (v3+ trailing fields present)")
    void roundTripRequestV4Flexible() {
        InitProducerIdRequest r = roundTripReq((short) 4, req());
        assertEquals("prod-txn", r.transactionalId());
        assertEquals(60_000, r.transactionTimeoutMs());
    }

    // ── Request byte layout ─────────────────────────────────────────────

    @Test
    @DisplayName("v0 request bytes: int16-len string + int32 timeout")
    void bytesRequestV0() {
        // "prod-txn" = 8 bytes; timeout 60_000 = 0x0000EA60
        byte[] expected = new byte[]{
                0x00, 0x08,                                       // string length = 8
                'p', 'r', 'o', 'd', '-', 't', 'x', 'n',           // "prod-txn"
                0x00, 0x00, (byte) 0xEA, 0x60,                     // timeout = 60_000
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeRequest((short) 0, req()));
    }

    @Test
    @DisplayName("v0 request bytes: null transactionalId = int16 -1")
    void bytesRequestNullTxnId() {
        byte[] expected = new byte[]{
                (byte) 0xFF, (byte) 0xFF,                         // string length = -1
                0x00, 0x00, (byte) 0xEA, 0x60,                     // timeout = 60_000
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeRequest((short) 0, reqNullTxnId()));
    }

    @Test
    @DisplayName("v3 request bytes: flexible + v3+ ProducerId(-1 int64) + ProducerEpoch(-1 int16) appended")
    void bytesRequestV3() {
        // v3 is flexible: compact string (varint 8+1) ... timeout ... v3+ fields ... endTags
        byte[] expected = new byte[]{
                0x09,                                             // compact string len (8+1)
                'p', 'r', 'o', 'd', '-', 't', 'x', 'n',           // "prod-txn"
                0x00, 0x00, (byte) 0xEA, 0x60,                     // timeout = 60_000
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // ProducerId = -1
                (byte) 0xFF, (byte) 0xFF,                         // ProducerEpoch = -1
                0x00,                                             // top-level endTags
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeRequest((short) 3, req()));
    }

    @Test
    @DisplayName("v2 flexible request bytes: compact string + int32 timeout + endTags")
    void bytesRequestV2Flexible() {
        // compact "prod-txn": varint(8+1)=0x09 + 8 bytes; timeout 60_000; top endTags 0
        byte[] expected = new byte[]{
                0x09,                                             // compact string len (8+1)
                'p', 'r', 'o', 'd', '-', 't', 'x', 'n',           // "prod-txn"
                0x00, 0x00, (byte) 0xEA, 0x60,                     // timeout = 60_000
                0x00,                                             // top-level endTags
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeRequest((short) 2, req()));
    }

    // ── Response round-trips ────────────────────────────────────────────

    @Test
    @DisplayName("v0 response round-trips: errorCode + producerId + producerEpoch")
    void roundTripResponseV0() {
        InitProducerIdResponse r = roundTripResp((short) 0, resp());
        assertEquals(0, r.errorCode());
        assertEquals(123_456, r.producerId());
        assertEquals(1, r.producerEpoch());
    }

    @Test
    @DisplayName("v2 flexible response round-trips")
    void roundTripResponseV2Flexible() {
        InitProducerIdResponse r = roundTripResp((short) 2, resp());
        assertEquals(0, r.errorCode());
        assertEquals(123_456, r.producerId());
        assertEquals(1, r.producerEpoch());
    }

    @Test
    @DisplayName("v4 flexible response round-trips with non-zero errorCode")
    void roundTripResponseV4WithTopLevelError() {
        InitProducerIdResponse r = roundTripResp((short) 4, respErr((short) 22));
        assertEquals(22, r.errorCode());
        assertEquals(-1, r.producerId());
        assertEquals(-1, r.producerEpoch());
    }

    // ── Response byte layout ────────────────────────────────────────────

    @Test
    @DisplayName("v0 response bytes: throttle(0) + errorCode + producerId + producerEpoch")
    void bytesResponseV0() {
        // ThrottleTimeMs 0; ErrorCode 0; ProducerId 123_456 = 0x0001E240; ProducerEpoch 1
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                           // ThrottleTimeMs = 0
                0x00, 0x00,                                       // ErrorCode = 0
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, (byte) 0xE2, 0x40,   // ProducerId = 123_456
                0x00, 0x01,                                       // ProducerEpoch = 1
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeResponse((short) 0, resp()));
    }

    @Test
    @DisplayName("v2 flexible response bytes: fixed 4 fields + trailing top-level endTags")
    void bytesResponseV2Flexible() {
        // same fixed 4 fields as v0, then top-level endTags 0
        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x00,                           // ThrottleTimeMs = 0
                0x00, 0x00,                                       // ErrorCode = 0
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, (byte) 0xE2, 0x40,   // ProducerId = 123_456
                0x00, 0x01,                                       // ProducerEpoch = 1
                0x00,                                             // top-level endTags
        };
        assertArrayEquals(expected, InitProducerIdCodec.encodeResponse((short) 2, resp()));
    }

    // ── Encoding properties ─────────────────────────────────────────────

    @Test
    @DisplayName("encoding is deterministic (two encodes → identical bytes)")
    void deterministic() {
        assertArrayEquals(
                InitProducerIdCodec.encodeRequest((short) 0, req()),
                InitProducerIdCodec.encodeRequest((short) 0, req()));
        assertArrayEquals(
                InitProducerIdCodec.encodeResponse((short) 0, resp()),
                InitProducerIdCodec.encodeResponse((short) 0, resp()));
    }

    // ── Facade wiring ───────────────────────────────────────────────────

    @Test
    @DisplayName("KafkaCodec facade delegates to InitProducerIdCodec")
    void facadeDelegation() {
        byte[] reqFacade = KafkaCodec.encodeInitProducerIdRequest((short) 0, req());
        byte[] reqDirect = InitProducerIdCodec.encodeRequest((short) 0, req());
        assertArrayEquals(reqDirect, reqFacade);

        InitProducerIdRequest decodedFacade =
                KafkaCodec.decodeInitProducerIdRequest((short) 0, ByteBuffer.wrap(reqDirect));
        assertEquals("prod-txn", decodedFacade.transactionalId());
        assertEquals(60_000, decodedFacade.transactionTimeoutMs());

        byte[] respFacade = KafkaCodec.encodeInitProducerIdResponse((short) 0, resp());
        byte[] respDirect = InitProducerIdCodec.encodeResponse((short) 0, resp());
        assertArrayEquals(respDirect, respFacade);

        InitProducerIdResponse decodedResp =
                KafkaCodec.decodeInitProducerIdResponse((short) 0, ByteBuffer.wrap(respDirect));
        assertEquals(123_456, decodedResp.producerId());
        assertEquals(1, decodedResp.producerEpoch());
    }

    // ── Pinned-version integration ──────────────────────────────────────

    @Test
    @DisplayName("pinned v0 round-trip through encode/decode of the full shapes")
    void pinnedRoundTrip() {
        InitProducerIdRequest r = roundTripReq((short) 0, req());
        assertEquals(req().transactionalId(), r.transactionalId());

        InitProducerIdResponse s = roundTripResp((short) 0, resp());
        assertEquals(resp().producerId(), s.producerId());
    }
}
