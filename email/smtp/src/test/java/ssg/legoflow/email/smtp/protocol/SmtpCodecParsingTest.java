package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Additional tests for {@link SmtpCodec} parsing methods and edge cases.
 */
class SmtpCodecParsingTest {

    // --- parseMailFromAddress ---

    @Test
    void testParseMailFromAddress() {
        var addr = SmtpCodec.parseMailFromAddress("FROM:<user@example.com> SIZE=1024");
        assertThat(addr).isEqualTo("user@example.com");
    }

    @Test
    void testParseMailFromAddressEmpty() {
        var addr = SmtpCodec.parseMailFromAddress("FROM:<>");
        assertThat(addr).isEmpty();
    }

    // --- parseRcptToAddress ---

    @Test
    void testParseRcptToAddress() {
        var addr = SmtpCodec.parseRcptToAddress("TO:<dest@example.com> NOTIFY=SUCCESS,FAILURE");
        assertThat(addr).isEqualTo("dest@example.com");
    }

    // --- parseExtensionParams ---

    @Test
    void testParseExtensionParamsWithParams() {
        var params = SmtpCodec.parseExtensionParams("FROM:<user@example.com> SIZE=1024 BODY=8BITMIME");
        assertThat(params).isEqualTo("SIZE=1024 BODY=8BITMIME");
    }

    @Test
    void testParseExtensionParamsNull() {
        var params = SmtpCodec.parseExtensionParams(null);
        assertThat(params).isEmpty();
    }

    @Test
    void testParseExtensionParamsNoExtensions() {
        var params = SmtpCodec.parseExtensionParams("TO:<user@example.com>");
        assertThat(params).isEmpty();
    }

    // --- parseBdatParams ---

    @Test
    void testParseBdatParamsSizeOnly() {
        var result = SmtpCodec.parseBdatParams("1024");
        assertThat(result[0]).isEqualTo("1024");
        assertThat(result[1]).isNull();
    }

    @Test
    void testParseBdatParamsWithLast() {
        var result = SmtpCodec.parseBdatParams("2048 LAST");
        assertThat(result[0]).isEqualTo("2048");
        assertThat(result[1]).isEqualTo("LAST");
    }

    @Test
    void testParseBdatParamsNullThrows() {
        assertThatThrownBy(() -> SmtpCodec.parseBdatParams(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseBdatParamsBlankThrows() {
        assertThatThrownBy(() -> SmtpCodec.parseBdatParams("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- readLine edge cases ---

    @Test
    void testReadLineCRLF() throws IOException {
        var reader = new BufferedReader(new StringReader("test line\r\n"));
        assertThat(SmtpCodec.readLine(reader)).isEqualTo("test line");
    }

    @Test
    void testReadLineLFOnly() throws IOException {
        var reader = new BufferedReader(new StringReader("test line\n"));
        assertThat(SmtpCodec.readLine(reader)).isEqualTo("test line");
    }

    @Test
    void testReadLineEmptyStream() throws IOException {
        var reader = new BufferedReader(new StringReader(""));
        assertThat(SmtpCodec.readLine(reader)).isNull();
    }

    // --- encodeReply with enhanced status code ---

    @Test
    void testEncodeReplyWithEnhancedCode() {
        var reply = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "Requested mail action okay");
        var encoded = SmtpCodec.encodeReply(reply);
        assertThat(encoded).contains("2.0.0");
        assertThat(encoded.endsWith("\r\n")).isTrue();
    }

    // --- decodeCommand edge cases ---

    @Test
    void testDecodeCommandBlankThrows() {
        assertThatThrownBy(() -> SmtpCodec.decodeCommand(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeCommandNullThrows() {
        assertThatThrownBy(() -> SmtpCodec.decodeCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeCommandSingleWordNoSpace() {
        var result = SmtpCodec.decodeCommand("RSET");
        assertThat(result[0]).isEqualTo("RSET");
        assertThat(result[1]).isNull();
    }

    // --- encodeReply multi-line reply ---

    @Test
    void testEncodeReplyMultiLineWithDash() {
        var lines = java.util.List.of("First line", "Second line with details", "Final line OK");
        var reply = SmtpReply.ofLines(250, lines);
        var encoded = SmtpCodec.encodeReply(reply);
        // Middle lines should have dash before code
        assertThat(encoded).contains("250-First line");
        assertThat(encoded).contains("250-Second line with details");
        assertThat(encoded).contains("250 Final line OK");
    }

    // --- encodeCommand null check ---

    @Test
    void testEncodeReplyNullThrows() {
        assertThatThrownBy(() -> SmtpCodec.encodeReply(null))
                .isInstanceOf(NullPointerException.class);
    }
}
