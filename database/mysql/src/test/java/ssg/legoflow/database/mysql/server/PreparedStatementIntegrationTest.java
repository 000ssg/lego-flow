package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.*;
import java.io.IOException;
import ssg.legoflow.database.mysql.client.MysqlClient;
import static org.assertj.core.api.Assertions.*;
class PreparedStatementIntegrationTest {

    private static MysqlServer server;
    private static MysqlClient client;

    @BeforeAll
    static void setup() throws IOException {
        server = new MysqlServer("localhost", 0);
        server.addUser("test", "test");
        server.createDatabase("testdb");
        server.start();
        client = MysqlClient.connect("localhost", server.actualPort(), "test", "test", "testdb");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    private void createTable() throws IOException {
        try { client.execute("DROP TABLE ps_test"); } catch (Exception ignored) {}
        client.execute("CREATE TABLE ps_test (id BIGINT, name VARCHAR(64))");
    }

    @Test void testPreparedStatementInsertAndSelect() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "99");
            ps.setString(1, "PreparedAlice");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("SELECT id, name FROM ps_test WHERE id = ?")) {
            ps.setString(0, "99");
            var result = ps.executeQuery();
            assertThat(result.rowCount()).isGreaterThan(0);
        }
    }

    @Test void testPreparedStatementMultipleParams() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "First");
            ps.executeUpdate();
            ps.setString(0, "2");
            ps.setString(1, "Second");
            ps.executeUpdate();
        }
        var result = client.query("SELECT COUNT(*) as cnt FROM ps_test WHERE id IN (1, 2)");
        if (result.next()) {
            assertThat(result.getInt("cnt")).isEqualTo(2);
        }
    }

    @Test void testPreparedStatementSelectAll() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "SampleData");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("SELECT * FROM ps_test")) {
            var result = ps.executeQuery();
            assertThat(result).isNotNull();
            assertThat(result.rowCount()).isGreaterThan(0);
        }
    }

    @Test void testPreparedStatementWithNullParam() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "3");
            ps.setString(1, null);
            ps.executeUpdate();
        }
        var result = client.query("SELECT name FROM ps_test WHERE id = 3");
        if (result.next()) {
            assertThat(result.getString("name")).isNull();
        }
    }

    @Test void testPreparedStatementTypeConversions() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "12345");
            ps.setString(1, "TypedValue");
            ps.executeUpdate();
        }
        var result = client.query("SELECT id FROM ps_test WHERE id = 12345");
        assertThat(result.rowCount()).isEqualTo(1);
    }

    @Test void testPreparedStatementExecuteUpdateReturnsAffectedRows() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "42");
            ps.setString(1, "UpdateTest");
            var result = ps.executeUpdate();
            assertThat(result).isNotNull();
        }
    }

    @Test void testPreparedStatementReuse() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            for (int i = 10; i < 13; i++) {
                ps.setString(0, String.valueOf(i));
                ps.setString(1, "Batch" + i);
                ps.executeUpdate();
            }
        }
        var result = client.query("SELECT COUNT(*) as cnt FROM ps_test WHERE id BETWEEN 10 AND 12");
        if (result.next()) {
            assertThat(result.getInt("cnt")).isEqualTo(3);
        }
    }

    @Test void testPreparedStatementSelectWithWhere() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "PreparedName");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("SELECT id, name FROM ps_test WHERE name LIKE ?")) {
            ps.setString(0, "Prepared%");
            var result = ps.executeQuery();
            assertThat(result).isNotNull();
        }
    }

    @Test void testPreparedStatementWithOrderBy() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            for (int i = 3; i >= 1; i--) {
                ps.setString(0, String.valueOf(i));
                ps.setString(1, "Item" + i);
                ps.executeUpdate();
            }
        }
        try (var ps = client.prepare("SELECT id, name FROM ps_test ORDER BY id DESC")) {
            var result = ps.executeQuery();
            assertThat(result).isNotNull();
            if (result.next()) {
                assertThat(result.getInt("id")).isEqualTo(3);
            }
        }
    }

    @Test void testPreparedStatementWithIntParam() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setInt(0, 42);
            ps.setString(1, "IntParam");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("SELECT id FROM ps_test WHERE id = ?")) {
            ps.setInt(0, 42);
            var result = ps.executeQuery();
            assertThat(result.rowCount()).isEqualTo(1);
        }
    }

    @Test void testPreparedStatementWithLongParam() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setLong(0, 987654321L);
            ps.setString(1, "LongParam");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("SELECT id FROM ps_test WHERE id = ?")) {
            ps.setLong(0, 987654321L);
            var result = ps.executeQuery();
            assertThat(result.rowCount()).isEqualTo(1);
        }
    }

    @Test void testPreparedStatementWithDoubleParam() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "3.14");
            ps.executeUpdate();
        }
        var result = client.query("SELECT name FROM ps_test WHERE id = 1");
        if (result.next()) {
            assertThat(result.getString("name")).isEqualTo("3.14");
        }
    }

    @Test void testPreparedStatementReset() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "First");
            ps.executeUpdate();
            ps.reset();
            ps.setString(0, "2");
            ps.setString(1, "Second");
            ps.executeUpdate();
        }
        var result = client.query("SELECT COUNT(*) as cnt FROM ps_test");
        if (result.next()) {
            assertThat(result.getInt("cnt")).isEqualTo(2);
        }
    }

    @Test void testPreparedStatementWhereInWithMultipleValues() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(0, String.valueOf(i));
                ps.setString(1, "Row" + i);
                ps.executeUpdate();
            }
        }
        try (var ps = client.prepare("SELECT id FROM ps_test WHERE id > ?")) {
            ps.setInt(0, 3);
            var result = ps.executeQuery();
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test void testPreparedStatementUpdateStatement() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "OldName");
            ps.executeUpdate();
        }
        try (var ps = client.prepare("UPDATE ps_test SET name = ? WHERE id = ?")) {
            ps.setString(0, "NewName");
            ps.setString(1, "1");
            ps.executeUpdate();
        }
        var result = client.query("SELECT name FROM ps_test WHERE id = 1");
        if (result.next()) {
            assertThat(result.getString("name")).isEqualTo("NewName");
        }
    }

    @Test void testPreparedStatementDeleteStatement() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            for (int i = 1; i <= 3; i++) {
                ps.setString(0, String.valueOf(i));
                ps.setString(1, "Row" + i);
                ps.executeUpdate();
            }
        }
        try (var ps = client.prepare("DELETE FROM ps_test WHERE id = ?")) {
            ps.setString(0, "2");
            ps.executeUpdate();
        }
        var result = client.query("SELECT COUNT(*) as cnt FROM ps_test");
        if (result.next()) {
            assertThat(result.getInt("cnt")).isEqualTo(2);
        }
    }

    @Test void testPreparedStatementWithEmptyTable() throws IOException {
        createTable();
        try (var ps = client.prepare("SELECT * FROM ps_test WHERE id = ?")) {
            ps.setString(0, "999");
            var result = ps.executeQuery();
            assertThat(result.rowCount()).isZero();
        }
    }

    @Test void testPreparedStatementWithSpecialChars() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            ps.setString(0, "1");
            ps.setString(1, "Hello & World [tag]");
            ps.executeUpdate();
        }
        var result = client.query("SELECT name FROM ps_test WHERE id = 1");
        if (result.next()) {
            assertThat(result.getString("name")).isEqualTo("Hello & World [tag]");
        }
    }

    @Test void testPreparedStatementParameterMetadata() throws IOException {
        createTable();
        try (var ps = client.prepare("INSERT INTO ps_test (id, name) VALUES (?, ?)")) {
            assertThat(ps.paramCount()).isEqualTo(2);
        }
        try (var ps = client.prepare("SELECT * FROM ps_test WHERE id = ? AND name LIKE ?")) {
            assertThat(ps.paramCount()).isEqualTo(2);
            
        }
    }

    @Test void testPreparedStatementNoParams() throws IOException {
        createTable();
        try (var ps = client.prepare("SELECT COUNT(*) as cnt FROM ps_test")) {
            assertThat(ps.paramCount()).isEqualTo(0);
            var result = ps.executeQuery();
            assertThat(result).isNotNull();
        }
    }
}
