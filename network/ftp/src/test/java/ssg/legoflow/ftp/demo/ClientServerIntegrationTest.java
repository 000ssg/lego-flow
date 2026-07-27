package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.client.FtpFileEntry;
import ssg.legoflow.ftp.protocol.FtpTransferType;
import ssg.legoflow.ftp.server.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Full client-server integration tests exercising realistic FTP workflows.
 */
class ClientServerIntegrationTest {

    private FtpServer server;
    private InMemoryFileSystem fs;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        fs = new InMemoryFileSystem();
        fs.createDirectory("/upload");
        fs.createDirectory("/download");
        fs.putFile("/download/sample.txt", "Sample content for download".getBytes(StandardCharsets.UTF_8));

        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(0)
                .serverName("Integration Test Server")
                .build();
        server = new FtpServer(config);
        server.setFileSystem(fs);
        server.setAuthenticator(FtpAuthenticator.singleUser("test", "test123"));
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void testFullUploadDownloadWorkflow() throws IOException {
        try (var client = createClient()) {
            // Upload
            byte[] original = "Hello, FTP World!".getBytes(StandardCharsets.UTF_8);
            client.setTransferType(FtpTransferType.BINARY);
            client.put(new ByteArrayInputStream(original), "/upload/hello.txt");

            // Download
            try (var is = client.get("/upload/hello.txt")) {
                byte[] downloaded = is.readAllBytes();
                assertThat(downloaded).isEqualTo(original);
            }
        }
    }

    @Test
    void testDirectoryNavigationWorkflow() throws IOException {
        try (var client = createClient()) {
            // Start at root
            assertThat(client.pwd()).isEqualTo("/");

            // Navigate to upload
            client.cd("/upload");
            assertThat(client.pwd()).isEqualTo("/upload");

            // Go back
            client.cdup();
            assertThat(client.pwd()).isEqualTo("/");

            // Navigate to download
            client.cd("/download");
            assertThat(client.pwd()).isEqualTo("/download");
        }
    }

    @Test
    void testDirectoryCreationWorkflow() throws IOException {
        try (var client = createClient()) {
            // Create nested directories
            client.mkdir("/projects");
            client.mkdir("/projects/java");
            client.mkdir("/projects/java/src");

            // Verify they exist
            client.cd("/projects/java/src");
            assertThat(client.pwd()).isEqualTo("/projects/java/src");
        }
    }

    @Test
    void testFileRenameWorkflow() throws IOException {
        try (var client = createClient()) {
            // Upload a file
            client.put(new ByteArrayInputStream("data".getBytes()), "/upload/original.txt");

            // Rename it
            client.rename("/upload/original.txt", "/upload/renamed.txt");

            // Verify old name is gone and new name exists
            assertThat(fs.exists("/upload/original.txt")).isFalse();
            assertThat(fs.exists("/upload/renamed.txt")).isTrue();
        }
    }

    @Test
    void testFileDeleteWorkflow() throws IOException {
        try (var client = createClient()) {
            // Upload
            client.put(new ByteArrayInputStream("temp".getBytes()), "/upload/temp.txt");
            assertThat(fs.exists("/upload/temp.txt")).isTrue();

            // Delete
            client.delete("/upload/temp.txt");
            assertThat(fs.exists("/upload/temp.txt")).isFalse();
        }
    }

    @Test
    void testMultipleFileUploads() throws IOException {
        try (var client = createClient()) {
            client.setTransferType(FtpTransferType.BINARY);
            for (int i = 0; i < 5; i++) {
                String content = "File content " + i;
                client.put(new ByteArrayInputStream(content.getBytes()), "/upload/file" + i + ".txt");
            }
            // Verify all files exist
            for (int i = 0; i < 5; i++) {
                assertThat(fs.exists("/upload/file" + i + ".txt")).isTrue();
            }
        }
    }

    @Test
    void testBinaryTransferIntegrity() throws IOException {
        try (var client = createClient()) {
            client.setTransferType(FtpTransferType.BINARY);

            // Upload binary data (all byte values)
            byte[] data = new byte[256];
            for (int i = 0; i < 256; i++) data[i] = (byte) i;
            client.put(new ByteArrayInputStream(data), "/upload/binary.bin");

            // Download and verify
            try (var is = client.get("/upload/binary.bin")) {
                byte[] downloaded = is.readAllBytes();
                assertThat(downloaded).isEqualTo(data);
            }
        }
    }

    @Test
    void testListDirectory() throws IOException {
        try (var client = createClient()) {
            List<FtpFileEntry> entries = client.list(null);
            assertThat(entries).isNotEmpty();
        }
    }

    @Test
    void testNlstDirectory() throws IOException {
        try (var client = createClient()) {
            List<String> names = client.nlst(null);
            assertThat(names).isNotEmpty();
        }
    }

    @Test
    void testSystemType() throws IOException {
        try (var client = createClient()) {
            String syst = client.systemType();
            assertThat(syst).isNotEmpty();
        }
    }

    @Test
    void testNoopKeepAlive() throws IOException {
        try (var client = createClient()) {
            for (int i = 0; i < 3; i++) {
                var reply = client.noop();
                assertThat(reply.isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testSizeCommand() throws IOException {
        try (var client = createClient()) {
            long size = client.size("/download/sample.txt");
            assertThat(size).isEqualTo(27);
        }
    }

    @Test
    void testConcurrentClients() throws Exception {
        Thread[] threads = new Thread[3];
        boolean[] success = new boolean[3];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                try (var client = createClient()) {
                    client.setTransferType(FtpTransferType.BINARY);
                    byte[] data = ("concurrent-" + idx).getBytes();
                    client.put(new ByteArrayInputStream(data), "/upload/concurrent-" + idx + ".txt");
                    try (var is = client.get("/upload/concurrent-" + idx + ".txt")) {
                        byte[] downloaded = is.readAllBytes();
                        success[idx] = java.util.Arrays.equals(data, downloaded);
                    }
                } catch (IOException e) {
                    success[idx] = false;
                }
            });
        }
        for (Thread t : threads) t.join(10_000);
        assertThat(success).containsOnly(true);
    }

    @Test
    void testVirtualFsDemo() throws IOException {
        try (var vServer = VirtualFsDemo.start(0)) {
            assertThat(vServer.isRunning()).isTrue();
            try (var client = new FtpClient()) {
                client.connect("127.0.0.1", vServer.getPort());
                client.login("any", "any");
                var entries = client.list(null);
                assertThat(entries).isNotEmpty();
            }
        }
    }

    @Test
    void testTransferTypeSwitch() throws IOException {
        try (var client = createClient()) {
            client.setTransferType(FtpTransferType.ASCII);
            assertThat(client.getTransferType()).isEqualTo(FtpTransferType.ASCII);

            client.setTransferType(FtpTransferType.BINARY);
            assertThat(client.getTransferType()).isEqualTo(FtpTransferType.BINARY);
        }
    }

    private FtpClient createClient() throws IOException {
        var client = new FtpClient();
        client.connect("127.0.0.1", port);
        client.login("test", "test123");
        client.setPassiveMode(true);
        return client;
    }
}
