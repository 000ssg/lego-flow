package ssg.legoflow.database.postgresql.demo;

import ssg.legoflow.database.postgresql.auth.CleartextAuth;
import ssg.legoflow.database.postgresql.auth.Md5Auth;
import ssg.legoflow.database.postgresql.auth.ScramSha256;
import ssg.legoflow.database.postgresql.client.PgClient;
import ssg.legoflow.database.postgresql.client.PgResult;
import ssg.legoflow.database.postgresql.client.PgStatement;
import ssg.legoflow.database.postgresql.server.PgServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
/**
 * Comprehensive demo of all PostgreSQL module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link PgServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports the full PostgreSQL v3 wire protocol:
 * simple query, extended query (prepared statements), COPY IN/OUT, LISTEN/NOTIFY,
 * transactions, and authentication (trust, cleartext, MD5, SCRAM-SHA-256).
 * Backed by an {@link ssg.legoflow.database.postgresql.server.InMemoryDatabase}
 * supporting CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, and DROP TABLE.
 * Ideal for development, testing, CI/CD, and learning the PostgreSQL wire protocol.</p>
 *
 * <p><b>Alternative: External PostgreSQL, CockroachDB, or YugabyteDB</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with real query planning and execution</li>
 *   <li>Advanced SQL features (joins, aggregates, window functions, CTEs)</li>
 *   <li>Persistence, replication, and high availability</li>
 *   <li>Integration testing against a real PostgreSQL cluster</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (simple query, prepared statements, COPY, LISTEN/NOTIFY) uses the
 * same API regardless of backend. When {@code USE_EXTERNAL=true}, the demo skips
 * server creation and connects directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Simple query — CREATE TABLE, INSERT, SELECT, UPDATE, DELETE</li>
 *   <li>Extended query — prepared statements with parameterized queries ($1, $2)</li>
 *   <li>COPY protocol — bulk data import (COPY FROM STDIN) and export (COPY TO STDOUT)</li>
 *   <li>LISTEN/NOTIFY — asynchronous notifications between client sessions</li>
 *   <li>Transactions — BEGIN/COMMIT/ROLLBACK</li>
 *   <li>Cleartext authentication — simple password authentication</li>
 *   <li>MD5 authentication — salted MD5 hash authentication</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoPostgreSqlAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoPostgreSqlAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house PgServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for PostgreSQL
    // =========================================================================

    /** Set to {@code true} to connect to an external PostgreSQL server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external PostgreSQL server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external PostgreSQL server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 5432;

    private DemoPostgreSqlAll() {}

    /**
     * Results from running the full demo.
     *
     * @param simpleQueryRows     number of rows returned from final SELECT
     * @param extendedQueryRows   number of rows inserted via prepared statements
     * @param copyInRows          number of rows imported via COPY FROM STDIN
     * @param copyOutRows         number of rows exported via COPY TO STDOUT
     * @param listenNotify        true if LISTEN/NOTIFY notification was received
     * @param transactionCommit   number of rows visible after transaction COMMIT
     * @param authCleartext       true if cleartext authentication succeeded
     * @param authMd5             true if MD5 authentication succeeded
     * @param authScramSha256     true if SCRAM-SHA-256 authentication succeeded
     * @param aggregateQueries    number of aggregate result values verified
     * @param joinQueries         number of JOIN result rows
     */
    public record Results(
            int simpleQueryRows,
            int extendedQueryRows,
            int copyInRows,
            int copyOutRows,
            boolean listenNotify,
            int transactionCommit,
            boolean authCleartext,
            boolean authMd5,
            boolean authScramSha256,
            int aggregateQueries,
            int joinQueries
    ) {}

    /**
     * Runs the comprehensive demo covering all PostgreSQL features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT,
                    "testdb", "testuser", null);
        }

        int simpleQueryRows;
        int extendedQueryRows;
        int copyInRows;
        int copyOutRows;
        boolean listenNotify;
        int transactionCommit;

        // Main features using trust auth (no password)
        try (var server = new PgServer()) {
            server.start(0);
            int port = server.port();
            LOG.info("In-house PgServer started on port {}", port);

            simpleQueryRows = demoSimpleQuery("127.0.0.1", port, "testdb", "testuser", null);
            extendedQueryRows = demoExtendedQuery("127.0.0.1", port, "testdb", "testuser", null);
            copyInRows = demoCopyIn("127.0.0.1", port, "testdb", "testuser", null);
            copyOutRows = demoCopyOut("127.0.0.1", port, "testdb", "testuser", null);
            listenNotify = demoListenNotify(server);
            transactionCommit = demoTransactions("127.0.0.1", port, "testdb", "testuser", null);
        }

        // Auth demos need separate servers with authenticators configured
        boolean authCleartext = demoCleartextAuth();
        boolean authMd5 = demoMd5Auth();
        boolean authScramSha256 = demoScramSha256Auth();

        // SQL feature demos
        int aggregateQueries;
        int joinQueries;
        try (var server2 = new PgServer()) {
            server2.start(0);
            int port2 = server2.port();
            aggregateQueries = demoAggregateQueries("127.0.0.1", port2, "testdb", "testuser", null);
            joinQueries = demoJoinQueries("127.0.0.1", port2, "testdb", "testuser", null);
        }

        return new Results(simpleQueryRows, extendedQueryRows, copyInRows,
                copyOutRows, listenNotify, transactionCommit, authCleartext, authMd5,
                authScramSha256, aggregateQueries, joinQueries);
    }

    private static Results runWithExternalServer(String host, int port,
                                                  String db, String user, String password) throws Exception {
        int simpleQueryRows = demoSimpleQuery(host, port, db, user, password);
        int extendedQueryRows = demoExtendedQuery(host, port, db, user, password);
        // COPY and LISTEN/NOTIFY require specific setup on external servers
        return new Results(simpleQueryRows, extendedQueryRows, 0, 0,
                false, 0, false, false, false, 0, 0);
    }

    // ======================== 1. SIMPLE QUERY ================================

    /**
     * Demonstrates the simple query protocol: CREATE TABLE, INSERT, SELECT,
     * UPDATE, DELETE using plain SQL text.
     */
    static int demoSimpleQuery(String host, int port, String db, String user,
                                String password) throws IOException {
        LOG.info("=== 1. Simple Query ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            // CREATE TABLE
            client.query("CREATE TABLE demo_users (id int4, name varchar, email varchar)");
            LOG.info("Created table demo_users");

            // INSERT rows
            client.query("INSERT INTO demo_users VALUES (1, 'Alice', 'alice@example.com')");
            client.query("INSERT INTO demo_users VALUES (2, 'Bob', 'bob@example.com')");
            client.query("INSERT INTO demo_users VALUES (3, 'Charlie', 'charlie@example.com')");
            LOG.info("Inserted 3 rows");

            // SELECT all
            PgResult allRows = client.query("SELECT * FROM demo_users");
            LOG.info("SELECT * returned {} rows", allRows.rowCount());

            // SELECT with WHERE
            PgResult filtered = client.query("SELECT name, email FROM demo_users WHERE id = 2");
            LOG.info("SELECT WHERE id=2: name={}", filtered.getString(0, "name"));

            // UPDATE
            client.query("UPDATE demo_users SET email = 'newalice@example.com' WHERE id = 1");

            // DELETE
            client.query("DELETE FROM demo_users WHERE id = 3");

            // Final SELECT
            PgResult finalResult = client.query("SELECT * FROM demo_users");
            LOG.info("After UPDATE+DELETE: {} rows", finalResult.rowCount());

            // Cleanup
            client.query("DROP TABLE demo_users");

            return finalResult.rowCount();
        }
    }

    // ======================== 2. EXTENDED QUERY ==============================

    /**
     * Demonstrates the extended query protocol with prepared statements.
     * <p>
     * Prepared statements use $1, $2, ... parameter placeholders. The server
     * parses the SQL once, then binds and executes with different parameters.
     * This avoids SQL injection and improves performance for repeated queries.
     */
    static int demoExtendedQuery(String host, int port, String db, String user,
                                  String password) throws IOException {
        LOG.info("=== 2. Extended Query (Prepared Statements) ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            client.query("CREATE TABLE demo_products (id int4, name varchar, price varchar)");

            int inserted = 0;

            // Prepare INSERT statement
            try (PgStatement insertStmt = client.prepare(
                    "INSERT INTO demo_products VALUES ($1, $2, $3)")) {
                insertStmt.execute("1", "Widget", "9.99");
                insertStmt.execute("2", "Gadget", "19.99");
                insertStmt.execute("3", "Doohickey", "4.99");
                inserted = 3;
            }

            // Prepare SELECT statement
            try (PgStatement selectStmt = client.prepare(
                    "SELECT * FROM demo_products WHERE id = $1")) {
                PgResult result = selectStmt.execute("2");
                LOG.info("Prepared SELECT: name={}", result.getString(0, "name"));
            }

            // Verify all rows
            PgResult all = client.query("SELECT * FROM demo_products");
            LOG.info("Extended query: {} rows inserted", all.rowCount());

            // Cleanup
            client.query("DROP TABLE demo_products");

            return inserted;
        }
    }

    // ======================== 3. COPY IN =====================================

    /**
     * Demonstrates COPY FROM STDIN for bulk data import.
     * <p>
     * The COPY protocol bypasses the regular query parser for high-throughput
     * data loading. Data rows are sent as tab-delimited text with newline terminators.
     */
    static int demoCopyIn(String host, int port, String db, String user,
                           String password) throws IOException {
        LOG.info("=== 3. COPY IN (Bulk Import) ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            client.query("CREATE TABLE demo_copy (id int4, name varchar, score varchar)");

            // Bulk import via COPY FROM STDIN
            List<String> rows = List.of(
                    "1\tAlpha\t100\n",
                    "2\tBravo\t200\n",
                    "3\tCharlie\t300\n",
                    "4\tDelta\t400\n",
                    "5\tEcho\t500\n"
            );
            PgResult copyResult = client.copyIn(
                    "COPY demo_copy FROM STDIN", rows);
            LOG.info("COPY IN result: {}", copyResult.commandTag());

            // Verify
            PgResult verify = client.query("SELECT * FROM demo_copy");
            LOG.info("COPY IN imported {} rows", verify.rowCount());

            // Keep table for COPY OUT demo
            return verify.rowCount();
        }
    }

    // ======================== 4. COPY OUT ====================================

    /**
     * Demonstrates COPY TO STDOUT for bulk data export.
     */
    static int demoCopyOut(String host, int port, String db, String user,
                            String password) throws IOException {
        LOG.info("=== 4. COPY OUT (Bulk Export) ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            // Export via COPY TO STDOUT (table created in COPY IN demo)
            List<String> exportedRows = client.copyOut(
                    "COPY demo_copy TO STDOUT");
            LOG.info("COPY OUT exported {} rows", exportedRows.size());

            for (String row : exportedRows) {
                LOG.info("  Exported: {}", row);
            }

            // Cleanup
            client.query("DROP TABLE demo_copy");

            return exportedRows.size();
        }
    }

    // ======================== 5. LISTEN/NOTIFY ===============================

    /**
     * Demonstrates asynchronous LISTEN/NOTIFY between client sessions.
     * <p>
     * One client LISTENs on a channel, another sends NOTIFY with a payload.
     * Notifications are delivered asynchronously via the PostgreSQL backend protocol.
     */
    static boolean demoListenNotify(PgServer server) throws Exception {
        LOG.info("=== 5. LISTEN/NOTIFY ===");
        int port = server.port();
        var receivedPayload = new AtomicReference<String>();

        // Listener registers on its session, then the notifier sends a notification.
        // Since the in-house server delivers notifications piggyback on query responses,
        // the listener must issue a subsequent query to receive the pending notification.
        try (PgClient listener = PgClient.connect("127.0.0.1", port,
                "testdb", "testuser", null)) {
            listener.listen("demo_channel", notif ->
                    receivedPayload.set(notif.payload()));

            // Notifier sends a notification on a separate connection
            try (PgClient notifier = PgClient.connect("127.0.0.1", port,
                    "testdb", "testuser", null)) {
                notifier.notify("demo_channel", "hello-from-demo");
            }

            // Issue a no-op query on the listener to flush pending notifications
            listener.query("CREATE TABLE notify_test (id int4)");
            listener.query("DROP TABLE notify_test");
        }

        String payload = receivedPayload.get();
        LOG.info("LISTEN/NOTIFY received: {}", payload);
        return "hello-from-demo".equals(payload);
    }

    // ======================== 6. TRANSACTIONS ================================

    /**
     * Demonstrates transactions using BEGIN/COMMIT.
     * <p>
     * Transaction status is tracked via ReadyForQuery indicators:
     * 'I' (idle), 'T' (in transaction), 'E' (failed transaction).
     */
    static int demoTransactions(String host, int port, String db, String user,
                                 String password) throws IOException {
        LOG.info("=== 6. Transactions ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            client.query("CREATE TABLE demo_txn (id int4, value varchar)");

            // Begin transaction
            client.query("BEGIN");
            client.query("INSERT INTO demo_txn VALUES (1, 'committed-1')");
            client.query("INSERT INTO demo_txn VALUES (2, 'committed-2')");
            client.query("COMMIT");

            // Verify committed rows
            PgResult result = client.query("SELECT * FROM demo_txn");
            LOG.info("Transaction committed: {} rows", result.rowCount());

            // Cleanup
            client.query("DROP TABLE demo_txn");

            return result.rowCount();
        }
    }

    // ======================== 7. CLEARTEXT AUTHENTICATION ====================

    /**
     * Demonstrates cleartext password authentication.
     * <p>
     * The server is configured with a {@link CleartextAuth} authenticator.
     * The client sends the password in plain text during the startup handshake.
     * Simple but least secure — always use TLS in production.
     */
    static boolean demoCleartextAuth() throws IOException {
        LOG.info("=== 7. Cleartext Authentication ===");
        var auth = new CleartextAuth();
        auth.addUser("demouser", "demopass");

        try (var server = new PgServer(auth)) {
            server.start(0);
            int port = server.port();

            try (PgClient client = PgClient.connect("127.0.0.1", port,
                    "testdb", "demouser", "demopass")) {
                // Verify connection works by executing a DDL statement
                PgResult result = client.query("CREATE TABLE auth_test (id int4)");
                LOG.info("Cleartext auth: connected, command={}", result.commandTag());
                client.query("DROP TABLE auth_test");
                return "CREATE TABLE".equals(result.commandTag());
            }
        }
    }

    // ======================== 8. MD5 AUTHENTICATION ==========================

    /**
     * Demonstrates MD5 password authentication.
     * <p>
     * The server sends a 4-byte random salt. The client computes:
     * {@code "md5" + md5(md5(password + username) + salt)}
     * <p>
     * More secure than cleartext as the password never crosses the wire
     * in plain text, even without TLS.
     */
    static boolean demoMd5Auth() throws IOException {
        LOG.info("=== 8. MD5 Authentication ===");
        var auth = new Md5Auth();
        auth.addUser("md5user", "md5pass");

        try (var server = new PgServer(auth)) {
            server.start(0);
            int port = server.port();

            try (PgClient client = PgClient.connect("127.0.0.1", port,
                    "testdb", "md5user", "md5pass")) {
                // Verify connection works by executing a DDL statement
                PgResult result = client.query("CREATE TABLE auth_test (id int4)");
                LOG.info("MD5 auth: connected, command={}", result.commandTag());
                client.query("DROP TABLE auth_test");
                return "CREATE TABLE".equals(result.commandTag());
            }
        }
    }

    // ======================== 9. SCRAM-SHA-256 AUTHENTICATION ================

    /**
     * Demonstrates SCRAM-SHA-256 authentication (RFC 5802).
     * <p>
     * The most secure built-in PostgreSQL authentication mechanism. Uses a
     * four-step challenge-response handshake with PBKDF2-HMAC-SHA-256 key
     * derivation. The password never crosses the wire in any form -- only
     * cryptographic proofs are exchanged.
     */
    static boolean demoScramSha256Auth() throws IOException {
        LOG.info("=== 9. SCRAM-SHA-256 Authentication ===");
        var auth = new ScramSha256().addUser("scramuser", "scrampass");

        try (var server = new PgServer(auth)) {
            server.start(0);
            int port = server.port();

            try (PgClient client = PgClient.connect("127.0.0.1", port,
                    "testdb", "scramuser", "scrampass")) {
                // Verify connection works by executing a query
                client.query("CREATE TABLE scram_test (id int4, value varchar)");
                client.query("INSERT INTO scram_test VALUES (1, 'authenticated')");
                PgResult result = client.query("SELECT * FROM scram_test");
                LOG.info("SCRAM-SHA-256 auth: connected, rows={}", result.rowCount());
                client.query("DROP TABLE scram_test");
                return result.rowCount() == 1;
            }
        }
    }

    // ======================== 10. AGGREGATE QUERIES ==========================

    /**
     * Demonstrates aggregate functions (COUNT, SUM, AVG, MIN, MAX) with GROUP BY.
     * <p>
     * The in-memory database supports aggregate functions in SELECT,
     * GROUP BY for grouping rows, and HAVING for filtering groups.
     */
    static int demoAggregateQueries(String host, int port, String db, String user,
                                     String password) throws IOException {
        LOG.info("=== 10. Aggregate Queries ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            client.query("CREATE TABLE demo_agg (dept varchar, product varchar, amount int4)");
            client.query("INSERT INTO demo_agg VALUES ('A', 'Widget', 100)");
            client.query("INSERT INTO demo_agg VALUES ('A', 'Gadget', 200)");
            client.query("INSERT INTO demo_agg VALUES ('B', 'Widget', 150)");
            client.query("INSERT INTO demo_agg VALUES ('B', 'Gadget', 300)");
            client.query("INSERT INTO demo_agg VALUES ('B', 'Doohickey', 50)");

            // COUNT(*)
            PgResult countResult = client.query("SELECT COUNT(*) AS cnt FROM demo_agg");
            LOG.info("COUNT(*): {}", countResult.getString(0, "cnt"));

            // GROUP BY with SUM
            PgResult groupResult = client.query("SELECT dept, SUM(amount) AS total FROM demo_agg GROUP BY dept");
            LOG.info("GROUP BY dept: {} groups", groupResult.rowCount());

            int verified = 0;
            if ("5".equals(countResult.getString(0, "cnt"))) verified++;
            if (groupResult.rowCount() == 2) verified++;

            // Cleanup
            client.query("DROP TABLE demo_agg");

            return verified;
        }
    }

    // ======================== 11. JOIN QUERIES ===============================

    /**
     * Demonstrates JOIN support (INNER JOIN, LEFT JOIN) between tables.
     * <p>
     * The in-memory database supports table aliases, qualified column
     * references, INNER JOIN and LEFT JOIN with ON conditions.
     */
    static int demoJoinQueries(String host, int port, String db, String user,
                                String password) throws IOException {
        LOG.info("=== 11. JOIN Queries ===");
        try (PgClient client = PgClient.connect(host, port, db, user, password)) {
            client.query("CREATE TABLE demo_customers (id int4, name varchar)");
            client.query("INSERT INTO demo_customers VALUES (1, 'Alice')");
            client.query("INSERT INTO demo_customers VALUES (2, 'Bob')");
            client.query("INSERT INTO demo_customers VALUES (3, 'Charlie')");

            client.query("CREATE TABLE demo_orders (id int4, customer_id int4, product varchar)");
            client.query("INSERT INTO demo_orders VALUES (101, 1, 'Widget')");
            client.query("INSERT INTO demo_orders VALUES (102, 1, 'Gadget')");
            client.query("INSERT INTO demo_orders VALUES (103, 2, 'Widget')");

            // INNER JOIN
            PgResult innerResult = client.query(
                    "SELECT c.name, o.product FROM demo_customers c JOIN demo_orders o ON c.id = o.customer_id");
            LOG.info("INNER JOIN: {} rows", innerResult.rowCount());

            // LEFT JOIN
            PgResult leftResult = client.query(
                    "SELECT c.name, o.product FROM demo_customers c LEFT JOIN demo_orders o ON c.id = o.customer_id");
            LOG.info("LEFT JOIN: {} rows", leftResult.rowCount());

            int totalRows = innerResult.rowCount() + leftResult.rowCount();

            // Cleanup
            client.query("DROP TABLE demo_orders");
            client.query("DROP TABLE demo_customers");

            return totalRows;
        }
    }
}
