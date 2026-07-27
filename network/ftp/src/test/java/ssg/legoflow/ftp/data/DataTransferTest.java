package ssg.legoflow.ftp.data;

import ssg.legoflow.ftp.protocol.FtpTransferType;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DataTransfer}.
 */
class DataTransferTest {

    @Test
    void testBinarySendReceiveRoundTrip() throws IOException {
        byte[] data = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        var pipe = new ByteArrayOutputStream();
        transfer.send(new ByteArrayInputStream(data), pipe);
        byte[] received = pipe.toByteArray();
        assertThat(received).isEqualTo(data);
    }

    @Test
    void testBinaryTransferLargeData() throws IOException {
        byte[] data = new byte[100_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        var pipe = new ByteArrayOutputStream();
        long bytes = transfer.send(new ByteArrayInputStream(data), pipe);
        assertThat(bytes).isEqualTo(100_000);
        assertThat(pipe.toByteArray()).isEqualTo(data);
    }

    @Test
    void testBinaryReceive() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        var output = new ByteArrayOutputStream();
        long bytes = transfer.receive(new ByteArrayInputStream(data), output);
        assertThat(bytes).isEqualTo(5);
        assertThat(output.toByteArray()).isEqualTo(data);
    }

    @Test
    void testAsciiSendConvertsCrlfOnWire() throws IOException {
        String local = "line1\nline2\nline3\n";
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        var pipe = new ByteArrayOutputStream();
        transfer.send(new ByteArrayInputStream(local.getBytes(StandardCharsets.UTF_8)), pipe);
        String wireData = pipe.toString(StandardCharsets.UTF_8);
        assertThat(wireData).contains("\r\n");
    }

    @Test
    void testAsciiReceiveConvertsToLocal() throws IOException {
        String wireData = "line1\r\nline2\r\nline3\r\n";
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        var output = new ByteArrayOutputStream();
        transfer.receive(
                new ByteArrayInputStream(wireData.getBytes(StandardCharsets.UTF_8)),
                output);
        String local = output.toString(StandardCharsets.UTF_8);
        // Should contain local line separators
        assertThat(local).contains(System.lineSeparator());
        assertThat(local).contains("line1");
        assertThat(local).contains("line2");
    }

    @Test
    void testLocalToWireConversion() {
        assertThat(DataTransfer.localToWire("a\nb\nc")).isEqualTo("a\r\nb\r\nc");
    }

    @Test
    void testLocalToWireAlreadyCrlf() {
        assertThat(DataTransfer.localToWire("a\r\nb")).isEqualTo("a\r\nb");
    }

    @Test
    void testWireToLocalConversion() {
        String result = DataTransfer.wireToLocal("a\r\nb\r\nc");
        String expected = "a" + System.lineSeparator() + "b" + System.lineSeparator() + "c";
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testBinaryEmptyData() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        var pipe = new ByteArrayOutputStream();
        long bytes = transfer.send(new ByteArrayInputStream(new byte[0]), pipe);
        assertThat(bytes).isEqualTo(0);
        assertThat(pipe.toByteArray()).isEmpty();
    }

    @Test
    void testAsciiEmptyData() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        var pipe = new ByteArrayOutputStream();
        long bytes = transfer.send(new ByteArrayInputStream(new byte[0]), pipe);
        assertThat(bytes).isEqualTo(0);
    }

    @Test
    void testCustomBufferSize() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY, 128);
        byte[] data = new byte[1000];
        var pipe = new ByteArrayOutputStream();
        long bytes = transfer.send(new ByteArrayInputStream(data), pipe);
        assertThat(bytes).isEqualTo(1000);
    }

    @Test
    void testInvalidBufferSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DataTransfer(FtpTransferType.BINARY, 0));
    }

    @Test
    void testNullTransferTypeThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DataTransfer(null));
    }
}
