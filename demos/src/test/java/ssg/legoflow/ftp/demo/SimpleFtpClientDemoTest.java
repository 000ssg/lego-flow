package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.server.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Functional tests for {@link SimpleFtpClientDemo}.
 */
class SimpleFtpClientDemoTest {

    private FtpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(0)
                .build();
        var fs = new InMemoryFileSystem();
        server = new FtpServer(config);
        server.setFileSystem(fs);
        server.setAuthenticator(FtpAuthenticator.singleUser("user", "pass"));
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void testDemoRunsSuccessfully() {
        assertThatNoException().isThrownBy(
                () -> SimpleFtpClientDemo.run("127.0.0.1", port, "user", "pass"));
    }

    @Test
    void testDemoWithWrongCredentialsFails() {
        assertThatIOException().isThrownBy(
                () -> SimpleFtpClientDemo.run("127.0.0.1", port, "bad", "bad"));
    }

    @Test
    void testDemoWithInvalidHostFails() {
        assertThatException().isThrownBy(
                () -> SimpleFtpClientDemo.run("invalid-host-that-does-not-exist.local", 99999, "user", "pass"));
    }
}
