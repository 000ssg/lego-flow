package ssg.legoflow.interop.postgresql;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.postgresql.client.PgClient;
import ssg.legoflow.database.postgresql.client.PgResult;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability test: Lego Flow PostgreSQL client ↔ real PostgreSQL server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlInteropTest {

    private final String host = System.getProperty("interop.pg.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.pg.port", "5432"));
    private final String user = System.getProperty("interop.pg.user", "legoflow");
    private final String password = System.getProperty("interop.pg.password", "legoflow");
    private final String database = System.getProperty("interop.pg.db", "legoflow_test");

    private PgClient client;

    @BeforeAll
    void connect() throws IOException {
        this.client = PgClient.connect(host, port, database, user, password);
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testVersionQuery() throws Exception {
        PgResult result = client.query("SELECT version();");
        assertThat(result.rowCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getString(0, 0)).contains("PostgreSQL");
    }

    @Test
    void testCreateAndQueryTable() throws Exception {
        client.query("DROP TABLE IF EXISTS lego_flow_interop_test;");
        client.query("CREATE TABLE lego_flow_interop_test (id SERIAL PRIMARY KEY, name VARCHAR(100), value INT);");

        client.query("INSERT INTO lego_flow_interop_test (name, value) VALUES ('alpha', 100);");
        client.query("INSERT INTO lego_flow_interop_test (name, value) VALUES ('beta', 200);");
        client.query("INSERT INTO lego_flow_interop_test (name, value) VALUES ('gamma', 300);");

        PgResult selectResult = client.query(
                "SELECT name, value FROM lego_flow_interop_test ORDER BY id;");

        assertThat(selectResult.rowCount()).isEqualTo(3);
        assertThat(selectResult.getString(0, 0)).isEqualTo("alpha");
        assertThat(selectResult.getString(0, 1)).isEqualTo("100");
        assertThat(selectResult.getString(1, 0)).isEqualTo("beta");
        assertThat(selectResult.getString(2, 0)).isEqualTo("gamma");

        client.query("DROP TABLE IF EXISTS lego_flow_interop_test;");
    }

    @Test
    void testAggregateFunctions() throws Exception {
        client.query("DROP TABLE IF EXISTS lego_agg_test;");
        client.query("CREATE TABLE lego_agg_test (category TEXT, amount INT);");
        client.query("INSERT INTO lego_agg_test VALUES ('A', 10), ('A', 20), ('B', 30);");

        PgResult result = client.query(
                "SELECT category, COUNT(*), SUM(amount) FROM lego_agg_test GROUP BY category ORDER BY category;");

        assertThat(result.rowCount()).isEqualTo(2);

        assertThat(result.getString(0, 0)).isEqualTo("A");
        assertThat(result.getString(0, 1)).isEqualTo("2");
        assertThat(result.getString(0, 2)).isEqualTo("30");

        assertThat(result.getString(1, 0)).isEqualTo("B");
        assertThat(result.getString(1, 1)).isEqualTo("1");
        assertThat(result.getString(1, 2)).isEqualTo("30");

        client.query("DROP TABLE IF EXISTS lego_agg_test;");
    }

    @Test
    void testTransaction() throws Exception {
        client.query("DROP TABLE IF EXISTS lego_txn_test;");
        client.query("CREATE TABLE lego_txn_test (id INT PRIMARY KEY, data TEXT);");

        client.query("BEGIN;");
        client.query("INSERT INTO lego_txn_test VALUES (1, 'inside-txn');");

        PgResult verifyResult = client.query(
                "SELECT data FROM lego_txn_test WHERE id = 1;");
        assertThat(verifyResult.rowCount()).isEqualTo(1);
        assertThat(verifyResult.getString(0, 0)).isEqualTo("inside-txn");

        client.query("ROLLBACK;");

        PgResult checkResult = client.query(
                "SELECT COUNT(*) FROM lego_txn_test WHERE id = 1;");
        assertThat(checkResult.getString(0, 0)).isEqualTo("0");

        client.query("DROP TABLE IF EXISTS lego_txn_test;");
    }

    @Test
    void testListDatabases() throws Exception {
        PgResult result = client.query("SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;");
        assertThat(result.rowCount()).isGreaterThan(0);
        boolean found = false;
        for (int i = 0; i < result.rowCount(); i++) {
            if (result.getString(i, 0).equals(database)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Test database should be listed").isTrue();
    }

    @Test
    void testConnectionParameters() throws Exception {
        // Note: inet_server_addr() returns the Docker container internal IP, not "localhost".
        // We verify the user, database, and port match; for the server address we just
        // check it returned a non-null value (validating the connection is alive).
        PgResult result = client.query(
                "SELECT current_user, current_database(), inet_server_addr(), inet_server_port();");

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.getString(0, 0)).isEqualTo(user);
        assertThat(result.getString(0, 1)).isEqualTo(database);
        // Server IP is Docker's internal address (e.g. 172.18.0.x) — just verify it's not null
        assertThat(result.getString(0, 2)).isNotNull();
        assertThat(result.getString(0, 3)).isEqualTo(String.valueOf(port));
    }
}
