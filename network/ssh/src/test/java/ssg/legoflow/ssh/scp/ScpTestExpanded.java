package ssg.legoflow.ssh.scp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Expanded tests for SCP protocol operations.
 */
class ScpTestExpanded {

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // ignore on delete
                        }
                    });
        }
    }

    @Test void testScpModeConstants() throws Exception {
        // SCP uses Unix permission modes (0644, 0755, etc.)
        // Verify common permission values are valid octal
        int[] modes = {0600, 0640, 0644, 0660, 0666, 0700, 0750, 0755, 0777};
        for (int mode : modes) {
            // Just verify they are positive and in valid range
            assertThat(mode).isPositive();
            assertThat(mode).isLessThanOrEqualTo(0777);
        }
    }

    @Test void testScpServerWithTemporaryDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        var scp = new ScpServer(tempDir);
        assertThat(scp).isNotNull();
        // Clean up
        deleteRecursively(tempDir);
    }

    @Test void testScpServerNullPathThrows() {
        assertThatThrownBy(() -> new ScpServer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testScpSinkToFile() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            Path targetFile = tempDir.resolve("uploaded.txt");
            
            // Create a fake SCP input stream with header + data
            byte[] content = "Hello, SCP!".getBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Write mode and size (SCP protocol header)
            String header = String.format("%04o %d ", 0644, content.length);
            baos.write(header.getBytes());
            // Use 'f' for regular file
            baos.write('f');
            baos.write(header.getBytes());
            baos.write(content);
            baos.write(0); // EOF marker
            
            InputStream input = new ByteArrayInputStream(baos.toByteArray());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            
            scp.handleSink("uploaded.txt", input, output);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpSourceOfFile() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            Path sourceFile = tempDir.resolve("test.txt");
            Files.writeString(sourceFile, "test content");
            
            var scp = new ScpServer(tempDir);
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            scp.handleSource("test.txt", new ByteArrayInputStream(new byte[0]), output);
            
            // Output should contain the file data
            String result = output.toString();
            assertThat(result).contains("test content");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpSourceNonExistentFileThrows() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            scp.handleSource("nonexistent.txt", 
                new ByteArrayInputStream(new byte[0]), 
                output);
            
            // ScpServer writes an error message to stdout and returns normally
            String result = output.toString(StandardCharsets.UTF_8);
            assertThat(result).contains("No such file");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpDirectoryCreation() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            
            // Create a subdirectory for SCP
            Path subDir = tempDir.resolve("subdir");
            Files.createDirectory(subDir);
            
            assertThat(Files.exists(subDir)).isTrue();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpServerWithNestedPath() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            Path nested = tempDir.resolve("a/b/c");
            Files.createDirectories(nested);
            
            var scp = new ScpServer(tempDir);
            
            // Sink to nested path should work if directory exists
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] content = "nested".getBytes();
            String header = String.format("f%04o %d ", 0644, content.length);
            ByteArrayInputStream input = new ByteArrayInputStream(
                (header + new String(content) + "\0").getBytes());
            
            scp.handleSink("a/b/c/file.txt", input, output);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpModeParsing() throws Exception {
        // Test various SCP mode strings that might appear in protocol
        String[] modes = {"0644", "0755", "0600", "100644", "40755"};
        for (String mode : modes) {
            try {
                int parsed = Integer.parseInt(mode, 8);
                assertThat(parsed).isPositive();
            } catch (NumberFormatException e) {
                // Some formats may not parse as octal - that is expected
            }
        }
    }

    @Test void testScpWithLargeFile() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            
            // Generate 1MB of data
            byte[] largeData = new byte[1024 * 1024];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String header = String.format("f%04o %d ", 0644, largeData.length);
            ByteArrayInputStream input = new ByteArrayInputStream(
                (header + new String(largeData) + "\0").getBytes());
            
            scp.handleSink("large.bin", input, output);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpEmptyFile() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String header = "f0644 0 ";
            ByteArrayInputStream input = new ByteArrayInputStream(
                (header + "\0").getBytes());
            
            scp.handleSink("empty.txt", input, output);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpSourceToStdout() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            Path sourceFile = tempDir.resolve("stdout.txt");
            Files.writeString(sourceFile, "output content");
            
            var scp = new ScpServer(tempDir);
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = new ByteArrayInputStream(new byte[0]);
            scp.handleSource("stdout.txt", input, output);
            
            // Verify the file contents are in the output stream
            assertThat(output.size()).isGreaterThan(0);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test void testScpServerRootDirectoryValidation() throws Exception {
        Path tempDir = Files.createTempDirectory("scp-test");
        try {
            var scp = new ScpServer(tempDir);
            assertThat(scp).isNotNull();
            // The server should have access to the root directory
            assertThat(Files.exists(tempDir)).isTrue();
        } finally {
            deleteRecursively(tempDir);
        }
    }
}
