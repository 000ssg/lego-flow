package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for SmtpSession state machine by exercising all command handlers
 * through the client connection.
 */
class SmtpSessionStateTest {

    @Test void testRsetAfterMailFrom() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var rsetReply = client.reset();
                assertThat(rsetReply.isSuccess()).isTrue();

                var reply = client.send("sender@example.com",
                        List.of("rcpt@example.com"), "Subject: After reset\r\n\r\nBody");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.MILLISECONDS.sleep(200);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }

    @Test void testMultipleRcptTo() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var recipients = List.of(
                        "rcpt1@example.com",
                        "rcpt2@example.com",
                        "rcpt3@example.com"
                );
                var reply = client.send("sender@example.com", recipients,
                        "Subject: Multiple recipients\r\n\r\nBody");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.MILLISECONDS.sleep(200);
                var messages = store.getMessages();
                assertThat(messages).hasSize(1);
                assertThat(messages.get(0).recipients()).containsExactlyElementsOf(recipients);
            }
        }
    }

    @Test void testMailParamsWithSize() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var reply = client.send("sender@example.com",
                        List.of("rcpt@example.com"),
                        "Subject: Test\r\n\r\nBody", "SIZE=1024");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.MILLISECONDS.sleep(200);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }

    @Test void testVerifyCommand() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var reply = client.verify("testuser");
                assertThat(reply).isNotNull();
            }
        }
    }

    @Test void testNoopInVariousStates() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                assertThat(client.noop().isSuccess()).isTrue();

                client.send("s@example.com", List.of("r@example.com"), "Subject: Test\r\n\r\nBody");
                TimeUnit.MILLISECONDS.sleep(200);
                assertThat(client.noop().isSuccess()).isTrue();
            }
        }
    }

    @Test void testConnectionCount() throws Exception {
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(new InMemoryMessageStore());
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();

            try (var client1 = new SmtpClient(config)) {
                client1.connect();
                try (var client2 = new SmtpClient(config)) {
                    client2.connect();
                    TimeUnit.MILLISECONDS.sleep(100);
                    assertThat(server.getConnectionCount()).isEqualTo(2);

                    client2.close();
                    TimeUnit.MILLISECONDS.sleep(100);
                    assertThat(server.getConnectionCount()).isEqualTo(1);
                }
            }
        }
    }

    @Test void testServerWithRequireAuth() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.setRelayConfig(RelayConfig.builder().requireAuth(true).build());
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort())
                    .auth("testuser", "testpass").build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var reply = client.send("sender@example.com",
                        List.of("rcpt@example.com"), "Subject: Auth test\r\n\r\nBody");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.MILLISECONDS.sleep(200);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }

    @Test void testForDomainsAcceptsLocalRecipients() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.forDomains("example.com"));
            server.start();

            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            try (var client = new SmtpClient(config)) {
                client.connect();
                var reply = client.send("sender@example.com",
                        List.of("rcpt@example.com"), "Subject: Allowed\r\n\r\nBody");
                assertThat(reply.isSuccess()).isTrue();

                TimeUnit.MILLISECONDS.sleep(200);
                assertThat(store.getMessages()).hasSize(1);
            }
        }
    }
}
