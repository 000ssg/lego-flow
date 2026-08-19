package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.database.postgresql.common.PgSeverity;
import ssg.legoflow.database.postgresql.common.SqlState;
import ssg.legoflow.database.postgresql.protocol.BackendMessage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Executes queries against the in-memory database and produces backend messages.
 *
 * @since 0.1.0
 */
public final class QueryExecutor {

    private final InMemoryDatabase database;

    /**
     * Creates a new query executor.
     *
     * @param database the in-memory database
     */
    public QueryExecutor(InMemoryDatabase database) {
        this.database = database;
    }

    /**
     * Returns the underlying database.
     *
     * @return the in-memory database
     */
    public InMemoryDatabase database() {
        return database;
    }

    /**
     * Executes a simple query and returns result messages.
     *
     * @param sql the SQL query (may contain multiple statements separated by ';')
     * @return the result messages
     */
    public java.util.List<BackendMessage> executeSimple(String sql) {
        var messages = new java.util.ArrayList<BackendMessage>();
        String[] statements = sql.split(";");

        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) {
                messages.add(new BackendMessage.EmptyQueryResponse());
                continue;
            }

            try {
                ResultSet rs = database.execute(trimmed);
                if (rs.hasData()) {
                    messages.add(new BackendMessage.RowDescription(rs.columns()));
                    for (String[] row : rs.rows()) {
                        messages.add(new BackendMessage.DataRow(toByteArrays(row)));
                    }
                }
                messages.add(new BackendMessage.CommandComplete(rs.tag()));
            } catch (InMemoryDatabase.SqlException e) {
                messages.add(makeError(e.sqlState(), e.getMessage()));
            }
        }
        return messages;
    }

    /**
     * Executes a portal (extended query protocol).
     *
     * @param portal  the portal to execute
     * @param maxRows maximum rows (0 for unlimited)
     * @return the result messages
     */
    public java.util.List<BackendMessage> executeExtended(Portal portal, int maxRows) {
        var messages = new java.util.ArrayList<BackendMessage>();
        try {
            String sql = portal.statement().sql();
            String[] params = portal.parameterStrings();
            ResultSet rs = database.execute(sql, params);

            if (rs.hasData()) {
                int rowCount = rs.rows().size();
                int limit = (maxRows > 0 && maxRows < rowCount) ? maxRows : rowCount;
                for (int i = 0; i < limit; i++) {
                    messages.add(new BackendMessage.DataRow(toByteArrays(rs.rows().get(i))));
                }
                if (maxRows > 0 && maxRows < rowCount) {
                    messages.add(new BackendMessage.PortalSuspended());
                } else {
                    messages.add(new BackendMessage.CommandComplete(rs.tag()));
                }
            } else {
                messages.add(new BackendMessage.CommandComplete(rs.tag()));
            }
        } catch (InMemoryDatabase.SqlException e) {
            messages.add(makeError(e.sqlState(), e.getMessage()));
        }
        return messages;
    }

    /**
     * Creates an ErrorResponse message.
     *
     * @param sqlState the SQLSTATE
     * @param message  the error message
     * @return the error response
     */
    public static BackendMessage.ErrorResponse makeError(SqlState sqlState, String message) {
        return makeError(PgSeverity.ERROR, sqlState, message);
    }

    /**
     * Creates an ErrorResponse message with severity.
     *
     * @param severity the severity
     * @param sqlState the SQLSTATE
     * @param message  the error message
     * @return the error response
     */
    public static BackendMessage.ErrorResponse makeError(PgSeverity severity, SqlState sqlState, String message) {
        Map<Byte, String> fields = new LinkedHashMap<>();
        fields.put((byte) 'S', severity.label());
        fields.put((byte) 'V', severity.label());
        fields.put((byte) 'C', sqlState.code());
        fields.put((byte) 'M', message);
        return new BackendMessage.ErrorResponse(fields);
    }

    /**
     * Creates a NoticeResponse message.
     *
     * @param severity the severity
     * @param sqlState the SQLSTATE
     * @param message  the notice message
     * @return the notice response
     */
    public static BackendMessage.NoticeResponse makeNotice(PgSeverity severity, SqlState sqlState, String message) {
        Map<Byte, String> fields = new LinkedHashMap<>();
        fields.put((byte) 'S', severity.label());
        fields.put((byte) 'C', sqlState.code());
        fields.put((byte) 'M', message);
        return new BackendMessage.NoticeResponse(fields);
    }

    private byte[][] toByteArrays(String[] row) {
        byte[][] result = new byte[row.length][];
        for (int i = 0; i < row.length; i++) {
            result[i] = row[i] == null ? null : row[i].getBytes(StandardCharsets.UTF_8);
        }
        return result;
    }
}
