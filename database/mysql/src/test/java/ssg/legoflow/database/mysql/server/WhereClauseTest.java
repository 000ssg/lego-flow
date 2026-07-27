package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for advanced WHERE clause: AND, OR, comparisons, LIKE, IS NULL, IN.
 */
class WhereClauseTest {

    @Test
    void testWhereEquals() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'Bob')");

            MysqlResult result = env.query("SELECT name FROM items WHERE name = 'Alice'");
            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Alice");
        }
    }

    @Test
    void testWhereAnd() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), category VARCHAR(20))");
            env.exec("INSERT INTO items (id, name, category) VALUES (1, 'Alice', 'A')");
            env.exec("INSERT INTO items (id, name, category) VALUES (2, 'Bob', 'A')");
            env.exec("INSERT INTO items (id, name, category) VALUES (3, 'Charlie', 'B')");

            MysqlResult result = env.query(
                    "SELECT name FROM items WHERE category = 'A' AND name = 'Alice'");
            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Alice");
        }
    }

    @Test
    void testWhereOr() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'Bob')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Charlie')");

            MysqlResult result = env.query(
                    "SELECT name FROM items WHERE name = 'Alice' OR name = 'Charlie'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereNotEquals() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'Bob')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Charlie')");

            MysqlResult result = env.query("SELECT name FROM items WHERE name != 'Bob'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereLessThan() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, price VARCHAR(20))");
            env.exec("INSERT INTO items (id, price) VALUES (1, '10')");
            env.exec("INSERT INTO items (id, price) VALUES (2, '20')");
            env.exec("INSERT INTO items (id, price) VALUES (3, '30')");

            MysqlResult result = env.query("SELECT id FROM items WHERE price < '25'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereGreaterThanOrEqual() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, price VARCHAR(20))");
            env.exec("INSERT INTO items (id, price) VALUES (1, '10')");
            env.exec("INSERT INTO items (id, price) VALUES (2, '20')");
            env.exec("INSERT INTO items (id, price) VALUES (3, '30')");

            MysqlResult result = env.query("SELECT id FROM items WHERE price >= '20'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereLikePercent() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'Bob')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Andrew')");

            MysqlResult result = env.query("SELECT name FROM items WHERE name LIKE 'A%'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereLikeUnderscore() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, code VARCHAR(10))");
            env.exec("INSERT INTO items (id, code) VALUES (1, 'AB')");
            env.exec("INSERT INTO items (id, code) VALUES (2, 'AC')");
            env.exec("INSERT INTO items (id, code) VALUES (3, 'ABC')");

            MysqlResult result = env.query("SELECT code FROM items WHERE code LIKE 'A_'");
            assertThat(result.rowCount()).isEqualTo(2); // AB, AC (not ABC)
        }
    }

    @Test
    void testWhereIsNull() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, NULL)");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Charlie')");

            MysqlResult result = env.query("SELECT id FROM items WHERE name IS NULL");
            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("2");
        }
    }

    @Test
    void testWhereIsNotNull() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, NULL)");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Charlie')");

            MysqlResult result = env.query("SELECT id FROM items WHERE name IS NOT NULL");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testWhereIn() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'Alice')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'Bob')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'Charlie')");
            env.exec("INSERT INTO items (id, name) VALUES (4, 'Diana')");

            MysqlResult result = env.query(
                    "SELECT name FROM items WHERE name IN ('Alice', 'Charlie', 'Diana')");
            assertThat(result.rowCount()).isEqualTo(3);
        }
    }

    @Test
    void testWhereAndOr() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), cat VARCHAR(10))");
            env.exec("INSERT INTO items (id, name, cat) VALUES (1, 'Alice', 'A')");
            env.exec("INSERT INTO items (id, name, cat) VALUES (2, 'Bob', 'B')");
            env.exec("INSERT INTO items (id, name, cat) VALUES (3, 'Charlie', 'A')");
            env.exec("INSERT INTO items (id, name, cat) VALUES (4, 'Diana', 'B')");

            // cat='A' AND name='Alice' => 1 row. OR name='Diana' => +1 = 2 rows
            MysqlResult result = env.query(
                    "SELECT name FROM items WHERE cat = 'A' AND name = 'Alice' OR name = 'Diana'");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    /**
     * Test environment helper.
     */
    private static class TestEnv implements AutoCloseable {
        final MysqlServer server;
        final MysqlClient client;

        TestEnv(MysqlServer server, MysqlClient client) {
            this.server = server;
            this.client = client;
        }

        static TestEnv create() throws IOException {
            var server = new MysqlServer("localhost", 0);
            server.addUser("test", "test");
            server.createDatabase("testdb");
            server.start();
            var client = MysqlClient.connect("localhost", server.actualPort(), "test", "test", "testdb");
            return new TestEnv(server, client);
        }

        void exec(String sql) throws IOException { client.execute(sql); }
        MysqlResult query(String sql) throws IOException { return client.query(sql); }

        @Override
        public void close() throws IOException {
            client.close();
            server.close();
        }
    }
}
