package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for aggregate functions (COUNT, SUM, AVG, MIN, MAX), GROUP BY, and HAVING.
 */
class AggregateQueryTest {

    @Test
    void testCountStar() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'C')");

            MysqlResult result = env.query("SELECT COUNT(*) FROM items");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("3");
        }
    }

    @Test
    void testCountColumn() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, NULL)");
            env.exec("INSERT INTO items (id, name) VALUES (3, 'C')");

            MysqlResult result = env.query("SELECT COUNT(name) FROM items");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("2"); // NULL not counted
        }
    }

    @Test
    void testSum() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, price VARCHAR(20))");
            env.exec("INSERT INTO items (id, price) VALUES (1, '10')");
            env.exec("INSERT INTO items (id, price) VALUES (2, '20')");
            env.exec("INSERT INTO items (id, price) VALUES (3, '30')");

            MysqlResult result = env.query("SELECT SUM(price) FROM items");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("60");
        }
    }

    @Test
    void testAvg() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, price VARCHAR(20))");
            env.exec("INSERT INTO items (id, price) VALUES (1, '10')");
            env.exec("INSERT INTO items (id, price) VALUES (2, '20')");
            env.exec("INSERT INTO items (id, price) VALUES (3, '30')");

            MysqlResult result = env.query("SELECT AVG(price) FROM items");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(Double.parseDouble(result.getString(0))).isEqualTo(20.0);
        }
    }

    @Test
    void testMinMax() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, price VARCHAR(20))");
            env.exec("INSERT INTO items (id, price) VALUES (1, '10')");
            env.exec("INSERT INTO items (id, price) VALUES (2, '50')");
            env.exec("INSERT INTO items (id, price) VALUES (3, '30')");

            MysqlResult minResult = env.query("SELECT MIN(price) FROM items");
            minResult.next();
            assertThat(minResult.getString(0)).isEqualTo("10");

            MysqlResult maxResult = env.query("SELECT MAX(price) FROM items");
            maxResult.next();
            assertThat(maxResult.getString(0)).isEqualTo("50");
        }
    }

    @Test
    void testGroupBy() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE orders (id INT, category VARCHAR(20), amount VARCHAR(20))");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (1, 'A', '10')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (2, 'B', '20')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (3, 'A', '30')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (4, 'B', '40')");

            MysqlResult result = env.query(
                    "SELECT category, COUNT(*) AS cnt FROM orders GROUP BY category");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testGroupByWithSum() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE orders (id INT, category VARCHAR(20), amount VARCHAR(20))");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (1, 'A', '10')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (2, 'B', '20')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (3, 'A', '30')");

            MysqlResult result = env.query(
                    "SELECT category, SUM(amount) AS total FROM orders GROUP BY category");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testGroupByHaving() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE orders (id INT, category VARCHAR(20), amount VARCHAR(20))");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (1, 'A', '10')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (2, 'B', '20')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (3, 'A', '30')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (4, 'A', '40')");

            MysqlResult result = env.query(
                    "SELECT category, COUNT(*) AS cnt FROM orders GROUP BY category HAVING cnt > 1");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("A");
        }
    }

    @Test
    void testCountStarWithAlias() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE items (id INT, name VARCHAR(50))");
            env.exec("INSERT INTO items (id, name) VALUES (1, 'A')");
            env.exec("INSERT INTO items (id, name) VALUES (2, 'B')");

            MysqlResult result = env.query("SELECT COUNT(*) AS total FROM items");

            assertThat(result.rowCount()).isEqualTo(1);
            assertThat(result.columnCount()).isEqualTo(1);
            // Column should be named 'total'
            assertThat(result.columns().get(0).name()).isEqualTo("total");
        }
    }

    @Test
    void testGroupByWithOrderBy() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE orders (id INT, category VARCHAR(20), amount VARCHAR(20))");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (1, 'B', '20')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (2, 'A', '10')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (3, 'B', '40')");
            env.exec("INSERT INTO orders (id, category, amount) VALUES (4, 'A', '30')");

            MysqlResult result = env.query(
                    "SELECT category, SUM(amount) AS total FROM orders GROUP BY category ORDER BY total DESC");

            assertThat(result.rowCount()).isEqualTo(2);
            result.next();
            assertThat(result.getString(0)).isEqualTo("B"); // total = 60
            result.next();
            assertThat(result.getString(0)).isEqualTo("A"); // total = 40
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
