package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.server.ColumnDefinition;
import java.util.List;
/**
 * MySQL result set accessor.
 *
 * <p>Provides access to query results including column metadata, row data,
 * affected rows, and last insert ID. Supports both text and binary protocol
 * result sets.
 *
 * @since 0.1.0
 */
public class MysqlResult {

    private final List<ColumnDefinition> columns;
    private final List<List<String>> rows;
    private final long affectedRows;
    private final long lastInsertId;
    private final boolean isResultSet;
    private int currentRow = -1;

    /**
     * Creates a result set from query results.
     *
     * @param columns the column definitions
     * @param rows the row data
     */
    public MysqlResult(List<ColumnDefinition> columns, List<List<String>> rows) {
        this.columns = columns;
        this.rows = rows;
        this.affectedRows = 0;
        this.lastInsertId = 0;
        this.isResultSet = true;
    }

    /**
     * Creates an OK result (no result set).
     *
     * @param affectedRows the number of affected rows
     * @param lastInsertId the last insert ID
     */
    public MysqlResult(long affectedRows, long lastInsertId) {
        this.columns = List.of();
        this.rows = List.of();
        this.affectedRows = affectedRows;
        this.lastInsertId = lastInsertId;
        this.isResultSet = false;
    }

    /**
     * Returns whether this result contains a result set.
     *
     * @return true if this is a result set
     */
    public boolean isResultSet() {
        return isResultSet;
    }

    /**
     * Returns the number of affected rows (for INSERT/UPDATE/DELETE).
     *
     * @return the affected row count
     */
    public long affectedRows() {
        return affectedRows;
    }

    /**
     * Returns the last auto-increment insert ID.
     *
     * @return the last insert ID
     */
    public long lastInsertId() {
        return lastInsertId;
    }

    /**
     * Returns the column definitions.
     *
     * @return list of column definitions
     */
    public List<ColumnDefinition> columns() {
        return columns;
    }

    /**
     * Returns the number of columns.
     *
     * @return the column count
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * Returns all rows.
     *
     * @return list of rows (each row is a list of string values)
     */
    public List<List<String>> rows() {
        return rows;
    }

    /**
     * Returns the number of rows.
     *
     * @return the row count
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Advances to the next row.
     *
     * @return true if there is a next row
     */
    public boolean next() {
        currentRow++;
        return currentRow < rows.size();
    }

    /**
     * Resets the row cursor to before the first row.
     */
    public void reset() {
        currentRow = -1;
    }

    /**
     * Returns the value at the given column index in the current row.
     *
     * @param columnIndex the column index (0-based)
     * @return the string value, or null
     */
    public String getString(int columnIndex) {
        if (currentRow < 0 || currentRow >= rows.size()) {
            throw new IllegalStateException("No current row; call next() first");
        }
        var row = rows.get(currentRow);
        return (columnIndex < row.size()) ? row.get(columnIndex) : null;
    }

    /**
     * Returns the value for the given column name in the current row.
     *
     * @param columnName the column name
     * @return the string value, or null
     */
    public String getString(String columnName) {
        int index = findColumn(columnName);
        return (index >= 0) ? getString(index) : null;
    }

    /**
     * Returns the value as an integer at the given column index.
     *
     * @param columnIndex the column index (0-based)
     * @return the integer value, or 0 if null
     */
    public int getInt(int columnIndex) {
        var value = getString(columnIndex);
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * Returns the value as an integer for the given column name.
     *
     * @param columnName the column name
     * @return the integer value, or 0 if null
     */
    public int getInt(String columnName) {
        var value = getString(columnName);
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * Returns the value as a long at the given column index.
     *
     * @param columnIndex the column index (0-based)
     * @return the long value, or 0 if null
     */
    public long getLong(int columnIndex) {
        var value = getString(columnIndex);
        return value != null ? Long.parseLong(value) : 0;
    }

    /**
     * Returns the value as a double at the given column index.
     *
     * @param columnIndex the column index (0-based)
     * @return the double value, or 0.0 if null
     */
    public double getDouble(int columnIndex) {
        var value = getString(columnIndex);
        return value != null ? Double.parseDouble(value) : 0.0;
    }

    /**
     * Checks if the value at the given column index is null.
     *
     * @param columnIndex the column index (0-based)
     * @return true if the value is null
     */
    public boolean isNull(int columnIndex) {
        return getString(columnIndex) == null;
    }

    private int findColumn(String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }
}
