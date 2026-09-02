package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.protocol.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link FtpCommandHandler}.
 */
class FtpCommandHandlerTest {

    private FtpSession session;
    private InMemoryFileSystem fs;
    private FtpCommandHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        session = new FtpSession("test-client");
        fs = new InMemoryFileSystem();
        fs.createDirectory("/home");
        fs.putFile("/home/test.txt", "Hello".getBytes());
        var config = FtpServerConfig.builder().build();
        handler = new FtpCommandHandler(session, fs, FtpAuthenticator.singleUser("user", "pass"), config);
    }

    @Test
    void testUserCommand() {
        var reply = handler.handle(FtpCommand.USER, "user", null);
        assertThat(reply.code()).isEqualTo(331);
        assertThat(session.state()).isEqualTo(FtpSession.State.USER_PROVIDED);
    }

    @Test
    void testPassCommandSuccess() {
        handler.handle(FtpCommand.USER, "user", null);
        var reply = handler.handle(FtpCommand.PASS, "pass", null);
        assertThat(reply.code()).isEqualTo(230);
        assertThat(session.isAuthenticated()).isTrue();
    }

    @Test
    void testPassCommandFailure() {
        handler.handle(FtpCommand.USER, "user", null);
        var reply = handler.handle(FtpCommand.PASS, "wrongpass", null);
        assertThat(reply.code()).isEqualTo(530);
        assertThat(session.isAuthenticated()).isFalse();
    }

    @Test
    void testPassWithoutUserFails() {
        var reply = handler.handle(FtpCommand.PASS, "pass", null);
        assertThat(reply.code()).isEqualTo(503);
    }

    @Test
    void testQuit() {
        var reply = handler.handle(FtpCommand.QUIT, null, null);
        assertThat(reply.code()).isEqualTo(221);
    }

    @Test
    void testSyst() {
        var reply = handler.handle(FtpCommand.SYST, null, null);
        assertThat(reply.code()).isEqualTo(215);
        assertThat(reply.text()).contains("UNIX");
    }

    @Test
    void testFeat() {
        var reply = handler.handle(FtpCommand.FEAT, null, null);
        assertThat(reply.code()).isEqualTo(211);
        assertThat(reply.isMultiLine()).isTrue();
    }

    @Test
    void testHelp() {
        var reply = handler.handle(FtpCommand.HELP, null, null);
        assertThat(reply.code()).isEqualTo(214);
    }

    @Test
    void testNoop() {
        var reply = handler.handle(FtpCommand.NOOP, null, null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testCommandRequiresAuth() {
        var reply = handler.handle(FtpCommand.PWD, null, null);
        assertThat(reply.code()).isEqualTo(530);
    }

    @Test
    void testPwdAfterAuth() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.PWD, null, null);
        assertThat(reply.code()).isEqualTo(257);
        assertThat(reply.text()).contains("/");
    }

    @Test
    void testCwd() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.CWD, "/home", null);
        assertThat(reply.code()).isEqualTo(250);
        assertThat(session.currentDirectory()).isEqualTo("/home");
    }

    @Test
    void testCwdNonExistent() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.CWD, "/nonexistent", null);
        assertThat(reply.code()).isEqualTo(550);
    }

    @Test
    void testCdup() {
        authenticateSession();
        session.setCurrentDirectory("/home");
        var reply = handler.handle(FtpCommand.CDUP, null, null);
        assertThat(reply.code()).isEqualTo(250);
        assertThat(session.currentDirectory()).isEqualTo("/");
    }

    @Test
    void testMkd() throws Exception {
        authenticateSession();
        var reply = handler.handle(FtpCommand.MKD, "/newdir", null);
        assertThat(reply.code()).isEqualTo(257);
        assertThat(fs.exists("/newdir")).isTrue();
    }

    @Test
    void testRmd() throws IOException {
        authenticateSession();
        fs.createDirectory("/emptydir");
        var reply = handler.handle(FtpCommand.RMD, "/emptydir", null);
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testDele() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.DELE, "/home/test.txt", null);
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testRnfrRnto() {
        authenticateSession();
        var rnfrReply = handler.handle(FtpCommand.RNFR, "/home/test.txt", null);
        assertThat(rnfrReply.code()).isEqualTo(350);
        var rntoReply = handler.handle(FtpCommand.RNTO, "/home/renamed.txt", null);
        assertThat(rntoReply.code()).isEqualTo(250);
    }

    @Test
    void testRntoWithoutRnfr() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.RNTO, "/home/renamed.txt", null);
        assertThat(reply.code()).isEqualTo(503);
    }

    @Test
    void testTypeAscii() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.TYPE, "A", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(session.transferType()).isEqualTo(FtpTransferType.ASCII);
    }

    @Test
    void testTypeBinary() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.TYPE, "I", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(session.transferType()).isEqualTo(FtpTransferType.BINARY);
    }

    @Test
    void testTypeInvalid() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.TYPE, "Z", null);
        assertThat(reply.code()).isEqualTo(504);
    }

    @Test
    void testSize() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.SIZE, "/home/test.txt", null);
        assertThat(reply.code()).isEqualTo(213);
        assertThat(reply.text()).isEqualTo("5");
    }

    @Test
    void testMdtm() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.MDTM, "/home/test.txt", null);
        assertThat(reply.code()).isEqualTo(213);
        assertThat(reply.text()).hasSize(14); // YYYYMMDDHHMMSS
    }

    @Test
    void testStruFile() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.STRU, "F", null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testStruNonFile() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.STRU, "R", null);
        assertThat(reply.code()).isEqualTo(504);
    }

    @Test
    void testModeStream() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.MODE, "S", null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testModeNonStream() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.MODE, "B", null);
        assertThat(reply.code()).isEqualTo(504);
    }

    @Test
    void testOptsUtf8() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "UTF8 ON", null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testRein() {
        authenticateSession();
        session.setCurrentDirectory("/home");
        var reply = handler.handle(FtpCommand.REIN, null, null);
        assertThat(reply.code()).isEqualTo(220);
        assertThat(session.isAuthenticated()).isFalse();
        assertThat(session.currentDirectory()).isEqualTo("/");
    }

    @Test
    void testAlloNoOp() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.ALLO, "1024", null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testPbsz() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.PBSZ, "0", null);
        assertThat(reply.code()).isEqualTo(200);
    }

    @Test
    void testProtP() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.PROT, "P", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(session.isDataProtected()).isTrue();
    }

    @Test
    void testProtC() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.PROT, "C", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(session.isDataProtected()).isFalse();
    }

    @Test
    void testGenerateListOutput() throws IOException {
        authenticateSession();
        String output = handler.generateListOutput("/home");
        assertThat(output).contains("test.txt");
    }

    @Test
    void testGenerateNlstOutput() throws IOException {
        authenticateSession();
        String output = handler.generateNlstOutput("/home");
        assertThat(output).contains("test.txt");
    }

    @Test
    void testGenerateMlsdOutput() throws IOException {
        authenticateSession();
        String output = handler.generateMlsdOutput("/home");
        assertThat(output).contains("test.txt");
        assertThat(output).contains("type=file");
    }

    @Test
    void testRestSetsRestartOffset() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.REST, "1024", null);
        assertThat(reply.code()).isEqualTo(350);
        assertThat(session.restartOffset()).isEqualTo(1024);
    }

    @Test
    void testRestInvalidOffset() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.REST, "abc", null);
        assertThat(reply.code()).isEqualTo(501);
    }

    @Test
    void testRestNegativeOffset() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.REST, "-5", null);
        assertThat(reply.code()).isEqualTo(501);
    }

    @Test
    void testRestNullArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.REST, null, null);
        assertThat(reply.code()).isEqualTo(501);
    }

    @Test
    void testRestOffsetConsumedAfterRetr() {
        authenticateSession();
        handler.handle(FtpCommand.REST, "100", null);
        assertThat(session.restartOffset()).isEqualTo(100);
        // Simulate what performRetrieve does: consumes the offset
        long consumed = session.consumeRestartOffset();
        assertThat(consumed).isEqualTo(100);
        assertThat(session.restartOffset()).isEqualTo(0);
    }

    @Test
    void testCccWithoutTls() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.CCC, null, null);
        assertThat(reply.code()).isEqualTo(502);
    }

    @Test
    void testCccWithTls() {
        authenticateSession();
        session.setTlsEnabled(true);
        session.setDataProtected(true);
        var reply = handler.handle(FtpCommand.CCC, null, null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(session.isTlsEnabled()).isFalse();
        assertThat(session.isCccIssued()).isTrue();
        // Data channel protection should remain
        assertThat(session.isDataProtected()).isTrue();
    }

    // ---- ACCT tests (RFC 959 §4.1.1) ----

    @Test
    void testAcctStoresAccountInfo() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.ACCT, "myaccount", null);
        assertThat(reply.code()).isEqualTo(230);
        assertThat(session.account()).isEqualTo("myaccount");
    }

    @Test
    void testAcctNullArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.ACCT, null, null);
        assertThat(reply.code()).isEqualTo(230);
        assertThat(session.account()).isNull();
    }

    @Test
    void testAcctClearedByRein() {
        authenticateSession();
        handler.handle(FtpCommand.ACCT, "myaccount", null);
        assertThat(session.account()).isEqualTo("myaccount");
        handler.handle(FtpCommand.REIN, null, null);
        assertThat(session.account()).isNull();
    }

    @Test
    void testAcctRequiresAuth() {
        var reply = handler.handle(FtpCommand.ACCT, "myaccount", null);
        assertThat(reply.code()).isEqualTo(530);
    }

    // ---- SMNT tests (RFC 959 §4.1.1) ----

    @Test
    void testSmntRootMountPoint() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.SMNT, "/", null);
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testSmntNullArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.SMNT, null, null);
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testSmntEmptyArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.SMNT, "", null);
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testSmntInvalidMountPoint() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.SMNT, "/other", null);
        assertThat(reply.code()).isEqualTo(550);
    }

    @Test
    void testSmntRequiresAuth() {
        var reply = handler.handle(FtpCommand.SMNT, "/", null);
        assertThat(reply.code()).isEqualTo(530);
    }

    // ---- OPTS extensibility tests (RFC 2389 §4) ----

    @Test
    void testOptsMlstAllFacts() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "MLST type;size;modify;perm;", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(reply.text()).contains("MLST OPTS");
        assertThat(reply.text()).contains("type");
        assertThat(reply.text()).contains("size");
    }

    @Test
    void testOptsMlstSubsetFacts() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "MLST type;size;", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(reply.text()).contains("type");
        assertThat(reply.text()).contains("size");
    }

    @Test
    void testOptsMlstNoArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "MLST", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(reply.text()).contains("type;size;modify;perm;");
    }

    @Test
    void testOptsMlstIgnoresUnknownFacts() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "MLST type;unknown;size;", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(reply.text()).contains("type");
        assertThat(reply.text()).contains("size");
        assertThat(reply.text()).doesNotContain("unknown");
    }

    @Test
    void testOptsNullArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, null, null);
        assertThat(reply.code()).isEqualTo(501);
    }

    @Test
    void testOptsBlankArgument() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "  ", null);
        assertThat(reply.code()).isEqualTo(501);
    }

    @Test
    void testOptsUnknownOption() {
        authenticateSession();
        var reply = handler.handle(FtpCommand.OPTS, "BOGUS ON", null);
        assertThat(reply.code()).isEqualTo(504);
    }

    @Test
    void testOptsRegisterCustomOption() {
        authenticateSession();
        handler.registerOption("CUSTOM", arg ->
                FtpReply.of(FtpReplyCode.COMMAND_OK, "CUSTOM accepted: " + arg));
        var reply = handler.handle(FtpCommand.OPTS, "CUSTOM myarg", null);
        assertThat(reply.code()).isEqualTo(200);
        assertThat(reply.text()).contains("CUSTOM accepted: myarg");
    }

    @Test
    void testOptsUnregisterOption() {
        authenticateSession();
        handler.registerOption("TEMP", arg -> FtpReply.of(FtpReplyCode.COMMAND_OK, "TEMP ok"));
        assertThat(handler.registeredOptions()).contains("TEMP");
        handler.unregisterOption("TEMP");
        var reply = handler.handle(FtpCommand.OPTS, "TEMP", null);
        assertThat(reply.code()).isEqualTo(504);
    }

    @Test
    void testOptsRegisteredOptionsIncludesDefaults() {
        assertThat(handler.registeredOptions()).contains("UTF8", "MLST");
    }

    private void authenticateSession() {
        handler.handle(FtpCommand.USER, "user", null);
        handler.handle(FtpCommand.PASS, "pass", null);
    }
}
