package ssg.legoflow.http2.hpack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HpackStaticTableTest {

    @Test
    void testStaticTableSize() {
        assertThat(HpackStaticTable.SIZE).isEqualTo(61);
    }

    @Test
    void testFirstEntry() {
        var entry = HpackStaticTable.get(1);
        assertThat(entry.name()).isEqualTo(":authority");
        assertThat(entry.value()).isEmpty();
    }

    @Test
    void testMethodGetEntry() {
        var entry = HpackStaticTable.get(2);
        assertThat(entry.name()).isEqualTo(":method");
        assertThat(entry.value()).isEqualTo("GET");
    }

    @Test
    void testMethodPostEntry() {
        var entry = HpackStaticTable.get(3);
        assertThat(entry.name()).isEqualTo(":method");
        assertThat(entry.value()).isEqualTo("POST");
    }

    @Test
    void testPathRootEntry() {
        var entry = HpackStaticTable.get(4);
        assertThat(entry.name()).isEqualTo(":path");
        assertThat(entry.value()).isEqualTo("/");
    }

    @Test
    void testStatus200Entry() {
        var entry = HpackStaticTable.get(8);
        assertThat(entry.name()).isEqualTo(":status");
        assertThat(entry.value()).isEqualTo("200");
    }

    @Test
    void testLastEntry() {
        var entry = HpackStaticTable.get(61);
        assertThat(entry.name()).isEqualTo("www-authenticate");
        assertThat(entry.value()).isEmpty();
    }

    @Test
    void testFindNameValueIndex() {
        assertThat(HpackStaticTable.findNameValueIndex(":method", "GET")).isEqualTo(2);
        assertThat(HpackStaticTable.findNameValueIndex(":method", "POST")).isEqualTo(3);
        assertThat(HpackStaticTable.findNameValueIndex(":path", "/")).isEqualTo(4);
        assertThat(HpackStaticTable.findNameValueIndex(":status", "200")).isEqualTo(8);
    }

    @Test
    void testFindNameValueIndexNotFound() {
        assertThat(HpackStaticTable.findNameValueIndex(":method", "PUT")).isEqualTo(0);
        assertThat(HpackStaticTable.findNameValueIndex("x-custom", "value")).isEqualTo(0);
    }

    @Test
    void testFindNameIndex() {
        assertThat(HpackStaticTable.findNameIndex(":authority")).isEqualTo(1);
        assertThat(HpackStaticTable.findNameIndex(":method")).isEqualTo(2);
        assertThat(HpackStaticTable.findNameIndex("content-type")).isEqualTo(31);
    }

    @Test
    void testFindNameIndexNotFound() {
        assertThat(HpackStaticTable.findNameIndex("x-custom")).isEqualTo(0);
    }

    @Test
    void testInvalidIndex() {
        assertThatThrownBy(() -> HpackStaticTable.get(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HpackStaticTable.get(62))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
