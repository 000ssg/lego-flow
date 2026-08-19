package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlResult;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for real transaction BEGIN/COMMIT/ROLLBACK behavior.
 */
class TransactionRollbackTest {

    @Test
    void testCommitPersistsData() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            env.exec("BEGIN");
            env.exec("INSERT INTO accounts (id, balance) VALUES (2, '200')");
            env.exec("COMMIT");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testRollbackRevertsInsert() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            env.exec("BEGIN");
            env.exec("INSERT INTO accounts (id, balance) VALUES (2, '200')");
            env.exec("INSERT INTO accounts (id, balance) VALUES (3, '300')");
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("1");
        }
    }

    @Test
    void testRollbackRevertsDelete() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");
            env.exec("INSERT INTO accounts (id, balance) VALUES (2, '200')");

            env.exec("BEGIN");
            env.exec("DELETE FROM accounts WHERE id = '1'");
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(2);
        }
    }

    @Test
    void testRollbackRevertsUpdate() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            env.exec("BEGIN");
            env.exec("UPDATE accounts SET balance = '999' WHERE id = '1'");
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT balance FROM accounts WHERE id = '1'");
            assertThat(result.rowCount()).isEqualTo(1);
            result.next();
            assertThat(result.getString(0)).isEqualTo("100");
        }
    }

    @Test
    void testMultipleTransactions() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");

            // First transaction: commit
            env.exec("BEGIN");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");
            env.exec("COMMIT");

            // Second transaction: rollback
            env.exec("BEGIN");
            env.exec("INSERT INTO accounts (id, balance) VALUES (2, '200')");
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(1);
        }
    }

    @Test
    void testRollbackWithoutBegin() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            // ROLLBACK without BEGIN should not crash
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(1);
        }
    }

    @Test
    void testStartTransaction() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            env.exec("START TRANSACTION");
            env.exec("INSERT INTO accounts (id, balance) VALUES (2, '200')");
            env.exec("ROLLBACK");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(1);
        }
    }

    @Test
    void testCommitWithoutBegin() throws Exception {
        try (var env = TestEnv.create()) {
            env.exec("CREATE TABLE accounts (id INT, balance VARCHAR(20))");
            env.exec("INSERT INTO accounts (id, balance) VALUES (1, '100')");

            // COMMIT without BEGIN should not crash
            env.exec("COMMIT");

            MysqlResult result = env.query("SELECT * FROM accounts");
            assertThat(result.rowCount()).isEqualTo(1);
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
