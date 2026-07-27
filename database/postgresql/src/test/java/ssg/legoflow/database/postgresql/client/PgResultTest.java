package ssg.legoflow.database.postgresql.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.protocol.BackendMessage.ColumnDescription;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PgResult}.
 */
class PgResultTest {

    private PgResult makeResult(String[]... rows) {
        var columns = List.of(
                new ColumnDescription("id", 0, (short) 0, 23, (short) 4, -1, (short) 0),
                new ColumnDescription("name", 0, (short) 0, 25, (short) -1, -1, (short) 0)
        );
        List<byte[][]> dataRows = new java.util.ArrayList<>();
        for (String[] row : rows) {
            byte[][] byteRow = new byte[row.length][];
            for (int i = 0; i < row.length; i++) {
                byteRow[i] = row[i] == null ? null : row[i].getBytes();
            }
            dataRows.add(byteRow);
        }
        return new PgResult(columns, dataRows, "SELECT " + rows.length);
    }

    @Test
    void testRowCount() {
        PgResult result = makeResult(new String[]{"1", "Alice"}, new String[]{"2", "Bob"});
        assertThat(result.rowCount()).isEqualTo(2);
    }

    @Test
    void testColumnCount() {
        PgResult result = makeResult(new String[]{"1", "Alice"});
        assertThat(result.columnCount()).isEqualTo(2);
    }

    @Test
    void testGetString() {
        PgResult result = makeResult(new String[]{"1", "Alice"});
        assertThat(result.getString(0, 0)).isEqualTo("1");
        assertThat(result.getString(0, 1)).isEqualTo("Alice");
    }

    @Test
    void testGetStringByName() {
        PgResult result = makeResult(new String[]{"1", "Alice"});
        assertThat(result.getString(0, "id")).isEqualTo("1");
        assertThat(result.getString(0, "name")).isEqualTo("Alice");
    }

    @Test
    void testGetInt() {
        PgResult result = makeResult(new String[]{"42", "test"});
        assertThat(result.getInt(0, 0)).isEqualTo(42);
    }

    @Test
    void testGetIntByName() {
        PgResult result = makeResult(new String[]{"42", "test"});
        assertThat(result.getInt(0, "id")).isEqualTo(42);
    }

    @Test
    void testGetLong() {
        PgResult result = makeResult(new String[]{"9999999999", "test"});
        assertThat(result.getLong(0, 0)).isEqualTo(9999999999L);
    }

    @Test
    void testGetBoolean() {
        PgResult result = makeResult(new String[]{"t", "test"});
        assertThat(result.getBoolean(0, 0)).isTrue();
    }

    @Test
    void testIsNull() {
        PgResult result = makeResult(new String[]{"1", null});
        assertThat(result.isNull(0, 0)).isFalse();
        assertThat(result.isNull(0, 1)).isTrue();
    }

    @Test
    void testGetStringNull() {
        PgResult result = makeResult(new String[]{null, null});
        assertThat(result.getString(0, 0)).isNull();
    }

    @Test
    void testCommandTag() {
        PgResult result = makeResult(new String[]{"1", "Alice"});
        assertThat(result.commandTag()).isEqualTo("SELECT 1");
    }

    @Test
    void testAffectedRows() {
        PgResult result = new PgResult(List.of(), List.of(), "INSERT 0 5");
        assertThat(result.affectedRows()).isEqualTo(5);
    }

    @Test
    void testAffectedRowsSelect() {
        PgResult result = new PgResult(List.of(), List.of(), "SELECT 10");
        assertThat(result.affectedRows()).isEqualTo(10);
    }

    @Test
    void testAffectedRowsNonNumeric() {
        PgResult result = new PgResult(List.of(), List.of(), "CREATE TABLE");
        assertThat(result.affectedRows()).isEqualTo(-1);
    }

    @Test
    void testAffectedRowsNull() {
        PgResult result = new PgResult(List.of(), List.of(), null);
        assertThat(result.affectedRows()).isEqualTo(-1);
    }

    @Test
    void testAllRows() {
        PgResult result = makeResult(new String[]{"1", "Alice"}, new String[]{"2", "Bob"});
        List<String[]> rows = result.allRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[0]).isEqualTo("1");
        assertThat(rows.get(1)[1]).isEqualTo("Bob");
    }

    @Test
    void testColumns() {
        PgResult result = makeResult(new String[]{"1", "test"});
        assertThat(result.columns().get(0).name()).isEqualTo("id");
        assertThat(result.columns().get(0).typeOid()).isEqualTo(23);
        assertThat(result.columns().get(1).name()).isEqualTo("name");
        assertThat(result.columns().get(1).typeOid()).isEqualTo(25);
    }

    @Test
    void testFindColumnNotFound() {
        PgResult result = makeResult(new String[]{"1", "test"});
        assertThatThrownBy(() -> result.getString(0, "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }
}
