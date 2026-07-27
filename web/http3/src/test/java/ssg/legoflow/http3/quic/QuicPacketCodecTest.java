package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link QuicPacketCodec} — variable-length integer encoding/decoding
 * and packet encode/decode round-trips for each packet type.
 *
 * @since 1.0.0
 */
class QuicPacketCodecTest {

    // ── Variable-length integer encoding (RFC 9000 §16) ──

    @Test
    void testVarIntEncodeDecode1Byte() {
        // Values 0-63 use 1 byte
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 37);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(37);
    }

    @Test
    void testVarIntEncodeDecode1ByteMax() {
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 63);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(63);
    }

    @Test
    void testVarIntEncodeDecode1ByteZero() {
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 0);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(0);
    }

    @Test
    void testVarIntEncodeDecode2Bytes() {
        // Values 64-16383 use 2 bytes
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 15293);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(15293);
    }

    @Test
    void testVarIntEncodeDecode2BytesMin() {
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 64);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(64);
    }

    @Test
    void testVarIntEncodeDecode2BytesMax() {
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 16383);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(16383);
    }

    @Test
    void testVarIntEncodeDecode4Bytes() {
        // Values 16384-1073741823 use 4 bytes
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 494878333);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(4);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(494878333);
    }

    @Test
    void testVarIntEncodeDecode4BytesMin() {
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 16384);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(4);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(16384);
    }

    @Test
    void testVarIntEncodeDecode8Bytes() {
        // Values > 1073741823 use 8 bytes
        var buf = ByteBuffer.allocate(16);
        QuicPacketCodec.encodeVarInt(buf, 151288809941952652L);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(8);
        assertThat(QuicPacketCodec.decodeVarInt(buf)).isEqualTo(151288809941952652L);
    }

    @Test
    void testVarIntEncodeNegativeThrows() {
        var buf = ByteBuffer.allocate(16);
        assertThatThrownBy(() -> QuicPacketCodec.encodeVarInt(buf, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Packet encode/decode round-trips ──

    @Test
    void testInitialPacketRoundTrip() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var ping = new QuicFrame(QuicFrameType.PING, 0, ByteBuffer.allocate(0), 0, false);
        var packet = new QuicPacket(QuicPacketType.INITIAL, 12345L, 0, List.of(ping), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.type()).isEqualTo(QuicPacketType.INITIAL);
        assertThat(decoded.connectionId()).isEqualTo(12345L);
        assertThat(decoded.version()).isEqualTo(QuicPacketCodec.QUIC_VERSION_1);
    }

    @Test
    void testHandshakePacketRoundTrip() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var crypto = QuicFrame.connectionFrame(QuicFrameType.CRYPTO, ByteBuffer.wrap(new byte[]{0x01, 0x02}));
        var packet = new QuicPacket(QuicPacketType.HANDSHAKE, 9999L, 1, List.of(crypto), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.type()).isEqualTo(QuicPacketType.HANDSHAKE);
        assertThat(decoded.connectionId()).isEqualTo(9999L);
    }

    @Test
    void testZeroRttPacketRoundTrip() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var stream = QuicFrame.streamFrame(4, ByteBuffer.wrap("early".getBytes()), 0, false);
        var packet = new QuicPacket(QuicPacketType.ZERO_RTT, 42L, 0, List.of(stream), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.type()).isEqualTo(QuicPacketType.ZERO_RTT);
        assertThat(decoded.connectionId()).isEqualTo(42L);
    }

    @Test
    void testRetryPacketRoundTrip() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var packet = new QuicPacket(QuicPacketType.RETRY, 777L, 0, List.of(), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.type()).isEqualTo(QuicPacketType.RETRY);
        assertThat(decoded.connectionId()).isEqualTo(777L);
    }

    @Test
    void testOneRttPacketRoundTrip() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var ping = new QuicFrame(QuicFrameType.PING, 0, ByteBuffer.allocate(0), 0, false);
        var packet = new QuicPacket(QuicPacketType.ONE_RTT, 54321L, 10, List.of(ping), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.type()).isEqualTo(QuicPacketType.ONE_RTT);
        assertThat(decoded.connectionId()).isEqualTo(54321L);
    }

    @Test
    void testPacketWithStreamFrame() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var data = ByteBuffer.wrap("hello QUIC".getBytes());
        var stream = QuicFrame.streamFrame(4, data, 0, true);
        var packet = new QuicPacket(QuicPacketType.ONE_RTT, 100L, 5, List.of(stream), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.frames()).hasSize(1);
        assertThat(decoded.frames().getFirst().type()).isEqualTo(QuicFrameType.STREAM);
    }

    @Test
    void testPacketWithMultipleFrames() {
        var codec = new QuicPacketCodec(QuicPacketCodec.Mode.ENCODE);
        var ping = new QuicFrame(QuicFrameType.PING, 0, ByteBuffer.allocate(0), 0, false);
        var stream = QuicFrame.streamFrame(0, ByteBuffer.wrap(new byte[]{1, 2, 3}), 0, false);
        var packet = new QuicPacket(QuicPacketType.ONE_RTT, 200L, 1, List.of(ping, stream), QuicPacketCodec.QUIC_VERSION_1);

        var encoded = codec.encodePacket(packet);
        var decoded = codec.decodePacket(encoded);

        assertThat(decoded.frames()).hasSizeGreaterThanOrEqualTo(2);
    }
}
