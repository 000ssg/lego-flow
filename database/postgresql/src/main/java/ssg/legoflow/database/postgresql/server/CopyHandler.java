package ssg.legoflow.database.postgresql.server;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles COPY IN/OUT operations for transferring bulk data.
 *
 * @since 0.1.0
 */
public final class CopyHandler {

    private final InMemoryDatabase database;

    /**
     * Creates a new COPY handler.
     *
     * @param database the in-memory database
     */
    public CopyHandler(InMemoryDatabase database) {
        this.database = database;
    }

    /**
     * Processes COPY IN data: parses tab-separated rows and inserts them.
     *
     * @param tableName the target table name
     * @param data      the accumulated COPY data chunks
     * @return the number of rows inserted
     */
    public int processCopyIn(String tableName, List<byte[]> data) {
        String combined = combineData(data);
        String[] lines = combined.split("\n");
        int count = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] values = line.split("\t");
            // Build an INSERT statement
            StringBuilder sb = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(", ");
                String v = values[i].trim();
                if ("\\N".equals(v)) {
                    sb.append("NULL");
                } else {
                    sb.append("'").append(v.replace("'", "''")).append("'");
                }
            }
            sb.append(")");
            database.execute(sb.toString());
            count++;
        }
        return count;
    }

    /**
     * Generates COPY OUT data for a table.
     *
     * @param tableName the source table name
     * @return the tab-separated data rows
     */
    public List<byte[]> generateCopyOut(String tableName) {
        ResultSet rs = database.execute("SELECT * FROM " + tableName);
        List<byte[]> chunks = new ArrayList<>();

        for (String[] row : rs.rows()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append('\t');
                sb.append(row[i] == null ? "\\N" : row[i]);
            }
            sb.append('\n');
            chunks.add(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return chunks;
    }

    private String combineData(List<byte[]> data) {
        int total = data.stream().mapToInt(d -> d.length).sum();
        byte[] combined = new byte[total];
        int offset = 0;
        for (byte[] chunk : data) {
            System.arraycopy(chunk, 0, combined, offset, chunk.length);
            offset += chunk.length;
        }
        return new String(combined, StandardCharsets.UTF_8);
    }
}
