package ssg.legoflow.database.postgresql.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.database.postgresql.client.PgClient;
import ssg.legoflow.database.postgresql.client.PgResult;
import ssg.legoflow.database.postgresql.client.PgStatement;
import ssg.legoflow.database.postgresql.server.PgServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * Demo showing the full lifecycle of a PostgreSQL wire protocol session:
 * connection, simple query, extended query, COPY, and LISTEN/NOTIFY.
 *
 * @since 0.1.0
 */
public final class PostgreSqlDemo {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlDemo.class);

    private PgServer server;
    private int port;

    /**
     * Creates a new demo instance.
     */
    public PostgreSqlDemo() {}

    /**
     * Starts the demo server on an ephemeral port.
     *
     * @throws IOException if the server cannot be started
     */
    public void startServer() throws IOException {
        server = new PgServer();
        server.start(0);
        port = server.port();
        LOG.info("Demo server started on port {}", port);
    }

    /**
     * Returns the server port.
     *
     * @return the port
     */
    public int port() {
        return port;
    }

    /**
     * Runs the simple query demo.
     *
     * @return the results from the demo queries
     * @throws IOException if an I/O error occurs
     */
    public List<PgResult> runSimpleQueryDemo() throws IOException {
        List<PgResult> results = new ArrayList<>();
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            // CREATE TABLE
            results.add(client.query("CREATE TABLE users (id int4, name varchar, email varchar)"));

            // INSERT
            results.add(client.query("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')"));
            results.add(client.query("INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')"));
            results.add(client.query("INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com')"));

            // SELECT
            results.add(client.query("SELECT * FROM users"));
            results.add(client.query("SELECT name, email FROM users WHERE id = 2"));

            // UPDATE
            results.add(client.query("UPDATE users SET email = 'newalice@example.com' WHERE id = 1"));

            // DELETE
            results.add(client.query("DELETE FROM users WHERE id = 3"));

            // Final SELECT
            results.add(client.query("SELECT * FROM users"));
        }
        return results;
    }

    /**
     * Runs the extended query (prepared statement) demo.
     *
     * @return the results from prepared statement queries
     * @throws IOException if an I/O error occurs
     */
    public List<PgResult> runExtendedQueryDemo() throws IOException {
        List<PgResult> results = new ArrayList<>();
        try (PgClient client = PgClient.connect("127.0.0.1", port, "testdb", "testuser", null)) {
            // Table may already exist from simple query demo
            try {
                client.query("CREATE TABLE IF NOT EXISTS products (id int4, name varchar, price varchar)");
            } catch (IOException e) {
                // ignore if exists
            }

            // Prepare and execute INSERT
            try (PgStatement insertStmt = client.prepare("INSERT INTO products VALUES ($1, $2, $3)")) {
                results.add(insertStmt.execute("1", "Widget", "9.99"));
                results.add(insertStmt.execute("2", "Gadget", "19.99"));
                results.add(insertStmt.execute("3", "Doohickey", "4.99"));
            }

            // Prepare and execute SELECT
            try (PgStatement selectStmt = client.prepare("SELECT * FROM products WHERE id = $1")) {
                results.add(selectStmt.execute("2"));
            }

            // Select all
            results.add(client.query("SELECT * FROM products"));
        }
        return results;
    }

    /**
     * Stops the demo server.
     */
    public void stopServer() {
        if (server != null) {
            server.close();
        }
    }
}
