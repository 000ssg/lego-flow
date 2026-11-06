package ssg.legoflow.interop.mysql;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

/**
 * Interoperability test: Lego Flow MySQL client ↔ real MySQL server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Requires external MySQL: docker compose -f interop-tests/docker-compose.core.yml up -d mysql")
class MysqlInteropTest {

    private final String host = System.getProperty("interop.mysql.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.mysql.port", "3306"));
    private final String user = System.getProperty("interop.mysql.user", "root");
    private final String password = System.getProperty("interop.mysql.password", "root");
    private final String database = System.getProperty("interop.mysql.db", "legoflow_test");

    private MysqlClient client;

    @BeforeAll
    void connect() throws IOException {
        this.client = MysqlClient.connect(host, port, user, password, database);
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testVersionQuery() throws Exception {
        MysqlResult result = client.query("SELECT VERSION()");
        assertThat(result.rowCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testCreateAndQueryTable() throws Exception {
        client.execute("DROP TABLE IF EXISTS lego_flow_interop_test");
        client.execute("CREATE TABLE lego_flow_interop_test (id INT PRIMARY KEY, name VARCHAR(100), value INT)");

        client.execute("INSERT INTO lego_flow_interop_test (id, name, value) VALUES (1, 'alpha', 100)");
        client.execute("INSERT INTO lego_flow_interop_test (id, name, value) VALUES (2, 'beta', 200)");
        client.execute("INSERT INTO lego_flow_interop_test (id, name, value) VALUES (3, 'gamma', 300)");

        MysqlResult selectResult = client.query("SELECT name, value FROM lego_flow_interop_test ORDER BY id");

        assertThat(selectResult.rowCount()).isEqualTo(3);
        assertThat(selectResult.getString(0, 0)).isEqualTo("alpha");
        assertThat(selectResult.getString(0, 1)).isEqualTo("100");
        assertThat(selectResult.getString(1, 0)).isEqualTo("beta");
        assertThat(selectResult.getString(2, 0)).isEqualTo("gamma");

        client.execute("DROP TABLE IF EXISTS lego_flow_interop_test");
    }
}
