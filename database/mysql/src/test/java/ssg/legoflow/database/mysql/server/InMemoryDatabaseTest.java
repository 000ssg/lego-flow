package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.ColumnType;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class InMemoryDatabaseTest {

    @Test void testName() {
        var db = new InMemoryDatabase("testdb");
        assertThat(db.name()).isEqualTo("testdb");
    }

    @Test void testCreateTable() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        cols.put("name", ColumnType.VAR_STRING);
        
        assertThat(db.createTable("users", cols)).isTrue();
        assertThat(db.hasTable("users")).isTrue();
    }

    @Test void testCreateTableAlreadyExists() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        
        assertThat(db.createTable("t", cols)).isTrue();
        assertThat(db.createTable("t", cols)).isFalse(); // already exists
    }

    @Test void testDropTable() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("x", ColumnType.VAR_STRING);
        
        db.createTable("t", cols);
        assertThat(db.dropTable("t")).isTrue();
        assertThat(db.hasTable("t")).isFalse();
    }

    @Test void testDropTableNonexistent() {
        var db = new InMemoryDatabase("testdb");
        assertThat(db.dropTable("nope")).isFalse();
    }

    @Test void testGetTable() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("x", ColumnType.VAR_STRING);
        
        db.createTable("t", cols);
        assertThat(db.getTable("t")).isNotNull();
        assertThat(db.getTable("nope")).isNull();
    }

    @Test void testGetTableNames() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("x", ColumnType.VAR_STRING);
        
        db.createTable("a", cols);
        db.createTable("b", cols);
        
        assertThat(db.getTableNames()).containsExactlyInAnyOrder("a", "b");
    }

    @Test void testHasTable() {
        var db = new InMemoryDatabase("testdb");
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("x", ColumnType.VAR_STRING);
        
        assertThat(db.hasTable("t")).isFalse();
        db.createTable("t", cols);
        assertThat(db.hasTable("t")).isTrue();
    }

    // === Table tests ===

    @Test void testTableNameAndColumns() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        cols.put("name", ColumnType.VAR_STRING);
        
        var table = new InMemoryDatabase.Table("users", cols);
        assertThat(table.name()).isEqualTo("users");
        assertThat(table.columns().keySet()).containsExactly("id", "name");
    }

    @Test void testColumnNames() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("first", ColumnType.VAR_STRING);
        cols.put("second", ColumnType.LONG);
        
        var table = new InMemoryDatabase.Table("t", cols);
        assertThat(table.columnNames()).containsExactly("first", "second");
    }

    @Test void testColumnType() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        
        var table = new InMemoryDatabase.Table("t", cols);
        assertThat(table.columnType("id")).isEqualTo(ColumnType.LONG);
        assertThat(table.columnType("nope")).isNull();
    }

    @Test void testInsert() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("name", ColumnType.VAR_STRING);
        
        var table = new InMemoryDatabase.Table("t", cols);
        Map<String, String> values = Map.of("name", "Alice");
        
        assertThat(table.insert(values)).isEqualTo(1L);
        assertThat(table.selectAll()).hasSize(1);
    }

    @Test void testInsertWithAutoId() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        cols.put("name", ColumnType.VAR_STRING);
        
        var table = new InMemoryDatabase.Table("t", cols);
        Map<String, String> values = Map.of("name", "Alice"); // no id
        
        long insertedId = table.insert(values);
        assertThat(insertedId).isEqualTo(1L);
        var rows = table.selectAll();
        assertThat(rows.get(0).get("id")).isEqualTo("1");
    }

    @Test void testSelectAll() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("name", ColumnType.VAR_STRING);
        
        var table = new InMemoryDatabase.Table("t", cols);
        table.insert(Map.of("name", "A"));
        table.insert(Map.of("name", "B"));
        
        assertThat(table.selectAll()).hasSize(2);
    }

    @Test void testInsertMultipleAutoIncrementIds() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("id", ColumnType.LONG);
        
        var table = new InMemoryDatabase.Table("t", cols);
        table.insert(Map.of()); // id=1
        long secondId = table.insert(Map.of()); // should be id=2
        
        assertThat(secondId).isEqualTo(2L);
    }

    @Test void testEmptySelectAll() {
        var cols = new LinkedHashMap<String, ColumnType>();
        cols.put("x", ColumnType.VAR_STRING);
        
        var table = new InMemoryDatabase.Table("t", cols);
        assertThat(table.selectAll()).isEmpty();
    }
}
