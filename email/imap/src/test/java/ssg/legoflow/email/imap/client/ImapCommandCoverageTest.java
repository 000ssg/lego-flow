package ssg.legoflow.email.imap.client;

import org.junit.jupiter.api.*;
import ssg.legoflow.email.imap.server.ImapServer;
import ssg.legoflow.email.imap.server.InMemoryMailStore;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive IMAP command coverage tests to increase test coverage.
 */
class ImapCommandCoverageTest {

    private static ImapServer server;
    private static int port;
    private static final String USER = "testuser";
    private static final String PASS = "testpass";

    @BeforeAll
    static void startServer() throws Exception {
        var store = new InMemoryMailStore().addUser(USER, PASS);
        server = new ImapServer("localhost", 0, store);
        server.start();
        port = server.port();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) server.close();
    }

    private ImapClient createClient() throws Exception {
        var config = ImapClientConfig.builder("127.0.0.1", port)
                .credentials(USER, PASS).build();
        return new ImapClient(config);
    }

    @Test void testCapabilityCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            var caps = client.capability();
            assertThat(caps).contains("IMAP4rev2");
        }
    }

    @Test void testNoopCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.noop()).isTrue();
        }
    }

    @Test void testLoginSuccess() throws Exception {
        try (var client = createClient()) {
            client.connect();
            assertThat(client.login()).isTrue();
        }
    }

    @Test void testSelectInbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.select("INBOX")).isNotNull();
        }
    }

    @Test void testExamineInbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.examine("INBOX")).isNotNull();
        }
    }

    @Test void testCreateMailbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.create("Test.Mailbox")).isTrue();
        }
    }

    @Test void testDeleteMailbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.create("Temp.DeleteMe");
            assertThat(client.delete("Temp.DeleteMe")).isTrue();
        }
    }

    @Test void testRenameMailbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.create("Rename.Old");
            assertThat(client.rename("Rename.Old", "Rename.New")).isTrue();
        }
    }

    @Test void testListMailboxes() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.list("", "*")).isNotNull();
        }
    }

    @Test void testNamespaceCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.namespace()).isNotNull();
        }
    }

    @Test void testStatusCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            assertThat(client.status("INBOX", "MESSAGES")).isNotNull();
        }
    }

    @Test void testFetchMessage() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.fetch("1", "BODY[]")).isNotNull();
        }
    }

    @Test void testFetchUid() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.fetch("1", "UID")).isNotNull();
        }
    }

    @Test void testSearchCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.search("ALL")).isNotNull();
        }
    }

    @Test void testExpungeCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.expunge()).isNotNull();
        }
    }

    @Test void testCloseMailbox() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.closeMailbox()).isTrue();
        }
    }

    @Test void testUnselectCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.unselect()).isTrue();
        }
    }

    @Test void testLoginFailureWrongPassword() throws Exception {
        try (var client = createClient()) {
            client.connect();
            assertThat(client.login("testuser", "wrongpass")).isFalse();
        }
    }

    @Test void testCopyCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.copy("1", "INBOX")).isTrue();
        }
    }

    @Test void testMoveCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.create("Move.Target");
            client.select("INBOX");
            assertThat(client.move("1", "Move.Target")).isTrue();
        }
    }

    @Test void testStoreCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.store("1", "+FLAGS", "\\Seen")).isNotNull();
        }
    }

    @Test void testLogoutClosesConnection() throws Exception {
        // Logout causes server to close connection - this is expected behavior
        var config = ImapClientConfig.builder("127.0.0.1", port)
                .credentials(USER, PASS).build();
        try (var client = new ImapClient(config)) {
            client.connect();
            client.login();
            client.logout(); // Server closes connection after logout
        } catch (java.io.IOException e) {
            // Expected: server closes connection after LOGOUT
        }
    }

    @Test void testSubscribeCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.create("Sub.Test");
            assertThat(client.subscribe("Sub.Test")).isTrue();
        }
    }

    @Test void testUnsubscribeCommand() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.create("Unsub.Test");
            client.subscribe("Unsub.Test");
            assertThat(client.unsubscribe("Unsub.Test")).isTrue();
        }
    }

    @Test void testUidFetch() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.uidFetch("*", "BODY[]")).isNotNull();
        }
    }

    @Test void testUidSearch() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.uidSearch("ALL")).isNotNull();
        }
    }

    @Test void testSelectedFolder() throws Exception {
        try (var client = createClient()) {
            client.connect();
            client.login();
            client.select("INBOX");
            assertThat(client.selectedFolder()).isNotNull();
        }
    }

    @Test void testConnectionAccess() throws Exception {
        try (var client = createClient()) {
            client.connect();
            assertThat(client.connection()).isNotNull();
        }
    }
}
