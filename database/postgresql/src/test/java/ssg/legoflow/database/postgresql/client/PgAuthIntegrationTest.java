package ssg.legoflow.database.postgresql.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.auth.CleartextAuth;
import ssg.legoflow.database.postgresql.auth.Md5Auth;
import ssg.legoflow.database.postgresql.auth.ScramSha256;
import ssg.legoflow.database.postgresql.server.PgServer;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for authentication methods.
 */
class PgAuthIntegrationTest {

    // ======== Cleartext ========

    @Test
    void testCleartextAuthSuccess() throws IOException {
        var auth = new CleartextAuth().addUser("alice", "secret");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "alice", "secret")) {
                assertThat(client.isConnected()).isTrue();
                PgResult result = client.query("CREATE TABLE t (id int4)");
                assertThat(result.commandTag()).isEqualTo("CREATE TABLE");
            }
        }
    }

    @Test
    void testCleartextAuthFailure() throws IOException {
        var auth = new CleartextAuth().addUser("alice", "secret");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "alice", "wrong"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testCleartextAuthNoPassword() throws IOException {
        var auth = new CleartextAuth().addUser("alice", "secret");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "alice", null))
                    .isInstanceOf(IOException.class);
        }
    }

    // ======== MD5 ========

    @Test
    void testMd5AuthSuccess() throws IOException {
        var auth = new Md5Auth().addUser("bob", "password123");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "bob", "password123")) {
                assertThat(client.isConnected()).isTrue();
                client.query("CREATE TABLE t (id int4)");
                PgResult result = client.query("INSERT INTO t VALUES (1)");
                assertThat(result.commandTag()).isEqualTo("INSERT 0 1");
            }
        }
    }

    @Test
    void testMd5AuthFailure() throws IOException {
        var auth = new Md5Auth().addUser("bob", "password123");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "bob", "wrongpass"))
                    .isInstanceOf(IOException.class);
        }
    }

    // ======== SCRAM-SHA-256 ========

    @Test
    void testScramAuthSuccess() throws IOException {
        var auth = new ScramSha256().addUser("charlie", "scrampass");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "charlie", "scrampass")) {
                assertThat(client.isConnected()).isTrue();
                client.query("CREATE TABLE t (id int4, name varchar)");
                client.query("INSERT INTO t VALUES (1, 'test')");
                PgResult result = client.query("SELECT * FROM t");
                assertThat(result.rowCount()).isEqualTo(1);
            }
        }
    }

    @Test
    void testScramAuthFailure() throws IOException {
        var auth = new ScramSha256().addUser("charlie", "scrampass");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "charlie", "wrongpass"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testScramAuthUnknownUser() throws IOException {
        var auth = new ScramSha256().addUser("charlie", "scrampass");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            assertThatThrownBy(() ->
                    PgClient.connect("127.0.0.1", server.port(), "testdb", "unknown", "scrampass"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void testScramAuthWithCustomIterations() throws IOException {
        var auth = new ScramSha256().withIterations(2048).addUser("dave", "mypass");
        var server = new PgServer(auth);
        server.start(0);
        try (server) {
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "dave", "mypass")) {
                assertThat(client.isConnected()).isTrue();
            }
        }
    }

    // ======== Trust (no auth) ========

    @Test
    void testTrustAuth() throws IOException {
        var server = new PgServer();
        server.start(0);
        try (server) {
            try (PgClient client = PgClient.connect("127.0.0.1", server.port(), "testdb", "anyone", null)) {
                assertThat(client.isConnected()).isTrue();
            }
        }
    }
}
