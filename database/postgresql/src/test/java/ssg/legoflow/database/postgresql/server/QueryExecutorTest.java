package ssg.legoflow.database.postgresql.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.database.postgresql.common.PgSeverity;
import ssg.legoflow.database.postgresql.common.SqlState;
import ssg.legoflow.database.postgresql.protocol.BackendMessage;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link QueryExecutor}.
 */
class QueryExecutorTest {

    private QueryExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new QueryExecutor(new InMemoryDatabase());
        executor.database().execute("CREATE TABLE t (id int4, name varchar)");
        executor.database().execute("INSERT INTO t VALUES (1, 'Alice')");
        executor.database().execute("INSERT INTO t VALUES (2, 'Bob')");
    }

    @Test
    void testSimpleSelect() {
        var messages = executor.executeSimple("SELECT * FROM t");
        assertThat(messages).hasSize(4); // RowDescription + 2 DataRow + CommandComplete
        assertThat(messages.get(0)).isInstanceOf(BackendMessage.RowDescription.class);
        assertThat(messages.get(1)).isInstanceOf(BackendMessage.DataRow.class);
        assertThat(messages.get(2)).isInstanceOf(BackendMessage.DataRow.class);
        assertThat(messages.get(3)).isInstanceOf(BackendMessage.CommandComplete.class);
    }

    @Test
    void testSimpleInsert() {
        var messages = executor.executeSimple("INSERT INTO t VALUES (3, 'Charlie')");
        assertThat(messages).hasSize(1);
        assertThat(((BackendMessage.CommandComplete) messages.get(0)).tag()).isEqualTo("INSERT 0 1");
    }

    @Test
    void testSimpleUpdate() {
        var messages = executor.executeSimple("UPDATE t SET name = 'Alicia' WHERE id = 1");
        assertThat(messages).hasSize(1);
        assertThat(((BackendMessage.CommandComplete) messages.get(0)).tag()).isEqualTo("UPDATE 1");
    }

    @Test
    void testSimpleDelete() {
        var messages = executor.executeSimple("DELETE FROM t WHERE id = 1");
        assertThat(messages).hasSize(1);
        assertThat(((BackendMessage.CommandComplete) messages.get(0)).tag()).isEqualTo("DELETE 1");
    }

    @Test
    void testSimpleError() {
        var messages = executor.executeSimple("SELECT * FROM nonexistent");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(BackendMessage.ErrorResponse.class);
        assertThat(((BackendMessage.ErrorResponse) messages.get(0)).sqlState()).isEqualTo("42P01");
    }

    @Test
    void testEmptyQuery() {
        var messages = executor.executeSimple("");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(BackendMessage.EmptyQueryResponse.class);
    }

    @Test
    void testMultipleStatements() {
        var messages = executor.executeSimple(
                "INSERT INTO t VALUES (3, 'Charlie'); SELECT * FROM t");
        // INSERT result + RowDescription + 3 DataRow + CommandComplete
        assertThat(messages.stream().filter(m -> m instanceof BackendMessage.CommandComplete).count())
                .isEqualTo(2);
    }

    @Test
    void testExtendedQuery() {
        var stmt = new PreparedStatement("s1", "SELECT * FROM t WHERE id = $1", new int[]{23});
        byte[][] params = {"1".getBytes()};
        var portal = new Portal("p1", stmt, params);

        var messages = executor.executeExtended(portal, 0);
        assertThat(messages.stream().filter(m -> m instanceof BackendMessage.DataRow).count())
                .isEqualTo(1);
    }

    @Test
    void testExtendedQueryWithLimit() {
        var stmt = new PreparedStatement("s1", "SELECT * FROM t", new int[0]);
        var portal = new Portal("p1", stmt, new byte[0][]);

        var messages = executor.executeExtended(portal, 1);
        assertThat(messages.stream().filter(m -> m instanceof BackendMessage.DataRow).count())
                .isEqualTo(1);
        assertThat(messages.get(messages.size() - 1)).isInstanceOf(BackendMessage.PortalSuspended.class);
    }

    @Test
    void testMakeError() {
        var error = QueryExecutor.makeError(SqlState.SYNTAX_ERROR, "bad query");
        assertThat(error.severity()).isEqualTo("ERROR");
        assertThat(error.sqlState()).isEqualTo("42601");
        assertThat(error.message()).isEqualTo("bad query");
    }

    @Test
    void testMakeErrorWithSeverity() {
        var error = QueryExecutor.makeError(PgSeverity.FATAL, SqlState.INTERNAL_ERROR, "crash");
        assertThat(error.severity()).isEqualTo("FATAL");
        assertThat(error.sqlState()).isEqualTo("XX000");
    }

    @Test
    void testMakeNotice() {
        var notice = QueryExecutor.makeNotice(PgSeverity.NOTICE, SqlState.SUCCESSFUL_COMPLETION, "all good");
        assertThat(notice.severity()).isEqualTo("NOTICE");
        assertThat(notice.message()).isEqualTo("all good");
    }
}
