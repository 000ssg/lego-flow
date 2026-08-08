package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SmtpServer lifecycle, getters/setters, and server behavior.
 */
class SmtpServerLifecycleTest {

    @Test
    void testServerCreationAndGetters() {
        try (var server = new SmtpServer("mail.example.com", 2525)) {
            assertThat(server.getHostname()).isEqualTo("mail.example.com");
            assertThat(server.isRunning()).isFalse();
            assertThat(server.getConnectionCount()).isEqualTo(0);
        }
    }

    @Test
    void testServerStartAndPort() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.start();
            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isGreaterThan(0);
        }
    }

    @Test
    void testServerStartFailsWithoutMessageStore() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            assertThatThrownBy(() -> server.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MessageStore");
        }
    }

    @Test
    void testServerSetSslContext() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            var ctx = javax.net.ssl.SSLContext.getInstance("TLSv1.3");
            ctx.init(null, null, null);
            server.setSslContext(ctx);
            server.setMessageStore(new InMemoryMessageStore());
            server.start();
            assertThat(server.getPort()).isGreaterThan(0);
        }
    }

    @Test
    void testServerSetRelayConfig() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.setRelayConfig(RelayConfig.builder().requireAuth(true).build());
            server.start();
            assertThat(server.getPort()).isGreaterThan(0);
        }
    }

    @Test
    void testServerStopExplicitly() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.start();
            TimeUnit.MILLISECONDS.sleep(200);
            server.stop();
            assertThat(server.isRunning()).isFalse();
        }
    }

    @Test
    void testServerClose() throws Exception {
        var server = new SmtpServer("localhost", 0);
        server.setMessageStore(new InMemoryMessageStore());
        server.start();
        TimeUnit.MILLISECONDS.sleep(100);
        server.close();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testHandlerAcceptSender() throws Exception {
        SmtpHandler handler = new SmtpHandler() {
            @Override public boolean acceptSender(String sender) {
                return "allowed@test.com".equals(sender);
            }
        };

        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(handler);
            server.start();

            var config = ssg.legoflow.email.smtp.client.SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new ssg.legoflow.email.smtp.client.SmtpClient(config)) {
                client.connect();
                try {
                    client.send("blocked@test.com", List.of("rcpt@test.com"), "Subject: X\r\n\r\nBody");
                    fail("Expected SmtpException");
                } catch (ssg.legoflow.email.smtp.client.SmtpException e) {
                    assertThat(e.replyCode()).isEqualTo(550);
                }
            }
        }
    }

    @Test
    void testHandlerAcceptMessage() throws Exception {
        SmtpHandler handler = new SmtpHandler() {
            @Override public boolean acceptMessage(MailEnvelope env) {
                return env.size() <= 10;
            }
        };

        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(handler);
            server.start();

            var config = ssg.legoflow.email.smtp.client.SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new ssg.legoflow.email.smtp.client.SmtpClient(config)) {
                client.connect();
                var reply = client.send("a@b.com", List.of("c@d.com"), "Hi");
                assertThat(reply.isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testHandlerAuthenticate() throws Exception {
        SmtpHandler authHandler = new SmtpHandler() {
            @Override public boolean authenticate(String u, String p) {
                return "valid".equals(u) && "secret".equals(p);
            }
        };

        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(authHandler);
            server.start();

            var config = ssg.legoflow.email.smtp.client.SmtpClientConfig.builder("127.0.0.1", server.getPort())
                    .auth("valid", "secret").authMechanism("PLAIN").build();
            try (var client = new ssg.legoflow.email.smtp.client.SmtpClient(config)) {
                client.connect();
                assertThat(client.isAuthenticated()).isTrue();
            }
        }
    }

    @Test
    void testHandlerForDomains() throws Exception {
        SmtpHandler handler = SmtpHandler.forDomains("allowed.com");
        
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(handler);
            server.start();

            var config = ssg.legoflow.email.smtp.client.SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new ssg.legoflow.email.smtp.client.SmtpClient(config)) {
                client.connect();
                var reply = client.send("ext@other.com", List.of("user@allowed.com"), "Subject: OK\r\n\r\nBody");
                assertThat(reply.isSuccess()).isTrue();
            }
        }
    }

    @Test
    void testHandlerAcceptAll() {
        SmtpHandler handler = SmtpHandler.acceptAll();
        assertThat(handler.authenticate("any", "thing")).isTrue();
        assertThat(handler.acceptSender("any@test.com")).isTrue();
        assertThat(handler.acceptRecipient("rcpt@test.com", "sender@test.com")).isTrue();
    }

    @Test
    void testNullHostnameThrows() {
        assertThatThrownBy(() -> new SmtpServer(null, 25))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMailEnvelopeDataCopy() throws Exception {
        byte[] data = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var env = new MailEnvelope("sender@test.com", List.of("rcpt@test.com"), data, "msg-1");
        
        // Mutate original array - should not affect envelope data
        data[0] = 'X';
        assertThat(env.data()[0]).isEqualTo((byte)'h');
    }

    @Test
    void testMailEnvelopeDataAsString() throws Exception {
        String body = "Hello, World!";
        var env = new MailEnvelope("s@test.com", List.of("r@test.com"),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), "msg-1");
        assertThat(env.dataAsString()).isEqualTo(body);
    }

    @Test
    void testMailEnvelopeToString() throws Exception {
        var env = new MailEnvelope("s@test.com", List.of("r@test.com"),
                "body".getBytes(), "msg-42");
        String str = env.toString();
        assertThat(str).contains("from=s@test.com");
        assertThat(str).contains("size=4");
    }

    @Test
    void testMailEnvelopeRcptParams() throws Exception {
        java.util.Map<String, java.util.Map<String, String>> rcptP = new java.util.HashMap<>();
        rcptP.put("rcpt@test.com", java.util.Map.of("NOTIFY", "SUCCESS"));
        
        var env = new MailEnvelope("s@test.com", List.of("rcpt@test.com"),
                "body".getBytes(), "msg-1", null, rcptP);
        assertThat(env.rcptParams("rcpt@test.com")).containsEntry("NOTIFY", "SUCCESS");
        assertThat(env.rcptParams("other@test.com")).isEmpty();
    }

    @Test
    void testMailEnvelopeSize() throws Exception {
        var env = new MailEnvelope("s@test.com", List.of("r@test.com"),
                "12345".getBytes(), "msg-1");
        assertThat(env.size()).isEqualTo(5);
    }
}
