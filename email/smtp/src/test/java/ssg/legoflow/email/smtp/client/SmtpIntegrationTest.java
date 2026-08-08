package ssg.legoflow.email.smtp.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import ssg.legoflow.email.smtp.server.InMemoryMessageStore;
import ssg.legoflow.email.smtp.server.MailEnvelope;
import ssg.legoflow.email.smtp.server.RelayConfig;
import ssg.legoflow.email.smtp.server.SmtpHandler;
import ssg.legoflow.email.smtp.server.SmtpServer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SMTP client against a real {@link SmtpServer}.
 */
class SmtpIntegrationTest {

    @Test
    void testBasicSendReceive() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                SmtpReply reply = client.send("alice@example.com",
                        List.of("bob@example.com"),
                        "Subject: Test\r\nFrom: alice@example.com\r\nTo: bob@example.com\r\n\r\nHello!");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.SECONDS.sleep(1);
                var messages = store.getMessages();
                assertThat(messages).hasSize(1);
                MailEnvelope env = messages.get(0);
                assertThat(env.sender()).isEqualTo("alice@example.com");
                assertThat(env.recipients()).containsExactly("bob@example.com");
            }
        }
    }

    @Test
    void testSendMultipleRecipients() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                client.send("alice@example.com",
                        List.of("bob@example.com", "carol@example.com"),
                        "Subject: Group\r\n\r\nHi all!");
                TimeUnit.SECONDS.sleep(1);
                assertThat(store.getMessages()).hasSize(1);
                assertThat(store.getMessages().get(0).recipients())
                        .containsExactlyInAnyOrder("bob@example.com", "carol@example.com");
            }
        }
    }

    @Test
    void testSendMultipleMessages() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                client.send("alice@example.com", List.of("bob@example.com"), "Subject: 1\r\n\r\nOne");
                client.send("carol@example.com", List.of("dave@example.com"), "Subject: 2\r\n\r\nTwo");
                TimeUnit.SECONDS.sleep(1);
                assertThat(store.getMessages()).hasSize(2);
            }
        }
    }

    @Test
    void testAuthPlain() throws Exception {
        var store = new InMemoryMessageStore();
        SmtpHandler authHandler = new SmtpHandler() {
            @Override
            public boolean authenticate(String u, String p) {
                return "testuser".equals(u) && "testpass".equals(p);
            }
        };
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(authHandler);
            server.setRelayConfig(RelayConfig.builder().requireAuth(true).build());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort())
                    .auth("testuser", "testpass")
                    .authMechanism("PLAIN")
                    .build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                SmtpReply reply = client.send("alice@example.com",
                        List.of("bob@example.com"),
                        "Subject: Authed\r\n\r\nAuthenticated!");
                assertThat(reply.isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testAuthFailure() throws Exception {
        var store = new InMemoryMessageStore();
        SmtpHandler authHandler = new SmtpHandler() {
            @Override
            public boolean authenticate(String u, String p) {
                return false; // reject all
            }
        };
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(authHandler);
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort())
                    .auth("user", "wrong")
                    .build();
            try (var client = new SmtpClient(config)) {
                assertThatThrownBy(() -> client.connect())
                        .isInstanceOf(Exception.class);
            }
        }
    }

    @Test
    void testRejectSender() throws Exception {
        var store = new InMemoryMessageStore();
        SmtpHandler rejectSender = new SmtpHandler() {
            @Override
            public boolean acceptSender(String sender) {
                return !"spam@example.com".equals(sender);
            }
        };
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(rejectSender);
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThatThrownBy(() -> client.send(
                        "spam@example.com",
                        List.of("victim@example.com"),
                        "Subject: Spam\r\n\r\nBuy now!"))
                        .isInstanceOf(SmtpException.class);
            }
        }
    }

    @Test
    void testRejectRecipient() throws Exception {
        var store = new InMemoryMessageStore();
        SmtpHandler rejectRecipient = SmtpHandler.forDomains("allowed.com");
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(rejectRecipient);
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThatThrownBy(() -> client.send(
                        "sender@allowed.com",
                        List.of("rcpt@blocked.com"),
                        "Subject: Blocked\r\n\r\nNope"))
                        .isInstanceOf(SmtpException.class);
            }
        }
    }

    @Test
    void testRelayConfigRequireAuth() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.setRelayConfig(RelayConfig.builder().requireAuth(true).build());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThatThrownBy(() -> client.send(
                        "sender@example.com",
                        List.of("rcpt@example.com"),
                        "Subject: NoAuth\r\n\r\nBody"))
                        .isInstanceOf(SmtpException.class);
            }
        }
    }

    @Test
    void testSmtpClientConfigBuilder() {
        var config = SmtpClientConfig.builder("smtp.example.com", 587)
                .localHostname("myhost.local")
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .readTimeout(java.time.Duration.ofSeconds(60))
                .auth("user", "pass")
                .build();

        assertThat(config.host()).isEqualTo("smtp.example.com");
        assertThat(config.port()).isEqualTo(587);
        assertThat(config.localHostname()).isEqualTo("myhost.local");
        assertThat(config.hasAuth()).isTrue();
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.password()).isEqualTo("pass");
    }

    @Test
    void testSmtpClientConfigBuilderNoAuth() {
        var config = SmtpClientConfig.builder("smtp.example.com", 25).build();
        assertThat(config.hasAuth()).isFalse();
        assertThat(config.tlsMode()).isEqualTo(SmtpClientConfig.TlsMode.NONE);
    }

    @Test
    void testSmtpClientConfigBuilderTlsMode() {
        var config = SmtpClientConfig.builder("smtp.example.com", 465)
                .tlsMode(SmtpClientConfig.TlsMode.IMPLICIT)
                .build();
        assertThat(config.tlsMode()).isEqualTo(SmtpClientConfig.TlsMode.IMPLICIT);
    }

    @Test
    void testSmtpClientClose() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            SmtpClient client = new SmtpClient(config);
            client.connect();
            client.close();
        }
    }

    @Test
    void testInMemoryMessageStoreOperations() {
        var store = new InMemoryMessageStore();
        byte[] data = "test msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("sender@test.com",
                List.of("rcpt@test.com"), data, "msg-001");
        var result = store.store(envelope);
        assertThat(result.messageId()).isNotBlank();
        assertThat(result.accepted()).isTrue();

        assertThat(store.getMessages()).hasSize(1);
        store.clear();
        assertThat(store.getMessages()).isEmpty();
    }

    @Test
    void testDeliveryResultSuccess() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            var result = MessageSubmission.send(
                    config, "alice@example.com", List.of("bob@example.com"),
                    "Subject: Test\r\n\r\nBody");
            assertThat(result.success()).isTrue();
        }
    }

    @Test
    void testDeliveryResultFailure() {
        var config = SmtpClientConfig.builder("127.0.0.1", 1).build(); // port 1 will fail
        var result = MessageSubmission.send(
                config, "alice@example.com", List.of("bob@example.com"),
                "Subject: Test\r\n\r\nBody");
        assertThat(result.success()).isFalse();
    }

    @Test
    void testDeliveryResultSimple() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            var result = MessageSubmission.sendSimple(
                    config, "alice@example.com", List.of("bob@example.com"),
                    "Test Subject", "Hello Body");
            assertThat(result.success()).isTrue();
        }
    }

    @Test
    void testServerStartStopLifecycle() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.start();
            assertThat(server.getPort()).isGreaterThan(0);
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    @Test
    void testServerWithoutMessageStoreThrows() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            assertThatThrownBy(() -> server.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MessageStore");
        }
    }

    @Test
    void testOpenRelayAllowsExternal() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                SmtpReply reply = client.send("external@other.com",
                        List.of("recipient@anywhere.com"),
                        "Subject: Open\r\n\r\nOpen relay test");
                assertThat(reply.isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testSmtpExceptionWithReply() {
        SmtpReply reply = SmtpReply.of(550, "Access denied");
        var ex = new SmtpException("test", reply);
        assertThat(ex.reply()).isEqualTo(reply);
        assertThat(ex.replyCode()).isEqualTo(550);
        assertThat(ex.getMessage()).contains("test");
    }

    @Test
    void testSmtpExceptionWithoutReply() {
        var ex = new SmtpException("plain error");
        assertThat(ex.reply()).isNull();
        assertThat(ex.replyCode()).isEqualTo(-1);
    }

    @Test
    void testSmtpExceptionWithCause() {
        var cause = new RuntimeException("IO failure");
        var ex = new SmtpException("network", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.replyCode()).isEqualTo(-1);
    }

    @Test
    void testMailEnvelopeFields() {
        byte[] data = "body".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("sender@test.com", List.of("rcpt@test.com"), data, "msg-001");
        assertThat(envelope.sender()).isEqualTo("sender@test.com");
        assertThat(envelope.recipients()).containsExactly("rcpt@test.com");
        assertThat(envelope.data()).hasSize(4);
        assertThat(envelope.messageId()).isEqualTo("msg-001");
    }

    @Test
    void testMessageStoreException() {
        var ex = new ssg.legoflow.email.smtp.server.MessageStoreException("store error");
        assertThat(ex.getMessage()).isEqualTo("store error");
    }

    @Test
    void testDomainRestrictedHandler() throws Exception {
        var store = new InMemoryMessageStore();
        SmtpHandler domainHandler = SmtpHandler.forDomains("allowed.com", "local.org");
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(domainHandler);
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                SmtpReply reply = client.send("ext@other.com",
                        List.of("user@allowed.com"),
                        "Subject: Allowed\r\n\r\nOK");
                assertThat(reply.isSuccess()).isTrue();
                TimeUnit.SECONDS.sleep(1);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }

    @Test
    void testStoreResultSuccess() {
        var result = ssg.legoflow.email.smtp.server.MessageStore.StoreResult.success("msg-42");
        assertThat(result.messageId()).isEqualTo("msg-42");
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void testStoreResultRejected() {
        var result = ssg.legoflow.email.smtp.server.MessageStore.StoreResult.rejected("full");
        assertThat(result.messageId()).isNull();
        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).isEqualTo("full");
    }
}
