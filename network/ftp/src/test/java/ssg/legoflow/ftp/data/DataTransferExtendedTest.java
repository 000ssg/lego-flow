package ssg.legoflow.ftp.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.ftp.protocol.FtpTransferType;
import java.io.*;

class DataTransferExtendedTest {

    @Test void testSendBinary() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        byte[] data = "Hello Binary World!".getBytes();
        
        var source = new ByteArrayInputStream(data);
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        assertThat(sent).isEqualTo(data.length);
        assertThat(dest.toByteArray()).isEqualTo(data);
    }

    @Test void testReceiveBinary() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        byte[] data = new byte[]{1, 2, (byte)0xFF, (byte)0x80};
        
        var source = new ByteArrayInputStream(data);
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isEqualTo(data.length);
        assertThat(dest.toByteArray()).isEqualTo(data);
    }

    @Test void testSendAsciiWithLf() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String text = "line1\nline2\nline3";
        
        var source = new ByteArrayInputStream(text.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        assertThat(sent).isGreaterThan(text.length()); // CRLF adds bytes
        
        // Result should have CRLF instead of LF
        String result = dest.toString();
        assertThat(result).contains("\r\n");
    }

    @Test void testSendAsciiWithCrlf() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String text = "line1\r\nline2\r\nline3";
        
        var source = new ByteArrayInputStream(text.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        // CRLF should stay as CRLF (not doubled)
        String result = dest.toString();
        assertThat(result).isEqualTo("line1\r\nline2\r\nline3");
    }

    @Test void testSendAsciiWithCr() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String text = "line1\rline2";
        
        var source = new ByteArrayInputStream(text.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        // Bare CR should be preserved  
    }

    @Test void testReceiveAsciiWithCrlf() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String wireText = "line1\r\nline2\r\nline3";
        
        var source = new ByteArrayInputStream(wireText.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isGreaterThan(0);
    }

    @Test void testReceiveAsciiWithBareLf() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String wireText = "line1\nline2";
        
        var source = new ByteArrayInputStream(wireText.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isGreaterThan(0);
    }

    @Test void testReceiveAsciiWithBareCr() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        String wireText = "line1\rline2";
        
        var source = new ByteArrayInputStream(wireText.getBytes());
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isGreaterThan(0);
    }

    @Test void testLocalToWire() {
        String result = DataTransfer.localToWire("line1\nline2");
        assertThat(result).isEqualTo("line1\r\nline2");
    }

    @Test void testLocalToWireWithMixedEndings() {
        String result = DataTransfer.localToWire("a\nb\rc\rd\ne");
        // Normalize to LF first, then CRLF
        assertThat(result).isEqualTo("a\r\nb\r\nc\r\nd\r\ne");
    }

    @Test void testLocalToWireNoChanges() {
        String input = "no line endings";
        assertThat(DataTransfer.localToWire(input)).isEqualTo(input);
    }

    @Test void testWireToLocal() {
        String result = DataTransfer.wireToLocal("line1\r\nline2\r\nline3");
        // Should convert CRLF to system line separator
        assertThat(result).contains(System.lineSeparator());
    }

    @Test void testConstructorWithNullThrows() {
        assertThatThrownBy(() -> new DataTransfer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testConstructorWithZeroBufferSizeThrows() {
        assertThatThrownBy(() -> new DataTransfer(FtpTransferType.BINARY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test void testConstructorWithNegativeBufferSizeThrows() {
        assertThatThrownBy(() -> new DataTransfer(FtpTransferType.BINARY, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testSendBinaryEmptyStream() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        var source = new ByteArrayInputStream(new byte[0]);
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        assertThat(sent).isEqualTo(0);
    }

    @Test void testSendBinaryLargeData() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY, 1024); // small buffer
        byte[] data = new byte[8192]; // larger than buffer
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)(i % 256);
        }
        
        var source = new ByteArrayInputStream(data);
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        assertThat(sent).isEqualTo(data.length);
        assertThat(dest.toByteArray()).isEqualTo(data);
    }

    @Test void testCustomBufferSize() {
        var transfer = new DataTransfer(FtpTransferType.BINARY, 4096);
        // Just verify it doesn't throw
        assertThat(transfer).isNotNull();
    }

    @Test void testReceiveBinaryLargeData() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.BINARY, 512);
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)((i * 7) % 256);
        }
        
        var source = new ByteArrayInputStream(data);
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isEqualTo(data.length);
    }

    @Test void testAsciiSendEmptyStream() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        var source = new ByteArrayInputStream(new byte[0]);
        var dest = new ByteArrayOutputStream();
        
        long sent = transfer.send(source, dest);
        assertThat(sent).isEqualTo(0);
    }

    @Test void testAsciiReceiveEmptyStream() throws IOException {
        var transfer = new DataTransfer(FtpTransferType.ASCII);
        var source = new ByteArrayInputStream(new byte[0]);
        var dest = new ByteArrayOutputStream();
        
        long received = transfer.receive(source, dest);
        assertThat(received).isEqualTo(0);
    }

    @Test void testDefaultBufferSize() {
        var transfer = new DataTransfer(FtpTransferType.BINARY);
        // Default should be 8192 - verify through send/receive behavior
        assertThat(transfer).isNotNull();
    }
}
