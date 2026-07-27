package ssg.legoflow.ftp.client;

import ssg.legoflow.ftp.protocol.FtpTransferType;
import ssg.legoflow.ftp.server.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link FtpClient} with a real server.
 */
class FtpClientTest {

    private FtpServer server;
    private InMemoryFileSystem fileSystem;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        fileSystem = new InMemoryFileSystem();
        fileSystem.createDirectory("/pub");
        fileSystem.putFile("/pub/hello.txt", "Hello World!".getBytes(StandardCharsets.UTF_8));

        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(0) // ephemeral
                .build();
        server = new FtpServer(config);
        server.setFileSystem(fileSystem);
        server.setAuthenticator(FtpAuthenticator.singleUser("user", "pass"));
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void testConnectAndLogin() throws IOException {
        try (var client = new FtpClient()) {
            var greeting = client.connect("127.0.0.1", port);
            assertThat(greeting.code()).isEqualTo(220);
            assertThat(client.isConnected()).isTrue();

            var loginReply = client.login("user", "pass");
            assertThat(loginReply.code()).isEqualTo(230);
            assertThat(client.isLoggedIn()).isTrue();
        }
    }

    @Test
    void testLoginFails() throws IOException {
        try (var client = new FtpClient()) {
            client.connect("127.0.0.1", port);
            assertThatIOException().isThrownBy(() -> client.login("bad", "bad"));
        }
    }

    @Test
    void testPwd() throws IOException {
        try (var client = createConnectedClient()) {
            String pwd = client.pwd();
            assertThat(pwd).isEqualTo("/");
        }
    }

    @Test
    void testCdAndPwd() throws IOException {
        try (var client = createConnectedClient()) {
            client.cd("/pub");
            String pwd = client.pwd();
            assertThat(pwd).isEqualTo("/pub");
        }
    }

    @Test
    void testCdup() throws IOException {
        try (var client = createConnectedClient()) {
            client.cd("/pub");
            client.cdup();
            String pwd = client.pwd();
            assertThat(pwd).isEqualTo("/");
        }
    }

    @Test
    void testMkdirAndRmdir() throws IOException {
        try (var client = createConnectedClient()) {
            client.mkdir("/newdir");
            assertThat(fileSystem.exists("/newdir")).isTrue();
            client.rmdir("/newdir");
            assertThat(fileSystem.exists("/newdir")).isFalse();
        }
    }

    @Test
    void testUploadAndDownload() throws IOException {
        try (var client = createConnectedClient()) {
            client.setTransferType(FtpTransferType.BINARY);
            byte[] data = "Test file content".getBytes(StandardCharsets.UTF_8);
            client.put(new ByteArrayInputStream(data), "/test-upload.txt");

            try (var is = client.get("/test-upload.txt")) {
                byte[] downloaded = is.readAllBytes();
                assertThat(downloaded).isEqualTo(data);
            }
        }
    }

    @Test
    void testDeleteFile() throws IOException {
        try (var client = createConnectedClient()) {
            client.put(new ByteArrayInputStream("data".getBytes()), "/to-delete.txt");
            assertThat(fileSystem.exists("/to-delete.txt")).isTrue();
            client.delete("/to-delete.txt");
            assertThat(fileSystem.exists("/to-delete.txt")).isFalse();
        }
    }

    @Test
    void testRename() throws IOException {
        try (var client = createConnectedClient()) {
            client.put(new ByteArrayInputStream("data".getBytes()), "/old-name.txt");
            client.rename("/old-name.txt", "/new-name.txt");
            assertThat(fileSystem.exists("/old-name.txt")).isFalse();
            assertThat(fileSystem.exists("/new-name.txt")).isTrue();
        }
    }

    @Test
    void testSetTransferType() throws IOException {
        try (var client = createConnectedClient()) {
            var reply = client.setTransferType(FtpTransferType.ASCII);
            assertThat(reply.isSuccess()).isTrue();
            assertThat(client.getTransferType()).isEqualTo(FtpTransferType.ASCII);

            reply = client.setTransferType(FtpTransferType.BINARY);
            assertThat(reply.isSuccess()).isTrue();
            assertThat(client.getTransferType()).isEqualTo(FtpTransferType.BINARY);
        }
    }

    @Test
    void testNoop() throws IOException {
        try (var client = createConnectedClient()) {
            var reply = client.noop();
            assertThat(reply.isSuccess()).isTrue();
        }
    }

    @Test
    void testSystemType() throws IOException {
        try (var client = createConnectedClient()) {
            String syst = client.systemType();
            assertThat(syst).contains("UNIX");
        }
    }

    @Test
    void testSize() throws IOException {
        try (var client = createConnectedClient()) {
            long size = client.size("/pub/hello.txt");
            assertThat(size).isEqualTo(12);
        }
    }

    @Test
    void testDisconnect() throws IOException {
        var client = new FtpClient();
        client.connect("127.0.0.1", port);
        client.login("user", "pass");
        assertThat(client.isConnected()).isTrue();
        client.disconnect();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void testPassiveModeDefault() {
        var client = new FtpClient();
        assertThat(client.isPassiveMode()).isTrue();
    }

    @Test
    void testSetPassiveMode() {
        var client = new FtpClient();
        client.setPassiveMode(false);
        assertThat(client.isPassiveMode()).isFalse();
        client.setPassiveMode(true);
        assertThat(client.isPassiveMode()).isTrue();
    }

    private FtpClient createConnectedClient() throws IOException {
        var client = new FtpClient();
        client.connect("127.0.0.1", port);
        client.login("user", "pass");
        client.setPassiveMode(true);
        return client;
    }
}
