package ssg.legoflow.messaging.kafka.codec;

import ssg.legoflow.messaging.kafka.protocol.AlterConfigsRequest;
import ssg.legoflow.messaging.kafka.protocol.AlterConfigsResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Codec tests for {@link AlterConfigsCodec} (API key 33): round-trip (pinned v0 + v1 + v2),
 * exact-byte layout assertions for each version (v0 and v1 share the baseline wire layout;
 * v2 is the KIP-482 flexible variant), version bounds, and facade delegation.
 */
class AlterConfigsCodecTest {

    // ── Request: round-trip (pinned version) ────────────────────────────

    @Test
    void requestRoundTripSingleResource() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "a",
                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1"),
                                new AlterConfigsRequest.ConfigEntry("n2", "v2")))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest(req);
        AlterConfigsRequest dec = AlterConfigsCodec.decodeRequest(
                AlterConfigsCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void requestRoundTripMultipleResources() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(
                        new AlterConfigsRequest.ResourceConfig((byte) 2, "t1",
                                List.of(new AlterConfigsRequest.ConfigEntry("a", "1"))),
                        new AlterConfigsRequest.ResourceConfig((byte) 4, "1.2.3.4",
                                List.of())),
                false);
        byte[] enc = AlterConfigsCodec.encodeRequest(req);
        AlterConfigsRequest dec = AlterConfigsCodec.decodeRequest(
                AlterConfigsCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void requestRoundTripNullValues() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "t",
                        List.of(new AlterConfigsRequest.ConfigEntry("k1", null),
                                new AlterConfigsRequest.ConfigEntry("k2", "x")))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest(req);
        AlterConfigsRequest dec = AlterConfigsCodec.decodeRequest(
                AlterConfigsCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    @Test
    void requestRoundTripFlexibleV2() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "t",
                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1"),
                                new AlterConfigsRequest.ConfigEntry("n2", null)))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 2, req);
        AlterConfigsRequest dec = AlterConfigsCodec.decodeRequest((short) 2, ByteBuffer.wrap(enc));
        assertEquals(req, dec);
    }

    // ── Request: exact-byte layout, baseline (v0) ───────────────────────
    // v0 order: numResources(int32), per resource: ResourceType(int8),
    //   ResourceName(string), numConfigs(int32), per config: Name(string),
    //   Value(nullable string), trailing ValidateOnly(bool).

    @Test
    void requestV0SingleResource() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "a",
                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1"),
                                new AlterConfigsRequest.ConfigEntry("n2", null)))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 0, req);
        //  0: numResources=1
        //  4: ResourceType=2 (TOPIC)
        //  5: ResourceName "a": int16 len 1 + 1 byte
        //  8: numConfigs=2
        // 12: Name "n1": int16 len 2 + 2 bytes; Value "v1": int16 len 2 + 2 bytes
        // 19: Name "n2": int16 len 2 + 2 bytes; Value null: int16 -1
        // 25: ValidateOnly=true
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01,
                0x02,
                (byte) 0x00, 0x01, 'a',
                0x00, 0x00, 0x00, 0x02,
                (byte) 0x00, 0x02, 'n', '1', (byte) 0x00, 0x02, 'v', '1',
                (byte) 0x00, 0x02, 'n', '2', (byte) 0xFF, (byte) 0xFF,
                0x01
        };
        assertArrayEquals(expected, enc);
    }

    @Test
    void requestV0TwoResources() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(
                        new AlterConfigsRequest.ResourceConfig((byte) 2, "t1",
                                List.of(new AlterConfigsRequest.ConfigEntry("a", "1"))),
                        new AlterConfigsRequest.ResourceConfig((byte) 4, "1.2.3.4",
                                List.of())),
                false);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 0, req);
        //  0: numResources=2
        //  4: ResourceType=2; ResourceName "t1": len 2 + 2
        // 10: numConfigs=1; Name "a": len 1 + 1; Value "1": len 1 + 1
        // 17: ResourceType=4 (BROKER); ResourceName "1.2.3.4": len 7 + 7
        // 28: numConfigs=0
        // 32: ValidateOnly=false
        byte[] expected = {
                0x00, 0x00, 0x00, 0x02,
                0x02, 0x00, 0x02, 't', '1',
                0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 'a', 0x00, 0x01, '1',
                0x04, 0x00, 0x07, '1', '.', '2', '.', '3', '.', '4',
                0x00, 0x00, 0x00, 0x00,
                0x00
        };
        assertArrayEquals(expected, enc);
    }

    @Test
    void requestV0MixedNullValues() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "t",
                        List.of(new AlterConfigsRequest.ConfigEntry("k1", null),
                                new AlterConfigsRequest.ConfigEntry("k2", "x")))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 0, req);
        //  0: numResources=1
        //  4: ResourceType=2; ResourceName "t": int16 len 1 + 1
        //  8: numConfigs=2
        // 12: Name "k1": int16 len 2 + 2; Value null: int16 -1
        // 18: Name "k2": int16 len 2 + 2; Value "x": int16 len 1 + 1
        // 24: ValidateOnly=true
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01,
                0x02, (byte) 0x00, 0x01, 't',
                0x00, 0x00, 0x00, 0x02,
                (byte) 0x00, 0x02, 'k', '1', (byte) 0xFF, (byte) 0xFF,
                (byte) 0x00, 0x02, 'k', '2', (byte) 0x00, 0x01, 'x',
                0x01
        };
        assertArrayEquals(expected, enc);
    }

    @Test
    void requestV0Utf8ResourceName() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "тест",
                        List.of())),
                false);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 0, req);
        //  0: numResources=1
        //  4: ResourceType=2; ResourceName "тест": len 8 + 8 UTF-8 bytes
        // 14: numConfigs=0
        // 18: ValidateOnly=false
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01,
                (byte) 0x02, (byte) 0x00, (byte) 0x08, (byte) 0xD1, (byte) 0x82, (byte) 0xD0,
                (byte) 0xB5, (byte) 0xD1, (byte) 0x81, (byte) 0xD1, (byte) 0x82,
                0x00, 0x00, 0x00, 0x00,
                0x00
        };
        assertArrayEquals(expected, enc);
    }

    // ── Request: v1 byte-identical to v0 (spec: "Version 1 is the same") ─

    @Test
    void requestV1SameAsV0() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "t",
                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1")))),
                false);
        assertArrayEquals(AlterConfigsCodec.encodeRequest((short) 0, req),
                AlterConfigsCodec.encodeRequest((short) 1, req));
        AlterConfigsRequest dec = AlterConfigsCodec.decodeRequest(
                (short) 1, ByteBuffer.wrap(AlterConfigsCodec.encodeRequest((short) 1, req)));
        assertEquals(req, dec);
    }

    // ── Request: exact-byte layout, flexible (v2, KIP-482) ──────────────
    // v2 order: numResources(varint, N+1), per resource: ResourceType(int8),
    //   ResourceName(compact string), numConfigs(varint, N+1), per config:
    //   Name(compact string), Value(nullable compact string; null = varint 0),
    //   endTags(config) — all 0, endTags(resource) — all 0,
    //   trailing ValidateOnly(bool), endTags(top) — all 0.

    @Test
    void requestV2FlexibleSingleResource() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "a",
                        List.of(new AlterConfigsRequest.ConfigEntry("n1", "v1"),
                                new AlterConfigsRequest.ConfigEntry("n2", null)))),
                true);
        byte[] enc = AlterConfigsCodec.encodeRequest((short) 2, req);
        //  0: numResources=1 -> varint(2)=0x02
        //  1: ResourceType=2
        //  2: ResourceName "a": varint(2)=0x02 + 1 byte
        //  4: numConfigs=2 -> varint(3)=0x03
        //  5: Name "n1": varint(3)=0x03 + 2; Value "v1": varint(3)=0x03 + 2; endTags 0x00
        // 12: Name "n2": varint(3)=0x03 + 2; Value null: varint(0)=0x00; endTags 0x00
        // 17: endTags(resource) 0x00
        // 18: ValidateOnly=true
        // 19: endTags(top) 0x00
        byte[] expected = {
                0x02,
                0x02,
                0x02, 'a',
                0x03,
                0x03, 'n', '1', 0x03, 'v', '1', 0x00,
                0x03, 'n', '2', 0x00, 0x00,
                0x00,
                0x01,
                0x00
        };
        assertArrayEquals(expected, enc);
    }

    // ── Response: round-trip (pinned version) ───────────────────────────
    // Model exposes (errorCode, resourceName); ThrottleTimeMs, ErrorMessage and
    // ResourceType are written with spec defaults and read + discarded.

    @Test
    void responseRoundTripSingle() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 0, "tpc")));
        byte[] enc = AlterConfigsCodec.encodeResponse(resp);
        AlterConfigsResponse dec = AlterConfigsCodec.decodeResponse(
                AlterConfigsCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void responseRoundTripMultiple() {
        AlterConfigsResponse resp = new AlterConfigsResponse(
                List.of(new AlterConfigsResponse.ResourceResponse((short) 0, "t1"),
                        new AlterConfigsResponse.ResourceResponse((short) 29, "t2")));
        byte[] enc = AlterConfigsCodec.encodeResponse(resp);
        AlterConfigsResponse dec = AlterConfigsCodec.decodeResponse(
                AlterConfigsCodec.PINNED_VERSION, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    @Test
    void responseRoundTripFlexibleV2() {
        AlterConfigsResponse resp = new AlterConfigsResponse(
                List.of(new AlterConfigsResponse.ResourceResponse((short) 0, "tpc"),
                        new AlterConfigsResponse.ResourceResponse((short) 29, "bad")));
        byte[] enc = AlterConfigsCodec.encodeResponse((short) 2, resp);
        AlterConfigsResponse dec = AlterConfigsCodec.decodeResponse(
                (short) 2, ByteBuffer.wrap(enc));
        assertEquals(resp, dec);
    }

    // ── Response: exact-byte layout, baseline (v0) ──────────────────────
    // v0 order: ThrottleTimeMs(int32), numResults(int32), per result:
    //   ErrorCode(int16), ErrorMessage(nullable string), ResourceType(int8),
    //   ResourceName(string).

    @Test
    void responseV0SingleSuccess() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 0, "a")));
        byte[] enc = AlterConfigsCodec.encodeResponse((short) 0, resp);
        //  0: ThrottleTimeMs=0 (spec default — model does not expose it)
        //  4: numResults=1
        //  8: ErrorCode=0; ErrorMessage null: int16 -1; ResourceType=2 (spec default)
        // 11: ResourceName "a": int16 len 1 + 1 byte
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x02,
                (byte) 0x00, 0x01, 'a'
        };
        assertArrayEquals(expected, enc);
    }

    @Test
    void responseV1SameAsV0() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 0, "tpc")));
        assertArrayEquals(AlterConfigsCodec.encodeResponse((short) 0, resp),
                AlterConfigsCodec.encodeResponse((short) 1, resp));
        AlterConfigsResponse dec = AlterConfigsCodec.decodeResponse(
                (short) 1, ByteBuffer.wrap(AlterConfigsCodec.encodeResponse((short) 1, resp)));
        assertEquals(resp, dec);
    }

    // ── Response: exact-byte layout, flexible (v2, KIP-482) ─────────────
    // v2 order: ThrottleTimeMs(int32), numResults(varint, N+1), per result:
    //   ErrorCode(int16), ErrorMessage(nullable compact string; null = varint 0),
    //   ResourceType(int8), ResourceName(compact string), endTags(resource) — all 0,
    //   endTags(top) — all 0.

    @Test
    void responseV2FlexibleSingleSuccess() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 0, "tpc")));
        byte[] enc = AlterConfigsCodec.encodeResponse((short) 2, resp);
        //  0: ThrottleTimeMs=0 (spec default)
        //  4: numResults=1 -> varint(2)=0x02
        //  5: ErrorCode=0; ErrorMessage null: varint(0)=0x00; ResourceType=2 (spec default)
        //  8: ResourceName "tpc": varint(4)=0x04 + 3 bytes; endTags(resource) 0x00
        // 13: endTags(top) 0x00
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x02,
                0x00, 0x00, 0x00, 0x02,
                0x04, 't', 'p', 'c', 0x00,
                0x00
        };
        assertArrayEquals(expected, enc);
    }

    @Test
    void responseV2FlexibleWithErrorMessage() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 29, "bad")));
        byte[] enc = AlterConfigsCodec.encodeResponse((short) 2, resp);
        //  0: ThrottleTimeMs=0 (spec default)
        //  4: numResults=1 -> varint(2)=0x02
        //  5: ErrorCode=29 (UNKNOWN_TOPIC_OR_PARTITION); ErrorMessage null: varint 0; ResourceType=2
        //  8: ResourceName "bad": varint(4)=0x04 + 3 bytes; endTags(resource) 0x00
        // 13: endTags(top) 0x00
        byte[] expected = {
                0x00, 0x00, 0x00, 0x00,
                0x02,
                0x00, 0x1D, 0x00, 0x02,
                0x04, 'b', 'a', 'd', 0x00,
                0x00
        };
        assertArrayEquals(expected, enc);
    }

    // ── Version bounds ──────────────────────────────────────────────────

    @Test
    void versionsAboveMaxRejected() {
        AlterConfigsRequest req = new AlterConfigsRequest(List.of(), false);
        assertThrows(CodecNotImplementedException.class,
                () -> AlterConfigsCodec.encodeRequest((short) 3, req));
        assertThrows(CodecNotImplementedException.class,
                () -> AlterConfigsCodec.decodeRequest((short) 3, ByteBuffer.wrap(new byte[0])));
        AlterConfigsResponse resp = new AlterConfigsResponse(List.of());
        assertThrows(CodecNotImplementedException.class,
                () -> AlterConfigsCodec.encodeResponse((short) 3, resp));
        assertThrows(CodecNotImplementedException.class,
                () -> AlterConfigsCodec.decodeResponse((short) 3, ByteBuffer.wrap(new byte[0])));
    }

    @Test
    void negativeVersionRejected() {
        assertThrows(CodecNotImplementedException.class,
                () -> AlterConfigsCodec.encodeRequest((short) -1,
                        new AlterConfigsRequest(List.of(), false)));
    }

    // ── Facade delegation (KafkaCodec) ──────────────────────────────────

    @Test
    void facadeRequestDelegatesToCodec() {
        AlterConfigsRequest req = new AlterConfigsRequest(
                List.of(new AlterConfigsRequest.ResourceConfig((byte) 2, "t",
                        List.of(new AlterConfigsRequest.ConfigEntry("a", "1")))),
                true);
        byte[] viaFacade = KafkaCodec.encodeAlterConfigsRequest(req);
        byte[] viaCodec = AlterConfigsCodec.encodeRequest(req);
        assertArrayEquals(viaCodec, viaFacade);
        AlterConfigsRequest dec = KafkaCodec.decodeAlterConfigsRequest(ByteBuffer.wrap(viaFacade));
        assertEquals(req, dec);
    }

    @Test
    void facadeResponseDelegatesToCodec() {
        AlterConfigsResponse resp =
                new AlterConfigsResponse(List.of(new AlterConfigsResponse.ResourceResponse(
                        (short) 0, "tpc")));
        byte[] viaFacade = KafkaCodec.encodeAlterConfigsResponse(resp);
        byte[] viaCodec = AlterConfigsCodec.encodeResponse(resp);
        assertArrayEquals(viaCodec, viaFacade);
        AlterConfigsResponse dec = KafkaCodec.decodeAlterConfigsResponse(ByteBuffer.wrap(viaFacade));
        assertEquals(resp, dec);
    }
}
