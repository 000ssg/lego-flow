package ssg.legoflow.messaging.kafka.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.kafka.protocol.EndTxnRequest;
import ssg.legoflow.messaging.kafka.protocol.EndTxnResponse;

/**
 * Wire-format tests for {@link EndTxnCodec}.
 *
 * <p>Round-trip coverage for every spec version (v0–v3, request and response) plus exact-byte
 * golden checks (fixed-width, byte-identical v0–v2 and flexible v3), nullability, the
 * {@code Committed} commit/abort flag, the response leading {@code ThrottleTimeMs}
 * write/discard, and the version-range guard.
 */
class EndTxnCodecTest {

    private static final short MIN_VERSION = EndTxnCodec.MIN_VERSION;
    private static final short MAX_VERSION = EndTxnCodec.MAX_VERSION;
    private static final short PINNED = EndTxnCodec.PINNED_VERSION;

    // ---- Request round-trips, all spec versions -----------------------

    @Test
    void roundTripRequest_allVersions() {
        var req = new EndTxnRequest("txn-1", 1000L, (short) 3, true);
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = EndTxnCodec.encodeRequest(v, req);
            var decoded = EndTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-1");
            assertThat(decoded.producerId()).isEqualTo(1000L);
            assertThat(decoded.producerEpoch()).isEqualTo((short) 3);
            assertThat(decoded.committed()).isTrue();
        }
    }

    @Test
    void roundTripRequest_abort_allVersions() {
        var req = new EndTxnRequest("txn-2", 55L, (short) 1, false);
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = EndTxnCodec.encodeRequest(v, req);
            var decoded = EndTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-2");
            assertThat(decoded.producerId()).isEqualTo(55L);
            assertThat(decoded.committed()).isFalse();
        }
    }

    @Test
    void roundTripRequest_nullTransactionalId_allVersions() {
        var req = new EndTxnRequest(null, 77L, (short) 0, true);
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = EndTxnCodec.encodeRequest(v, req);
            var decoded = EndTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isNull();
            assertThat(decoded.committed()).isTrue();
        }
    }

    @Test
    void roundTripRequest_unicodeStrings() {
        var req = new EndTxnRequest("txn-ünïcødé", 1L, (short) 0, true);
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            byte[] encoded = EndTxnCodec.encodeRequest(v, req);
            var decoded = EndTxnCodec.decodeRequest(v, ByteBuffer.wrap(encoded));
            assertThat(decoded.transactionalId()).isEqualTo("txn-ünïcødé");
        }
    }

    // ---- Request exact-byte goldens -----------------------------------

    @Test
    void requestFixed_commit_exactBytes() {
        // v0: TransactionalId="txn-1"(int16 len=5 + 5 bytes) ProducerId=1000(int64)
        //     ProducerEpoch=0(int16) Committed=true(1)  →  2+5+8+2+1 = 18 bytes
        byte[] expected = {
                0x00, 0x05, 't', 'x', 'n', '-', '1',
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xE8,
                0x00, 0x00,
                0x01
        };
        var req = new EndTxnRequest("txn-1", 1000L, (short) 0, true);
        assertThat(EndTxnCodec.encodeRequest((short) 0, req)).isEqualTo(expected);
        assertThat(EndTxnCodec.encodeRequest((short) 1, req)).isEqualTo(expected);
        assertThat(EndTxnCodec.encodeRequest((short) 2, req)).isEqualTo(expected);
    }

    @Test
    void requestFixed_abort_nullTransactionalId_exactBytes() {
        // v0: TransactionalId=null(int16 -1) ProducerId=55(int64) ProducerEpoch=7(int16)
        //     Committed=false(0)
        byte[] expected = {
                (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37,
                0x00, 0x07,
                0x00
        };
        var req = new EndTxnRequest(null, 55L, (short) 7, false);
        assertThat(EndTxnCodec.encodeRequest((short) 0, req)).isEqualTo(expected);
    }

    @Test
    void requestFlexible_commit_exactBytes() {
        // v3: TransactionalId="txn-1"(varint 6 + 5 bytes) ProducerId=1000 ProducerEpoch=0
        //     Committed=true(1) endTags=0  →  1+5+8+2+1+1 = 18 bytes
        byte[] expected = {
                0x06, 't', 'x', 'n', '-', '1',
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xE8,
                0x00, 0x00,
                0x01,
                0x00
        };
        var req = new EndTxnRequest("txn-1", 1000L, (short) 0, true);
        assertThat(EndTxnCodec.encodeRequest((short) 3, req)).isEqualTo(expected);
    }

    @Test
    void requestFlexible_nullTransactionalId_exactBytes() {
        // v3: TransactionalId=null(varint 0) ProducerId=55 ProducerEpoch=7 Committed=false(0)
        //     endTags=0  →  1+8+2+1+1 = 13 bytes
        byte[] expected = {
                0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37,
                0x00, 0x07,
                0x00,
                0x00
        };
        var req = new EndTxnRequest(null, 55L, (short) 7, false);
        assertThat(EndTxnCodec.encodeRequest((short) 3, req)).isEqualTo(expected);
    }

    // ---- Response round-trips, all spec versions ----------------------

    @Test
    void roundTripResponse_allVersions() {
        for (short v = MIN_VERSION; v <= MAX_VERSION; v++) {
            for (short errorCode : new short[] {0, 1, 2, 65, (byte) 0x80, (byte) 0xFF}) {
                byte[] encoded = EndTxnCodec.encodeResponse(v, new EndTxnResponse(errorCode));
                var decoded = EndTxnCodec.decodeResponse(v, ByteBuffer.wrap(encoded));
                assertThat(decoded.errorCode()).isEqualTo(errorCode);
            }
        }
    }

    // ---- Response exact-byte goldens ----------------------------------

    @Test
    void responseFixed_exactBytes() {
        // v0: ThrottleTimeMs=0(int32) ErrorCode=65(int16)  →  6 bytes
        byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x41};
        assertThat(EndTxnCodec.encodeResponse((short) 0, new EndTxnResponse((short) 65)))
                .isEqualTo(expected);
        assertThat(EndTxnCodec.encodeResponse((short) 1, new EndTxnResponse((short) 65)))
                .isEqualTo(expected);
        assertThat(EndTxnCodec.encodeResponse((short) 2, new EndTxnResponse((short) 65)))
                .isEqualTo(expected);
    }

    @Test
    void responseFlexible_exactBytes() {
        // v3: ThrottleTimeMs=0(int32 — primitives stay fixed-width in flexible) ErrorCode=1
        //     endTags=0  →  7 bytes
        byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00};
        assertThat(EndTxnCodec.encodeResponse((short) 3, new EndTxnResponse((short) 1)))
                .isEqualTo(expected);
    }

    @Test
    void response_discardsThrottleTimeMs() {
        // A real broker writes a non-zero ThrottleTimeMs; the in-house model exposes no such
        // field, so the decoder must read+discard it and still recover ErrorCode.
        // v0 wire (6 bytes): ThrottleTimeMs = 0x00411234 (discarded), ErrorCode = 0x002A = 42
        byte[] wire = {0x00, 0x41, 0x12, 0x34, 0x00, 0x2A};
        var decoded = EndTxnCodec.decodeResponse((short) 0, ByteBuffer.wrap(wire));
        assertThat(decoded.errorCode()).isEqualTo((short) 42);
    }

    @Test
    void response_leadingTruncated_throws() {
        // 3 bytes: an int32 ThrottleTimeMs needs 4 — the raw read propagates an underflow,
        // per the project's "do not hide issues" rule (no wrapping, matching sibling codecs).
        assertThatThrownBy(() -> EndTxnCodec.decodeResponse(
                (short) 0, ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x01})))
                .isInstanceOf(BufferUnderflowException.class);
    }

    // ---- Version-range guard ------------------------------------------

    @Test
    void request_outOfRange_throws() {
        var req = new EndTxnRequest("t", 1L, (short) 0, true);
        assertThatThrownBy(() -> EndTxnCodec.encodeRequest((short) -1, req))
                .isInstanceOf(CodecNotImplementedException.class);
        assertThatThrownBy(() -> EndTxnCodec.encodeRequest((short) 4, req))
                .isInstanceOf(CodecNotImplementedException.class)
                .hasMessageContaining("v4");
        assertThatThrownBy(() -> EndTxnCodec.decodeRequest((short) 4,
                ByteBuffer.wrap(new byte[8])))
                .isInstanceOf(CodecNotImplementedException.class);
    }

    @Test
    void response_outOfRange_throws() {
        assertThatThrownBy(() -> EndTxnCodec.encodeResponse((short) 4,
                new EndTxnResponse((short) 0)))
                .isInstanceOf(CodecNotImplementedException.class)
                .hasMessageContaining("v4");
        assertThatThrownBy(() -> EndTxnCodec.decodeResponse((short) -1,
                ByteBuffer.wrap(new byte[6])))
                .isInstanceOf(CodecNotImplementedException.class);
    }

    // ---- Facade parity -------------------------------------------------

    @Test
    void facadeDelegatesPinnedVersion() {
        var req = new EndTxnRequest("txn-1", 1000L, (short) 0, true);
        byte[] viaFacade = ssg.legoflow.messaging.kafka.codec.KafkaCodec.encodeEndTxnRequest(req);
        byte[] viaCodec = EndTxnCodec.encodeRequest(PINNED, req);
        assertThat(viaFacade).isEqualTo(viaCodec);

        var resp = new EndTxnResponse((short) 0);
        assertThat(ssg.legoflow.messaging.kafka.codec.KafkaCodec.encodeEndTxnResponse(resp))
                .isEqualTo(EndTxnCodec.encodeResponse(PINNED, resp));

        var decodedReq = ssg.legoflow.messaging.kafka.codec.KafkaCodec
                .decodeEndTxnRequest(ByteBuffer.wrap(viaFacade));
        assertThat(decodedReq.transactionalId()).isEqualTo("txn-1");
        assertThat(decodedReq.committed()).isTrue();
    }
}
