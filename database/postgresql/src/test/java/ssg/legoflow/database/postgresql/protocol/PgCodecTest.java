package ssg.legoflow.database.postgresql.protocol;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link PgCodec}: encode/decode round-trip for all message types.
 */
class PgCodecTest {

    // ======== Frontend message round-trips ========

    @Test
    void testStartupMessageRoundTrip() throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user", "testuser");
        params.put("database", "testdb");
        var msg = new FrontendMessage.StartupMessage(196608, params);

        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.StartupMessage) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), true);

        assertThat(decoded.protocolVersion()).isEqualTo(196608);
        assertThat(decoded.parameters()).containsEntry("user", "testuser");
        assertThat(decoded.parameters()).containsEntry("database", "testdb");
    }

    @Test
    void testSSLRequestRoundTrip() throws IOException {
        var msg = new FrontendMessage.SSLRequest();
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.SSLRequest) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), true);
        assertThat(decoded).isNotNull();
    }

    @Test
    void testCancelRequestRoundTrip() throws IOException {
        var msg = new FrontendMessage.CancelRequest(12345, 67890);
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.CancelRequest) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), true);

        assertThat(decoded.processId()).isEqualTo(12345);
        assertThat(decoded.secretKey()).isEqualTo(67890);
    }

    @Test
    void testPasswordMessageRoundTrip() throws IOException {
        var msg = new FrontendMessage.PasswordMessage("secret123");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.PasswordMessage) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.password()).isEqualTo("secret123");
    }

    @Test
    void testQueryRoundTrip() throws IOException {
        var msg = new FrontendMessage.Query("SELECT * FROM users WHERE id = 1");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Query) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.sql()).isEqualTo("SELECT * FROM users WHERE id = 1");
    }

    @Test
    void testParseRoundTrip() throws IOException {
        var msg = new FrontendMessage.Parse("stmt1", "SELECT $1, $2", new int[]{23, 25});
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Parse) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.statementName()).isEqualTo("stmt1");
        assertThat(decoded.sql()).isEqualTo("SELECT $1, $2");
        assertThat(decoded.parameterTypes()).containsExactly(23, 25);
    }

    @Test
    void testBindRoundTrip() throws IOException {
        byte[][] paramValues = {
                "hello".getBytes(), null, "42".getBytes()
        };
        var msg = new FrontendMessage.Bind("portal1", "stmt1",
                new short[]{0, 0, 0}, paramValues, new short[]{0});
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Bind) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.portalName()).isEqualTo("portal1");
        assertThat(decoded.statementName()).isEqualTo("stmt1");
        assertThat(decoded.parameterFormats()).containsExactly((short) 0, (short) 0, (short) 0);
        assertThat(decoded.parameterValues()[0]).isEqualTo("hello".getBytes());
        assertThat(decoded.parameterValues()[1]).isNull();
        assertThat(decoded.parameterValues()[2]).isEqualTo("42".getBytes());
        assertThat(decoded.resultFormats()).containsExactly((short) 0);
    }

    @Test
    void testDescribeRoundTrip() throws IOException {
        var msg = new FrontendMessage.Describe((byte) 'S', "stmt1");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Describe) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.target()).isEqualTo((byte) 'S');
        assertThat(decoded.name()).isEqualTo("stmt1");
    }

    @Test
    void testExecuteRoundTrip() throws IOException {
        var msg = new FrontendMessage.Execute("portal1", 100);
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Execute) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.portalName()).isEqualTo("portal1");
        assertThat(decoded.maxRows()).isEqualTo(100);
    }

    @Test
    void testSyncRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeFrontend(new FrontendMessage.Sync());
        var decoded = PgCodec.decodeFrontend(new ByteArrayInputStream(encoded), false);
        assertThat(decoded).isInstanceOf(FrontendMessage.Sync.class);
    }

    @Test
    void testFlushRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeFrontend(new FrontendMessage.Flush());
        var decoded = PgCodec.decodeFrontend(new ByteArrayInputStream(encoded), false);
        assertThat(decoded).isInstanceOf(FrontendMessage.Flush.class);
    }

    @Test
    void testCloseRoundTrip() throws IOException {
        var msg = new FrontendMessage.Close((byte) 'P', "portal1");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Close) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.target()).isEqualTo((byte) 'P');
        assertThat(decoded.name()).isEqualTo("portal1");
    }

    @Test
    void testCopyDataFrontendRoundTrip() throws IOException {
        var msg = new FrontendMessage.CopyData("row1\tval1\n".getBytes());
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.CopyData) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.data()).isEqualTo("row1\tval1\n".getBytes());
    }

    @Test
    void testCopyDoneFrontendRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeFrontend(new FrontendMessage.CopyDone());
        var decoded = PgCodec.decodeFrontend(new ByteArrayInputStream(encoded), false);
        assertThat(decoded).isInstanceOf(FrontendMessage.CopyDone.class);
    }

    @Test
    void testCopyFailRoundTrip() throws IOException {
        var msg = new FrontendMessage.CopyFail("data format error");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.CopyFail) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.errorMessage()).isEqualTo("data format error");
    }

    @Test
    void testTerminateRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeFrontend(new FrontendMessage.Terminate());
        var decoded = PgCodec.decodeFrontend(new ByteArrayInputStream(encoded), false);
        assertThat(decoded).isInstanceOf(FrontendMessage.Terminate.class);
    }

    // ======== Backend message round-trips ========

    @Test
    void testAuthenticationOkRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.AuthenticationOk());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.AuthenticationOk.class);
    }

    @Test
    void testAuthCleartextPasswordRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.AuthenticationCleartextPassword());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.AuthenticationCleartextPassword.class);
    }

    @Test
    void testAuthMD5PasswordRoundTrip() throws IOException {
        byte[] salt = {1, 2, 3, 4};
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.AuthenticationMD5Password(salt));
        var decoded = (BackendMessage.AuthenticationMD5Password) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.salt()).isEqualTo(salt);
    }

    @Test
    void testAuthSASLRoundTrip() throws IOException {
        var msg = new BackendMessage.AuthenticationSASL(List.of("SCRAM-SHA-256"));
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.AuthenticationSASL) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.mechanisms()).containsExactly("SCRAM-SHA-256");
    }

    @Test
    void testAuthSASLContinueRoundTrip() throws IOException {
        byte[] data = "r=nonce,s=salt,i=4096".getBytes();
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.AuthenticationSASLContinue(data));
        var decoded = (BackendMessage.AuthenticationSASLContinue) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.data()).isEqualTo(data);
    }

    @Test
    void testAuthSASLFinalRoundTrip() throws IOException {
        byte[] data = "v=signature".getBytes();
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.AuthenticationSASLFinal(data));
        var decoded = (BackendMessage.AuthenticationSASLFinal) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.data()).isEqualTo(data);
    }

    @Test
    void testParameterStatusRoundTrip() throws IOException {
        var msg = new BackendMessage.ParameterStatus("server_version", "16.0");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.ParameterStatus) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.name()).isEqualTo("server_version");
        assertThat(decoded.value()).isEqualTo("16.0");
    }

    @Test
    void testBackendKeyDataRoundTrip() throws IOException {
        var msg = new BackendMessage.BackendKeyData(1234, 5678);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.BackendKeyData) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.processId()).isEqualTo(1234);
        assertThat(decoded.secretKey()).isEqualTo(5678);
    }

    @Test
    void testReadyForQueryRoundTrip() throws IOException {
        for (TransactionStatus status : TransactionStatus.values()) {
            var msg = new BackendMessage.ReadyForQuery(status);
            byte[] encoded = PgCodec.encodeBackend(msg);
            var decoded = (BackendMessage.ReadyForQuery) PgCodec.decodeBackend(
                    new ByteArrayInputStream(encoded));
            assertThat(decoded.status()).isEqualTo(status);
        }
    }

    @Test
    void testRowDescriptionRoundTrip() throws IOException {
        var columns = List.of(
                new BackendMessage.ColumnDescription("id", 16384, (short) 1, 23, (short) 4, -1, (short) 0),
                new BackendMessage.ColumnDescription("name", 16384, (short) 2, 25, (short) -1, -1, (short) 0)
        );
        var msg = new BackendMessage.RowDescription(columns);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.RowDescription) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));

        assertThat(decoded.columns()).hasSize(2);
        assertThat(decoded.columns().get(0).name()).isEqualTo("id");
        assertThat(decoded.columns().get(0).typeOid()).isEqualTo(23);
        assertThat(decoded.columns().get(1).name()).isEqualTo("name");
        assertThat(decoded.columns().get(1).typeOid()).isEqualTo(25);
    }

    @Test
    void testDataRowRoundTrip() throws IOException {
        byte[][] values = {"1".getBytes(), "Alice".getBytes(), null};
        var msg = new BackendMessage.DataRow(values);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.DataRow) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));

        assertThat(decoded.values().length).isEqualTo(3);
        assertThat(decoded.values()[0]).isEqualTo("1".getBytes());
        assertThat(decoded.values()[1]).isEqualTo("Alice".getBytes());
        assertThat(decoded.values()[2]).isNull();
    }

    @Test
    void testCommandCompleteRoundTrip() throws IOException {
        var msg = new BackendMessage.CommandComplete("SELECT 5");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CommandComplete) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.tag()).isEqualTo("SELECT 5");
    }

    @Test
    void testEmptyQueryResponseRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.EmptyQueryResponse());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.EmptyQueryResponse.class);
    }

    @Test
    void testParseCompleteRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.ParseComplete());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.ParseComplete.class);
    }

    @Test
    void testBindCompleteRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.BindComplete());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.BindComplete.class);
    }

    @Test
    void testCloseCompleteRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.CloseComplete());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.CloseComplete.class);
    }

    @Test
    void testNoDataRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.NoData());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.NoData.class);
    }

    @Test
    void testParameterDescriptionRoundTrip() throws IOException {
        var msg = new BackendMessage.ParameterDescription(new int[]{23, 25, 16});
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.ParameterDescription) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.parameterOids()).containsExactly(23, 25, 16);
    }

    @Test
    void testPortalSuspendedRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.PortalSuspended());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.PortalSuspended.class);
    }

    @Test
    void testCopyInResponseRoundTrip() throws IOException {
        var msg = new BackendMessage.CopyInResponse((byte) 0, new short[]{0, 0});
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CopyInResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.overallFormat()).isEqualTo((byte) 0);
        assertThat(decoded.columnFormats()).containsExactly((short) 0, (short) 0);
    }

    @Test
    void testCopyOutResponseRoundTrip() throws IOException {
        var msg = new BackendMessage.CopyOutResponse((byte) 0, new short[]{0});
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CopyOutResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.overallFormat()).isEqualTo((byte) 0);
        assertThat(decoded.columnFormats()).containsExactly((short) 0);
    }

    @Test
    void testCopyBothResponseRoundTrip() throws IOException {
        var msg = new BackendMessage.CopyBothResponse((byte) 1, new short[]{1, 1});
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CopyBothResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.overallFormat()).isEqualTo((byte) 1);
        assertThat(decoded.columnFormats()).containsExactly((short) 1, (short) 1);
    }

    @Test
    void testCopyDataBackendRoundTrip() throws IOException {
        byte[] data = "data chunk".getBytes();
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.CopyData(data));
        var decoded = (BackendMessage.CopyData) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.data()).isEqualTo(data);
    }

    @Test
    void testCopyDoneBackendRoundTrip() throws IOException {
        byte[] encoded = PgCodec.encodeBackend(new BackendMessage.CopyDone());
        var decoded = PgCodec.decodeBackend(new ByteArrayInputStream(encoded));
        assertThat(decoded).isInstanceOf(BackendMessage.CopyDone.class);
    }

    @Test
    void testNotificationResponseRoundTrip() throws IOException {
        var msg = new BackendMessage.NotificationResponse(1234, "my_channel", "hello world");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.NotificationResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.processId()).isEqualTo(1234);
        assertThat(decoded.channel()).isEqualTo("my_channel");
        assertThat(decoded.payload()).isEqualTo("hello world");
    }

    @Test
    void testErrorResponseRoundTrip() throws IOException {
        Map<Byte, String> fields = new LinkedHashMap<>();
        fields.put((byte) 'S', "ERROR");
        fields.put((byte) 'C', "42P01");
        fields.put((byte) 'M', "relation does not exist");
        fields.put((byte) 'D', "Table \"foo\" not found");
        fields.put((byte) 'H', "Create the table first");

        var msg = new BackendMessage.ErrorResponse(fields);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.ErrorResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));

        assertThat(decoded.severity()).isEqualTo("ERROR");
        assertThat(decoded.sqlState()).isEqualTo("42P01");
        assertThat(decoded.message()).isEqualTo("relation does not exist");
        assertThat(decoded.detail()).isEqualTo("Table \"foo\" not found");
        assertThat(decoded.hint()).isEqualTo("Create the table first");
    }

    @Test
    void testNoticeResponseRoundTrip() throws IOException {
        Map<Byte, String> fields = new LinkedHashMap<>();
        fields.put((byte) 'S', "NOTICE");
        fields.put((byte) 'C', "00000");
        fields.put((byte) 'M', "Table created successfully");

        var msg = new BackendMessage.NoticeResponse(fields);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.NoticeResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));

        assertThat(decoded.severity()).isEqualTo("NOTICE");
        assertThat(decoded.sqlState()).isEqualTo("00000");
        assertThat(decoded.message()).isEqualTo("Table created successfully");
    }

    // ======== Edge cases ========

    @Test
    void testEmptyParseParameters() throws IOException {
        var msg = new FrontendMessage.Parse("", "SELECT 1", new int[0]);
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Parse) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.statementName()).isEmpty();
        assertThat(decoded.sql()).isEqualTo("SELECT 1");
        assertThat(decoded.parameterTypes()).isEmpty();
    }

    @Test
    void testBindWithEmptyFormats() throws IOException {
        var msg = new FrontendMessage.Bind("", "", new short[0], new byte[0][], new short[0]);
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Bind) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.portalName()).isEmpty();
        assertThat(decoded.parameterFormats()).isEmpty();
        assertThat(decoded.parameterValues()).isEmpty();
        assertThat(decoded.resultFormats()).isEmpty();
    }

    @Test
    void testMultipleSASLMechanisms() throws IOException {
        var msg = new BackendMessage.AuthenticationSASL(
                List.of("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS"));
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.AuthenticationSASL) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.mechanisms()).containsExactly("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS");
    }

    @Test
    void testEmptyDataRow() throws IOException {
        var msg = new BackendMessage.DataRow(new byte[0][]);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.DataRow) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.values()).isEmpty();
    }

    @Test
    void testCommandCompleteInsert() throws IOException {
        var msg = new BackendMessage.CommandComplete("INSERT 0 1");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CommandComplete) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.tag()).isEqualTo("INSERT 0 1");
    }

    @Test
    void testCommandCompleteUpdate() throws IOException {
        var msg = new BackendMessage.CommandComplete("UPDATE 42");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CommandComplete) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.tag()).isEqualTo("UPDATE 42");
    }

    @Test
    void testCommandCompleteDelete() throws IOException {
        var msg = new BackendMessage.CommandComplete("DELETE 7");
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.CommandComplete) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.tag()).isEqualTo("DELETE 7");
    }

    @Test
    void testStartupMessageProtocolVersion() throws IOException {
        var msg = new FrontendMessage.StartupMessage(196608, Map.of("user", "test"));
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.StartupMessage) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), true);
        assertThat(decoded.protocolVersion()).isEqualTo(FrontendMessage.StartupMessage.PROTOCOL_VERSION_30);
    }

    @Test
    void testErrorResponseWithPosition() throws IOException {
        Map<Byte, String> fields = new LinkedHashMap<>();
        fields.put((byte) 'S', "ERROR");
        fields.put((byte) 'C', "42601");
        fields.put((byte) 'M', "syntax error");
        fields.put((byte) 'P', "15");

        var msg = new BackendMessage.ErrorResponse(fields);
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.ErrorResponse) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.position()).isEqualTo("15");
    }

    @Test
    void testDescribePortal() throws IOException {
        var msg = new FrontendMessage.Describe((byte) 'P', "portal1");
        byte[] encoded = PgCodec.encodeFrontend(msg);
        var decoded = (FrontendMessage.Describe) PgCodec.decodeFrontend(
                new ByteArrayInputStream(encoded), false);

        assertThat(decoded.target()).isEqualTo((byte) 'P');
        assertThat(decoded.name()).isEqualTo("portal1");
    }

    @Test
    void testEndOfStreamThrows() {
        assertThatThrownBy(() -> PgCodec.decodeBackend(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testRowDescriptionEmptyColumns() throws IOException {
        var msg = new BackendMessage.RowDescription(List.of());
        byte[] encoded = PgCodec.encodeBackend(msg);
        var decoded = (BackendMessage.RowDescription) PgCodec.decodeBackend(
                new ByteArrayInputStream(encoded));
        assertThat(decoded.columns()).isEmpty();
    }
}
