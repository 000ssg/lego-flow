package ssg.legoflow.email.imap.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.imap.server.ImapServer;
import ssg.legoflow.email.imap.server.InMemoryMailStore;
import static org.assertj.core.api.Assertions.*;

class ImapIntegrationTest {

    @Test void testConnectAndLogin() throws Exception {
        var store = new InMemoryMailStore().addUser("testuser", "testpass");
        try (var server = new ImapServer("localhost", 0, store)) {
            server.start();

            var config = ImapClientConfig.builder("127.0.0.1", server.port())
                    .credentials("testuser", "testpass").build();
            try (var client = new ImapClient(config)) {
                client.connect();
                boolean ok = client.login();
                assertThat(ok).isTrue();
            }
        }
    }

    @Test void testConnectList() throws Exception {
        var store = new InMemoryMailStore().addUser("testuser", "testpass");
        try (var server = new ImapServer("localhost", 0, store)) {
            server.start();

            var config = ImapClientConfig.builder("127.0.0.1", server.port())
                    .credentials("testuser", "testpass").build();
            try (var client = new ImapClient(config)) {
                client.connect();
                client.login();
                var mailboxes = client.list("", "*");
                assertThat(mailboxes).isNotNull();
            }
        }
    }

    @Test void testConnectSelectInbox() throws Exception {
        var store = new InMemoryMailStore().addUser("testuser", "testpass");
        try (var server = new ImapServer("localhost", 0, store)) {
            server.start();

            var config = ImapClientConfig.builder("127.0.0.1", server.port())
                    .credentials("testuser", "testpass").build();
            try (var client = new ImapClient(config)) {
                client.connect();
                client.login();
                var folder = client.select("INBOX");
                assertThat(folder).isNotNull();
            }
        }
    }

    @Test void testCloseWithoutConnect() throws Exception {
        var config = ImapClientConfig.builder("localhost", 143)
                .credentials("u", "p").build();
        try (var client = new ImapClient(config)) {}
    }

    @Test void testClientRejectsNullConfig() {
        assertThatThrownBy(() -> new ImapClient(null)).isInstanceOf(NullPointerException.class);
    }
}
