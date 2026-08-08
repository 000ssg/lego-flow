package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ResultSetWriterTest {

    @Test void testWriteTextResultSetBasic() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("id", ColumnType.LONG, 8));
        var rows = List.of(List.of("1"), List.of("2"));
        
        int seqId = ResultSetWriter.writeTextResultSet(out, cols, rows, 0, 0);
        
        // count(1) + col def(1) + eof(1) + row(1) + row(1) + final eof(1) = 6
        assertThat(seqId).isEqualTo(6);
        assertThat(out.size()).isGreaterThan(0);
    }

    @Test void testWriteTextResultSetWithDeprecateEof() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("name", ColumnType.VAR_STRING, 32));
        var rows = List.of(List.of("Alice"));
        
        int seqId = ResultSetWriter.writeTextResultSet(
                out, cols, rows, CapabilityFlags.CLIENT_DEPRECATE_EOF, 0);
        
        // No intermediate EOF with DEPRECATE_EOF: count(1) + col def(1) + row(1) + final ok-as-eof(1) = 4
        assertThat(seqId).isEqualTo(4);
    }

    @Test void testWriteTextResultSetEmptyRows() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("x", ColumnType.VAR_STRING, 16));
        
        int seqId = ResultSetWriter.writeTextResultSet(out, cols, List.of(), 0, 5);
        
        // 5 + count(1) + col def(1) + eof(1) + final eof(1) = 9
        assertThat(seqId).isEqualTo(9);
    }

    @Test void testWriteTextResultSetMultipleColumns() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(
                ColumnDefinition.of("id", ColumnType.LONG, 8),
                ColumnDefinition.of("name", ColumnType.VAR_STRING, 64));
        var rows = List.of(List.of("1", "Alice"), List.of("2", "Bob"));
        
        int seqId = ResultSetWriter.writeTextResultSet(out, cols, rows, 0, 0);
        
        // count(1) + col def(1) + col def(1) + eof(1) + row(1) + row(1) + final eof(1) = 7
        assertThat(seqId).isEqualTo(7);
    }

    @Test void testEncodeTextRow() throws Exception {
        byte[] encoded = ResultSetWriter.encodeTextRow(List.of("hello", "world"));
        assertThat(encoded).isNotEmpty();
        
        var decoded = ResultSetWriter.decodeTextRow(encoded, 2);
        assertThat(decoded).containsExactly("hello", "world");
    }

    @Test void testEncodeTextRowWithNull() throws Exception {
        var values = new ArrayList<String>();
        values.add("val");
        values.add(null);
        
        byte[] encoded = ResultSetWriter.encodeTextRow(values);
        
        var decoded = ResultSetWriter.decodeTextRow(encoded, 2);
        assertThat(decoded.get(0)).isEqualTo("val");
        assertThat(decoded.get(1)).isNull();
    }

    @Test void testEncodeTextRowEmptyValues() throws Exception {
        byte[] encoded = ResultSetWriter.encodeTextRow(List.of("", ""));
        
        var decoded = ResultSetWriter.decodeTextRow(encoded, 2);
        assertThat(decoded).containsExactly("", "");
    }

    @Test void testEncodeBinaryRow() throws Exception {
        var cols = List.of(
                ColumnDefinition.of("id", ColumnType.LONG, 8),
                ColumnDefinition.of("name", ColumnType.VAR_STRING, 32));
        
        byte[] encoded = ResultSetWriter.encodeBinaryRow(List.of("42", "Alice"), cols);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testEncodeBinaryRowWithNull() throws Exception {
        var cols = List.of(ColumnDefinition.of("x", ColumnType.VAR_STRING, 16));
        
        var values = new ArrayList<String>();
        values.add(null);
        
        byte[] encoded = ResultSetWriter.encodeBinaryRow(values, cols);
        assertThat(encoded).isNotEmpty();
    }

    @Test void testDecodeBinaryRow() throws Exception {
        var cols = List.of(ColumnDefinition.of("id", ColumnType.LONG, 8));
        
        byte[] encoded = ResultSetWriter.encodeBinaryRow(List.of("123"), cols);
        var decoded = ResultSetWriter.decodeBinaryRow(encoded, cols);
        assertThat(decoded).containsExactly("123");
    }

    @Test void testWriteTextResultSetFromMaps() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("name", ColumnType.VAR_STRING, 32));
        var rows = List.of(Map.of("name", "Alice"));
        
        int seqId = ResultSetWriter.writeTextResultSetFromMaps(
                out, cols, rows, 0, 0);
        
        assertThat(seqId).isGreaterThan(0);
        assertThat(out.size()).isGreaterThan(0);
    }

    @Test void testWriteTextResultSetFromMapsMissingColumn() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("name", ColumnType.VAR_STRING, 32));
        var rows = List.of(Map.<String, String>of());
        
        int seqId = ResultSetWriter.writeTextResultSetFromMaps(
                out, cols, rows, 0, 0);
        
        assertThat(seqId).isGreaterThan(0);
    }

    @Test void testWriteBinaryResultSet() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("id", ColumnType.LONG, 8));
        var rows = List.of(List.of("1"), List.of("2"));
        
        int seqId = ResultSetWriter.writeBinaryResultSet(out, cols, rows, 0, 0);
        
        assertThat(seqId).isGreaterThan(0);
        assertThat(out.size()).isGreaterThan(0);
    }

    @Test void testWriteTextResultSetLargePayload() throws Exception {
        var out = new ByteArrayOutputStream();
        var cols = List.of(ColumnDefinition.of("data", ColumnType.VAR_STRING, 1024));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append((char)('a' + (i % 26)));
        var rows = List.of(List.of(sb.toString()));
        
        int seqId = ResultSetWriter.writeTextResultSet(out, cols, rows, 0, 0);
        assertThat(out.size()).isGreaterThan(100);
    }

    @Test void testDecodeTextRowSingleColumn() throws Exception {
        byte[] encoded = ResultSetWriter.encodeTextRow(List.of("single"));
        var decoded = ResultSetWriter.decodeTextRow(encoded, 1);
        assertThat(decoded).containsExactly("single");
    }
}
