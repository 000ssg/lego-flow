package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.*;
import ssg.legoflow.database.mysql.auth.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Per-client session state and command handler for the MySQL server.
 *
 * <p>Manages the connection lifecycle: handshake, authentication, and
 * command processing. Each client connection runs in its own virtual thread.
 *
 * @since 0.1.0
 */
public class ClientSession implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ClientSession.class);

    private final int connectionId;
    private final InputStream in;
    private final OutputStream out;
    private final MysqlServer server;
    private final AtomicInteger nextStmtId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PreparedStatement> preparedStatements = new ConcurrentHashMap<>();

    private String username;
    private String currentDatabase;
    private int capabilities;
    private volatile boolean running = true;
    private boolean inTransaction;
    private Map<String, Map<String, java.util.List<Map<String, String>>>> transactionSnapshot;

    /**
     * Creates a new client session.
     *
     * @param connectionId the connection ID
     * @param in the client input stream
     * @param out the client output stream
     * @param server the parent server
     */
    public ClientSession(int connectionId, InputStream in, OutputStream out, MysqlServer server) {
        this.connectionId = connectionId;
        this.in = in;
        this.out = out;
        this.server = server;
    }

    /**
     * Returns the connection ID.
     *
     * @return the connection ID
     */
    public int connectionId() {
        return connectionId;
    }

    /**
     * Returns the current database.
     *
     * @return the selected database name, or null
     */
    public String currentDatabase() {
        return currentDatabase;
    }

    /**
     * Returns the username.
     *
     * @return the authenticated username
     */
    public String username() {
        return username;
    }

    /**
     * Returns the negotiated capabilities.
     *
     * @return the capabilities bitmask
     */
    public int capabilities() {
        return capabilities;
    }

    @Override
    public void run() {
        try {
            if (!performHandshake()) {
                return;
            }
            processCommands();
        } catch (IOException e) {
            LOG.debug("Client {} disconnected: {}", connectionId, e.getMessage());
        } finally {
            running = false;
            try {
                in.close();
                out.close();
            } catch (IOException ignored) {}
        }
    }

    private boolean performHandshake() throws IOException {
        // Send HandshakeV10
        var handshake = HandshakeV10.create(connectionId, server.defaultAuthPlugin());
        new MysqlPacket(0, handshake.encode()).writeTo(out);
        out.flush();

        // Read HandshakeResponse41
        var responsePacket = MysqlPacket.readFrom(in);
        var response = MysqlCodec.decodeHandshakeResponse(responsePacket.payload());

        this.username = response.username();
        this.capabilities = response.capabilities() & handshake.capabilityFlags();
        this.currentDatabase = response.database();

        // Authenticate
        var authPlugin = server.getAuthPlugin(
                response.authPluginName() != null ? response.authPluginName() : server.defaultAuthPlugin());

        boolean authenticated = false;
        if (authPlugin != null) {
            var storedHash = server.getStoredHash(username);
            if (storedHash != null) {
                authenticated = authPlugin.verify(response.authResponse(),
                        handshake.authPluginData(), storedHash);
            } else {
                // No stored hash — accept empty password
                authenticated = response.authResponse().length == 0;
            }
        } else {
            authenticated = true; // unknown plugin, accept
        }

        if (authenticated) {
            var ok = OkPacket.ok();
            new MysqlPacket(2, ok.encode(capabilities)).writeTo(out);
            out.flush();
            LOG.debug("Client {} authenticated as '{}'", connectionId, username);
            return true;
        } else {
            var err = ErrPacket.accessDenied(username, "localhost");
            new MysqlPacket(2, err.encode(capabilities)).writeTo(out);
            out.flush();
            LOG.debug("Client {} authentication failed for '{}'", connectionId, username);
            return false;
        }
    }

    private void processCommands() throws IOException {
        var executor = server.queryExecutor();

        while (running) {
            var packet = MysqlPacket.readFrom(in);
            int seqId = 1; // response sequence starts at 1 (after command at 0)
            var payload = packet.payload();

            if (payload.length == 0) continue;

            int command = MysqlCodec.commandByte(payload);

            switch (command) {
                case MysqlCodec.COM_QUIT -> {
                    running = false;
                    return;
                }

                case MysqlCodec.COM_PING -> {
                    var ok = OkPacket.ok();
                    new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                    out.flush();
                }

                case MysqlCodec.COM_INIT_DB -> {
                    String dbName = MysqlCodec.decodeInitDb(payload);
                    if (server.hasDatabase(dbName)) {
                        currentDatabase = dbName;
                        var ok = OkPacket.ok();
                        new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                    } else {
                        var err = ErrPacket.unknownDatabase(dbName);
                        new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
                    }
                    out.flush();
                }

                case MysqlCodec.COM_QUERY -> {
                    String sql = MysqlCodec.decodeQuery(payload);
                    LOG.debug("Client {} query: {}", connectionId, sql);

                    // Handle USE database
                    var useMatcher = java.util.regex.Pattern.compile(
                            "(?i)USE\\s+`?(\\w+)`?").matcher(sql.trim());
                    if (useMatcher.matches()) {
                        String dbName = useMatcher.group(1);
                        if (server.hasDatabase(dbName)) {
                            currentDatabase = dbName;
                            var ok = OkPacket.ok();
                            new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                        } else {
                            var err = ErrPacket.unknownDatabase(dbName);
                            new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
                        }
                    } else {
                        // Check for transaction statements that need session-level handling
                        String trimmedSql = sql.trim();
                        if (trimmedSql.endsWith(";")) {
                            trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
                        }
                        String upperSql = trimmedSql.toUpperCase();
                        if (upperSql.equals("BEGIN") || upperSql.equals("START TRANSACTION")
                                || upperSql.equals("COMMIT") || upperSql.equals("ROLLBACK")) {
                            var txnResult = handleTransaction(upperSql.equals("START TRANSACTION") ? "BEGIN" : upperSql);
                            executor.writeResult(out, txnResult, capabilities, seqId);
                        } else {
                            var result = executor.execute(sql, currentDatabase);
                            executor.writeResult(out, result, capabilities, seqId);
                        }
                    }
                    out.flush();
                }

                case MysqlCodec.COM_STMT_PREPARE -> {
                    String sql = MysqlCodec.decodePrepare(payload);
                    LOG.debug("Client {} prepare: {}", connectionId, sql);
                    handlePrepare(sql, seqId);
                    out.flush();
                }

                case MysqlCodec.COM_STMT_EXECUTE -> {
                    handleExecute(payload, seqId, executor);
                    out.flush();
                }

                case MysqlCodec.COM_STMT_CLOSE -> {
                    int stmtId = MysqlCodec.decodeStmtClose(payload);
                    preparedStatements.remove(stmtId);
                    // No response for COM_STMT_CLOSE
                }

                case MysqlCodec.COM_STMT_RESET -> {
                    int stmtId = MysqlCodec.decodeExecuteStatementId(payload);
                    var stmt = preparedStatements.get(stmtId);
                    if (stmt != null) {
                        stmt.resetLongData();
                        var ok = OkPacket.ok();
                        new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                    } else {
                        var err = ErrPacket.error(1243, "Unknown prepared statement handler");
                        new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
                    }
                    out.flush();
                }

                case MysqlCodec.COM_STMT_SEND_LONG_DATA -> {
                    int stmtId = MysqlCodec.decodeExecuteStatementId(payload);
                    var buf = ByteBuffer.wrap(payload);
                    buf.position(5); // skip command + stmtId
                    int paramId = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
                    var data = new byte[buf.remaining()];
                    buf.get(data);
                    var stmt = preparedStatements.get(stmtId);
                    if (stmt != null) {
                        stmt.setLongData(paramId, data);
                    }
                    // No response for COM_STMT_SEND_LONG_DATA
                }

                case MysqlCodec.COM_STATISTICS -> {
                    String stats = "Uptime: " + server.uptimeSeconds()
                            + "  Threads: " + server.activeConnections()
                            + "  Questions: 0  Slow queries: 0  Opens: 0"
                            + "  Flush tables: 0  Open tables: 0  Queries per second avg: 0.000";
                    new MysqlPacket(seqId, stats.getBytes(java.nio.charset.StandardCharsets.UTF_8)).writeTo(out);
                    out.flush();
                }

                case MysqlCodec.COM_FIELD_LIST -> {
                    var buf = ByteBuffer.wrap(payload);
                    buf.get(); // skip command byte
                    String tableName = LengthEncodedString.readNullTerminated(buf);
                    handleFieldList(tableName, seqId);
                    out.flush();
                }

                case MysqlCodec.COM_SET_OPTION -> {
                    var ok = OkPacket.ok();
                    new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                    out.flush();
                }

                case MysqlCodec.COM_RESET_CONNECTION -> {
                    currentDatabase = null;
                    preparedStatements.clear();
                    var ok = OkPacket.ok();
                    new MysqlPacket(seqId, ok.encode(capabilities)).writeTo(out);
                    out.flush();
                }

                default -> {
                    var err = ErrPacket.error(1047, "Unknown command: 0x" + Integer.toHexString(command));
                    new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
                    out.flush();
                }
            }
        }
    }

    private QueryExecutor.QueryResult handleTransaction(String statement) {
        return switch (statement) {
            case "BEGIN" -> {
                // Snapshot all databases
                inTransaction = true;
                transactionSnapshot = new java.util.HashMap<>();
                for (var dbEntry : server.allDatabases().entrySet()) {
                    transactionSnapshot.put(dbEntry.getKey(), dbEntry.getValue().snapshotAll());
                }
                yield new QueryExecutor.QueryResult.Ok(0, 0);
            }
            case "COMMIT" -> {
                // Discard snapshot
                inTransaction = false;
                transactionSnapshot = null;
                yield new QueryExecutor.QueryResult.Ok(0, 0);
            }
            case "ROLLBACK" -> {
                // Restore from snapshot
                if (inTransaction && transactionSnapshot != null) {
                    for (var dbEntry : transactionSnapshot.entrySet()) {
                        var db = server.getDatabase(dbEntry.getKey());
                        if (db != null) {
                            db.restoreAll(dbEntry.getValue());
                        }
                    }
                }
                inTransaction = false;
                transactionSnapshot = null;
                yield new QueryExecutor.QueryResult.Ok(0, 0);
            }
            default -> new QueryExecutor.QueryResult.Ok(0, 0);
        };
    }

    private void handlePrepare(String sql, int seqId) throws IOException {
        int paramCount = PreparedStatement.countParameters(sql);
        int stmtId = nextStmtId.getAndIncrement();

        // Build parameter definitions
        var paramDefs = new java.util.ArrayList<ColumnDefinition>();
        for (int i = 0; i < paramCount; i++) {
            paramDefs.add(ColumnDefinition.of("?", ColumnType.VAR_STRING, 255));
        }

        // For now, column definitions are empty until execution
        var colDefs = new java.util.ArrayList<ColumnDefinition>();

        var stmt = new PreparedStatement(stmtId, sql, paramCount, paramDefs, colDefs);
        preparedStatements.put(stmtId, stmt);

        // Send PrepareOk
        var prepareOk = new MysqlCodec.PrepareOk(stmtId, colDefs.size(), paramCount, 0);
        new MysqlPacket(seqId++, MysqlCodec.encodePrepareOk(prepareOk)).writeTo(out);

        // Send parameter definitions
        if (paramCount > 0) {
            for (var paramDef : paramDefs) {
                new MysqlPacket(seqId++, paramDef.encode()).writeTo(out);
            }
            if (!CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
                new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
            }
        }

        // Send column definitions (if any)
        if (!colDefs.isEmpty()) {
            for (var colDef : colDefs) {
                new MysqlPacket(seqId++, colDef.encode()).writeTo(out);
            }
            if (!CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
                new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
            }
        }
    }

    private void handleExecute(byte[] payload, int seqId, QueryExecutor executor) throws IOException {
        int stmtId = MysqlCodec.decodeExecuteStatementId(payload);
        var stmt = preparedStatements.get(stmtId);

        if (stmt == null) {
            var err = ErrPacket.error(1243, "Unknown prepared statement handler");
            new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
            return;
        }

        // Parse parameters from the execute payload
        var paramValues = new String[stmt.parameterCount()];
        if (stmt.parameterCount() > 0) {
            var buf = ByteBuffer.wrap(payload);
            buf.position(10); // skip command(1) + stmtId(4) + flags(1) + iteration_count(4)

            // NULL bitmap
            int nullBitmapLength = (stmt.parameterCount() + 7) / 8;
            var nullBitmap = new byte[nullBitmapLength];
            if (buf.remaining() >= nullBitmapLength) {
                buf.get(nullBitmap);
            }

            // new-params-bound-flag
            int newParamsBound = buf.hasRemaining() ? (buf.get() & 0xFF) : 0;

            if (newParamsBound == 1) {
                // Read parameter types (2 bytes each)
                for (int i = 0; i < stmt.parameterCount(); i++) {
                    if (buf.remaining() >= 2) {
                        buf.get(); // type
                        buf.get(); // unsigned flag
                    }
                }
            }

            // Read parameter values
            for (int i = 0; i < stmt.parameterCount(); i++) {
                int bytePos = i / 8;
                int bitPos = i % 8;
                boolean isNull = (nullBitmap[bytePos] & (1 << bitPos)) != 0;

                if (isNull) {
                    paramValues[i] = null;
                } else {
                    var longData = stmt.getLongData(i);
                    if (longData != null) {
                        paramValues[i] = new String(longData, java.nio.charset.StandardCharsets.UTF_8);
                    } else if (buf.hasRemaining()) {
                        paramValues[i] = LengthEncodedString.read(buf);
                    }
                }
            }
        }

        // Substitute and execute
        String sql = stmt.substitute(paramValues);
        var result = executor.execute(sql, currentDatabase);

        // Write result using binary protocol for result sets
        switch (result) {
            case QueryExecutor.QueryResult.Ok ok -> {
                var packet = OkPacket.ok(ok.affectedRows(), ok.lastInsertId());
                new MysqlPacket(seqId, packet.encode(capabilities)).writeTo(out);
            }
            case QueryExecutor.QueryResult.ResultSet rs ->
                    ResultSetWriter.writeBinaryResultSet(out, rs.columns(), rs.rows(), capabilities, seqId);
            case QueryExecutor.QueryResult.Error err -> {
                var packet = new ErrPacket(err.errorCode(), err.sqlState(), err.message());
                new MysqlPacket(seqId, packet.encode(capabilities)).writeTo(out);
            }
        }
    }

    private void handleFieldList(String tableName, int seqId) throws IOException {
        if (currentDatabase == null) {
            var err = ErrPacket.error(1046, "No database selected");
            new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
            return;
        }

        var db = server.getDatabase(currentDatabase);
        if (db == null) {
            var err = ErrPacket.unknownDatabase(currentDatabase);
            new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
            return;
        }

        var table = db.getTable(tableName);
        if (table == null) {
            var err = ErrPacket.unknownTable(tableName);
            new MysqlPacket(seqId, err.encode(capabilities)).writeTo(out);
            return;
        }

        for (var entry : table.columns().entrySet()) {
            var colDef = ColumnDefinition.of(currentDatabase, tableName,
                    entry.getKey(), entry.getValue(), 255, 0);
            new MysqlPacket(seqId++, colDef.encode()).writeTo(out);
        }

        if (!CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            new MysqlPacket(seqId, EofPacket.eof().encode(capabilities)).writeTo(out);
        }
    }
}
