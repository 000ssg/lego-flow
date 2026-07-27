package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpCommand}.
 */
class FtpCommandTest {

    @Test
    void testParseUpperCase() {
        assertThat(FtpCommand.parse("USER")).isEqualTo(FtpCommand.USER);
        assertThat(FtpCommand.parse("PASS")).isEqualTo(FtpCommand.PASS);
        assertThat(FtpCommand.parse("QUIT")).isEqualTo(FtpCommand.QUIT);
    }

    @Test
    void testParseLowerCase() {
        assertThat(FtpCommand.parse("user")).isEqualTo(FtpCommand.USER);
        assertThat(FtpCommand.parse("list")).isEqualTo(FtpCommand.LIST);
        assertThat(FtpCommand.parse("retr")).isEqualTo(FtpCommand.RETR);
    }

    @Test
    void testParseMixedCase() {
        assertThat(FtpCommand.parse("User")).isEqualTo(FtpCommand.USER);
        assertThat(FtpCommand.parse("PaSv")).isEqualTo(FtpCommand.PASV);
    }

    @Test
    void testParseWithWhitespace() {
        assertThat(FtpCommand.parse("  USER  ")).isEqualTo(FtpCommand.USER);
    }

    @Test
    void testParseNullThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpCommand.parse(null));
    }

    @Test
    void testParseBlankThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpCommand.parse("  "));
    }

    @Test
    void testParseUnknownThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpCommand.parse("BOGUS"));
    }

    @Test
    void testWireForm() {
        assertThat(FtpCommand.USER.wireForm()).isEqualTo("USER");
        assertThat(FtpCommand.PASV.wireForm()).isEqualTo("PASV");
        assertThat(FtpCommand.AUTH.wireForm()).isEqualTo("AUTH");
    }

    @Test
    void testAllRfc959Commands() {
        // Core RFC 959 commands
        assertThat(FtpCommand.valueOf("USER")).isNotNull();
        assertThat(FtpCommand.valueOf("PASS")).isNotNull();
        assertThat(FtpCommand.valueOf("ACCT")).isNotNull();
        assertThat(FtpCommand.valueOf("CWD")).isNotNull();
        assertThat(FtpCommand.valueOf("CDUP")).isNotNull();
        assertThat(FtpCommand.valueOf("SMNT")).isNotNull();
        assertThat(FtpCommand.valueOf("QUIT")).isNotNull();
        assertThat(FtpCommand.valueOf("REIN")).isNotNull();
        assertThat(FtpCommand.valueOf("PORT")).isNotNull();
        assertThat(FtpCommand.valueOf("PASV")).isNotNull();
        assertThat(FtpCommand.valueOf("TYPE")).isNotNull();
        assertThat(FtpCommand.valueOf("STRU")).isNotNull();
        assertThat(FtpCommand.valueOf("MODE")).isNotNull();
        assertThat(FtpCommand.valueOf("RETR")).isNotNull();
        assertThat(FtpCommand.valueOf("STOR")).isNotNull();
        assertThat(FtpCommand.valueOf("STOU")).isNotNull();
        assertThat(FtpCommand.valueOf("APPE")).isNotNull();
        assertThat(FtpCommand.valueOf("ALLO")).isNotNull();
        assertThat(FtpCommand.valueOf("REST")).isNotNull();
        assertThat(FtpCommand.valueOf("RNFR")).isNotNull();
        assertThat(FtpCommand.valueOf("RNTO")).isNotNull();
        assertThat(FtpCommand.valueOf("DELE")).isNotNull();
        assertThat(FtpCommand.valueOf("RMD")).isNotNull();
        assertThat(FtpCommand.valueOf("MKD")).isNotNull();
        assertThat(FtpCommand.valueOf("PWD")).isNotNull();
        assertThat(FtpCommand.valueOf("LIST")).isNotNull();
        assertThat(FtpCommand.valueOf("NLST")).isNotNull();
        assertThat(FtpCommand.valueOf("SITE")).isNotNull();
        assertThat(FtpCommand.valueOf("SYST")).isNotNull();
        assertThat(FtpCommand.valueOf("STAT")).isNotNull();
        assertThat(FtpCommand.valueOf("HELP")).isNotNull();
        assertThat(FtpCommand.valueOf("NOOP")).isNotNull();
    }

    @Test
    void testExtensionCommands() {
        assertThat(FtpCommand.valueOf("FEAT")).isNotNull();
        assertThat(FtpCommand.valueOf("OPTS")).isNotNull();
        assertThat(FtpCommand.valueOf("SIZE")).isNotNull();
        assertThat(FtpCommand.valueOf("MDTM")).isNotNull();
        assertThat(FtpCommand.valueOf("MLST")).isNotNull();
        assertThat(FtpCommand.valueOf("MLSD")).isNotNull();
        assertThat(FtpCommand.valueOf("EPRT")).isNotNull();
        assertThat(FtpCommand.valueOf("EPSV")).isNotNull();
    }

    @Test
    void testTlsCommands() {
        assertThat(FtpCommand.valueOf("AUTH")).isNotNull();
        assertThat(FtpCommand.valueOf("PBSZ")).isNotNull();
        assertThat(FtpCommand.valueOf("PROT")).isNotNull();
        assertThat(FtpCommand.valueOf("CCC")).isNotNull();
    }

    @Test
    void testTotalCommandCount() {
        // Ensure we have all expected commands
        assertThat(FtpCommand.values().length).isGreaterThanOrEqualTo(40);
    }

    @Test
    void testParseRoundTrip() {
        for (FtpCommand cmd : FtpCommand.values()) {
            assertThat(FtpCommand.parse(cmd.wireForm())).isEqualTo(cmd);
        }
    }
}
