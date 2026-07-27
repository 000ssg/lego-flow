package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.ColumnType;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side prepared statement.
 *
 * <p>Holds the parsed SQL query, parameter metadata, and any long data
 * received via COM_STMT_SEND_LONG_DATA.
 *
 * @since 1.0.0
 */
public class PreparedStatement {

    private final int statementId;
    private final String query;
    private final int parameterCount;
    private final List<ColumnDefinition> parameterDefinitions;
    private final List<ColumnDefinition> columnDefinitions;
    private final byte[][] longData;

    /**
     * Creates a new prepared statement.
     *
     * @param statementId the server-assigned statement ID
     * @param query the SQL query with '?' placeholders
     * @param parameterCount the number of parameters
     * @param parameterDefinitions the parameter metadata
     * @param columnDefinitions the result column metadata
     */
    public PreparedStatement(int statementId, String query, int parameterCount,
                             List<ColumnDefinition> parameterDefinitions,
                             List<ColumnDefinition> columnDefinitions) {
        this.statementId = statementId;
        this.query = query;
        this.parameterCount = parameterCount;
        this.parameterDefinitions = new ArrayList<>(parameterDefinitions);
        this.columnDefinitions = new ArrayList<>(columnDefinitions);
        this.longData = new byte[parameterCount][];
    }

    /**
     * Returns the statement ID.
     *
     * @return the statement ID
     */
    public int statementId() {
        return statementId;
    }

    /**
     * Returns the SQL query.
     *
     * @return the query string
     */
    public String query() {
        return query;
    }

    /**
     * Returns the parameter count.
     *
     * @return the number of parameters
     */
    public int parameterCount() {
        return parameterCount;
    }

    /**
     * Returns the parameter definitions.
     *
     * @return list of parameter column definitions
     */
    public List<ColumnDefinition> parameterDefinitions() {
        return parameterDefinitions;
    }

    /**
     * Returns the result column definitions.
     *
     * @return list of column definitions
     */
    public List<ColumnDefinition> columnDefinitions() {
        return columnDefinitions;
    }

    /**
     * Sets long data for a parameter.
     *
     * @param paramIndex the parameter index
     * @param data the data bytes
     */
    public void setLongData(int paramIndex, byte[] data) {
        if (paramIndex >= 0 && paramIndex < parameterCount) {
            if (longData[paramIndex] == null) {
                longData[paramIndex] = data;
            } else {
                var existing = longData[paramIndex];
                var combined = new byte[existing.length + data.length];
                System.arraycopy(existing, 0, combined, 0, existing.length);
                System.arraycopy(data, 0, combined, existing.length, data.length);
                longData[paramIndex] = combined;
            }
        }
    }

    /**
     * Returns the long data for a parameter.
     *
     * @param paramIndex the parameter index
     * @return the long data bytes, or null if not set
     */
    public byte[] getLongData(int paramIndex) {
        return (paramIndex >= 0 && paramIndex < parameterCount) ? longData[paramIndex] : null;
    }

    /**
     * Resets all long data.
     */
    public void resetLongData() {
        for (int i = 0; i < parameterCount; i++) {
            longData[i] = null;
        }
    }

    /**
     * Substitutes parameter placeholders with values for execution.
     *
     * @param paramValues the parameter values (as strings)
     * @return the query with placeholders replaced
     */
    public String substitute(String[] paramValues) {
        if (parameterCount == 0) {
            return query;
        }

        var result = new StringBuilder();
        int paramIdx = 0;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '?' && paramIdx < paramValues.length) {
                var value = paramValues[paramIdx++];
                if (value == null) {
                    result.append("NULL");
                } else {
                    result.append("'").append(value.replace("'", "''")).append("'");
                }
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Counts parameter placeholders in a query.
     *
     * @param query the SQL query
     * @return the number of '?' placeholders
     */
    public static int countParameters(String query) {
        int count = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (inString) {
                if (ch == stringChar && (i + 1 >= query.length() || query.charAt(i + 1) != stringChar)) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"') {
                inString = true;
                stringChar = ch;
            } else if (ch == '?') {
                count++;
            }
        }
        return count;
    }
}
