package ssg.legoflow.database.postgresql.server;

import ssg.legoflow.database.postgresql.protocol.BackendMessage.ColumnDescription;

import java.util.List;

/**
 * Query result with column descriptions and data rows.
 *
 * @param columns  the column descriptors
 * @param rows     the data rows (each row is an array of string values, null for SQL NULL)
 * @param tag      the command completion tag (e.g., "SELECT 5", "INSERT 0 1")
 * @since 0.1.0
 */
public record ResultSet(
        List<ColumnDescription> columns,
        List<String[]> rows,
        String tag
) {

    /**
     * Creates an empty result set with only a command tag.
     *
     * @param tag the command tag
     * @return the empty result set
     */
    public static ResultSet commandOnly(String tag) {
        return new ResultSet(List.of(), List.of(), tag);
    }

    /**
     * Returns true if this result set has row data.
     *
     * @return true if there are columns and/or rows
     */
    public boolean hasData() {
        return !columns.isEmpty();
    }
}
