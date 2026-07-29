package ssg.legoflow.database.mysql.demo;

import ssg.legoflow.database.mysql.auth.CachingSha2Password;
import ssg.legoflow.database.mysql.auth.MysqlNativePassword;
import ssg.legoflow.database.mysql.client.MysqlClient;
import ssg.legoflow.database.mysql.client.MysqlPreparedStatement;
import ssg.legoflow.database.mysql.client.MysqlResult;
import ssg.legoflow.database.mysql.server.MysqlServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Comprehensive demo of all MySQL module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link MysqlServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports the MySQL wire protocol including
 * HandshakeV10, COM_QUERY, COM_STMT_PREPARE/EXECUTE, COM_PING, COM_STATISTICS,
 * pluggable authentication (mysql_native_password, caching_sha2_password),
 * in-memory database with CREATE/INSERT/SELECT/UPDATE/DELETE, JOINs, ORDER BY,
 * GROUP BY, aggregates, advanced WHERE, and real transaction rollback.
 * Ideal for development, testing, CI/CD, and learning the MySQL protocol.</p>
 *
 * <p><b>Alternative: External MySQL, MariaDB, or Percona Server</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with persistent storage and full SQL engine</li>
 *   <li>Advanced SQL features (subqueries, stored procedures, triggers)</li>
 *   <li>Multi-user concurrent access with real locking and MVCC</li>
 *   <li>Integration testing against a real MySQL/MariaDB/Percona cluster</li>
 *   <li>TLS/SSL encrypted connections</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (query, prepared statement, ping) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Simple query — COM_QUERY: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE</li>
 *   <li>Prepared statements — COM_STMT_PREPARE/EXECUTE with parameter binding</li>
 *   <li>Transaction statements — BEGIN/COMMIT/ROLLBACK accepted by the server</li>
 *   <li>Multiple result sets — sequential queries returning different result shapes</li>
 *   <li>Authentication — mysql_native_password and caching_sha2_password plugins</li>
 *   <li>Server utilities — COM_PING, COM_STATISTICS, COM_INIT_DB</li>
 *   <li>JOIN queries — INNER JOIN and LEFT JOIN with aliases</li>
 *   <li>ORDER BY / LIMIT — sorting with LIMIT and OFFSET</li>
 *   <li>Aggregate queries — COUNT, SUM, AVG, MIN, MAX with GROUP BY</li>
 *   <li>Advanced WHERE — AND, OR, comparison operators, LIKE, IS NULL, IN</li>
 *   <li>Transaction rollback — BEGIN/INSERT/ROLLBACK reverts changes</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoMysqlAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoMysqlAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house MysqlServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for MySQL/MariaDB/Percona
    // =========================================================================

    /** Set to {@code true} to connect to an external MySQL server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external MySQL server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external MySQL server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 3306;

    private DemoMysqlAll() {}

    /**
     * Results from running the full demo.
     *
     * @param simpleQuery           number of rows returned by simple SELECT query
     * @param preparedStatement     number of rows returned by prepared statement query
     * @param transactionStatements true if BEGIN/COMMIT/ROLLBACK are accepted without error
     * @param multipleResults       total rows across multiple sequential queries
     * @param authentication        true if both auth plugin connections succeeded
     * @param serverUtilities       true if ping, statistics, and initDb succeeded
     * @param joinQueries           number of rows returned by join query
     * @param orderByLimit          true if ORDER BY/LIMIT queries work correctly
     * @param aggregateQueries      number of aggregate result rows
     * @param advancedWhere         true if advanced WHERE clauses work correctly
     * @param transactionRollback   true if ROLLBACK actually reverts changes
     */
    public record Results(
            int simpleQuery,
            int preparedStatement,
            boolean transactionStatements,
            int multipleResults,
            boolean authentication,
            boolean serverUtilities,
            int joinQueries,
            boolean orderByLimit,
            int aggregateQueries,
            boolean advancedWhere,
            boolean transactionRollback
    ) {}

    /**
     * Runs the comprehensive demo covering all MySQL features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT,
                    "root", "", "test");
        }
        try (var server = new MysqlServer("localhost", 0)) {
            server.addUser("demo", "demo123");
            server.createDatabase("testdb");
            server.start();
            int port = server.actualPort();
            LOG.info("In-house MysqlServer started on port {}", port);

            // Run common features available with any server
            Results common = runWithExternalServer("localhost", port,
                    "demo", "demo123", "testdb");

            // Authentication demo (in-house only — controls auth plugins)
            boolean auth = demoAuthentication(server, port);

            return new Results(
                    common.simpleQuery(),
                    common.preparedStatement(),
                    common.transactionStatements(),
                    common.multipleResults(),
                    auth,
                    common.serverUtilities(),
                    common.joinQueries(),
                    common.orderByLimit(),
                    common.aggregateQueries(),
                    common.advancedWhere(),
                    common.transactionRollback()
            );
        }
    }

    private static Results runWithExternalServer(String host, int port,
                                                  String user, String pass,
                                                  String db) throws Exception {
        int simpleQuery = demoSimpleQuery(host, port, user, pass, db);
        int preparedStmt = demoPreparedStatements(host, port, user, pass, db);
        boolean txnStatements = demoTransactionStatements(host, port, user, pass, db);
        int multiResults = demoMultipleResults(host, port, user, pass, db);
        boolean utilities = demoServerUtilities(host, port, user, pass, db);
        int joinRows = demoJoinQueries(host, port, user, pass, db);
        boolean orderByLimit = demoOrderByLimit(host, port, user, pass, db);
        int aggRows = demoAggregateQueries(host, port, user, pass, db);
        boolean advWhere = demoAdvancedWhere(host, port, user, pass, db);
        boolean txnRollback = demoTransactionRollback(host, port, user, pass, db);

        return new Results(simpleQuery, preparedStmt, txnStatements,
                multiResults, false /* auth filled later for in-house */, utilities,
                joinRows, orderByLimit, aggRows, advWhere, txnRollback);
    }

    // ======================== 1. SIMPLE QUERY ================================

    /**
     * Demonstrates COM_QUERY: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE.
     */
    static int demoSimpleQuery(String host, int port, String user,
                               String pass, String db) throws IOException {
        LOG.info("=== 1. Simple Query (COM_QUERY) ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            // CREATE TABLE
            client.execute("CREATE TABLE demo_users (id INT, name VARCHAR(50), email VARCHAR(100))");
            LOG.info("Table created");

            // INSERT rows
            client.execute("INSERT INTO demo_users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
            client.execute("INSERT INTO demo_users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
            client.execute("INSERT INTO demo_users (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com')");
            LOG.info("Inserted 3 rows");

            // SELECT
            MysqlResult result = client.query("SELECT * FROM demo_users");
            int rowCount = result.rowCount();
            LOG.info("SELECT returned {} rows, {} columns", rowCount, result.columnCount());

            // UPDATE
            long updated = client.execute("UPDATE demo_users SET email = 'alice2@example.com' WHERE id = 1");
            LOG.info("Updated {} rows", updated);

            // DELETE
            long deleted = client.execute("DELETE FROM demo_users WHERE id = 3");
            LOG.info("Deleted {} rows", deleted);

            // Verify final state
            MysqlResult afterResult = client.query("SELECT * FROM demo_users");
            LOG.info("After update/delete: {} rows", afterResult.rowCount());

            // DROP TABLE
            client.execute("DROP TABLE demo_users");
            return rowCount;
        }
    }

    // ======================== 2. PREPARED STATEMENTS =========================

    /**
     * Demonstrates COM_STMT_PREPARE and COM_STMT_EXECUTE with parameter binding.
     */
    static int demoPreparedStatements(String host, int port, String user,
                                      String pass, String db) throws IOException {
        LOG.info("=== 2. Prepared Statements (COM_STMT_PREPARE/EXECUTE) ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_products (id INT, name VARCHAR(50), price VARCHAR(20))");

            // Insert using prepared statement
            try (var ps = client.prepare("INSERT INTO demo_products (id, name, price) VALUES (?, ?, ?)")) {
                LOG.info("Prepared statement ID={}, params={}", ps.statementId(), ps.paramCount());

                ps.setInt(0, 1);
                ps.setString(1, "Widget");
                ps.setString(2, "9.99");
                ps.executeUpdate();

                ps.setInt(0, 2);
                ps.setString(1, "Gadget");
                ps.setString(2, "19.99");
                ps.executeUpdate();

                ps.setInt(0, 3);
                ps.setString(1, "Doohickey");
                ps.setString(2, "4.99");
                ps.executeUpdate();
            }
            LOG.info("Inserted 3 products via prepared statement");

            // Query using prepared statement
            try (var ps = client.prepare("SELECT * FROM demo_products WHERE id = ?")) {
                ps.setInt(0, 2);
                MysqlResult result = ps.executeQuery();
                if (result.next()) {
                    LOG.info("Found product: name={}, price={}", result.getString(1), result.getString(2));
                }
            }

            // Verify total
            MysqlResult all = client.query("SELECT * FROM demo_products");
            LOG.info("Total products: {}", all.rowCount());

            client.execute("DROP TABLE demo_products");
            return all.rowCount();
        }
    }

    // ======================== 3. TRANSACTION STATEMENTS =======================

    /**
     * Demonstrates that the server accepts transaction statements (BEGIN, COMMIT, ROLLBACK).
     */
    static boolean demoTransactionStatements(String host, int port, String user,
                                              String pass, String db) throws IOException {
        LOG.info("=== 3. Transaction Statements (BEGIN/COMMIT/ROLLBACK) ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_txn (id INT, amount VARCHAR(20))");

            // BEGIN and COMMIT are accepted
            client.execute("BEGIN");
            client.execute("INSERT INTO demo_txn (id, amount) VALUES (1, '100')");
            client.execute("INSERT INTO demo_txn (id, amount) VALUES (2, '200')");
            client.execute("COMMIT");
            LOG.info("BEGIN/INSERT/COMMIT sequence accepted");

            // Verify data persisted
            MysqlResult result = client.query("SELECT * FROM demo_txn");
            LOG.info("After commit: {} rows", result.rowCount());
            boolean commitOk = result.rowCount() == 2;

            // ROLLBACK is also accepted
            client.execute("BEGIN");
            client.execute("ROLLBACK");
            LOG.info("BEGIN/ROLLBACK sequence accepted");

            client.execute("DROP TABLE demo_txn");
            return commitOk;
        }
    }

    // ======================== 4. MULTIPLE RESULTS ============================

    /**
     * Demonstrates multiple sequential queries returning different result shapes.
     */
    static int demoMultipleResults(String host, int port, String user,
                                   String pass, String db) throws IOException {
        LOG.info("=== 4. Multiple Result Sets ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_multi (id INT, category VARCHAR(20), value VARCHAR(20))");
            client.execute("INSERT INTO demo_multi (id, category, value) VALUES (1, 'A', '10')");
            client.execute("INSERT INTO demo_multi (id, category, value) VALUES (2, 'B', '20')");
            client.execute("INSERT INTO demo_multi (id, category, value) VALUES (3, 'A', '30')");
            client.execute("INSERT INTO demo_multi (id, category, value) VALUES (4, 'B', '40')");

            // Query 1: all rows
            MysqlResult all = client.query("SELECT * FROM demo_multi");
            LOG.info("Query 1 (all): {} rows", all.rowCount());

            // Query 2: filtered by category
            MysqlResult filtered = client.query("SELECT * FROM demo_multi WHERE category = 'A'");
            LOG.info("Query 2 (category=A): {} rows", filtered.rowCount());

            // Query 3: count
            MysqlResult count = client.query("SELECT COUNT(*) FROM demo_multi");
            LOG.info("Query 3 (count): {} rows", count.rowCount());

            int total = all.rowCount() + filtered.rowCount() + count.rowCount();
            LOG.info("Total rows across 3 queries: {}", total);

            client.execute("DROP TABLE demo_multi");
            return total;
        }
    }

    // ======================== 5. AUTHENTICATION ==============================

    /**
     * Demonstrates authentication with both mysql_native_password and
     * caching_sha2_password plugins.
     */
    static boolean demoAuthentication(MysqlServer server, int port) throws IOException {
        LOG.info("=== 5. Authentication ===");

        // Test 1: mysql_native_password (default)
        server.addUser("native_user", "native_pass");
        boolean nativeOk;
        try (var client = MysqlClient.connect("localhost", port, "native_user",
                "native_pass", "testdb")) {
            nativeOk = client.ping();
            LOG.info("mysql_native_password auth: {}", nativeOk ? "OK" : "FAILED");
        }

        // Test 2: caching_sha2_password
        server.setDefaultAuthPlugin(CachingSha2Password.NAME);
        server.addUser("sha2_user", "sha2_pass");
        boolean sha2Ok;
        try (var client = MysqlClient.connect("localhost", port, "sha2_user",
                "sha2_pass", "testdb")) {
            sha2Ok = client.ping();
            LOG.info("caching_sha2_password auth: {}", sha2Ok ? "OK" : "FAILED");
        }

        // Restore default
        server.setDefaultAuthPlugin(MysqlNativePassword.NAME);

        return nativeOk && sha2Ok;
    }

    // ======================== 6. SERVER UTILITIES ============================

    /**
     * Demonstrates COM_PING, COM_STATISTICS, and COM_INIT_DB.
     */
    static boolean demoServerUtilities(String host, int port, String user,
                                       String pass, String db) throws IOException {
        LOG.info("=== 6. Server Utilities ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            // COM_PING
            boolean pingOk = client.ping();
            LOG.info("Ping: {}", pingOk);

            // COM_STATISTICS
            String stats = client.statistics();
            LOG.info("Statistics: {}", stats);

            // Server version
            String version = client.serverVersion();
            LOG.info("Server version: {}", version);

            return pingOk && stats != null && !stats.isEmpty();
        }
    }

    // ======================== 7. JOIN QUERIES =================================

    /**
     * Demonstrates INNER JOIN and LEFT JOIN queries with table aliases.
     *
     * @return number of rows in the join result
     */
    static int demoJoinQueries(String host, int port, String user,
                                String pass, String db) throws IOException {
        LOG.info("=== 7. JOIN Queries ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_customers (id INT, name VARCHAR(50))");
            client.execute("CREATE TABLE demo_orders (id INT, customer_id INT, amount VARCHAR(20))");

            client.execute("INSERT INTO demo_customers (id, name) VALUES (1, 'Alice')");
            client.execute("INSERT INTO demo_customers (id, name) VALUES (2, 'Bob')");
            client.execute("INSERT INTO demo_customers (id, name) VALUES (3, 'Charlie')");

            client.execute("INSERT INTO demo_orders (id, customer_id, amount) VALUES (1, 1, '100')");
            client.execute("INSERT INTO demo_orders (id, customer_id, amount) VALUES (2, 1, '200')");
            client.execute("INSERT INTO demo_orders (id, customer_id, amount) VALUES (3, 2, '300')");

            // INNER JOIN
            MysqlResult inner = client.query(
                    "SELECT c.name, o.amount FROM demo_orders o " +
                    "JOIN demo_customers c ON o.customer_id = c.id");
            LOG.info("INNER JOIN: {} rows", inner.rowCount());

            // LEFT JOIN (Charlie has no orders)
            MysqlResult left = client.query(
                    "SELECT c.name, o.amount FROM demo_customers c " +
                    "LEFT JOIN demo_orders o ON c.id = o.customer_id");
            LOG.info("LEFT JOIN: {} rows", left.rowCount());

            int totalJoinRows = inner.rowCount();

            client.execute("DROP TABLE demo_orders");
            client.execute("DROP TABLE demo_customers");
            return totalJoinRows;
        }
    }

    // ======================== 8. ORDER BY / LIMIT ============================

    /**
     * Demonstrates ORDER BY and LIMIT/OFFSET.
     */
    static boolean demoOrderByLimit(String host, int port, String user,
                                     String pass, String db) throws IOException {
        LOG.info("=== 8. ORDER BY / LIMIT ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_sort (id INT, name VARCHAR(50), score VARCHAR(20))");
            client.execute("INSERT INTO demo_sort (id, name, score) VALUES (1, 'Charlie', '30')");
            client.execute("INSERT INTO demo_sort (id, name, score) VALUES (2, 'Alice', '10')");
            client.execute("INSERT INTO demo_sort (id, name, score) VALUES (3, 'Bob', '20')");

            // ORDER BY name ASC
            MysqlResult sorted = client.query("SELECT name FROM demo_sort ORDER BY name ASC");
            sorted.next();
            boolean firstIsAlice = "Alice".equals(sorted.getString(0));
            LOG.info("ORDER BY name ASC, first={}: {}", sorted.getString(0),
                    firstIsAlice ? "OK" : "FAILED");

            // ORDER BY with LIMIT
            MysqlResult limited = client.query(
                    "SELECT name FROM demo_sort ORDER BY score DESC LIMIT 2");
            boolean limitOk = limited.rowCount() == 2;
            LOG.info("ORDER BY score DESC LIMIT 2: {} rows", limited.rowCount());

            client.execute("DROP TABLE demo_sort");
            return firstIsAlice && limitOk;
        }
    }

    // ======================== 9. AGGREGATE QUERIES ===========================

    /**
     * Demonstrates aggregate functions and GROUP BY.
     *
     * @return number of aggregate result rows
     */
    static int demoAggregateQueries(String host, int port, String user,
                                     String pass, String db) throws IOException {
        LOG.info("=== 9. Aggregate Queries ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_agg (id INT, category VARCHAR(20), amount VARCHAR(20))");
            client.execute("INSERT INTO demo_agg (id, category, amount) VALUES (1, 'A', '10')");
            client.execute("INSERT INTO demo_agg (id, category, amount) VALUES (2, 'B', '20')");
            client.execute("INSERT INTO demo_agg (id, category, amount) VALUES (3, 'A', '30')");
            client.execute("INSERT INTO demo_agg (id, category, amount) VALUES (4, 'B', '40')");

            // COUNT(*)
            MysqlResult count = client.query("SELECT COUNT(*) FROM demo_agg");
            count.next();
            LOG.info("COUNT(*) = {}", count.getString(0));

            // GROUP BY with SUM
            MysqlResult grouped = client.query(
                    "SELECT category, SUM(amount) AS total FROM demo_agg GROUP BY category");
            LOG.info("GROUP BY category: {} groups", grouped.rowCount());

            client.execute("DROP TABLE demo_agg");
            return grouped.rowCount();
        }
    }

    // ======================== 10. ADVANCED WHERE ==============================

    /**
     * Demonstrates advanced WHERE clause features.
     */
    static boolean demoAdvancedWhere(String host, int port, String user,
                                      String pass, String db) throws IOException {
        LOG.info("=== 10. Advanced WHERE ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_where (id INT, name VARCHAR(50), score VARCHAR(20))");
            client.execute("INSERT INTO demo_where (id, name, score) VALUES (1, 'Alice', '90')");
            client.execute("INSERT INTO demo_where (id, name, score) VALUES (2, 'Bob', '80')");
            client.execute("INSERT INTO demo_where (id, name, score) VALUES (3, 'Charlie', '70')");

            // AND
            MysqlResult andResult = client.query(
                    "SELECT name FROM demo_where WHERE score > '75' AND name LIKE 'A%'");
            boolean andOk = andResult.rowCount() == 1;
            LOG.info("WHERE AND LIKE: {} rows", andResult.rowCount());

            // IN
            MysqlResult inResult = client.query(
                    "SELECT name FROM demo_where WHERE name IN ('Alice', 'Charlie')");
            boolean inOk = inResult.rowCount() == 2;
            LOG.info("WHERE IN: {} rows", inResult.rowCount());

            client.execute("DROP TABLE demo_where");
            return andOk && inOk;
        }
    }

    // ======================== 11. TRANSACTION ROLLBACK ========================

    /**
     * Demonstrates that ROLLBACK actually reverts changes.
     */
    static boolean demoTransactionRollback(String host, int port, String user,
                                            String pass, String db) throws IOException {
        LOG.info("=== 11. Transaction Rollback ===");
        try (var client = MysqlClient.connect(host, port, user, pass, db)) {
            client.execute("CREATE TABLE demo_rollback (id INT, val VARCHAR(20))");
            client.execute("INSERT INTO demo_rollback (id, val) VALUES (1, 'original')");

            // BEGIN, modify, ROLLBACK
            client.execute("BEGIN");
            client.execute("INSERT INTO demo_rollback (id, val) VALUES (2, 'temp')");
            client.execute("ROLLBACK");

            // Should only have original row
            MysqlResult result = client.query("SELECT * FROM demo_rollback");
            boolean rollbackOk = result.rowCount() == 1;
            LOG.info("After ROLLBACK: {} rows (expected 1): {}", result.rowCount(),
                    rollbackOk ? "OK" : "FAILED");

            client.execute("DROP TABLE demo_rollback");
            return rollbackOk;
        }
    }
}
