package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.protocol.*;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * High-level MySQL client providing connection, queries, and prepared statements.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (var client = MysqlClient.connect("localhost", 3306, "root", "pass", "mydb")) {
 *     var result = client.query("SELECT * FROM users");
 *     while (result.next()) {
 *         System.out.println(result.getString("name"));
 *     }
 *
 *     try (var ps = client.prepare("INSERT INTO users (name) VALUES (?)")) {
 *         ps.setString(0, "Alice");
 *         ps.executeUpdate();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public class MysqlClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MysqlClient.class);

    private final MysqlConnection connection;

    /**
     * Creates a client wrapping an existing connection.
     *
     * @param connection the MySQL connection
     */
    public MysqlClient(MysqlConnection connection) {
        this.connection = connection;
    }

    /**
     * Connects to a MySQL server.
     *
     * @param host the server host
     * @param port the server port
     * @param username the username
     * @param password the password
     * @param database the initial database (or null)
     * @return the connected client
     * @throws IOException if connection fails
     */
    public static MysqlClient connect(String host, int port, String username,
                                       String password, String database) throws IOException {
        var socket = new Socket(host, port);
        var conn = new MysqlConnection(socket);
        conn.connect(username, password, database, ConnectionAttributes.defaults());
        return new MysqlClient(conn);
    }

    /**
     * Connects with custom connection attributes.
     *
     * @param host the server host
     * @param port the server port
     * @param username the username
     * @param password the password
     * @param database the initial database (or null)
     * @param attributes connection attributes
     * @return the connected client
     * @throws IOException if connection fails
     */
    public static MysqlClient connect(String host, int port, String username,
                                       String password, String database,
                                       Map<String, String> attributes) throws IOException {
        var socket = new Socket(host, port);
        var conn = new MysqlConnection(socket);
        conn.connect(username, password, database, attributes);
        return new MysqlClient(conn);
    }

    /**
     * Executes a query that returns a result set.
     *
     * @param sql the SQL query
     * @return the result set
     * @throws IOException if an error occurs
     */
    public MysqlResult query(String sql) throws IOException {
        return new MysqlStatement(connection).executeQuery(sql);
    }

    /**
     * Executes an update statement (INSERT, UPDATE, DELETE, CREATE, DROP).
     *
     * @param sql the SQL statement
     * @return the number of affected rows
     * @throws IOException if an error occurs
     */
    public long execute(String sql) throws IOException {
        return new MysqlStatement(connection).executeUpdate(sql);
    }

    /**
     * Executes a generic SQL statement.
     *
     * @param sql the SQL statement
     * @return the result (may be a result set or affected rows)
     * @throws IOException if an error occurs
     */
    public MysqlResult executeStatement(String sql) throws IOException {
        return new MysqlStatement(connection).execute(sql);
    }

    /**
     * Prepares a SQL statement.
     *
     * @param sql the SQL query with '?' placeholders
     * @return the prepared statement
     * @throws IOException if an error occurs
     */
    public MysqlPreparedStatement prepare(String sql) throws IOException {
        return MysqlPreparedStatement.prepare(connection, sql);
    }

    /**
     * Sends a ping to the server.
     *
     * @return true if the server responded with OK
     * @throws IOException if an error occurs
     */
    public boolean ping() throws IOException {
        connection.sendPacket(MysqlCodec.encodePing());
        var response = connection.receivePacket();
        return MysqlCodec.isOk(response.payload());
    }

    /**
     * Selects a database.
     *
     * @param database the database name
     * @throws IOException if an error occurs
     * @throws MysqlConnection.MysqlException if the database doesn't exist
     */
    public void useDatabase(String database) throws IOException {
        connection.sendPacket(MysqlCodec.encodeInitDb(database));
        var response = connection.receivePacket();
        if (MysqlCodec.isErr(response.payload())) {
            var err = ErrPacket.decode(response.payload(), connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }
    }

    /**
     * Gets server statistics.
     *
     * @return the statistics string
     * @throws IOException if an error occurs
     */
    public String statistics() throws IOException {
        connection.sendPacket(MysqlCodec.encodeStatistics());
        var response = connection.receivePacket();
        return new String(response.payload(), StandardCharsets.UTF_8);
    }

    /**
     * Resets the connection session state.
     *
     * @throws IOException if an error occurs
     */
    public void resetConnection() throws IOException {
        connection.sendPacket(MysqlCodec.encodeResetConnection());
        var response = connection.receivePacket();
        if (MysqlCodec.isErr(response.payload())) {
            var err = ErrPacket.decode(response.payload(), connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }
    }

    /**
     * Sets multi-statement option.
     *
     * @param enable true to enable, false to disable
     * @throws IOException if an error occurs
     */
    public void setMultiStatements(boolean enable) throws IOException {
        connection.sendPacket(MysqlCodec.encodeSetOption(enable ? 0 : 1));
        var response = connection.receivePacket();
        // Response is EOF for COM_SET_OPTION
    }

    /**
     * Returns the underlying connection.
     *
     * @return the MySQL connection
     */
    public MysqlConnection connection() {
        return connection;
    }

    /**
     * Returns the server version string.
     *
     * @return the server version
     */
    public String serverVersion() {
        return connection.serverHandshake().serverVersion();
    }

    /**
     * Checks if the connection is alive.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connection.isConnected();
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
