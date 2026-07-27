package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.protocol.*;
import ssg.legoflow.database.mysql.server.ColumnDefinition;
import ssg.legoflow.database.mysql.server.ResultSetWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL text query execution.
 *
 * <p>Sends COM_QUERY commands and reads the response — either an OK/ERR
 * packet or a full result set with column definitions and rows.
 *
 * @since 1.0.0
 */
public class MysqlStatement {

    private final MysqlConnection connection;

    /**
     * Creates a new statement for the given connection.
     *
     * @param connection the MySQL connection
     */
    public MysqlStatement(MysqlConnection connection) {
        this.connection = connection;
    }

    /**
     * Executes a SQL query that returns a result set.
     *
     * @param sql the SQL query
     * @return the query result
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if the server returns an error
     */
    public MysqlResult executeQuery(String sql) throws IOException {
        connection.sendPacket(MysqlCodec.encodeQuery(sql));
        return readResult();
    }

    /**
     * Executes a SQL statement that modifies data (INSERT, UPDATE, DELETE).
     *
     * @param sql the SQL statement
     * @return the number of affected rows
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if the server returns an error
     */
    public long executeUpdate(String sql) throws IOException {
        connection.sendPacket(MysqlCodec.encodeQuery(sql));
        var result = readResult();
        return result.affectedRows();
    }

    /**
     * Executes a SQL statement (generic — works for both queries and updates).
     *
     * @param sql the SQL statement
     * @return the result
     * @throws IOException if an I/O error occurs
     * @throws MysqlConnection.MysqlException if the server returns an error
     */
    public MysqlResult execute(String sql) throws IOException {
        connection.sendPacket(MysqlCodec.encodeQuery(sql));
        return readResult();
    }

    private MysqlResult readResult() throws IOException {
        var firstPacket = connection.receivePacket();
        var payload = firstPacket.payload();

        // Check for OK
        if (MysqlCodec.isOk(payload)) {
            var ok = OkPacket.decode(payload, connection.capabilities());
            return new MysqlResult(ok.affectedRows(), ok.lastInsertId());
        }

        // Check for ERR
        if (MysqlCodec.isErr(payload)) {
            var err = ErrPacket.decode(payload, connection.capabilities());
            throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
        }

        // Result set: first packet is column count
        long columnCount = LengthEncodedInt.decode(payload);

        // Read column definitions
        var columns = new ArrayList<ColumnDefinition>();
        for (int i = 0; i < columnCount; i++) {
            var colPacket = connection.receivePacket();
            columns.add(ColumnDefinition.decode(colPacket.payload()));
        }

        // EOF after columns (unless DEPRECATE_EOF)
        if (!CapabilityFlags.hasCapability(connection.capabilities(), CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            connection.receivePacket(); // EOF
        }

        // Read rows until EOF — end-of-rows marker uses 0xFE header (catches both
        // legacy EOF packets and DEPRECATE_EOF OK packets with 0xFE header)
        var rows = new ArrayList<List<String>>();
        while (true) {
            var rowPacket = connection.receivePacket();
            var rowPayload = rowPacket.payload();

            if (EofPacket.isEof(rowPayload)) {
                break;
            }
            if (MysqlCodec.isErr(rowPayload)) {
                var err = ErrPacket.decode(rowPayload, connection.capabilities());
                throw new MysqlConnection.MysqlException(err.errorCode(), err.sqlState(), err.message());
            }

            rows.add(ResultSetWriter.decodeTextRow(rowPayload, (int) columnCount));
        }

        return new MysqlResult(columns, rows);
    }
}
