package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpProtocolCodec}.
 */
class FtpProtocolCodecTest {

    @Test
    void testEncodeCommandNoArgument() {
        String encoded = FtpProtocolCodec.encodeCommand(FtpCommand.QUIT);
        assertThat(encoded).isEqualTo("QUIT\r\n");
    }

    @Test
    void testEncodeCommandWithArgument() {
        String encoded = FtpProtocolCodec.encodeCommand(FtpCommand.USER, "admin");
        assertThat(encoded).isEqualTo("USER admin\r\n");
    }

    @Test
    void testEncodeCommandNullArgument() {
        String encoded = FtpProtocolCodec.encodeCommand(FtpCommand.NOOP, null);
        assertThat(encoded).isEqualTo("NOOP\r\n");
    }

    @Test
    void testEncodeCommandEmptyArgument() {
        String encoded = FtpProtocolCodec.encodeCommand(FtpCommand.LIST, "");
        assertThat(encoded).isEqualTo("LIST\r\n");
    }

    @Test
    void testDecodeCommandNoArgument() {
        String[] parsed = FtpProtocolCodec.decodeCommand("QUIT");
        assertThat(parsed[0]).isEqualTo("QUIT");
        assertThat(parsed[1]).isNull();
    }

    @Test
    void testDecodeCommandWithArgument() {
        String[] parsed = FtpProtocolCodec.decodeCommand("USER admin");
        assertThat(parsed[0]).isEqualTo("USER");
        assertThat(parsed[1]).isEqualTo("admin");
    }

    @Test
    void testDecodeCommandWithMultiWordArgument() {
        String[] parsed = FtpProtocolCodec.decodeCommand("CWD /some/long path");
        assertThat(parsed[0]).isEqualTo("CWD");
        assertThat(parsed[1]).isEqualTo("/some/long path");
    }

    @Test
    void testDecodeCommandLowerCase() {
        String[] parsed = FtpProtocolCodec.decodeCommand("user admin");
        assertThat(parsed[0]).isEqualTo("USER");
    }

    @Test
    void testDecodeCommandNullThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpProtocolCodec.decodeCommand(null));
    }

    @Test
    void testDecodeCommandBlankThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpProtocolCodec.decodeCommand("  "));
    }

    @Test
    void testEncodeReplySingleLine() {
        var reply = new FtpReply(220, "Ready");
        String encoded = FtpProtocolCodec.encodeReply(reply);
        assertThat(encoded).isEqualTo("220 Ready\r\n");
    }

    @Test
    void testEncodeReplyMultiLine() {
        var reply = new FtpReply(211, java.util.List.of("Features:", " SIZE", "End"));
        String encoded = FtpProtocolCodec.encodeReply(reply);
        assertThat(encoded).isEqualTo("211-Features:\r\n211- SIZE\r\n211 End\r\n");
    }

    @Test
    void testReadReplySingleLine() throws IOException {
        var reader = createReader("220 Service ready\r\n");
        FtpReply reply = FtpProtocolCodec.readReply(reader);
        assertThat(reply).isNotNull();
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).isEqualTo("Service ready");
        assertThat(reply.isMultiLine()).isFalse();
    }

    @Test
    void testReadReplyMultiLine() throws IOException {
        String raw = "211-Features:\r\n SIZE\r\n MDTM\r\n211 End\r\n";
        var reader = createReader(raw);
        FtpReply reply = FtpProtocolCodec.readReply(reader);
        assertThat(reply).isNotNull();
        assertThat(reply.code()).isEqualTo(211);
        assertThat(reply.isMultiLine()).isTrue();
        assertThat(reply.lines()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testReadReplyMultiLineWithDash() throws IOException {
        String raw = "211-Features:\r\n211- SIZE\r\n211 End\r\n";
        var reader = createReader(raw);
        FtpReply reply = FtpProtocolCodec.readReply(reader);
        assertThat(reply).isNotNull();
        assertThat(reply.code()).isEqualTo(211);
        assertThat(reply.isMultiLine()).isTrue();
    }

    @Test
    void testReadReplyNullOnEof() throws IOException {
        var reader = createReader("");
        FtpReply reply = FtpProtocolCodec.readReply(reader);
        assertThat(reply).isNull();
    }

    @Test
    void testReadReplyTooShortThrows() {
        var reader = createReader("20\r\n");
        assertThatIllegalArgumentException().isThrownBy(() -> FtpProtocolCodec.readReply(reader));
    }

    @Test
    void testDecodeReply() throws IOException {
        FtpReply reply = FtpProtocolCodec.decodeReply("250 File action ok\r\n");
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.text()).isEqualTo("File action ok");
    }

    @Test
    void testWriteCommand() throws IOException {
        var baos = new ByteArrayOutputStream();
        FtpProtocolCodec.writeCommand(baos, FtpCommand.USER, "test");
        assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("USER test\r\n");
    }

    @Test
    void testWriteReply() throws IOException {
        var baos = new ByteArrayOutputStream();
        FtpProtocolCodec.writeReply(baos, new FtpReply(200, "OK"));
        assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("200 OK\r\n");
    }

    @Test
    void testRoundTripAllCommands() {
        for (FtpCommand cmd : FtpCommand.values()) {
            String encoded = FtpProtocolCodec.encodeCommand(cmd, "arg");
            String line = encoded.trim();
            String[] decoded = FtpProtocolCodec.decodeCommand(line);
            assertThat(decoded[0]).isEqualTo(cmd.wireForm());
            assertThat(decoded[1]).isEqualTo("arg");
        }
    }

    @Test
    void testRoundTripReply() throws IOException {
        var original = new FtpReply(227, "Entering Passive Mode (127,0,0,1,4,1)");
        String encoded = FtpProtocolCodec.encodeReply(original);
        FtpReply decoded = FtpProtocolCodec.decodeReply(encoded);
        assertThat(decoded.code()).isEqualTo(original.code());
        assertThat(decoded.text()).isEqualTo(original.text());
    }

    private BufferedReader createReader(String content) {
        return new BufferedReader(new StringReader(content));
    }
}
