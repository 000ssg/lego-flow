package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.server.*;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Functional tests for {@link FileTransferDemo}.
 */
class FileTransferDemoTest {

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
        server.setAuthenticator(FtpAuthenticator.anonymous());
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void testSmallFileTransfer() throws IOException {
        long transferred = FileTransferDemo.run("127.0.0.1", port, 100);
        assertThat(transferred).isEqualTo(100);
    }

    @Test
    void testMediumFileTransfer() throws IOException {
        long transferred = FileTransferDemo.run("127.0.0.1", port, 10_000);
        assertThat(transferred).isEqualTo(10_000);
    }

    @Test
    void testLargeFileTransfer() throws IOException {
        long transferred = FileTransferDemo.run("127.0.0.1", port, 100_000);
        assertThat(transferred).isEqualTo(100_000);
    }

    @Test
    void testEmptyFileTransfer() throws IOException {
        long transferred = FileTransferDemo.run("127.0.0.1", port, 0);
        assertThat(transferred).isEqualTo(0);
    }

    @Test
    void testSingleByteTransfer() throws IOException {
        long transferred = FileTransferDemo.run("127.0.0.1", port, 1);
        assertThat(transferred).isEqualTo(1);
    }
}
