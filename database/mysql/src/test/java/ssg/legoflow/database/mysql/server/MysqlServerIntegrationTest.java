package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;
import ssg.legoflow.database.mysql.common.Charset;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class MysqlServerIntegrationTest {

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

    @AfterAll static void stopServer() { if (server != null) server.close(); }

    @BeforeEach void connectClient() throws IOException {
        client = MysqlClient.connect("localhost", port, "test", "test", "testdb");
    }

    @AfterEach void disconnect() throws IOException { if (client != null) client.close(); }

    // ===== Charset Tests =====

    @Test void testCharsetValues() {
        Charset c = Charset.UTF8MB4_GENERAL_CI;
        assertThat(c.id()).isEqualTo(45);
    }

    // ===== Server Info Tests =====

    @Test void testServerPort() throws IOException {
        assertThat(port).isGreaterThan(0);
        assertThat(server.actualPort()).isEqualTo(port);
    }

    @Test void testConnectionInfo() throws IOException {
        assertThat(client.isConnected()).isTrue();
    }

    // ===== Basic Operations =====

    @Test void testCreateTableAndSelect() throws IOException {
        client.execute("CREATE TABLE t1 (id INT, name VARCHAR(20))");
        MysqlResult r = client.query("SELECT * FROM t1");
        assertThat(r.rowCount()).isEqualTo(0);
    }

    // ===== Error Handling =====

    @Test void testTableNotFound() throws IOException {
        assertThatThrownBy(() -> client.query("SELECT * FROM nonexistent"))
                .isInstanceOf(Exception.class);
    }

    @Test void testAuthFailure() throws IOException {
        assertThatThrownBy(() -> 
            MysqlClient.connect("localhost", port, "test", "wrong", "testdb"))
                .isInstanceOf(Exception.class);
    }

    // ===== Connection Tests =====

    @Test void testCloseClient() throws IOException {
        client.close();
    }
}
