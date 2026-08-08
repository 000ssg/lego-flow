package ssg.legoflow.database.postgresql.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.database.postgresql.protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * PostgreSQL client supporting simple query, extended query, COPY, and LISTEN/NOTIFY.
 *
 * @since 0.1.0
 */
public final class PgClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PgClient.class);
    private static final AtomicInteger STATEMENT_COUNTER = new AtomicInteger();

    private final PgConnection connection;
    private final InputStream in;
    private final OutputStream out;
    private final List<Consumer<BackendMessage.NotificationResponse>> notificationListeners =
            new CopyOnWriteArrayList<>();

    private PgClient(PgConnection connection) {
        this.connection = connection;
        this.in = connection.inputStream();
        this.out = connection.outputStream();
    }

    /**
     * Connects to a PostgreSQL server.
     *
     * @param host     the server host
     * @param port     the server port
     * @param database the database name
     * @param username the username
     * @param password the password (null for trust auth)
     * @return the connected client
     * @throws IOException if the connection fails
     */
    public static PgClient connect(String host, int port, String database,
                                   String username, String password) throws IOException {
        Socket socket = new Socket(host, port);
        var conn = new PgConnection(socket);
        conn.startup(username, database, password);
        return new PgClient(conn);
    }

    /**
     * Returns the underlying connection.
     *
     * @return the connection
     */
    public PgConnection connection() {
        return connection;
    }

    // ======== Simple Query Protocol ========

    /**
     * Executes a simple query.
     *
     * @param sql the SQL query
     * @return the query result
     * @throws IOException if an I/O error occurs
     */
    public PgResult query(String sql) throws IOException {
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Query(sql)));

        List<BackendMessage.ColumnDescription> columns = new ArrayList<>();
        List<byte[][]> rows = new ArrayList<>();
        String tag = null;

        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.RowDescription rd -> columns = rd.columns();
                case BackendMessage.DataRow dr -> rows.add(dr.values());
                case BackendMessage.CommandComplete cc -> tag = cc.tag();
                case BackendMessage.EmptyQueryResponse eq -> tag = "";
                case BackendMessage.ErrorResponse er ->
                        throw new IOException("Query error: " + er.message() + " [" + er.sqlState() + "]");
                case BackendMessage.NoticeResponse nr ->
                        LOG.info("Notice: {}", nr.message());
                case BackendMessage.NotificationResponse notif -> dispatchNotification(notif);
                case BackendMessage.ReadyForQuery rq -> {
                    return new PgResult(columns, rows, tag);
                }
                default -> LOG.debug("Unexpected message: {}", msg.getClass().getSimpleName());
            }
        }
    }

    /**
     * Executes a SQL command that returns no result set (INSERT, UPDATE, DELETE, DDL).
     *
     * @param sql the SQL command
     * @return the number of affected rows
     * @throws IOException if an I/O error occurs
     */
    public int execute(String sql) throws IOException {
        PgResult result = query(sql);
        return result.affectedRows();
    }

    // ======== Extended Query Protocol ========

    /**
     * Creates a prepared statement using the extended query protocol.
     *
     * @param sql            the SQL query with $1, $2, ... parameter placeholders
     * @param parameterTypes the parameter type OIDs (use 0 for unspecified)
     * @return the prepared statement handle
     * @throws IOException if an I/O error occurs
     */
    public PgStatement prepare(String sql, int... parameterTypes) throws IOException {
        String name = "stmt_" + STATEMENT_COUNTER.incrementAndGet();

        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.Parse(name, sql, parameterTypes)));
        PgCodec.write(out, PgCodec.encodeFrontend(
                new FrontendMessage.Describe((byte) 'S', name)));
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Sync()));

        List<BackendMessage.ColumnDescription> columns = null;
        while (true) {
            BackendMessage msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.ParseComplete pc -> {}
                case BackendMessage.ParameterDescription pd -> {}
                case BackendMessage.RowDescription rd -> columns = rd.columns();
                case BackendMessage.NoData nd -> columns = List.of();
                case BackendMessage.ErrorResponse er ->
                        throw new IOException("Prepare error: " + er.message());
                case BackendMessage.ReadyForQuery rq -> {
                    return new PgStatement(name, in, out, columns);
                }
                default -> {}
            }
        }
    }

    // ======== COPY ========

    /**
     * Executes a COPY FROM STDIN command and sends the given data.
     *
     * @param copyCommand the COPY ... FROM STDIN command
     * @param rows        the data rows to send
     * @return the result
     * @throws IOException if an I/O error occurs
     */
    public PgResult copyIn(String copyCommand, List<String> rows) throws IOException {
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Query(copyCommand)));

        BackendMessage msg = PgCodec.decodeBackend(in);
        if (!(msg instanceof BackendMessage.CopyInResponse)) {
            throw new IOException("Expected CopyInResponse, got: " + msg.getClass().getSimpleName());
        }

        PgCopyStream copyStream = new PgCopyStream(in, out);
        copyStream.writeCopyData(rows);

        // Read result
        List<BackendMessage.ColumnDescription> columns = new ArrayList<>();
        List<byte[][]> dataRows = new ArrayList<>();
        String tag = null;

        while (true) {
            msg = PgCodec.decodeBackend(in);
            switch (msg) {
                case BackendMessage.CommandComplete cc -> tag = cc.tag();
                case BackendMessage.ErrorResponse er ->
                        throw new IOException("COPY error: " + er.message());
                case BackendMessage.ReadyForQuery rq -> {
                    return new PgResult(columns, dataRows, tag);
                }
                default -> {}
            }
        }
    }

    /**
     * Executes a COPY TO STDOUT command and reads the data.
     *
     * @param copyCommand the COPY ... TO STDOUT command
     * @return the COPY data rows
     * @throws IOException if an I/O error occurs
     */
    public List<String> copyOut(String copyCommand) throws IOException {
        PgCodec.write(out, PgCodec.encodeFrontend(new FrontendMessage.Query(copyCommand)));

        BackendMessage msg = PgCodec.decodeBackend(in);
        if (!(msg instanceof BackendMessage.CopyOutResponse)) {
            throw new IOException("Expected CopyOutResponse, got: " + msg.getClass().getSimpleName());
        }

        PgCopyStream copyStream = new PgCopyStream(in, out);
        List<String> rows = copyStream.readCopyData();

        // Read CommandComplete and ReadyForQuery
        while (true) {
            msg = PgCodec.decodeBackend(in);
            if (msg instanceof BackendMessage.ReadyForQuery) break;
        }

        return rows;
    }

    // ======== LISTEN/NOTIFY ========

    /**
     * Subscribes to notifications on a channel.
     *
     * @param channel  the channel name
     * @param listener the notification listener
     * @throws IOException if an I/O error occurs
     */
    public void listen(String channel, Consumer<BackendMessage.NotificationResponse> listener) throws IOException {
        notificationListeners.add(listener);
        query("LISTEN " + channel);
    }

    /**
     * Sends a notification on a channel.
     *
     * @param channel the channel name
     * @param payload the notification payload
     * @throws IOException if an I/O error occurs
     */
    public void notify(String channel, String payload) throws IOException {
        query("NOTIFY " + channel + ", '" + payload + "'");
    }

    /**
     * Adds a notification listener.
     *
     * @param listener the listener
     */
    public void addNotificationListener(Consumer<BackendMessage.NotificationResponse> listener) {
        notificationListeners.add(listener);
    }

    private void dispatchNotification(BackendMessage.NotificationResponse notif) {
        for (var listener : notificationListeners) {
            listener.accept(notif);
        }
    }

    // ======== Connection ========

    /**
     * Returns whether the client is connected.
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
