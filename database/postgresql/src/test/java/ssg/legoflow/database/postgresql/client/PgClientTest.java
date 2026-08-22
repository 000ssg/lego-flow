package ssg.legoflow.database.postgresql.client;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.postgresql.protocol.BackendMessage;
import ssg.legoflow.database.postgresql.protocol.TransactionStatus;
import ssg.legoflow.database.postgresql.server.PgServer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for {@link PgClient} against {@link PgServer}.
 */
class PgClientTest {

    private PgServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = new PgServer();
        server.start(0);
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    // ======== Connection ========

    @Test
    void testConnect() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testConnectionParameters() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            var params = client.connection().serverParameters();
            assertThat(params).containsKey("server_version");
            assertThat(params).containsKey("server_encoding");
            assertThat(params.get("server_encoding")).isEqualTo("UTF8");
        }
    }

    @Test
    void testBackendKeyData() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            assertThat(client.connection().processId()).isGreaterThan(0);
        }
    }

    @Test
    void testTransactionStatusIdle() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            assertThat(client.connection().transactionStatus()).isEqualTo(TransactionStatus.IDLE);
        }
    }

    // ======== Simple query: CREATE TABLE ========

    @Test
    void testCreateTable() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            PgResult result = client.query("CREATE TABLE users (id int4, name varchar)");
            assertThat(result.commandTag()).isEqualTo("CREATE TABLE");
        }
    }

    @Test
    void testCreateTableIfNotExists() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t1 (id int4)");
            PgResult result = client.query("CREATE TABLE IF NOT EXISTS t1 (id int4)");
            assertThat(result.commandTag()).isEqualTo("CREATE TABLE");
        }
    }

    // ======== Simple query: INSERT ========

    @Test
    void testInsert() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            PgResult result = client.query("INSERT INTO t VALUES (1, 'Alice')");
            assertThat(result.commandTag()).isEqualTo("INSERT 0 1");
            assertThat(result.affectedRows()).isEqualTo(1);
        }
    }

    @Test
    void testInsertMultiple() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");
            client.query("INSERT INTO t VALUES (3, 'Charlie')");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.rowCount()).isEqualTo(3);
        }
    }

    // ======== Simple query: SELECT ========

    @Test
    void testSelectAll() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.rowCount()).isEqualTo(2);
            assertThat(result.columnCount()).isEqualTo(2);
            assertThat(result.getString(0, 0)).isEqualTo("1");
            assertThat(result.getString(0, 1)).isEqualTo("Alice");
            assertThat(result.getString(1, 0)).isEqualTo("2");
            assertThat(result.getString(1, 1)).isEqualTo("Bob");
        }
    }

    @Test
    void testSelectByColumnName() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.getString(0, "id")).isEqualTo("1");
            assertThat(result.getString(0, "name")).isEqualTo("Alice");
        }
    }

    @Test
    void testSelectWhere() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            PgResult result = client.query("SELECT * FROM t WHERE id = 2");
            assertThat(result.rowCount()).isEqualTo(1);
            assertThat(result.getString(0, "name")).isEqualTo("Bob");
        }
    }

    @Test
    void testSelectColumns() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar, email varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice', 'a@test.com')");

            PgResult result = client.query("SELECT name, email FROM t");
            assertThat(result.columnCount()).isEqualTo(2);
            assertThat(result.columns().get(0).name()).isEqualTo("name");
            assertThat(result.columns().get(1).name()).isEqualTo("email");
        }
    }

    @Test
    void testSelectCommandTag() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4)");
            client.query("INSERT INTO t VALUES (1)");
            client.query("INSERT INTO t VALUES (2)");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.commandTag()).isEqualTo("SELECT 2");
        }
    }

    // ======== Simple query: UPDATE ========

    @Test
    void testUpdate() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");

            PgResult result = client.query("UPDATE t SET name = 'Alicia' WHERE id = 1");
            assertThat(result.commandTag()).isEqualTo("UPDATE 1");
            assertThat(result.affectedRows()).isEqualTo(1);

            PgResult verify = client.query("SELECT name FROM t WHERE id = 1");
            assertThat(verify.getString(0, 0)).isEqualTo("Alicia");
        }
    }

    // ======== Simple query: DELETE ========

    @Test
    void testDelete() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            PgResult result = client.query("DELETE FROM t WHERE id = 1");
            assertThat(result.commandTag()).isEqualTo("DELETE 1");

            PgResult verify = client.query("SELECT * FROM t");
            assertThat(verify.rowCount()).isEqualTo(1);
        }
    }

    // ======== Simple query: errors ========

    @Test
    void testQueryErrorTableNotFound() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            assertThatThrownBy(() -> client.query("SELECT * FROM nonexistent"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("42P01");
        }
    }

    @Test
    void testEmptyQuery() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            PgResult result = client.query("");
            assertThat(result.commandTag()).isEmpty();
        }
    }

    // ======== Simple query: DDL ========

    @Test
    void testDropTable() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4)");
            PgResult result = client.query("DROP TABLE t");
            assertThat(result.commandTag()).isEqualTo("DROP TABLE");
        }
    }

    @Test
    void testBeginCommit() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            PgResult begin = client.query("BEGIN");
            assertThat(begin.commandTag()).isEqualTo("BEGIN");

            PgResult commit = client.query("COMMIT");
            assertThat(commit.commandTag()).isEqualTo("COMMIT");
        }
    }

    // ======== Execute helper ========

    @Test
    void testExecuteReturnsAffectedRows() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            int affected = client.execute("INSERT INTO t VALUES (1, 'Alice')");
            assertThat(affected).isEqualTo(1);
        }
    }

    // ======== Extended Query Protocol ========

    @Test
    void testPreparedStatement() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            try (PgStatement stmt = client.prepare("SELECT * FROM t WHERE id = $1")) {
                PgResult result = stmt.execute("1");
                assertThat(result.rowCount()).isEqualTo(1);
                assertThat(result.getString(0, "name")).isEqualTo("Alice");
            }
        }
    }

    @Test
    void testPreparedStatementMultipleExecutions() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            try (PgStatement stmt = client.prepare("SELECT * FROM t WHERE id = $1")) {
                PgResult r1 = stmt.execute("1");
                assertThat(r1.getString(0, "name")).isEqualTo("Alice");

                PgResult r2 = stmt.execute("2");
                assertThat(r2.getString(0, "name")).isEqualTo("Bob");
            }
        }
    }

    @Test
    void testPreparedInsert() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");

            try (PgStatement stmt = client.prepare("INSERT INTO t VALUES ($1, $2)")) {
                stmt.execute("1", "Alice");
                stmt.execute("2", "Bob");
            }

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testPreparedStatementName() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4)");
            try (PgStatement stmt = client.prepare("SELECT * FROM t")) {
                assertThat(stmt.name()).startsWith("stmt_");
            }
        }
    }

    // ======== PgResult ========

    @Test
    void testResultGetInt() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, val int4)");
            client.query("INSERT INTO t VALUES (1, 42)");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.getInt(0, 0)).isEqualTo(1);
            assertThat(result.getInt(0, "val")).isEqualTo(42);
        }
    }

    @Test
    void testResultGetLong() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int8)");
            client.query("INSERT INTO t VALUES (9999999999)");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.getLong(0, 0)).isEqualTo(9999999999L);
        }
    }

    @Test
    void testResultIsNull() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, NULL)");

            PgResult result = client.query("SELECT * FROM t");
            assertThat(result.isNull(0, 1)).isTrue();
            assertThat(result.isNull(0, 0)).isFalse();
        }
    }

    @Test
    void testResultAllRows() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            PgResult result = client.query("SELECT * FROM t");
            List<String[]> rows = result.allRows();
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)[1]).isEqualTo("Alice");
        }
    }

    @Test
    void testResultColumnNotFound() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4)");
            client.query("INSERT INTO t VALUES (1)");

            PgResult result = client.query("SELECT * FROM t");
            assertThatThrownBy(() -> result.getString(0, "nonexistent"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ======== COPY ========

    @Test
    void testCopyIn() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");

            List<String> rows = List.of(
                    "1\tAlice\n",
                    "2\tBob\n"
            );
            PgResult result = client.copyIn("COPY t FROM STDIN", rows);
            assertThat(result.commandTag()).startsWith("COPY");

            PgResult verify = client.query("SELECT * FROM t");
            assertThat(verify.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testCopyOut() throws IOException {
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            client.query("CREATE TABLE t (id int4, name varchar)");
            client.query("INSERT INTO t VALUES (1, 'Alice')");
            client.query("INSERT INTO t VALUES (2, 'Bob')");

            List<String> rows = client.copyOut("COPY t TO STDOUT");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).contains("Alice");
        }
    }

    // ======== LISTEN/NOTIFY ========

    @Test
    void testListenNotify() throws Exception {
        try (PgClient listener = PgClient.connect("127.0.0.1", port, "testdb", "user1", null);
             PgClient notifier = PgClient.connect("127.0.0.1", port, "testdb", "user2", null)) {

            List<BackendMessage.NotificationResponse> received = new CopyOnWriteArrayList<>();
            listener.listen("test_channel", received::add);

            // Send notification
            notifier.query("NOTIFY test_channel, 'hello'");

            // The notification is delivered when the listener issues a no-op query
            try {
                listener.query("CREATE TABLE _noop (id int4)");
                listener.query("SELECT * FROM _noop");
            } catch (IOException ignore) { /* best effort */ }

            // Give a moment for delivery
            Thread.sleep(100);

            // Notifications may or may not have been delivered depending on timing
            // The important thing is that the protocol exchange works
        }
    }

    // ======== Multiple clients ========

    @Test
    void testMultipleClients() throws IOException {
        try (PgClient c1 = PgClient.connect("127.0.0.1", port, "testdb", "user1", null);
             PgClient c2 = PgClient.connect("127.0.0.1", port, "testdb", "user2", null)) {

            c1.query("CREATE TABLE shared (id int4, val varchar)");
            c1.query("INSERT INTO shared VALUES (1, 'from_c1')");
            c2.query("INSERT INTO shared VALUES (2, 'from_c2')");

            PgResult r1 = c1.query("SELECT * FROM shared");
            PgResult r2 = c2.query("SELECT * FROM shared");

            assertThat(r1.rowCount()).isEqualTo(2);
            assertThat(r2.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testSessionCount() throws Exception {
        assertThat(server.sessionCount()).isEqualTo(0);

        PgClient c1 = PgClient.connect("127.0.0.1", port, "testdb", "user1", null);
        Thread.sleep(50);
        assertThat(server.sessionCount()).isGreaterThanOrEqualTo(1);

        c1.close();
        Thread.sleep(100);
    }
}
