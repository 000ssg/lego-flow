package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.server.FtpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Functional tests for {@link FtpServerDemo}.
 */
class FtpServerDemoTest {

    @Test
    void testServerDemoStarts() throws IOException {
        try (FtpServer server = FtpServerDemo.start(0)) {
            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isGreaterThan(0);
        }
    }

    @Test
    void testServerDemoAcceptsAnonymous() throws IOException {
        try (FtpServer server = FtpServerDemo.start(0)) {
            try (var client = new FtpClient()) {
                client.connect("127.0.0.1", server.getPort());
                client.loginAnonymous();
                assertThat(client.isLoggedIn()).isTrue();
            }
        }
    }

    @Test
    void testServerDemoHasContent() throws IOException {
        try (FtpServer server = FtpServerDemo.start(0)) {
            try (var client = new FtpClient()) {
                client.connect("127.0.0.1", server.getPort());
                client.loginAnonymous();
                var entries = client.list(null);
                assertThat(entries).isNotEmpty();
            }
        }
    }

    @Test
    void testServerDemoReadFile() throws IOException {
        try (FtpServer server = FtpServerDemo.start(0)) {
            try (var client = new FtpClient()) {
                client.connect("127.0.0.1", server.getPort());
                client.loginAnonymous();
                try (var is = client.get("/pub/readme.txt")) {
                    byte[] data = is.readAllBytes();
                    assertThat(data.length).isGreaterThan(0);
                }
            }
        }
    }

    @Test
    void testServerDemoStopsCleanly() throws IOException {
        FtpServer server = FtpServerDemo.start(0);
        assertThat(server.isRunning()).isTrue();
        server.close();
        assertThat(server.isRunning()).isFalse();
    }
}
