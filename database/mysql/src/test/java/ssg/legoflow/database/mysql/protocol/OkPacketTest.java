package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link OkPacket}.
 */
class OkPacketTest {

    private static final int CAPS = CapabilityFlags.CLIENT_PROTOCOL_41;

    @Test
    void testEncodeDecode_basic() {
        var ok = OkPacket.ok();
        var encoded = ok.encode(CAPS);
        var decoded = OkPacket.decode(encoded, CAPS);

        assertThat(decoded.affectedRows()).isEqualTo(0);
        assertThat(decoded.lastInsertId()).isEqualTo(0);
        assertThat(StatusFlags.hasStatus(decoded.statusFlags(), StatusFlags.SERVER_STATUS_AUTOCOMMIT)).isTrue();
    }

    @Test
    void testEncodeDecode_withAffectedRows() {
        var ok = OkPacket.ok(5, 42);
        var encoded = ok.encode(CAPS);
        var decoded = OkPacket.decode(encoded, CAPS);

        assertThat(decoded.affectedRows()).isEqualTo(5);
        assertThat(decoded.lastInsertId()).isEqualTo(42);
    }

    @Test
    void testEncodeDecode_withInfo() {
        var ok = new OkPacket(1, 10, StatusFlags.DEFAULT_STATUS, 2, "rows matched: 1");
        var capsWithTrack = CAPS | CapabilityFlags.CLIENT_SESSION_TRACK;
        var encoded = ok.encode(capsWithTrack);
        var decoded = OkPacket.decode(encoded, capsWithTrack);

        assertThat(decoded.affectedRows()).isEqualTo(1);
        assertThat(decoded.lastInsertId()).isEqualTo(10);
        assertThat(decoded.warnings()).isEqualTo(2);
        assertThat(decoded.info()).isEqualTo("rows matched: 1");
    }

    @Test
    void testHeader() {
        var ok = OkPacket.ok();
        var encoded = ok.encode(CAPS);
        assertThat(encoded[0] & 0xFF).isEqualTo(OkPacket.HEADER);
    }

    @Test
    void testOk_factory() {
        var ok = OkPacket.ok();
        assertThat(ok.affectedRows()).isEqualTo(0);
        assertThat(ok.lastInsertId()).isEqualTo(0);
        assertThat(ok.warnings()).isEqualTo(0);
    }

    @Test
    void testOk_withRowsFactory() {
        var ok = OkPacket.ok(10, 5);
        assertThat(ok.affectedRows()).isEqualTo(10);
        assertThat(ok.lastInsertId()).isEqualTo(5);
    }

    @Test
    void testEncodeDecode_largeAffectedRows() {
        var ok = new OkPacket(100000, 999999, StatusFlags.DEFAULT_STATUS, 0, "");
        var encoded = ok.encode(CAPS);
        var decoded = OkPacket.decode(encoded, CAPS);
        assertThat(decoded.affectedRows()).isEqualTo(100000);
        assertThat(decoded.lastInsertId()).isEqualTo(999999);
    }
}
