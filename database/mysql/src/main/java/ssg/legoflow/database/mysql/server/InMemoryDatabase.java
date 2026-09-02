package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.ColumnType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Simple in-memory database for the MySQL server.
 *
 * <p>Provides basic table storage supporting CREATE TABLE, INSERT, SELECT,
 * UPDATE, and DELETE operations. Thread-safe for concurrent access from
 * multiple client sessions.
 *
 * @since 0.1.0
 */
public class InMemoryDatabase {

    private final String name;
    private final ConcurrentHashMap<String, Table> tables = new ConcurrentHashMap<>();

    /**
     * Creates a new in-memory database.
     *
     * @param name the database name
     */
    public InMemoryDatabase(String name) {
        this.name = name;
    }

    /**
     * Returns the database name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Creates a new table.
     *
     * @param tableName the table name
     * @param columns the column definitions (name to type mapping)
     * @return true if created, false if already exists
     */
    public boolean createTable(String tableName, LinkedHashMap<String, ColumnType> columns) {
        return tables.putIfAbsent(tableName, new Table(tableName, columns)) == null;
    }

    /**
     * Drops a table.
     *
     * @param tableName the table name
     * @return true if dropped, false if not found
     */
    public boolean dropTable(String tableName) {
        return tables.remove(tableName) != null;
    }

    /**
     * Returns a table by name.
     *
     * @param tableName the table name
     * @return the table, or null if not found
     */
    public Table getTable(String tableName) {
        return tables.get(tableName);
    }

    /**
     * Returns all table names.
     *
     * @return list of table names
     */
    public List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    /**
     * Checks if a table exists.
     *
     * @param tableName the table name
     * @return true if the table exists
     */
    public boolean hasTable(String tableName) {
        return tables.containsKey(tableName);
    }

    /**
     * In-memory table with column definitions and row storage.
     */
    public static class Table {
        private final String name;
        private final LinkedHashMap<String, ColumnType> columns;
        private final List<String> columnNames;
        private final CopyOnWriteArrayList<Map<String, String>> rows = new CopyOnWriteArrayList<>();
        private final AtomicLong autoIncrementId = new AtomicLong(1);

        /**
         * Creates a new table.
         *
         * @param name the table name
         * @param columns the column definitions
         */
        public Table(String name, LinkedHashMap<String, ColumnType> columns) {
            this.name = name;
            this.columns = new LinkedHashMap<>(columns);
            this.columnNames = new ArrayList<>(columns.keySet());
        }

        /**
         * Returns the table name.
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * Returns the column definitions.
         *
         * @return ordered map of column name to type
         */
        public LinkedHashMap<String, ColumnType> columns() {
            return new LinkedHashMap<>(columns);
        }

        /**
         * Returns the column names in order.
         *
         * @return list of column names
         */
        public List<String> columnNames() {
            return new ArrayList<>(columnNames);
        }

        /**
         * Returns the column type for a given column name.
         *
         * @param columnName the column name
         * @return the column type, or null if not found
         */
        public ColumnType columnType(String columnName) {
            return columns.get(columnName);
        }

        /**
         * Inserts a row into the table.
         *
         * @param values map of column name to string value
         * @return the auto-generated ID (if any auto-increment column)
         */
        public long insert(Map<String, String> values) {
            long id = autoIncrementId.getAndIncrement();
            var row = new LinkedHashMap<>(values);
            // If there's an "id" column and no value provided, auto-assign
            if (columns.containsKey("id") && !row.containsKey("id")) {
                row.put("id", String.valueOf(id));
            }
            rows.add(row);
            return id;
        }

        /**
         * Returns all rows.
         *
         * @return list of rows (each row is a map of column name to value)
         */
        public List<Map<String, String>> selectAll() {
            return new ArrayList<>(rows);
        }

        /**
         * Selects rows matching a simple WHERE condition.
         *
         * @param whereColumn the column to filter on
         * @param whereValue the value to match
         * @return matching rows
         */
        public List<Map<String, String>> selectWhere(String whereColumn, String whereValue) {
            var result = new ArrayList<Map<String, String>>();
            for (var row : rows) {
                var value = row.get(whereColumn);
                if (whereValue.equals(value)) {
                    result.add(new LinkedHashMap<>(row));
                }
            }
            return result;
        }

        /**
         * Updates rows matching a simple WHERE condition.
         *
         * @param setColumn the column to update
         * @param setValue the new value
         * @param whereColumn the column to filter on
         * @param whereValue the value to match
         * @return number of rows updated
         */
        public int update(String setColumn, String setValue,
                          String whereColumn, String whereValue) {
            int count = 0;
            for (var row : rows) {
                var value = row.get(whereColumn);
                if (whereValue.equals(value)) {
                    row.put(setColumn, setValue);
                    count++;
                }
            }
            return count;
        }

        /**
         * Updates all rows.
         *
         * @param setColumn the column to update
         * @param setValue the new value
         * @return number of rows updated
         */
        public int updateAll(String setColumn, String setValue) {
            int count = 0;
            for (var row : rows) {
                row.put(setColumn, setValue);
                count++;
            }
            return count;
        }

        /**
         * Deletes rows matching a simple WHERE condition.
         *
         * @param whereColumn the column to filter on
         * @param whereValue the value to match
         * @return number of rows deleted
         */
        public int deleteWhere(String whereColumn, String whereValue) {
            int sizeBefore = rows.size();
            rows.removeIf(row -> whereValue.equals(row.get(whereColumn)));
            return sizeBefore - rows.size();
        }

        /**
         * Deletes all rows.
         *
         * @return number of rows deleted
         */
        public int deleteAll() {
            int size = rows.size();
            rows.clear();
            return size;
        }

        /**
         * Returns the row count.
         *
         * @return number of rows
         */
        public int rowCount() {
            return rows.size();
        }

        /**
         * Takes a deep copy snapshot of the current rows for transaction support.
         *
         * @return a list of deep-copied row maps
         */
        public List<Map<String, String>> snapshot() {
            var snap = new ArrayList<Map<String, String>>();
            for (var row : rows) {
                snap.add(new LinkedHashMap<>(row));
            }
            return snap;
        }

        /**
         * Restores rows from a previously taken snapshot.
         *
         * @param snapshot the snapshot to restore
         */
        public void restoreSnapshot(List<Map<String, String>> snapshot) {
            rows.clear();
            for (var row : snapshot) {
                rows.add(new LinkedHashMap<>(row));
            }
        }
    }

    /**
     * Takes a snapshot of all tables in this database.
     *
     * @return a map of table name to list of deep-copied row maps
     */
    public Map<String, List<Map<String, String>>> snapshotAll() {
        var snap = new LinkedHashMap<String, List<Map<String, String>>>();
        for (var entry : tables.entrySet()) {
            snap.put(entry.getKey(), entry.getValue().snapshot());
        }
        return snap;
    }

    /**
     * Restores all tables from a previously taken snapshot.
     *
     * @param snapshot the snapshot to restore
     */
    public void restoreAll(Map<String, List<Map<String, String>>> snapshot) {
        for (var entry : snapshot.entrySet()) {
            var table = tables.get(entry.getKey());
            if (table != null) {
                table.restoreSnapshot(entry.getValue());
            }
        }
    }
}
