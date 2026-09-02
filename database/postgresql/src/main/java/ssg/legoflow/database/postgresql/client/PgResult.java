package ssg.legoflow.database.postgresql.client;

import ssg.legoflow.database.postgresql.protocol.BackendMessage.ColumnDescription;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
/**
 * Query result accessor for PostgreSQL query results.
 *
 * @since 0.1.0
 */
public final class PgResult {

    private final List<ColumnDescription> columns;
    private final List<byte[][]> rows;
    private final String commandTag;

    /**
     * Creates a new query result.
     *
     * @param columns    the column descriptors
     * @param rows       the data rows
     * @param commandTag the command completion tag
     */
    public PgResult(List<ColumnDescription> columns, List<byte[][]> rows, String commandTag) {
        this.columns = columns;
        this.rows = rows;
        this.commandTag = commandTag;
    }

    /**
     * Returns the column descriptors.
     *
     * @return the column list
     */
    public List<ColumnDescription> columns() {
        return columns;
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
     * Returns the number of columns.
     *
     * @return the column count
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * Returns the command completion tag.
     *
     * @return the tag (e.g., "SELECT 5", "INSERT 0 1")
     */
    public String commandTag() {
        return commandTag;
    }

    /**
     * Returns the number of affected rows from the command tag.
     *
     * @return the affected row count, or -1 if not applicable
     */
    public int affectedRows() {
        if (commandTag == null) return -1;
        String[] parts = commandTag.split(" ");
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns a string value from a row.
     *
     * @param row    the row index (0-based)
     * @param column the column index (0-based)
     * @return the string value, or null for SQL NULL
     */
    public String getString(int row, int column) {
        byte[] data = rows.get(row)[column];
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Returns a string value by column name.
     *
     * @param row        the row index (0-based)
     * @param columnName the column name
     * @return the string value, or null for SQL NULL
     */
    public String getString(int row, String columnName) {
        return getString(row, findColumn(columnName));
    }

    /**
     * Returns an integer value from a row.
     *
     * @param row    the row index (0-based)
     * @param column the column index (0-based)
     * @return the integer value
     */
    public int getInt(int row, int column) {
        String s = getString(row, column);
        return s == null ? 0 : Integer.parseInt(s);
    }

    /**
     * Returns an integer value by column name.
     *
     * @param row        the row index (0-based)
     * @param columnName the column name
     * @return the integer value
     */
    public int getInt(int row, String columnName) {
        return getInt(row, findColumn(columnName));
    }

    /**
     * Returns a long value from a row.
     *
     * @param row    the row index (0-based)
     * @param column the column index (0-based)
     * @return the long value
     */
    public long getLong(int row, int column) {
        String s = getString(row, column);
        return s == null ? 0 : Long.parseLong(s);
    }

    /**
     * Returns a boolean value from a row.
     *
     * @param row    the row index (0-based)
     * @param column the column index (0-based)
     * @return the boolean value
     */
    public boolean getBoolean(int row, int column) {
        String s = getString(row, column);
        return "t".equals(s) || "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    /**
     * Returns whether a value is NULL.
     *
     * @param row    the row index (0-based)
     * @param column the column index (0-based)
     * @return true if the value is NULL
     */
    public boolean isNull(int row, int column) {
        return rows.get(row)[column] == null;
    }

    /**
     * Returns all rows as string arrays.
     *
     * @return the rows
     */
    public List<String[]> allRows() {
        List<String[]> result = new ArrayList<>(rows.size());
        for (byte[][] row : rows) {
            String[] stringRow = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                stringRow[i] = row[i] == null ? null : new String(row[i], StandardCharsets.UTF_8);
            }
            result.add(stringRow);
        }
        return result;
    }

    private int findColumn(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Column not found: " + name);
    }
}
