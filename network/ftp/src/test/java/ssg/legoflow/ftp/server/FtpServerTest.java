package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.protocol.FtpProtocolCodec;
import ssg.legoflow.ftp.protocol.FtpReply;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpServer}.
 */
class FtpServerTest {

    private FtpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(0)
                .build();
        server = new FtpServer(config);
        server.setFileSystem(new InMemoryFileSystem());
        server.setAuthenticator(FtpAuthenticator.acceptAll());
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void testServerStartsAndAcceptsConnections() throws Exception {
        assertThat(server.isRunning()).isTrue();
        assertThat(port).isGreaterThan(0);

        try (var socket = new Socket("127.0.0.1", port)) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            FtpReply greeting = FtpProtocolCodec.readReply(reader);
            assertThat(greeting).isNotNull();
            assertThat(greeting.code()).isEqualTo(220);
        }
    }

    @Test
    void testServerStop() throws IOException {
        assertThat(server.isRunning()).isTrue();
        server.stop();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testLoginFlow() throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            // Read greeting
            FtpProtocolCodec.readReply(reader);

            // USER
            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.USER, "test");
            var userReply = FtpProtocolCodec.readReply(reader);
            assertThat(userReply.code()).isEqualTo(331);

            // PASS
            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.PASS, "test");
            var passReply = FtpProtocolCodec.readReply(reader);
            assertThat(passReply.code()).isEqualTo(230);
        }
    }

    @Test
    void testCommandBeforeLogin() throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            FtpProtocolCodec.readReply(reader); // greeting

            // Try PWD without logging in
            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.PWD, null);
            var reply = FtpProtocolCodec.readReply(reader);
            assertThat(reply.code()).isEqualTo(530); // Not logged in
        }
    }

    @Test
    void testQuit() throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            FtpProtocolCodec.readReply(reader);

            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.QUIT, null);
            var reply = FtpProtocolCodec.readReply(reader);
            assertThat(reply.code()).isEqualTo(221);
        }
    }

    @Test
    void testSystCommand() throws Exception {
        try (var socket = connectAndLogin()) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.SYST, null);
            var reply = FtpProtocolCodec.readReply(reader);
            assertThat(reply.code()).isEqualTo(215);
            assertThat(reply.text()).contains("UNIX");
        }
    }

    @Test
    void testFeatCommand() throws Exception {
        try (var socket = connectAndLogin()) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.FEAT, null);
            var reply = FtpProtocolCodec.readReply(reader);
            assertThat(reply.code()).isEqualTo(211);
        }
    }

    @Test
    void testUnknownCommand() throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = socket.getOutputStream();

            FtpProtocolCodec.readReply(reader);

            writer.write("BOGUS\r\n".getBytes(StandardCharsets.UTF_8));
            writer.flush();
            var reply = FtpProtocolCodec.readReply(reader);
            assertThat(reply.code()).isEqualTo(500);
        }
    }

    @Test
    void testConcurrentConnections() throws Exception {
        Socket[] sockets = new Socket[5];
        try {
            for (int i = 0; i < sockets.length; i++) {
                sockets[i] = new Socket("127.0.0.1", port);
                var reader = new BufferedReader(
                        new InputStreamReader(sockets[i].getInputStream(), StandardCharsets.UTF_8));
                var greeting = FtpProtocolCodec.readReply(reader);
                assertThat(greeting.code()).isEqualTo(220);
            }
            // Give connection count time to update
            Thread.sleep(100);
            assertThat(server.getConnectionCount()).isGreaterThanOrEqualTo(5);
        } finally {
            for (Socket s : sockets) {
                if (s != null) s.close();
            }
        }
    }

    @Test
    void testStartWithoutFileSystemThrows() {
        var config = FtpServerConfig.builder().port(0).build();
        var s = new FtpServer(config);
        assertThatIllegalStateException().isThrownBy(s::start);
    }

    private Socket connectAndLogin() throws Exception {
        var socket = new Socket("127.0.0.1", port);
        var reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        var writer = socket.getOutputStream();
        FtpProtocolCodec.readReply(reader); // greeting
        FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.USER, "test");
        FtpProtocolCodec.readReply(reader);
        FtpProtocolCodec.writeCommand(writer, ssg.legoflow.ftp.protocol.FtpCommand.PASS, "test");
        FtpProtocolCodec.readReply(reader);
        return socket;
    }
}
