package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SmtpCodec}.
 */
class SmtpCodecTest {

    // --- Command encoding ---

    @Test
    void testEncodeCommandNoParams() {
        assertThat(SmtpCodec.encodeCommand(SmtpCommand.QUIT)).isEqualTo("QUIT\r\n");
    }

    @Test
    void testEncodeCommandWithParams() {
        assertThat(SmtpCodec.encodeCommand(SmtpCommand.EHLO, "client.example.com"))
                .isEqualTo("EHLO client.example.com\r\n");
    }

    @Test
    void testEncodeCommandNullParams() {
        assertThat(SmtpCodec.encodeCommand(SmtpCommand.NOOP, null)).isEqualTo("NOOP\r\n");
    }

    @Test
    void testEncodeCommandEmptyParams() {
        assertThat(SmtpCodec.encodeCommand(SmtpCommand.RSET, "")).isEqualTo("RSET\r\n");
    }

    @Test
    void testEncodeMailFrom() {
        assertThat(SmtpCodec.encodeMailFrom("user@example.com", null))
                .isEqualTo("MAIL FROM:<user@example.com>\r\n");
    }

    @Test
    void testEncodeMailFromWithParams() {
        assertThat(SmtpCodec.encodeMailFrom("user@example.com", "SIZE=1024 BODY=8BITMIME"))
                .isEqualTo("MAIL FROM:<user@example.com> SIZE=1024 BODY=8BITMIME\r\n");
    }

    @Test
    void testEncodeMailFromEmptySender() {
        assertThat(SmtpCodec.encodeMailFrom(null, null))
                .isEqualTo("MAIL FROM:<>\r\n");
    }

    @Test
    void testEncodeRcptTo() {
        assertThat(SmtpCodec.encodeRcptTo("user@example.com", null))
                .isEqualTo("RCPT TO:<user@example.com>\r\n");
    }

    @Test
    void testEncodeRcptToWithParams() {
        assertThat(SmtpCodec.encodeRcptTo("user@example.com", "NOTIFY=SUCCESS,FAILURE"))
                .isEqualTo("RCPT TO:<user@example.com> NOTIFY=SUCCESS,FAILURE\r\n");
    }

    @Test
    void testEncodeBdat() {
        assertThat(SmtpCodec.encodeBdat(1024, false)).isEqualTo("BDAT 1024\r\n");
        assertThat(SmtpCodec.encodeBdat(512, true)).isEqualTo("BDAT 512 LAST\r\n");
    }

    // --- Command decoding ---

    @Test
    void testDecodeCommandNoArgs() {
        String[] result = SmtpCodec.decodeCommand("QUIT");
        assertThat(result[0]).isEqualTo("QUIT");
        assertThat(result[1]).isNull();
    }

    @Test
    void testDecodeCommandWithArgs() {
        String[] result = SmtpCodec.decodeCommand("EHLO client.example.com");
        assertThat(result[0]).isEqualTo("EHLO");
        assertThat(result[1]).isEqualTo("client.example.com");
    }

    @Test
    void testDecodeCommandCaseInsensitive() {
        String[] result = SmtpCodec.decodeCommand("ehlo client");
        assertThat(result[0]).isEqualTo("EHLO");
    }

    @Test
    void testDecodeCommandMailFrom() {
        String[] result = SmtpCodec.decodeCommand("MAIL FROM:<user@example.com> SIZE=1024");
        assertThat(result[0]).isEqualTo("MAIL");
        assertThat(result[1]).isEqualTo("FROM:<user@example.com> SIZE=1024");
    }

    @Test
    void testDecodeCommandNull() {
        assertThatThrownBy(() -> SmtpCodec.decodeCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeCommandBlank() {
        assertThatThrownBy(() -> SmtpCodec.decodeCommand(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Address parsing ---

    @Test
    void testParseMailFromAddress() {
        assertThat(SmtpCodec.parseMailFromAddress("FROM:<user@example.com>"))
                .isEqualTo("user@example.com");
    }

    @Test
    void testParseMailFromAddressCaseInsensitive() {
        assertThat(SmtpCodec.parseMailFromAddress("from:<user@example.com>"))
                .isEqualTo("user@example.com");
    }

    @Test
    void testParseMailFromEmptyAddress() {
        assertThat(SmtpCodec.parseMailFromAddress("FROM:<>")).isEmpty();
    }

    @Test
    void testParseMailFromWithExtParams() {
        assertThat(SmtpCodec.parseMailFromAddress("FROM:<user@example.com> SIZE=1024"))
                .isEqualTo("user@example.com");
    }

    @Test
    void testParseRcptToAddress() {
        assertThat(SmtpCodec.parseRcptToAddress("TO:<bob@example.com>"))
                .isEqualTo("bob@example.com");
    }

    @Test
    void testParseRcptToAddressCaseInsensitive() {
        assertThat(SmtpCodec.parseRcptToAddress("To:<bob@example.com>"))
                .isEqualTo("bob@example.com");
    }

    @Test
    void testParseExtensionParams() {
        assertThat(SmtpCodec.parseExtensionParams("FROM:<user@example.com> SIZE=1024 BODY=8BITMIME"))
                .isEqualTo("SIZE=1024 BODY=8BITMIME");
    }

    @Test
    void testParseExtensionParamsNoParams() {
        assertThat(SmtpCodec.parseExtensionParams("FROM:<user@example.com>")).isEmpty();
    }

    @Test
    void testParseExtensionParamsNull() {
        assertThat(SmtpCodec.parseExtensionParams(null)).isEmpty();
    }

    // --- BDAT parsing ---

    @Test
    void testParseBdatParams() {
        String[] result = SmtpCodec.parseBdatParams("1024");
        assertThat(result[0]).isEqualTo("1024");
        assertThat(result[1]).isNull();
    }

    @Test
    void testParseBdatParamsLast() {
        String[] result = SmtpCodec.parseBdatParams("512 LAST");
        assertThat(result[0]).isEqualTo("512");
        assertThat(result[1]).isEqualTo("LAST");
    }

    @Test
    void testParseBdatParamsNull() {
        assertThatThrownBy(() -> SmtpCodec.parseBdatParams(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Reply encoding ---

    @Test
    void testEncodeReplySingleLine() {
        var reply = SmtpReply.of(250, "OK");
        assertThat(SmtpCodec.encodeReply(reply)).isEqualTo("250 OK\r\n");
    }

    @Test
    void testEncodeReplyWithEnhanced() {
        var reply = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        assertThat(SmtpCodec.encodeReply(reply)).isEqualTo("250 2.0.0 OK\r\n");
    }

    @Test
    void testEncodeReplyMultiLine() {
        var reply = SmtpReply.ofLines(250, java.util.List.of("mail.example.com", "SIZE 10485760", "OK"));
        String encoded = SmtpCodec.encodeReply(reply);
        assertThat(encoded).isEqualTo(
                "250-mail.example.com\r\n" +
                        "250-SIZE 10485760\r\n" +
                        "250 OK\r\n");
    }

    @Test
    void testEncodeReplyMultiLineWithEnhanced() {
        var reply = SmtpReply.ofLines(250, EnhancedStatusCode.SUCCESS_OTHER,
                java.util.List.of("line1", "line2"));
        String encoded = SmtpCodec.encodeReply(reply);
        assertThat(encoded).contains("2.0.0");
    }

    // --- Reply reading ---

    @Test
    void testReadReplySingleLine() throws IOException {
        var reader = readerOf("250 OK\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.text()).isEqualTo("OK");
    }

    @Test
    void testReadReplyMultiLine() throws IOException {
        var reader = readerOf("250-mail.example.com\r\n250-SIZE 10485760\r\n250 OK\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.lines()).hasSize(3);
        assertThat(reply.lines().getFirst()).isEqualTo("mail.example.com");
        assertThat(reply.lines().getLast()).isEqualTo("OK");
    }

    @Test
    void testReadReplyWithEnhanced() throws IOException {
        var reader = readerOf("250 2.1.0 Sender OK\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.enhancedCode()).isEqualTo(EnhancedStatusCode.SUCCESS_ADDRESS);
        assertThat(reply.text()).isEqualTo("Sender OK");
    }

    @Test
    void testReadReplyGreeting() throws IOException {
        var reader = readerOf("220 mail.example.com ESMTP ready\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).contains("mail.example.com");
    }

    @Test
    void testReadReply354() throws IOException {
        var reader = readerOf("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(354);
        assertThat(reply.isIntermediate()).isTrue();
    }

    @Test
    void testReadReplyError() throws IOException {
        var reader = readerOf("550 5.1.1 Mailbox not found\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(550);
        assertThat(reply.enhancedCode()).isNotNull();
        assertThat(reply.enhancedCode().statusClass()).isEqualTo(5);
    }

    @Test
    void testReadReplyConnectionClosed() {
        var reader = readerOf("");
        assertThatThrownBy(() -> SmtpCodec.readReply(reader))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testReadReplyInvalidFormat() {
        var reader = readerOf("XX invalid\r\n");
        assertThatThrownBy(() -> SmtpCodec.readReply(reader))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadReplyAuthChallenge() throws IOException {
        var reader = readerOf("334 dGVzdA==\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(334);
        assertThat(reply.text()).isEqualTo("dGVzdA==");
    }

    @Test
    void testReadReplyAuthSuccess() throws IOException {
        var reader = readerOf("235 2.7.0 Authentication successful\r\n");
        var reply = SmtpCodec.readReply(reader);
        assertThat(reply.code()).isEqualTo(235);
        assertThat(reply.enhancedCode()).isNotNull();
    }

    // --- Round-trip ---

    @Test
    void testEncodeDecodeRoundTripSimple() throws IOException {
        var original = SmtpReply.of(250, "OK");
        String encoded = SmtpCodec.encodeReply(original);
        var decoded = SmtpCodec.readReply(readerOf(encoded));
        assertThat(decoded.code()).isEqualTo(original.code());
        assertThat(decoded.text()).isEqualTo(original.text());
    }

    @Test
    void testEncodeDecodeRoundTripEnhanced() throws IOException {
        var original = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        String encoded = SmtpCodec.encodeReply(original);
        var decoded = SmtpCodec.readReply(readerOf(encoded));
        assertThat(decoded.code()).isEqualTo(250);
        assertThat(decoded.enhancedCode()).isEqualTo(EnhancedStatusCode.SUCCESS_OTHER);
    }

    @Test
    void testEncodeDecodeRoundTripMultiLine() throws IOException {
        var original = SmtpReply.ofLines(250, java.util.List.of("host", "SIZE 1024", "OK"));
        String encoded = SmtpCodec.encodeReply(original);
        var decoded = SmtpCodec.readReply(readerOf(encoded));
        assertThat(decoded.code()).isEqualTo(250);
        assertThat(decoded.lines()).hasSize(3);
    }

    private BufferedReader readerOf(String content) {
        return new BufferedReader(new StringReader(content));
    }
}
