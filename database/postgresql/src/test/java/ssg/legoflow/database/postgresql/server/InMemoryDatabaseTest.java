package ssg.legoflow.database.postgresql.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.common.SqlState;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link InMemoryDatabase}.
 */
class InMemoryDatabaseTest {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
    }

    @Test
    void testCreateTable() {
        ResultSet rs = db.execute("CREATE TABLE users (id int4, name varchar)");
        assertThat(rs.tag()).isEqualTo("CREATE TABLE");
        assertThat(db.tableNames()).contains("users");
    }

    @Test
    void testCreateTableIfNotExists() {
        db.execute("CREATE TABLE users (id int4)");
        ResultSet rs = db.execute("CREATE TABLE IF NOT EXISTS users (id int4)");
        assertThat(rs.tag()).isEqualTo("CREATE TABLE");
    }

    @Test
    void testCreateDuplicateTable() {
        db.execute("CREATE TABLE users (id int4)");
        assertThatThrownBy(() -> db.execute("CREATE TABLE users (id int4)"))
                .isInstanceOf(InMemoryDatabase.SqlException.class)
                .satisfies(e -> assertThat(((InMemoryDatabase.SqlException) e).sqlState())
                        .isEqualTo(SqlState.DUPLICATE_TABLE));
    }

    @Test
    void testInsert() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        ResultSet rs = db.execute("INSERT INTO users VALUES (1, 'Alice')");
        assertThat(rs.tag()).isEqualTo("INSERT 0 1");
        assertThat(db.rowCount("users")).isEqualTo(1);
    }

    @Test
    void testInsertWithColumnList() {
        db.execute("CREATE TABLE users (id int4, name varchar, email varchar)");
        db.execute("INSERT INTO users (id, name) VALUES (1, 'Alice')");
        assertThat(db.rowCount("users")).isEqualTo(1);
    }

    @Test
    void testSelectAll() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet rs = db.execute("SELECT * FROM users");
        assertThat(rs.tag()).isEqualTo("SELECT 2");
        assertThat(rs.rows()).hasSize(2);
        assertThat(rs.columns()).hasSize(2);
    }

    @Test
    void testSelectColumns() {
        db.execute("CREATE TABLE users (id int4, name varchar, email varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@test.com')");

        ResultSet rs = db.execute("SELECT name, email FROM users");
        assertThat(rs.columns()).hasSize(2);
        assertThat(rs.columns().get(0).name()).isEqualTo("name");
        assertThat(rs.columns().get(1).name()).isEqualTo("email");
    }

    @Test
    void testSelectWhere() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");
        db.execute("INSERT INTO users VALUES (3, 'Charlie')");

        ResultSet rs = db.execute("SELECT * FROM users WHERE id = 2");
        assertThat(rs.rows()).hasSize(1);
        assertThat(rs.rows().get(0)[1]).isEqualTo("Bob");
    }

    @Test
    void testSelectWhereAnd() {
        db.execute("CREATE TABLE users (id int4, name varchar, age int4)");
        db.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        db.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
        db.execute("INSERT INTO users VALUES (3, 'Alice', 35)");

        ResultSet rs = db.execute("SELECT * FROM users WHERE name = 'Alice' AND age = 30");
        assertThat(rs.rows()).hasSize(1);
        assertThat(rs.rows().get(0)[0]).isEqualTo("1");
    }

    @Test
    void testSelectLimit() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");
        db.execute("INSERT INTO users VALUES (3, 'Charlie')");

        ResultSet rs = db.execute("SELECT * FROM users LIMIT 2");
        assertThat(rs.rows()).hasSize(2);
    }

    @Test
    void testSelectOrderBy() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (3, 'Charlie')");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet rs = db.execute("SELECT * FROM users ORDER BY name");
        assertThat(rs.rows().get(0)[1]).isEqualTo("Alice");
        assertThat(rs.rows().get(1)[1]).isEqualTo("Bob");
        assertThat(rs.rows().get(2)[1]).isEqualTo("Charlie");
    }

    @Test
    void testSelectOrderByDesc() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");
        db.execute("INSERT INTO users VALUES (3, 'Charlie')");

        ResultSet rs = db.execute("SELECT * FROM users ORDER BY name DESC");
        assertThat(rs.rows().get(0)[1]).isEqualTo("Charlie");
    }

    @Test
    void testUpdate() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet rs = db.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        assertThat(rs.tag()).isEqualTo("UPDATE 1");

        ResultSet select = db.execute("SELECT * FROM users WHERE id = 1");
        assertThat(select.rows().get(0)[1]).isEqualTo("Alicia");
    }

    @Test
    void testUpdateAll() {
        db.execute("CREATE TABLE users (id int4, status varchar)");
        db.execute("INSERT INTO users VALUES (1, 'active')");
        db.execute("INSERT INTO users VALUES (2, 'active')");

        ResultSet rs = db.execute("UPDATE users SET status = 'inactive'");
        assertThat(rs.tag()).isEqualTo("UPDATE 2");
    }

    @Test
    void testDelete() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet rs = db.execute("DELETE FROM users WHERE id = 1");
        assertThat(rs.tag()).isEqualTo("DELETE 1");
        assertThat(db.rowCount("users")).isEqualTo(1);
    }

    @Test
    void testDeleteAll() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        db.execute("INSERT INTO users VALUES (2, 'Bob')");

        ResultSet rs = db.execute("DELETE FROM users");
        assertThat(rs.tag()).isEqualTo("DELETE 2");
        assertThat(db.rowCount("users")).isEqualTo(0);
    }

    @Test
    void testDropTable() {
        db.execute("CREATE TABLE users (id int4)");
        db.execute("DROP TABLE users");
        assertThat(db.tableNames()).doesNotContain("users");
    }

    @Test
    void testDropTableIfExists() {
        db.execute("DROP TABLE IF EXISTS nonexistent");
        // Should not throw
    }

    @Test
    void testDropTableNotExists() {
        assertThatThrownBy(() -> db.execute("DROP TABLE nonexistent"))
                .isInstanceOf(InMemoryDatabase.SqlException.class)
                .satisfies(e -> assertThat(((InMemoryDatabase.SqlException) e).sqlState())
                        .isEqualTo(SqlState.UNDEFINED_TABLE));
    }

    @Test
    void testSelectFromNonexistentTable() {
        assertThatThrownBy(() -> db.execute("SELECT * FROM nonexistent"))
                .isInstanceOf(InMemoryDatabase.SqlException.class)
                .satisfies(e -> assertThat(((InMemoryDatabase.SqlException) e).sqlState())
                        .isEqualTo(SqlState.UNDEFINED_TABLE));
    }

    @Test
    void testSelectNonexistentColumn() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, 'Alice')");
        assertThatThrownBy(() -> db.execute("SELECT nosuchcol FROM users"))
                .isInstanceOf(InMemoryDatabase.SqlException.class)
                .satisfies(e -> assertThat(((InMemoryDatabase.SqlException) e).sqlState())
                        .isEqualTo(SqlState.UNDEFINED_COLUMN));
    }

    @Test
    void testUnsupportedSql() {
        assertThatThrownBy(() -> db.execute("ALTER TABLE users ADD COLUMN x int4"))
                .isInstanceOf(InMemoryDatabase.SqlException.class)
                .satisfies(e -> assertThat(((InMemoryDatabase.SqlException) e).sqlState())
                        .isEqualTo(SqlState.SYNTAX_ERROR));
    }

    @Test
    void testBeginCommitRollback() {
        assertThat(db.execute("BEGIN").tag()).isEqualTo("BEGIN");
        assertThat(db.execute("COMMIT").tag()).isEqualTo("COMMIT");
        assertThat(db.execute("ROLLBACK").tag()).isEqualTo("ROLLBACK");
    }

    @Test
    void testSet() {
        assertThat(db.execute("SET search_path = 'public'").tag()).isEqualTo("SET");
    }

    @Test
    void testParameterSubstitution() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES ($1, $2)", "1", "Alice");

        ResultSet rs = db.execute("SELECT * FROM users WHERE id = $1", "1");
        assertThat(rs.rows()).hasSize(1);
        assertThat(rs.rows().get(0)[1]).isEqualTo("Alice");
    }

    @Test
    void testNullValue() {
        db.execute("CREATE TABLE users (id int4, name varchar)");
        db.execute("INSERT INTO users VALUES (1, NULL)");

        ResultSet rs = db.execute("SELECT * FROM users");
        assertThat(rs.rows().get(0)[1]).isNull();
    }

    @Test
    void testResultSetHasData() {
        assertThat(ResultSet.commandOnly("INSERT 0 1").hasData()).isFalse();

        db.execute("CREATE TABLE t (x int4)");
        db.execute("INSERT INTO t VALUES (1)");
        ResultSet rs = db.execute("SELECT * FROM t");
        assertThat(rs.hasData()).isTrue();
    }

    @Test
    void testTableNames() {
        db.execute("CREATE TABLE a (x int4)");
        db.execute("CREATE TABLE b (x int4)");
        assertThat(db.tableNames()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testRowCount() {
        db.execute("CREATE TABLE t (x int4)");
        assertThat(db.rowCount("t")).isEqualTo(0);
        db.execute("INSERT INTO t VALUES (1)");
        assertThat(db.rowCount("t")).isEqualTo(1);
    }

    @Test
    void testRowCountNonexistent() {
        assertThat(db.rowCount("nonexistent")).isEqualTo(0);
    }

    @Test
    void testQuotedStringValues() {
        db.execute("CREATE TABLE t (name varchar)");
        db.execute("INSERT INTO t VALUES ('hello world')");
        ResultSet rs = db.execute("SELECT * FROM t");
        assertThat(rs.rows().get(0)[0]).isEqualTo("hello world");
    }

    @Test
    void testCreateTableWithConstraints() {
        db.execute("CREATE TABLE t (id int4, name varchar, PRIMARY KEY (id))");
        assertThat(db.tableNames()).contains("t");
    }

    @Test
    void testColumnTypeMapping() {
        db.execute("CREATE TABLE t (a integer, b bigint, c boolean, d real)");
        db.execute("INSERT INTO t VALUES (1, 2, true, 3.14)");
        ResultSet rs = db.execute("SELECT * FROM t");
        assertThat(rs.columns()).hasSize(4);
    }
}
