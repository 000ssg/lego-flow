package ssg.legoflow.database.mysql.client;

import ssg.legoflow.database.mysql.server.ColumnDefinition;
import ssg.legoflow.database.mysql.protocol.ColumnType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MysqlResultTest {

    @Test void testResultSetConstructor() {
        var cols = List.of(ColumnDefinition.of("name", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("value1"), List.of("value2"));
        var result = new MysqlResult(cols, rows);

        assertThat(result.isResultSet()).isTrue();
        assertThat(result.columnCount()).isEqualTo(1);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.affectedRows()).isEqualTo(0);
        assertThat(result.lastInsertId()).isEqualTo(0);
    }

    @Test void testOkResultConstructor() {
        var result = new MysqlResult(42L, 99L);

        assertThat(result.isResultSet()).isFalse();
        assertThat(result.affectedRows()).isEqualTo(42);
        assertThat(result.lastInsertId()).isEqualTo(99);
        assertThat(result.columnCount()).isEqualTo(0);
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test void testNextAndGetString() {
        var cols = List.of(ColumnDefinition.of("id", ColumnType.LONG, 8));
        var rows = List.of(List.of("1"), List.of("2"));
        var result = new MysqlResult(cols, rows);

        assertThat(result.next()).isTrue();
        assertThat(result.getString(0)).isEqualTo("1");

        assertThat(result.next()).isTrue();
        assertThat(result.getString(0)).isEqualTo("2");

        assertThat(result.next()).isFalse();
    }

    @Test void testGetStringNoCurrentRow() {
        var result = new MysqlResult(List.of(), List.of());
        assertThatThrownBy(() -> result.getString(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No current row");
    }

    @Test void testReset() {
        var cols = List.of(ColumnDefinition.of("id", ColumnType.LONG, 8));
        var rows = List.of(List.of("1"), List.of("2"));
        var result = new MysqlResult(cols, rows);

        result.next(); // row 0
        result.next(); // row 1
        result.next(); // exhausted
        
        result.reset();
        assertThat(result.next()).isTrue();
        assertThat(result.getString(0)).isEqualTo("1");
    }

    @Test void testGetStringByColumnName() {
        var cols = List.of(
                ColumnDefinition.of("name", ColumnType.VAR_STRING, 32),
                ColumnDefinition.of("age", ColumnType.LONG, 8));
        var rows = List.of(List.of("Alice", "30"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getString("name")).isEqualTo("Alice");
        assertThat(result.getString("age")).isEqualTo("30");
    }

    @Test void testGetStringByColumnNameCaseInsensitive() {
        var cols = List.of(ColumnDefinition.of("Name", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("Bob"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getString("name")).isEqualTo("Bob");
        assertThat(result.getString("NAME")).isEqualTo("Bob");
    }

    @Test void testGetStringByColumnNameNotFound() {
        var cols = List.of(ColumnDefinition.of("x", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("val"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getString("nonexistent")).isNull();
    }

    @Test void testGetInt() {
        var cols = List.of(ColumnDefinition.of("n", ColumnType.LONG, 8));
        var rows = List.of(List.of("42"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getInt(0)).isEqualTo(42);
        assertThat(result.getInt("n")).isEqualTo(42);
    }

    @Test void testGetIntReturnsZeroForNull() {
        var cols = List.of(ColumnDefinition.of("n", ColumnType.LONG, 8));
        var rows = List.of(List.<String>of());
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getInt(0)).isEqualTo(0);
    }

    @Test void testGetLong() {
        var cols = List.of(ColumnDefinition.of("n", ColumnType.LONGLONG, 8));
        var rows = List.of(List.of("999999999"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getLong(0)).isEqualTo(999999999L);
    }

    @Test void testGetDouble() {
        var cols = List.of(ColumnDefinition.of("n", ColumnType.DOUBLE, 8));
        var rows = List.of(List.of("3.14"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getDouble(0)).isCloseTo(3.14, within(0.01));
    }

    @Test void testIsNull() {
        var cols = List.of(ColumnDefinition.of("n", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.<String>of());
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.isNull(0)).isTrue();
    }

    @Test void testColumnsAccessor() {
        var cols = List.of(ColumnDefinition.of("a", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("x"));
        var result = new MysqlResult(cols, rows);

        assertThat(result.columns()).isSameAs(cols);
        assertThat(result.columns().get(0).name()).isEqualTo("a");
    }

    @Test void testRowsAccessor() {
        var cols = List.of(ColumnDefinition.of("a", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("x"), List.of("y"));
        var result = new MysqlResult(cols, rows);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(1).get(0)).isEqualTo("y");
    }

    @Test void testGetStringOutOfBoundsColumn() {
        var cols = List.of(ColumnDefinition.of("a", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("val"));
        var result = new MysqlResult(cols, rows);

        result.next();
        assertThat(result.getString(99)).isNull(); // column index out of bounds
    }
}
