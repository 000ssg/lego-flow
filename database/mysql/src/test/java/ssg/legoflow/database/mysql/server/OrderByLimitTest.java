package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for ORDER BY and LIMIT/OFFSET queries.
 */
class OrderByLimitTest {

    @Test
    void testOrderByAscending() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), price VARCHAR(20))");
            env.exec("INSERT INTO items (id, name, price) VALUES (1, 'Charlie', '30')");
            env.exec("INSERT INTO items (id, name, price) VALUES (2, 'Alice', '10')");
            env.exec("INSERT INTO items (id, name, price) VALUES (3, 'Bob', '20')");

            MysqlResult result = env.query("SELECT name FROM items ORDER BY name ASC");

            assertThat(result.rowCount()).isEqualTo(3);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Alice");
            result.next();
            assertThat(result.getString(0)).isEqualTo("Bob");
            result.next();
            assertThat(result.getString(0)).isEqualTo("Charlie");
        }
    }

    @Test
    void testOrderByDescending() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), price VARCHAR(20))");
            env.exec("INSERT INTO items (id, name, price) VALUES (1, 'Alice', '10')");
            env.exec("INSERT INTO items (id, name, price) VALUES (2, 'Bob', '20')");
            env.exec("INSERT INTO items (id, name, price) VALUES (3, 'Charlie', '30')");

            MysqlResult result = env.query("SELECT name FROM items ORDER BY name DESC");

            assertThat(result.rowCount()).isEqualTo(3);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Charlie");
        }
    }

    @Test
    void testOrderByNumeric() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), price VARCHAR(20))");
            env.exec("INSERT INTO items (id, name, price) VALUES (1, 'A', '100')");
            env.exec("INSERT INTO items (id, name, price) VALUES (2, 'B', '20')");
            env.exec("INSERT INTO items (id, name, price) VALUES (3, 'C', '3')");

            MysqlResult result = env.query("SELECT name, price FROM items ORDER BY price ASC");

            assertThat(result.rowCount()).isEqualTo(3);
            result.next();
            assertThat(result.getString(0)).isEqualTo("C");   // price 3
            result.next();
            assertThat(result.getString(0)).isEqualTo("B");   // price 20
            result.next();
            assertThat(result.getString(0)).isEqualTo("A");   // price 100
        }
    }

    @Test
    void testOrderByMultipleColumns() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, category VARCHAR(20), name VARCHAR(50))");
            env.exec("INSERT INTO items (id, category, name) VALUES (1, 'B', 'Zeta')");
            env.exec("INSERT INTO items (id, category, name) VALUES (2, 'A', 'Beta')");
            env.exec("INSERT INTO items (id, category, name) VALUES (3, 'A', 'Alpha')");
            env.exec("INSERT INTO items (id, category, name) VALUES (4, 'B', 'Gamma')");

            MysqlResult result = env.query(
                    "SELECT category, name FROM items ORDER BY category ASC, name ASC");

            assertThat(result.rowCount()).isEqualTo(4);
            result.next();
            assertThat(result.getString(1)).isEqualTo("Alpha");   // A, Alpha
            result.next();
            assertThat(result.getString(1)).isEqualTo("Beta");    // A, Beta
            result.next();
            assertThat(result.getString(1)).isEqualTo("Gamma");   // B, Gamma
            result.next();
            assertThat(result.getString(1)).isEqualTo("Zeta");    // B, Zeta
        }
    }

    @Test
    void testLimit() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'C')");
            env.exec("INSERT INTO items (id, name) VALUES (4, 'D')");

            MysqlResult result = env.query("SELECT name FROM items LIMIT 2");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testLimitWithOffset() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'C')");
            env.exec("INSERT INTO items (id, name) VALUES (4, 'D')");
            env.exec("INSERT INTO items (id, name) VALUES (5, 'E')");

            MysqlResult result = env.query(
                    "SELECT name FROM items ORDER BY id ASC LIMIT 2 OFFSET 2");

            assertThat(result.rowCount()).isEqualTo(2);
            result.next();
            assertThat(result.getString(0)).isEqualTo("C");
            result.next();
            assertThat(result.getString(0)).isEqualTo("D");
        }
    }

    @Test
    void testOrderByWithLimit() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50), price VARCHAR(20))");
            env.exec("INSERT INTO items (id, name, price) VALUES (1, 'A', '100')");
            env.exec("INSERT INTO items (id, name, price) VALUES (2, 'B', '20')");
            env.exec("INSERT INTO items (id, name, price) VALUES (3, 'C', '300')");
            env.exec("INSERT INTO items (id, name, price) VALUES (4, 'D', '50')");

            MysqlResult result = env.query(
                    "SELECT name, price FROM items ORDER BY price DESC LIMIT 2");

            assertThat(result.rowCount()).isEqualTo(2);
            result.next();
            assertThat(result.getString(0)).isEqualTo("C");   // 300
            result.next();
            assertThat(result.getString(0)).isEqualTo("A");   // 100
        }
    }

    @Test
    void testLimitExceedsRows() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");

            MysqlResult result = env.query("SELECT name FROM items LIMIT 100");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testOrderByDefaultAscending() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'C')");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");

            MysqlResult result = env.query("SELECT name FROM items ORDER BY name");

            assertThat(result.rowCount()).isEqualTo(3);
            result.next();
            assertThat(result.getString(0)).isEqualTo("A");
        }
    }

    @Test
    void testOrderByWithWhere() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, category VARCHAR(20), name VARCHAR(50))");
            env.exec("INSERT INTO items (id, category, name) VALUES (1, 'A', 'Zeta')");
            env.exec("INSERT INTO items (id, category, name) VALUES (2, 'B', 'Beta')");
            env.exec("INSERT INTO items (id, category, name) VALUES (3, 'A', 'Alpha')");

            MysqlResult result = env.query(
                    "SELECT name FROM items WHERE category = 'A' ORDER BY name ASC");

            assertThat(result.rowCount()).isEqualTo(2);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Alpha");
            result.next();
            assertThat(result.getString(0)).isEqualTo("Zeta");
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
