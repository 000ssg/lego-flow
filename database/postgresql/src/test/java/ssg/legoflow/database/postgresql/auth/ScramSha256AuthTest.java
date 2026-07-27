package ssg.legoflow.database.postgresql.auth;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.client.PgClient;
import ssg.legoflow.database.postgresql.client.PgResult;
import ssg.legoflow.database.postgresql.server.PgServer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SCRAM-SHA-256 authentication via full client/server handshake.
 */
class ScramSha256AuthTest {

    @Test
    void testScramAuthFullHandshakeSuccess() throws IOException {
        var auth = new ScramSha256().addUser("alice", "secret123");
        try (var server = new PgServer(auth)) {
            server.start(0);
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "alice", "secret123")) {
                assertThat(client.isConnected()).isTrue();
            }
        }
    }

    @Test
    void testScramAuthQueryAfterAuth() throws IOException {
        var auth = new ScramSha256().addUser("bob", "password");
        try (var server = new PgServer(auth)) {
            server.start(0);
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "bob", "password")) {
                client.query("CREATE TABLE scram_test (id int4, name varchar)");
                client.query("INSERT INTO scram_test VALUES (1, 'hello')");
                PgResult result = client.query("SELECT * FROM scram_test");
                assertThat(result.rowCount()).isEqualTo(1);
                assertThat(result.getString(0, "name")).isEqualTo("hello");
                client.query("DROP TABLE scram_test");
            }
        }
    }

    @Test
    void testScramAuthWrongPassword() throws IOException {
        var auth = new ScramSha256().addUser("charlie", "correct");
        try (var server = new PgServer(auth)) {
            server.start(0);
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "charlie", "wrong"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testScramAuthUnknownUser() throws IOException {
        var auth = new ScramSha256().addUser("known", "pass");
        try (var server = new PgServer(auth)) {
            server.start(0);
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "unknown", "pass"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testScramAuthNullPassword() throws IOException {
        var auth = new ScramSha256().addUser("user", "pass");
        try (var server = new PgServer(auth)) {
            server.start(0);
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "user", null))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testScramAuthMultipleUsers() throws IOException {
        var auth = new ScramSha256()
                .addUser("user1", "pass1")
                .addUser("user2", "pass2");
        try (var server = new PgServer(auth)) {
            server.start(0);
            // User1
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "user1", "pass1")) {
                assertThat(client.isConnected()).isTrue();
            }
            // User2
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "user2", "pass2")) {
                assertThat(client.isConnected()).isTrue();
            }
        }
    }

    @Test
    void testScramAuthCustomIterations() throws IOException {
        var auth = new ScramSha256().withIterations(2048).addUser("dave", "mypass");
        try (var server = new PgServer(auth)) {
            server.start(0);
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "dave", "mypass")) {
                assertThat(client.isConnected()).isTrue();
                PgResult result = client.query("CREATE TABLE iter_test (x int4)");
                assertThat(result.commandTag()).isEqualTo("CREATE TABLE");
                client.query("DROP TABLE iter_test");
            }
        }
    }

    @Test
    void testScramAuthPreparedStatement() throws IOException {
        var auth = new ScramSha256().addUser("prep", "stmt");
        try (var server = new PgServer(auth)) {
            server.start(0);
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "prep", "stmt")) {
                client.query("CREATE TABLE prep_test (id int4, val varchar)");
                try (var stmt = client.prepare("INSERT INTO prep_test VALUES ($1, $2)")) {
                    stmt.execute("1", "alpha");
                    stmt.execute("2", "beta");
                }
                PgResult result = client.query("SELECT * FROM prep_test");
                assertThat(result.rowCount()).isEqualTo(2);
                client.query("DROP TABLE prep_test");
            }
        }
    }

    @Test
    void testScramAuthConcurrentClients() throws Exception {
        var auth = new ScramSha256().addUser("shared", "pass");
        try (var server = new PgServer(auth)) {
            server.start(0);
            // Connect two clients concurrently
            try (PgClient c1 = PgClient.connect("127.0.0.1", server.port(), "testdb", "shared", "pass");
                 PgClient c2 = PgClient.connect("127.0.0.1", server.port(), "testdb", "shared", "pass")) {
                assertThat(c1.isConnected()).isTrue();
                assertThat(c2.isConnected()).isTrue();
                c1.query("CREATE TABLE shared_test (id int4)");
                c1.query("INSERT INTO shared_test VALUES (1)");
                PgResult r2 = c2.query("SELECT * FROM shared_test");
                assertThat(r2.rowCount()).isEqualTo(1);
                c1.query("DROP TABLE shared_test");
            }
        }
    }

    @Test
    void testScramAuthServerSignatureVerification() throws IOException {
        // The client verifies the server's signature - if it were wrong,
        // the connection would fail with IOException
        var auth = new ScramSha256().addUser("verify", "signature");
        try (var server = new PgServer(auth)) {
            server.start(0);
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "verify", "signature")) {
                // Connection succeeded means server signature was verified
                assertThat(client.isConnected()).isTrue();
            }
        }
    }
}
