package ssg.legoflow.http3.qpack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QpackStaticTableTest {

    @Test
    void testStaticTableSize() {
        // Given/When
        var size = QpackStaticTable.getSize();

        // Then: 99 entries per RFC 9204 Appendix A
        assertThat(size).isEqualTo(99);
    }

    @Test
    void testFirstEntry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(0);

        // Then
        assertThat(entry.name()).isEqualTo(":authority");
        assertThat(entry.value()).isEmpty();
    }

    @Test
    void testPathEntry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(1);

        // Then
        assertThat(entry.name()).isEqualTo(":path");
        assertThat(entry.value()).isEqualTo("/");
    }

    @Test
    void testMethodGetEntry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(17);

        // Then
        assertThat(entry.name()).isEqualTo(":method");
        assertThat(entry.value()).isEqualTo("GET");
    }

    @Test
    void testMethodPostEntry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(20);

        // Then
        assertThat(entry.name()).isEqualTo(":method");
        assertThat(entry.value()).isEqualTo("POST");
    }

    @Test
    void testStatus200Entry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(25);

        // Then
        assertThat(entry.name()).isEqualTo(":status");
        assertThat(entry.value()).isEqualTo("200");
    }

    @Test
    void testSchemeHttps() {
        // Given/When
        var entry = QpackStaticTable.getEntry(23);

        // Then
        assertThat(entry.name()).isEqualTo(":scheme");
        assertThat(entry.value()).isEqualTo("https");
    }

    @Test
    void testContentTypeJson() {
        // Given/When
        var entry = QpackStaticTable.getEntry(46);

        // Then
        assertThat(entry.name()).isEqualTo("content-type");
        assertThat(entry.value()).isEqualTo("application/json");
    }

    @Test
    void testLastEntry() {
        // Given/When
        var entry = QpackStaticTable.getEntry(98);

        // Then
        assertThat(entry.name()).isEqualTo("vary");
        assertThat(entry.value()).isEqualTo("accept-encoding");
    }

    @Test
    void testFindEntryExact() {
        // Given/When/Then
        assertThat(QpackStaticTable.findEntry(":method", "GET")).isEqualTo(17);
        assertThat(QpackStaticTable.findEntry(":method", "POST")).isEqualTo(20);
        assertThat(QpackStaticTable.findEntry(":path", "/")).isEqualTo(1);
        assertThat(QpackStaticTable.findEntry(":status", "200")).isEqualTo(25);
        assertThat(QpackStaticTable.findEntry(":scheme", "https")).isEqualTo(23);
    }

    @Test
    void testFindEntryNotFound() {
        // Given/When/Then
        assertThat(QpackStaticTable.findEntry(":method", "TRACE")).isEqualTo(-1);
        assertThat(QpackStaticTable.findEntry("x-custom", "value")).isEqualTo(-1);
    }

    @Test
    void testFindNameIndex() {
        // Given/When/Then
        assertThat(QpackStaticTable.findNameIndex(":authority")).isEqualTo(0);
        assertThat(QpackStaticTable.findNameIndex(":method")).isEqualTo(15);
        assertThat(QpackStaticTable.findNameIndex("content-type")).isEqualTo(44);
    }

    @Test
    void testFindNameIndexNotFound() {
        // Given/When/Then
        assertThat(QpackStaticTable.findNameIndex("x-custom")).isEqualTo(-1);
    }

    @Test
    void testInvalidIndex() {
        // Given/When/Then
        assertThatThrownBy(() -> QpackStaticTable.getEntry(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QpackStaticTable.getEntry(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAllEntriesNonNull() {
        // Given/When/Then: every entry in the table should be non-null
        for (int i = 0; i < QpackStaticTable.getSize(); i++) {
            var entry = QpackStaticTable.getEntry(i);
            assertThat(entry).isNotNull();
            assertThat(entry.name()).isNotNull().isNotEmpty();
            assertThat(entry.value()).isNotNull();
        }
    }
}
