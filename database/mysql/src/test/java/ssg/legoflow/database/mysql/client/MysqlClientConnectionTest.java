package ssg.legoflow.database.mysql.client;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.mysql.server.MysqlServer;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
class MysqlClientConnectionTest {

    private static MysqlServer server;
    private static int port;
    private MysqlClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = new MysqlServer("localhost", 0);
        server.addUser("test", "test");
        server.createDatabase("testdb");
        server.start();
        port = server.actualPort();
    }

    @AfterAll static void stopServer() throws Exception { if (server != null) server.close(); }

    @BeforeEach void connectClient() throws IOException {
        client = MysqlClient.connect("localhost", port, "test", "test", "testdb");
    }

    @AfterEach void disconnect() throws IOException { if (client != null) client.close(); }

    @Test void testConnectionInfo() throws Exception {
        assertThat(client.isConnected()).isTrue();
        assertThat(client.serverVersion()).startsWith("8.0");
        var conn = client.connection();
        assertThat(conn).isNotNull();
        assertThat(conn.isConnected()).isTrue();
    }

    @Test void testPing() throws Exception {
        assertThat(client.ping()).isTrue();
    }

    @Test void testCreateTableAndSelect() throws Exception {
        client.execute("CREATE TABLE IF NOT EXISTS conn_test (id INT PRIMARY KEY, name VARCHAR(20))");
        var result = client.query("SELECT * FROM conn_test");
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test void testConnectionErrorHandling() throws Exception {
        try {
            client.execute("INVALID SQL");
        } catch (Exception e) {
            // Expected - connection should still work after error
        }
        assertThat(client.isConnected()).isTrue();
    }

    @Test void testStatistics() throws Exception {
        assertThat(client.statistics()).isNotNull();
    }
}
