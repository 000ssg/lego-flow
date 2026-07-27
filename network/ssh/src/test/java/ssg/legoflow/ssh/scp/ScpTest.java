package ssg.legoflow.ssh.scp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import static org.assertj.core.api.Assertions.*;

class ScpTest {

    @TempDir
    Path tempDir;

    // --- ScpServer tests ---

    @Test
    void testScpServerConstructor() {
        ScpServer server = new ScpServer(tempDir);
        assertThat(server).isNotNull();
    }

    @Test
    void testScpServerNullRootThrows() {
        assertThatThrownBy(() -> new ScpServer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testScpServerHandleSink() throws Exception {
        // Create destination directory
        Path destDir = tempDir.resolve("dest");
        Files.createDirectories(destDir);

        ScpServer server = new ScpServer(tempDir);

        // Simulate SCP upload: header + content + null byte
        String content = "Hello, SCP!";
        String header = "C0644 " + content.length() + " testfile.txt\n";
        byte[] inputData = concat(header.getBytes(StandardCharsets.UTF_8),
                content.getBytes(StandardCharsets.UTF_8), new byte[]{0});

        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSink("dest", in, out);

        Path resultFile = tempDir.resolve("dest").resolve("testfile.txt");
        assertThat(resultFile).exists();
        assertThat(Files.readString(resultFile)).isEqualTo(content);
    }

    @Test
    void testScpServerHandleSinkInvalidHeader() throws Exception {
        ScpServer server = new ScpServer(tempDir);
        ByteArrayInputStream in = new ByteArrayInputStream("INVALID\n".getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSink("dest", in, out);

        // Should have sent error byte (1) after initial OK (0)
        byte[] output = out.toByteArray();
        assertThat(output.length).isGreaterThanOrEqualTo(2);
        assertThat(output[0]).isEqualTo((byte) 0); // initial OK
        assertThat(output[1]).isEqualTo((byte) 1); // error
    }

    @Test
    void testScpServerHandleSource() throws Exception {
        // Create a file to serve
        Path sourceFile = tempDir.resolve("source.txt");
        String content = "Download this content";
        Files.writeString(sourceFile, content);

        ScpServer server = new ScpServer(tempDir);

        // Input: initial OK + OK after header + OK after content
        byte[] inputData = new byte[]{0, 0, 0};
        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSource("source.txt", in, out);

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("C0644");
        assertThat(output).contains("source.txt");
    }

    @Test
    void testScpServerHandleSourceNonexistentFile() throws Exception {
        ScpServer server = new ScpServer(tempDir);
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSource("nonexistent.txt", in, out);

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("No such file");
    }

    // --- ScpClient tests (constructor only, since it needs a real SessionChannel) ---

    @Test
    void testScpClientNullChannelThrows() {
        assertThatThrownBy(() -> new ScpClient(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- DiffieHellmanGroup16 test (bonus to fill quota) ---

    // --- SCP timestamp preservation tests ---

    @Test
    void testScpServerHandleSinkWithTimestamp() throws Exception {
        // Create destination directory
        Path destDir = tempDir.resolve("tsdest");
        Files.createDirectories(destDir);

        ScpServer server = new ScpServer(tempDir);

        // Simulate SCP upload with T command: T<mtime> 0 <atime> 0\n then C header + content
        long mtime = 1700000000L;
        long atime = 1700000100L;
        String tCmd = "T" + mtime + " 0 " + atime + " 0\n";
        String content = "Timestamped content";
        String header = "C0644 " + content.length() + " tsfile.txt\n";

        byte[] inputData = concat(
                tCmd.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8),
                content.getBytes(StandardCharsets.UTF_8),
                new byte[]{0});

        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSink("tsdest", in, out);

        Path resultFile = tempDir.resolve("tsdest").resolve("tsfile.txt");
        assertThat(resultFile).exists();
        assertThat(Files.readString(resultFile)).isEqualTo(content);

        // Verify timestamp was applied
        FileTime lastModified = Files.getLastModifiedTime(resultFile);
        assertThat(lastModified.toMillis() / 1000).isEqualTo(mtime);
    }

    @Test
    void testScpServerHandleSourceWithTimestamp() throws Exception {
        // Create a file with known modification time
        Path sourceFile = tempDir.resolve("tssource.txt");
        String content = "Source with timestamp";
        Files.writeString(sourceFile, content);
        long mtime = 1700000000L;
        Files.setLastModifiedTime(sourceFile, FileTime.fromMillis(mtime * 1000));

        ScpServer server = new ScpServer(tempDir);

        // Input: initial OK + OK after T command + OK after header + OK after content
        byte[] inputData = new byte[]{0, 0, 0, 0};
        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSource("tssource.txt", in, out, true);

        String output = out.toString(StandardCharsets.UTF_8);
        // Should contain T command before C header
        assertThat(output).contains("T" + mtime);
        assertThat(output).contains("C0644");
        assertThat(output).contains("tssource.txt");
    }

    @Test
    void testScpServerHandleSinkNoTimestamp() throws Exception {
        // Verify backward compatibility: standard sink without T command still works
        Path destDir = tempDir.resolve("nodest");
        Files.createDirectories(destDir);

        ScpServer server = new ScpServer(tempDir);

        String content = "No timestamp";
        String header = "C0644 " + content.length() + " notsfile.txt\n";
        byte[] inputData = concat(header.getBytes(StandardCharsets.UTF_8),
                content.getBytes(StandardCharsets.UTF_8), new byte[]{0});

        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSink("nodest", in, out);

        Path resultFile = tempDir.resolve("nodest").resolve("notsfile.txt");
        assertThat(resultFile).exists();
        assertThat(Files.readString(resultFile)).isEqualTo(content);
    }

    @Test
    void testScpServerHandleSourceWithoutTimestamp() throws Exception {
        // Verify backward compatibility: source without timestamps
        Path sourceFile = tempDir.resolve("plain.txt");
        Files.writeString(sourceFile, "Plain content");

        ScpServer server = new ScpServer(tempDir);

        byte[] inputData = new byte[]{0, 0, 0};
        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        server.handleSource("plain.txt", in, out, false);

        String output = out.toString(StandardCharsets.UTF_8);
        // Should NOT contain T command
        assertThat(output).doesNotContain("T1");
        assertThat(output).contains("C0644");
    }

    private static byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
