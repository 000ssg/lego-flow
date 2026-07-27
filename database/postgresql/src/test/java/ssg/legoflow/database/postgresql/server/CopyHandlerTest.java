package ssg.legoflow.database.postgresql.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CopyHandler}.
 */
class CopyHandlerTest {

    private InMemoryDatabase db;
    private CopyHandler handler;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase();
        handler = new CopyHandler(db);
        db.execute("CREATE TABLE t (id int4, name varchar, value varchar)");
    }

    @Test
    void testCopyIn() {
        List<byte[]> data = List.of(
                "1\tAlice\t100\n".getBytes(StandardCharsets.UTF_8),
                "2\tBob\t200\n".getBytes(StandardCharsets.UTF_8)
        );

        int count = handler.processCopyIn("t", data);
        assertThat(count).isEqualTo(2);
        assertThat(db.rowCount("t")).isEqualTo(2);
    }

    @Test
    void testCopyInWithNull() {
        List<byte[]> data = List.of(
                "1\tAlice\t\\N\n".getBytes(StandardCharsets.UTF_8)
        );

        handler.processCopyIn("t", data);
        ResultSet rs = db.execute("SELECT * FROM t");
        assertThat(rs.rows().get(0)[2]).isNull();
    }

    @Test
    void testCopyOut() {
        db.execute("INSERT INTO t VALUES (1, 'Alice', '100')");
        db.execute("INSERT INTO t VALUES (2, 'Bob', '200')");

        List<byte[]> chunks = handler.generateCopyOut("t");
        assertThat(chunks).hasSize(2);

        String row1 = new String(chunks.get(0), StandardCharsets.UTF_8);
        assertThat(row1).contains("Alice");
        assertThat(row1).contains("\t");
    }

    @Test
    void testCopyOutEmpty() {
        List<byte[]> chunks = handler.generateCopyOut("t");
        assertThat(chunks).isEmpty();
    }

    @Test
    void testCopyInMultipleChunks() {
        List<byte[]> data = List.of(
                "1\tAlice\t100\n2\tBob\t200\n".getBytes(StandardCharsets.UTF_8)
        );

        int count = handler.processCopyIn("t", data);
        assertThat(count).isEqualTo(2);
    }
}
