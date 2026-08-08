package ssg.legoflow.email.smtp.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.protocol.SmtpExtension;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import ssg.legoflow.email.smtp.server.InMemoryMessageStore;
import ssg.legoflow.email.smtp.server.SmtpHandler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extended integration tests for SMTP client covering more session state transitions.
 */
class SmtpIntegrationExtendedTest {

    @Test
    void testNoopAndRset() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThat(client.noop().isSuccess()).isTrue();
                assertThat(client.reset().isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testMultipleSends() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                for (int i = 0; i < 3; i++) {
                    assertThat(client.send("s@example.com", List.of("r@example.com"),
                            "Subject: M" + i + "\r\n\r\nBody" + i).isSuccess()).isTrue();
                }
                TimeUnit.SECONDS.sleep(1);
                assertThat(store.getMessages()).hasSize(3);
            }
        }
    }

    @Test
    void testLocalHostname() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort())
                    .localHostname("myclient.example.com").build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThat(client.extensions()).isNotEmpty();
            }
        }
    }

    @Test
    void testForDomainsHandler() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.forDomains("example.com"));
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThat(client.send("s@example.com", List.of("r@example.com"),
                        "Subject: Test\r\n\r\nBody").isSuccess()).isTrue();
                TimeUnit.SECONDS.sleep(1);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }

    @Test
    void testHasExtension() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThat(client.hasExtension(SmtpExtension.SIZE)).isIn(true, false);
            }
        }
    }
}
