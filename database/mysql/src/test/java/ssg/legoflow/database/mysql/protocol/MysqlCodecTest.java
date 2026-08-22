package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MysqlCodec}.
 */
class MysqlCodecTest {

    @Test
    void testEncodeDecodeQuery() {
        var payload = MysqlCodec.encodeQuery("SELECT 1");
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_QUERY);
        assertThat(MysqlCodec.decodeQuery(payload)).isEqualTo("SELECT 1");
    }

    @Test
    void testEncodeDecodePrepare() {
        var payload = MysqlCodec.encodePrepare("SELECT ? FROM t WHERE id = ?");
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_STMT_PREPARE);
        assertThat(MysqlCodec.decodePrepare(payload)).isEqualTo("SELECT ? FROM t WHERE id = ?");
    }

    @Test
    void testEncodeDecodeInitDb() {
        var payload = MysqlCodec.encodeInitDb("mydb");
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_INIT_DB);
        assertThat(MysqlCodec.decodeInitDb(payload)).isEqualTo("mydb");
    }

    @Test
    void testEncodePing() {
        var payload = MysqlCodec.encodePing();
        assertThat(payload).hasSize(1);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_PING);
    }

    @Test
    void testEncodeQuit() {
        var payload = MysqlCodec.encodeQuit();
        assertThat(payload).hasSize(1);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_QUIT);
    }

    @Test
    void testEncodeStmtClose() {
        var payload = MysqlCodec.encodeStmtClose(123);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_STMT_CLOSE);
        assertThat(MysqlCodec.decodeStmtClose(payload)).isEqualTo(123);
    }

    @Test
    void testEncodeStmtReset() {
        var payload = MysqlCodec.encodeStmtReset(456);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_STMT_RESET);
    }

    @Test
    void testEncodeStatistics() {
        var payload = MysqlCodec.encodeStatistics();
        assertThat(payload).hasSize(1);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_STATISTICS);
    }

    @Test
    void testEncodeResetConnection() {
        var payload = MysqlCodec.encodeResetConnection();
        assertThat(payload).hasSize(1);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_RESET_CONNECTION);
    }

    @Test
    void testEncodeSetOption() {
        var payload = MysqlCodec.encodeSetOption(0);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_SET_OPTION);
        assertThat(payload).hasSize(3);
    }

    @Test
    void testCommandByte() {
        assertThat(MysqlCodec.commandByte(MysqlCodec.encodePing())).isEqualTo(MysqlCodec.COM_PING);
        assertThat(MysqlCodec.commandByte(MysqlCodec.encodeQuit())).isEqualTo(MysqlCodec.COM_QUIT);
        assertThat(MysqlCodec.commandByte(MysqlCodec.encodeQuery("x"))).isEqualTo(MysqlCodec.COM_QUERY);
    }

    @Test
    void testCommandName() {
        assertThat(MysqlCodec.commandName(MysqlCodec.COM_QUERY)).isEqualTo("COM_QUERY");
        assertThat(MysqlCodec.commandName(MysqlCodec.COM_PING)).isEqualTo("COM_PING");
        assertThat(MysqlCodec.commandName(MysqlCodec.COM_QUIT)).isEqualTo("COM_QUIT");
        assertThat(MysqlCodec.commandName(MysqlCodec.COM_STMT_PREPARE)).isEqualTo("COM_STMT_PREPARE");
        assertThat(MysqlCodec.commandName(0x99)).startsWith("UNKNOWN");
    }

    @Test
    void testIsOk() {
        assertThat(MysqlCodec.isOk(OkPacket.ok().encode(CapabilityFlags.CLIENT_PROTOCOL_41))).isTrue();
        assertThat(MysqlCodec.isOk(ErrPacket.error(1, "x").encode(CapabilityFlags.CLIENT_PROTOCOL_41))).isFalse();
    }

    @Test
    void testIsErr() {
        assertThat(MysqlCodec.isErr(ErrPacket.error(1, "x").encode(CapabilityFlags.CLIENT_PROTOCOL_41))).isTrue();
        assertThat(MysqlCodec.isErr(OkPacket.ok().encode(CapabilityFlags.CLIENT_PROTOCOL_41))).isFalse();
    }

    @Test
    void testIsEof() {
        assertThat(MysqlCodec.isEof(EofPacket.eof().encode(CapabilityFlags.CLIENT_PROTOCOL_41))).isTrue();
    }

    @Test
    void testPrepareOk_encodeDecode() {
        var ok = new MysqlCodec.PrepareOk(1, 3, 2, 0);
        var encoded = MysqlCodec.encodePrepareOk(ok);
        var decoded = MysqlCodec.decodePrepareOk(encoded);

        assertThat(decoded.statementId()).isEqualTo(1);
        assertThat(decoded.numColumns()).isEqualTo(3);
        assertThat(decoded.numParams()).isEqualTo(2);
        assertThat(decoded.warningCount()).isEqualTo(0);
    }

    @Test
    void testHandshakeResponse_encodeDecode() {
        int caps = CapabilityFlags.DEFAULT_CLIENT_CAPABILITIES | CapabilityFlags.CLIENT_CONNECT_WITH_DB;
        var attrs = Map.of("_client", "test");
        var payload = MysqlCodec.encodeHandshakeResponse(
                caps, MysqlPacket.MAX_PAYLOAD_SIZE, 45,
                "root", new byte[]{1, 2, 3}, "testdb",
                "mysql_native_password", new LinkedHashMap<>(attrs));

        var response = MysqlCodec.decodeHandshakeResponse(payload);
        assertThat(response.username()).isEqualTo("root");
        assertThat(response.database()).isEqualTo("testdb");
        assertThat(response.authPluginName()).isEqualTo("mysql_native_password");
        assertThat(response.authResponse()).containsExactly(1, 2, 3);
        assertThat(response.charset()).isEqualTo(45);
        assertThat(response.attributes()).containsEntry("_client", "test");
    }

    @Test
    void testExecuteHeader() {
        var header = MysqlCodec.encodeExecuteHeader(42, 0, 1);
        assertThat(header[0]).isEqualTo((byte) MysqlCodec.COM_STMT_EXECUTE);
        assertThat(MysqlCodec.decodeExecuteStatementId(header)).isEqualTo(42);
    }

    @Test
    void testFieldList() {
        var payload = MysqlCodec.encodeFieldList("users", "%");
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_FIELD_LIST);
    }

    @Test
    void testSendLongData() {
        byte[] data = {10, 20, 30};
        var payload = MysqlCodec.encodeSendLongData(1, 0, data);
        assertThat(payload[0]).isEqualTo((byte) MysqlCodec.COM_STMT_SEND_LONG_DATA);
    }
}
