package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.AddOffsetsToTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.AddOffsetsToTxnResponse;

/**
 * Wire-format tests for {@link AddOffsetsToTxnCodec}.
 *
 * <p>Round-trip coverage for every spec version (v0–v3, request and response) plus exact-byte
 * golden checks (fixed-width, byte-identical v0–v2 and flexible v3), nullability, the response
 * leading {@code ThrottleTimeMs} write/discard, and the version-range guard.
 */
class AddOffsetsToTxnCodecTest {

    private static final short MIN_VERSION = AddOffsetsToTxnCodec.MIN_VERSION;
    private static final short MAX_VERSION = AddOffsetsToTxnCodec.MAX_VERSION;
    private static final short FLEXIBLE = AddOffsetsToTxnCodec.FLEXIBLE_START;
    private static final short PINNED = AddOffsetsToTxnCodec.PINNED_VERSION;

    // ---- Request round-trips, all spec versions -----------------------

    @Test
    void roundTripRequest_allVersions() {
        var req = new AddOffsetsToTxnRequest("txn-1", 1000L, (short) 3, "my-group");
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = AddOffsetsToTxnCodec.encodeRequest(v, req);
            var decoded = AddOffsetsToTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-1");
            assertThat(decoded.producerId()).isEqualTo(1000L);
            assertThat(decoded.producerEpoch()).isEqualTo((short) 3);
            assertThat(decoded.groupId()).isEqualTo("my-group");
        }
    }

    @Test
    void roundTripRequest_nullGroupId_allVersions() {
        var req = new AddOffsetsToTxnRequest("txn-2", 55L, (short) 1, null);
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = AddOffsetsToTxnCodec.encodeRequest(v, req);
            var decoded = AddOffsetsToTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-2");
            assertThat(decoded.groupId()).isNull();
        }
    }

    @Test
    void roundTripRequest_nullTransactionalId_allVersions() {
        var req = new AddOffsetsToTxnRequest(null, 77L, (short) 0, "g");
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = AddOffsetsToTxnCodec.encodeRequest(v, req);
            var decoded = AddOffsetsToTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isNull();
            assertThat(decoded.groupId()).isEqualTo("g");
        }
    }

    @Test
    void roundTripRequest_unicodeStrings() {
        var req = new AddOffsetsToTxnRequest("txn-ünïcødé", 1L, (short) 0, "group-ünïcødé");
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = AddOffsetsToTxnCodec.encodeRequest(v, req);
            var decoded = AddOffsetsToTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-ünïcødé");
            assertThat(decoded.groupId()).isEqualTo("group-ünïcødé");
        }
    }

    // ---- Request exact-byte goldens -----------------------------------

    @Test
    void requestFixed_nonNullStrings_exactBytes() {
        // v0: TransactionalId="t1"(int16 len=2 + 2 bytes) ProducerId=1000(int64)
        //     ProducerEpoch=0(int16) GroupId="g"(int16 len=1 + 1 byte)
        byte[] expected = {
                0x00, 0x02, 't', '1',
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xE8,
                0x00, 0x00,
                0x00, 0x01, 'g'
        };
        var req = new AddOffsetsToTxnRequest("t1", 1000L, (short) 0, "g");
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 0, req)).isEqualTo(expected);
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 1, req)).isEqualTo(expected);
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 2, req)).isEqualTo(expected);
    }

    @Test
    void requestFixed_nullStrings_exactBytes() {
        // v0: TransactionalId=null(int16 -1) ProducerId=55(int64) ProducerEpoch=7(int16)
        //     GroupId=null(int16 -1)
        byte[] expected = {
                (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37,
                0x00, 0x07,
                (byte) 0xFF, (byte) 0xFF
        };
        var req = new AddOffsetsToTxnRequest(null, 55L, (short) 7, null);
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 0, req)).isEqualTo(expected);
    }

    @Test
    void requestFlexible_nonNullStrings_exactBytes() {
        // v3: TransactionalId="t1"(varint 3 + "t1") ProducerId=1000 ProducerEpoch=0
        //     GroupId="g"(varint 2 + "g") endTags=0
        byte[] expected = {
                0x03, 't', '1',
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xE8,
                0x00, 0x00,
                0x02, 'g',
                0x00
        };
        var req = new AddOffsetsToTxnRequest("t1", 1000L, (short) 0, "g");
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 3, req)).isEqualTo(expected);
    }

    @Test
    void requestFlexible_nullStrings_exactBytes() {
        // v3: TransactionalId=null(varint 0) ProducerId=55 ProducerEpoch=7 GroupId=null(varint 0)
        //     endTags=0
        byte[] expected = {
                0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37,
                0x00, 0x07,
                0x00,
                0x00
        };
        var req = new AddOffsetsToTxnRequest(null, 55L, (short) 7, null);
        assertThat(AddOffsetsToTxnCodec.encodeRequest((short) 3, req)).isEqualTo(expected);
    }

    // ---- Response round-trips, all spec versions ----------------------

    @Test
    void roundTripResponse_allVersions() {
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            for (short errorCode : new short[] {0, 1, 2, 65, (byte) 0x80, (byte) 0xFF}) {
                byte[] encoded = AddOffsetsToTxnCodec.encodeResponse(v, new AddOffsetsToTxnResponse(errorCode));
                var decoded = AddOffsetsToTxnCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.errorCode()).isEqualTo(errorCode);
            }
        }
    }

    // ---- Response exact-byte goldens ----------------------------------

    @Test
    void responseFixed_exactBytes() {
        // v0: ThrottleTimeMs=0(int32) ErrorCode=65(int16)  →  6 bytes
        byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x41};
        assertThat(AddOffsetsToTxnCodec.encodeResponse((short) 0, new AddOffsetsToTxnResponse((short) 65)))
                .isEqualTo(expected);
        assertThat(AddOffsetsToTxnCodec.encodeResponse((short) 1, new AddOffsetsToTxnResponse((short) 65)))
                .isEqualTo(expected);
        assertThat(AddOffsetsToTxnCodec.encodeResponse((short) 2, new AddOffsetsToTxnResponse((short) 65)))
                .isEqualTo(expected);
    }

    @Test
    void responseFlexible_exactBytes() {
        // v3: ThrottleTimeMs=0(int32 — primitives stay fixed-width in flexible) ErrorCode=1
        //     endTags=0  →  7 bytes
        byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00};
        assertThat(AddOffsetsToTxnCodec.encodeResponse((short) 3, new AddOffsetsToTxnResponse((short) 1)))
                .isEqualTo(expected);
    }

    @Test
    void response_discardsThrottleTimeMs() {
        // A real broker writes a non-zero ThrottleTimeMs; the in-house model exposes no such
        // field, so the decoder must read+discard it and still recover ErrorCode.
        // v0 wire (6 bytes): ThrottleTimeMs = 0x00411234 (discarded), ErrorCode = 0x002A = 42
        byte[] wire = {0x00, 0x41, 0x12, 0x34, 0x00, 0x2A};
        var decoded = AddOffsetsToTxnCodec.decodeResponse((short) 0, ByteBuffer.wrap(wire));
        assertThat(decoded.errorCode()).isEqualTo((short) 42);
    }

    @Test
    void response_leadingTruncated_throws() {
        // 3 bytes: an int32 ThrottleTimeMs needs 4 — the raw read propagates an underflow,
        // per the project's "do not hide issues" rule (no wrapping, matching sibling codecs).
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.decodeResponse(
                (short) 0, ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x01})))
                .isInstanceOf(BufferUnderflowException.class);
    }

    // ---- Version-range guard ------------------------------------------

    @Test
    void request_outOfRange_throws() {
        var req = new AddOffsetsToTxnRequest("t", 1L, (short) 0, "g");
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.encodeRequest((short) -1, req))
                .isInstanceOf(CodecNotImplementedException.class);
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.encodeRequest((short) 4, req))
                .isInstanceOf(CodecNotImplementedException.class)
                .hasMessageContaining("v4");
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.decodeRequest((short) 4,
                ByteBuffer.wrap(new byte[8])))
                .isInstanceOf(CodecNotImplementedException.class);
    }

    @Test
    void response_outOfRange_throws() {
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.encodeResponse((short) 4,
                new AddOffsetsToTxnResponse((short) 0)))
                .isInstanceOf(CodecNotImplementedException.class)
                .hasMessageContaining("v4");
        assertThatThrownBy(() -> AddOffsetsToTxnCodec.decodeResponse((short) -1,
                ByteBuffer.wrap(new byte[6])))
                .isInstanceOf(CodecNotImplementedException.class);
    }

    // ---- Facade parity -------------------------------------------------

    @Test
    void facadeDelegatesPinnedVersion() {
        var req = new AddOffsetsToTxnRequest("txn-1", 1000L, (short) 0, "my-group");
        byte[] viaFacade = ssg.legoflow.messaging.kafka.codec.KafkaCodec.encodeAddOffsetsToTxnRequest(req);
        byte[] viaCodec = AddOffsetsToTxnCodec.encodeRequest(PINNED, req);
        assertThat(viaFacade).isEqualTo(viaCodec);

        var resp = new AddOffsetsToTxnResponse((short) 0);
        assertThat(ssg.legoflow.messaging.kafka.codec.KafkaCodec.encodeAddOffsetsToTxnResponse(resp))
                .isEqualTo(AddOffsetsToTxnCodec.encodeResponse(PINNED, resp));

        var decodedReq = ssg.legoflow.messaging.kafka.codec.KafkaCodec
                .decodeAddOffsetsToTxnRequest(ByteBuffer.wrap(viaFacade));
        assertThat(decodedReq.transactionalId()).isEqualTo("txn-1");
        assertThat(decodedReq.groupId()).isEqualTo("my-group");
    }
}
