package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EofPacket}.
 */
class EofPacketTest {

    private static final int CAPS = CapabilityFlags.CLIENT_PROTOCOL_41;

    @Test
    void testEncodeDecode_default() {
        var eof = EofPacket.eof();
        var encoded = eof.encode(CAPS);
        var decoded = EofPacket.decode(encoded, CAPS);

        assertThat(decoded.warnings()).isEqualTo(0);
        assertThat(StatusFlags.hasStatus(decoded.statusFlags(), StatusFlags.SERVER_STATUS_AUTOCOMMIT)).isTrue();
    }

    @Test
    void testEncodeDecode_withWarnings() {
        var eof = new EofPacket(3, StatusFlags.SERVER_STATUS_IN_TRANS | StatusFlags.SERVER_STATUS_AUTOCOMMIT);
        var encoded = eof.encode(CAPS);
        var decoded = EofPacket.decode(encoded, CAPS);

        assertThat(decoded.warnings()).isEqualTo(3);
        assertThat(StatusFlags.hasStatus(decoded.statusFlags(), StatusFlags.SERVER_STATUS_IN_TRANS)).isTrue();
    }

    @Test
    void testIsEof_true() {
        var eof = EofPacket.eof();
        var encoded = eof.encode(CAPS);
        assertThat(EofPacket.isEof(encoded)).isTrue();
    }

    @Test
    void testIsEof_false_okPacket() {
        var ok = OkPacket.ok();
        assertThat(EofPacket.isEof(ok.encode(CAPS))).isFalse();
    }

    @Test
    void testIsEof_false_tooLong() {
        // Payload with 0xFE but > 8 bytes is NOT an EOF
        var payload = new byte[10];
        payload[0] = (byte) 0xFE;
        assertThat(EofPacket.isEof(payload)).isFalse();
    }

    @Test
    void testIsEof_false_empty() {
        assertThat(EofPacket.isEof(new byte[0])).isFalse();
    }

    @Test
    void testHeader() {
        assertThat(EofPacket.HEADER).isEqualTo(0xFE);
    }

    @Test
    void testEncode_protocol41_size() {
        var eof = EofPacket.eof();
        var encoded = eof.encode(CAPS);
        assertThat(encoded).hasSize(5); // 1 header + 2 warnings + 2 status
    }

    @Test
    void testEncode_noProtocol41_size() {
        var eof = EofPacket.eof();
        var encoded = eof.encode(0); // no PROTOCOL_41
        assertThat(encoded).hasSize(1); // just header
    }
}
