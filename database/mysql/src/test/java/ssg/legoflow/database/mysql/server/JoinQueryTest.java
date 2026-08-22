package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for INNER JOIN and LEFT JOIN queries.
 */
class JoinQueryTest {

    @Test
    void testInnerJoin() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE orders (id INT, customer_id INT, amount VARCHAR(20))");
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (2, 'Bob')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, '100')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (2, 1, '200')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (3, 2, '300')");

            MysqlResult result = env.client.query(
                    "SELECT orders.id, customers.name, orders.amount " +
                    "FROM orders JOIN customers ON orders.customer_id = customers.id");

            assertThat(result.rowCount()).isEqualTo(3);
        }
    }

    @Test
    void testInnerJoinExplicit() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE t1 (id INT, val VARCHAR(20))");
            env.client.execute("CREATE TABLE t2 (id INT, t1_id INT, info VARCHAR(20))");
            env.client.execute("INSERT INTO t1 (id, val) VALUES (1, 'a')");
            env.client.execute("INSERT INTO t1 (id, val) VALUES (2, 'b')");
            env.client.execute("INSERT INTO t2 (id, t1_id, info) VALUES (1, 1, 'x')");
            env.client.execute("INSERT INTO t2 (id, t1_id, info) VALUES (2, 1, 'y')");

            MysqlResult result = env.client.query(
                    "SELECT t1.val, t2.info FROM t1 INNER JOIN t2 ON t1.id = t2.t1_id");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testInnerJoinNoMatch() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE a (id INT, name VARCHAR(20))");
            env.client.execute("CREATE TABLE b (id INT, a_id INT, data VARCHAR(20))");
            env.client.execute("INSERT INTO a (id, name) VALUES (1, 'x')");
            env.client.execute("INSERT INTO b (id, a_id, data) VALUES (1, 99, 'z')");

            MysqlResult result = env.client.query(
                    "SELECT a.name, b.data FROM a JOIN b ON a.id = b.a_id");

            assertThat(result.rowCount()).isEqualTo(0);
        }
    }

    @Test
    void testLeftJoin() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("CREATE TABLE orders (id INT, customer_id INT, amount VARCHAR(20))");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (2, 'Bob')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (3, 'Charlie')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, '100')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (2, 1, '200')");

            MysqlResult result = env.client.query(
                    "SELECT customers.name, orders.amount " +
                    "FROM customers LEFT JOIN orders ON customers.id = orders.customer_id");

            // Alice has 2 orders, Bob has 0, Charlie has 0 => 2 + 1 + 1 = 4 rows
            assertThat(result.rowCount()).isEqualTo(4);

            // Check that non-matching rows have null amounts
            int nullAmounts = 0;
            while (result.next()) {
                if (result.isNull(1)) nullAmounts++;
            }
            assertThat(nullAmounts).isEqualTo(2); // Bob and Charlie
        }
    }

    @Test
    void testJoinWithAliases() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE orders (id INT, customer_id INT, amount VARCHAR(20))");
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, '100')");

            MysqlResult result = env.client.query(
                    "SELECT o.amount, c.name FROM orders o JOIN customers c ON o.customer_id = c.id");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("100");
            assertThat(result.getString(1)).isEqualTo("Alice");
        }
    }

    @Test
    void testMultipleJoins() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE products (id INT, name VARCHAR(50))");
            env.client.execute("CREATE TABLE orders (id INT, product_id INT, customer_id INT)");
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("INSERT INTO products (id, name) VALUES (1, 'Widget')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO orders (id, product_id, customer_id) VALUES (1, 1, 1)");

            MysqlResult result = env.client.query(
                    "SELECT p.name, c.name FROM orders o " +
                    "JOIN products p ON o.product_id = p.id " +
                    "JOIN customers c ON o.customer_id = c.id");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Widget");
            assertThat(result.getString(1)).isEqualTo("Alice");
        }
    }

    @Test
    void testJoinSelectStar() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE t1 (a INT, b VARCHAR(20))");
            env.client.execute("CREATE TABLE t2 (c INT, d VARCHAR(20))");
            env.client.execute("INSERT INTO t1 (a, b) VALUES (1, 'x')");
            env.client.execute("INSERT INTO t2 (c, d) VALUES (1, 'y')");

            MysqlResult result = env.client.query(
                    "SELECT * FROM t1 JOIN t2 ON t1.a = t2.c");

            assertThat(result.rowCount()).isEqualTo(1);
            assertThat(result.columnCount()).isEqualTo(4); // a, b, c, d
        }
    }

    @Test
    void testLeftJoinAllMatch() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE left_t (id INT, val VARCHAR(20))");
            env.client.execute("CREATE TABLE right_t (id INT, left_id INT, info VARCHAR(20))");
            env.client.execute("INSERT INTO left_t (id, val) VALUES (1, 'a')");
            env.client.execute("INSERT INTO right_t (id, left_id, info) VALUES (1, 1, 'x')");

            MysqlResult result = env.client.query(
                    "SELECT left_t.val, right_t.info FROM left_t LEFT JOIN right_t ON left_t.id = right_t.left_id");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("a");
            assertThat(result.getString(1)).isEqualTo("x");
        }
    }

    @Test
    void testJoinWithWhere() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE orders (id INT, customer_id INT, amount VARCHAR(20))");
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (2, 'Bob')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, '100')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (2, 2, '200')");

            MysqlResult result = env.client.query(
                    "SELECT c.name, o.amount FROM orders o JOIN customers c ON o.customer_id = c.id " +
                    "WHERE c.name = 'Alice'");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("Alice");
        }
    }

    @Test
    void testJoinWithOrderByLimit() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE orders (id INT, customer_id INT, amount VARCHAR(20))");
            env.client.execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            env.client.execute("INSERT INTO customers (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO customers (id, name) VALUES (2, 'Bob')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (1, 1, '100')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (2, 2, '200')");
            env.client.execute("INSERT INTO orders (id, customer_id, amount) VALUES (3, 1, '300')");

            MysqlResult result = env.client.query(
                    "SELECT c.name, o.amount FROM orders o JOIN customers c ON o.customer_id = c.id " +
                    "ORDER BY o.amount DESC LIMIT 2");

            assertThat(result.rowCount()).isEqualTo(2);
            result.next();
            assertThat(result.getString(1)).isEqualTo("300");
        }
    }

    @Test
    void testLeftJoinWithMultipleJoins() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE users (id INT, name VARCHAR(50))");
            env.client.execute("CREATE TABLE profiles (id INT, user_id INT, bio VARCHAR(100))");
            env.client.execute("CREATE TABLE settings (id INT, user_id INT, theme VARCHAR(20))");
            env.client.execute("INSERT INTO users (id, name) VALUES (1, 'Alice')");
            env.client.execute("INSERT INTO users (id, name) VALUES (2, 'Bob')");
            env.client.execute("INSERT INTO profiles (id, user_id, bio) VALUES (1, 1, 'Hello')");
            env.client.execute("INSERT INTO settings (id, user_id, theme) VALUES (1, 1, 'dark')");

            MysqlResult result = env.client.query(
                    "SELECT u.name, p.bio, s.theme FROM users u " +
                    "LEFT JOIN profiles p ON u.id = p.user_id " +
                    "LEFT JOIN settings s ON u.id = s.user_id");

            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testInnerJoinMixedWithLeftJoin() throws Exception {
        try (var env = TestEnv.create()) {
            env.client.execute("CREATE TABLE a (id INT, val VARCHAR(20))");
            env.client.execute("CREATE TABLE b (id INT, a_id INT, info VARCHAR(20))");
            env.client.execute("CREATE TABLE c (id INT, b_id INT, extra VARCHAR(20))");
            env.client.execute("INSERT INTO a (id, val) VALUES (1, 'x')");
            env.client.execute("INSERT INTO b (id, a_id, info) VALUES (1, 1, 'y')");
            // No rows in c

            MysqlResult result = env.client.query(
                    "SELECT a.val, b.info, c.extra FROM a " +
                    "JOIN b ON a.id = b.a_id " +
                    "LEFT JOIN c ON b.id = c.b_id");

            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("x");
            assertThat(result.getString(1)).isEqualTo("y");
            assertThat(result.isNull(2)).isTrue();
        }
    }

    /**
     * Test environment helper — creates a server and connected client.
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

        @Override
        public void close() throws IOException {
            client.close();
            server.close();
        }
    }
}
