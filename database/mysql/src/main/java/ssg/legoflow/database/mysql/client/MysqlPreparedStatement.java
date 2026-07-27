package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.protocol.*;
import ssg.legoflow.database.mysql.server.ColumnDefinition;
import ssg.legoflow.database.mysql.server.ResultSetWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL prepared statement with binary protocol.
 *
 * <p>Supports COM_STMT_PREPARE, COM_STMT_EXECUTE with parameter binding,
 * COM_STMT_CLOSE, COM_STMT_RESET, and COM_STMT_SEND_LONG_DATA.
 *
 * @since 1.0.0
 */
public class MysqlPreparedStatement implements AutoCloseable {

    private final MysqlConnection connection;
    private final int statementId;
    private final int paramCount;
    private final int columnCount;
    private final List<ColumnDefinition> paramDefinitions;
    private final List<ColumnDefinition> columnDefinitions;
    private final String[] paramValues;
    private final ColumnType[] paramTypes;

    /**
     * Creates a prepared statement (internal — use {@link #prepare} factory).
     */
    private MysqlPreparedStatement(MysqlConnection connection, int statementId,
                                    int paramCount, int columnCount,
                                    List<ColumnDefinition> paramDefinitions,
                                    List<ColumnDefinition> columnDefinitions) {
        this.connection = connection;
        this.statementId = statementId;
        this.paramCount = paramCount;
        this.columnCount = columnCount;
        this.paramDefinitions = paramDefinitions;
        this.columnDefinitions = columnDefinitions;
        this.paramValues = new String[paramCount];
        this.paramTypes = new ColumnType[paramCount];
        for (int i = 0; i < paramCount; i++) {
            paramTypes[i] = ColumnType.VAR_STRING;
        }
    }

    /**
     * Prepares a SQL statement on the server.
     *
     * @param connection the MySQL connection
     * @param sql the SQL query with '?' placeholders
     * @return the prepared statement
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if preparation fails
     */
    public static MysqlPreparedStatement prepare(MysqlConnection connection, String sql)
            throws IOException {
        connection.sendPacket(MysqlCodec.encodePrepare(sql));

        // Read prepare OK
        var okPacket = connection.receivePacket();
        var payload = okPacket.payload();

        if (MysqlCodec.isErr(payload)) {
            var err = ErrPacket.decode(payload, connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }

        var prepareOk = MysqlCodec.decodePrepareOk(payload);

        // Read parameter definitions
        var paramDefs = new ArrayList<ColumnDefinition>();
        if (prepareOk.numParams() > 0) {
            for (int i = 0; i < prepareOk.numParams(); i++) {
                var paramPacket = connection.receivePacket();
                paramDefs.add(ColumnDefinition.decode(paramPacket.payload()));
            }
            if (!CapabilityFlags.hasCapability(connection.capabilities(), CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
                connection.receivePacket(); // EOF
            }
        }

        // Read column definitions
        var colDefs = new ArrayList<ColumnDefinition>();
        if (prepareOk.numColumns() > 0) {
            for (int i = 0; i < prepareOk.numColumns(); i++) {
                var colPacket = connection.receivePacket();
                colDefs.add(ColumnDefinition.decode(colPacket.payload()));
            }
            if (!CapabilityFlags.hasCapability(connection.capabilities(), CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
                connection.receivePacket(); // EOF
            }
        }

        return new MysqlPreparedStatement(connection, prepareOk.statementId(),
                prepareOk.numParams(), prepareOk.numColumns(), paramDefs, colDefs);
    }

    /**
     * Returns the statement ID.
     *
     * @return the server-assigned statement ID
     */
    public int statementId() {
        return statementId;
    }

    /**
     * Returns the parameter count.
     *
     * @return the number of parameters
     */
    public int paramCount() {
        return paramCount;
    }

    /**
     * Sets a string parameter value.
     *
     * @param index the parameter index (0-based)
     * @param value the value
     */
    public void setString(int index, String value) {
        paramValues[index] = value;
        paramTypes[index] = ColumnType.VAR_STRING;
    }

    /**
     * Sets an integer parameter value.
     *
     * @param index the parameter index (0-based)
     * @param value the value
     */
    public void setInt(int index, int value) {
        paramValues[index] = String.valueOf(value);
        paramTypes[index] = ColumnType.LONG;
    }

    /**
     * Sets a long parameter value.
     *
     * @param index the parameter index (0-based)
     * @param value the value
     */
    public void setLong(int index, long value) {
        paramValues[index] = String.valueOf(value);
        paramTypes[index] = ColumnType.LONGLONG;
    }

    /**
     * Sets a double parameter value.
     *
     * @param index the parameter index (0-based)
     * @param value the value
     */
    public void setDouble(int index, double value) {
        paramValues[index] = String.valueOf(value);
        paramTypes[index] = ColumnType.DOUBLE;
    }

    /**
     * Sets a null parameter value.
     *
     * @param index the parameter index (0-based)
     */
    public void setNull(int index) {
        paramValues[index] = null;
        paramTypes[index] = ColumnType.NULL;
    }

    /**
     * Sends long data for a parameter (in chunks).
     *
     * @param index the parameter index (0-based)
     * @param data the data chunk
     * @throws IOException if an I/O error occurs
     */
    public void sendLongData(int index, byte[] data) throws IOException {
        connection.sendPacket(MysqlCodec.encodeSendLongData(statementId, index, data));
    }

    /**
     * Executes the prepared statement and returns a result set.
     *
     * @return the query result
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if execution fails
     */
    public MysqlResult executeQuery() throws IOException {
        sendExecute();
        return readBinaryResult();
    }

    /**
     * Executes the prepared statement and returns affected row count.
     *
     * @return the number of affected rows
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if execution fails
     */
    public long executeUpdate() throws IOException {
        sendExecute();
        return readBinaryResult().affectedRows();
    }

    /**
     * Resets the prepared statement (clears long data).
     *
     * @throws IOException if an I/O error occurs
     */
    public void reset() throws IOException {
        connection.sendPacket(MysqlCodec.encodeStmtReset(statementId));
        var response = connection.receivePacket();
        if (MysqlCodec.isErr(response.payload())) {
            var err = ErrPacket.decode(response.payload(), connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }
    }

    @Override
    public void close() throws IOException {
        connection.sendPacket(MysqlCodec.encodeStmtClose(statementId));
        // No response for COM_STMT_CLOSE
    }

    private void sendExecute() throws IOException {
        var buf = ByteBuffer.allocate(8192);

        // Header: command + stmtId + flags + iteration_count
        buf.put((byte) MysqlCodec.COM_STMT_EXECUTE);
        buf.put((byte) (statementId & 0xFF));
        buf.put((byte) ((statementId >> 8) & 0xFF));
        buf.put((byte) ((statementId >> 16) & 0xFF));
        buf.put((byte) ((statementId >> 24) & 0xFF));
        buf.put((byte) 0); // flags: no cursor
        buf.put((byte) 1); // iteration count
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);

        if (paramCount > 0) {
            // NULL bitmap
            int nullBitmapLength = (paramCount + 7) / 8;
            var nullBitmap = new byte[nullBitmapLength];
            for (int i = 0; i < paramCount; i++) {
                if (paramValues[i] == null) {
                    nullBitmap[i / 8] |= (byte) (1 << (i % 8));
                }
            }
            buf.put(nullBitmap);

            // new-params-bound-flag: always send types
            buf.put((byte) 1);

            // Parameter types (2 bytes each)
            for (int i = 0; i < paramCount; i++) {
                buf.put((byte) paramTypes[i].code());
                buf.put((byte) 0); // unsigned flag
            }

            // Parameter values
            for (int i = 0; i < paramCount; i++) {
                if (paramValues[i] != null) {
                    LengthEncodedString.write(buf, paramValues[i]);
                }
            }
        }

        buf.flip();
        var payload = new byte[buf.remaining()];
        buf.get(payload);
        connection.sendPacket(payload);
    }

    private MysqlResult readBinaryResult() throws IOException {
        var firstPacket = connection.receivePacket();
        var payload = firstPacket.payload();

        if (MysqlCodec.isOk(payload)) {
            var ok = OkPacket.decode(payload, connection.capabilities());
            return new MysqlResult(ok.affectedRows(), ok.lastInsertId());
        }

        if (MysqlCodec.isErr(payload)) {
            var err = ErrPacket.decode(payload, connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }

        // Result set
        long columnCount = LengthEncodedInt.decode(payload);

        var columns = new ArrayList<ColumnDefinition>();
        for (int i = 0; i < columnCount; i++) {
            var colPacket = connection.receivePacket();
            columns.add(ColumnDefinition.decode(colPacket.payload()));
        }

        if (!CapabilityFlags.hasCapability(connection.capabilities(), CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            connection.receivePacket(); // EOF
        }

        var rows = new ArrayList<List<String>>();
        while (true) {
            var rowPacket = connection.receivePacket();
            var rowPayload = rowPacket.payload();

            // End-of-rows: EOF packet (0xFE, length < 9) — catches both legacy EOF
            // and DEPRECATE_EOF OK packets (which use 0xFE header to avoid conflict
            // with binary row headers that also start with 0x00)
            if (EofPacket.isEof(rowPayload)) break;
            if (MysqlCodec.isErr(rowPayload)) {
                var err = ErrPacket.decode(rowPayload, connection.capabilities());
                throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
            }

            rows.add(ResultSetWriter.decodeBinaryRow(rowPayload, columns));
        }

        return new MysqlResult(columns, rows);
    }
}
