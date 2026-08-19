package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ErrPacket}.
 */
class ErrPacketTest {

    private static final int CAPS = CapabilityFlags.CLIENT_PROTOCOL_41;

    @Test
    void testEncodeDecode_basic() {
        var err = ErrPacket.error(1045, "Access denied");
        var encoded = err.encode(CAPS);
        var decoded = ErrPacket.decode(encoded, CAPS);

        assertThat(decoded.errorCode()).isEqualTo(1045);
        assertThat(decoded.sqlState()).isEqualTo("HY000");
        assertThat(decoded.message()).isEqualTo("Access denied");
    }

    @Test
    void testEncodeDecode_customSqlState() {
        var err = new ErrPacket(1064, "42000", "Syntax error");
        var encoded = err.encode(CAPS);
        var decoded = ErrPacket.decode(encoded, CAPS);

        assertThat(decoded.errorCode()).isEqualTo(1064);
        assertThat(decoded.sqlState()).isEqualTo("42000");
        assertThat(decoded.message()).isEqualTo("Syntax error");
    }

    @Test
    void testHeader() {
        var err = ErrPacket.error(1, "test");
        var encoded = err.encode(CAPS);
        assertThat(encoded[0] & 0xFF).isEqualTo(ErrPacket.HEADER);
    }

    @Test
    void testAccessDenied() {
        var err = ErrPacket.accessDenied("root", "localhost");
        assertThat(err.errorCode()).isEqualTo(1045);
        assertThat(err.sqlState()).isEqualTo("28000");
        assertThat(err.message()).contains("root");
        assertThat(err.message()).contains("localhost");
    }

    @Test
    void testSyntaxError() {
        var err = ErrPacket.syntaxError("near SELECT");
        assertThat(err.errorCode()).isEqualTo(1064);
        assertThat(err.sqlState()).isEqualTo("42000");
    }

    @Test
    void testUnknownDatabase() {
        var err = ErrPacket.unknownDatabase("baddb");
        assertThat(err.errorCode()).isEqualTo(1049);
        assertThat(err.message()).contains("baddb");
    }

    @Test
    void testTableExists() {
        var err = ErrPacket.tableExists("users");
        assertThat(err.errorCode()).isEqualTo(1050);
        assertThat(err.message()).contains("users");
    }

    @Test
    void testUnknownTable() {
        var err = ErrPacket.unknownTable("missing");
        assertThat(err.errorCode()).isEqualTo(1051);
    }

    @Test
    void testUnknownColumn() {
        var err = ErrPacket.unknownColumn("bad_col");
        assertThat(err.errorCode()).isEqualTo(1054);
        assertThat(err.message()).contains("bad_col");
    }

    @Test
    void testIsErr() {
        var err = ErrPacket.error(1, "test");
        assertThat(MysqlCodec.isErr(err.encode(CAPS))).isTrue();
        assertThat(MysqlCodec.isErr(OkPacket.ok().encode(CAPS))).isFalse();
    }
}
