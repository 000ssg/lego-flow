package ssg.legoflow.http3.qpack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QpackDynamicTableTest {

    @Test
    void testInsertAndRetrieve() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.insert("custom-header", "custom-value");

        // Then
        assertThat(table.size()).isEqualTo(1);
        var entry = table.getEntry(0);
        assertThat(entry.name()).isEqualTo("custom-header");
        assertThat(entry.value()).isEqualTo("custom-value");
    }

    @Test
    void testInsertMultiple() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.insert("header-1", "value-1");
        table.insert("header-2", "value-2");

        // Then: most recent entry is at index 0
        assertThat(table.size()).isEqualTo(2);
        assertThat(table.getEntry(0).name()).isEqualTo("header-2");
        assertThat(table.getEntry(1).name()).isEqualTo("header-1");
    }

    @Test
    void testEvictionOnCapacity() {
        // Given: small capacity (entry overhead = 32 bytes)
        var table = new QpackDynamicTable(64);

        // When: insert two entries that together exceed capacity
        table.insert("a", "1"); // size = 1 + 1 + 32 = 34
        table.insert("b", "2"); // size = 1 + 1 + 32 = 34, total = 68 > 64

        // Then: first entry evicted
        assertThat(table.size()).isEqualTo(1);
        assertThat(table.getEntry(0).name()).isEqualTo("b");
    }

    @Test
    void testSetCapacityEvicts() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header-1", "value-1");
        table.insert("header-2", "value-2");
        assertThat(table.size()).isEqualTo(2);

        // When: reduce capacity to 0
        table.setCapacity(0);

        // Then: all entries evicted
        assertThat(table.size()).isEqualTo(0);
        assertThat(table.capacity()).isEqualTo(0);
    }

    @Test
    void testCapacityTracking() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.insert("name", "value"); // 4 + 5 + 32 = 41

        // Then
        assertThat(table.capacity()).isEqualTo(41);
        assertThat(table.maxCapacity()).isEqualTo(4096);
    }

    @Test
    void testFindEntry() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header-1", "value-1");
        table.insert("header-2", "value-2");

        // When/Then
        assertThat(table.findEntry("header-2", "value-2")).isEqualTo(0);
        assertThat(table.findEntry("header-1", "value-1")).isEqualTo(1);
        assertThat(table.findEntry("unknown", "value")).isEqualTo(-1);
    }

    @Test
    void testFindNameIndex() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header-1", "value-1");
        table.insert("header-2", "value-2");

        // When/Then
        assertThat(table.findNameIndex("header-2")).isEqualTo(0);
        assertThat(table.findNameIndex("header-1")).isEqualTo(1);
        assertThat(table.findNameIndex("unknown")).isEqualTo(-1);
    }

    @Test
    void testStreamReferences() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.addStreamReference(4L);
        table.addStreamReference(4L);
        table.addStreamReference(8L);

        // Then
        assertThat(table.getStreamReferenceCount(4L)).isEqualTo(2);
        assertThat(table.getStreamReferenceCount(8L)).isEqualTo(1);
        assertThat(table.getTotalStreamReferences()).isEqualTo(2);

        // When: remove references
        table.removeStreamReference(4L);
        assertThat(table.getStreamReferenceCount(4L)).isEqualTo(1);

        table.removeStreamReference(4L);
        assertThat(table.getStreamReferenceCount(4L)).isEqualTo(0);
        assertThat(table.getTotalStreamReferences()).isEqualTo(1);
    }

    @Test
    void testInsertCount() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.insert("h1", "v1");
        table.insert("h2", "v2");

        // Then
        assertThat(table.getInsertCount()).isEqualTo(2);
    }

    @Test
    void testClear() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header", "value");

        // When
        table.clear();

        // Then
        assertThat(table.size()).isEqualTo(0);
        assertThat(table.capacity()).isEqualTo(0);
    }

    @Test
    void testInvalidIndex() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header", "value");

        // When/Then
        assertThatThrownBy(() -> table.getEntry(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table.getEntry(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEntryTooLargeForCapacity() {
        // Given: capacity too small for any entry
        var table = new QpackDynamicTable(10);

        // When
        table.insert("a-long-header", "a-long-value");

        // Then: entry not added
        assertThat(table.size()).isEqualTo(0);
    }

    // ==================== New Dynamic Table Feature Tests ====================

    @Test
    void testInsertWithStaticNameReference() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When: insert using static table name at index 0 (":authority")
        table.insertWithStaticNameReference(0, "example.com");

        // Then
        assertThat(table.size()).isEqualTo(1);
        var entry = table.getEntry(0);
        assertThat(entry.name()).isEqualTo(":authority");
        assertThat(entry.value()).isEqualTo("example.com");
    }

    @Test
    void testInsertWithDynamicNameReference() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("x-custom", "value-1");

        // When: insert with name from dynamic table index 0
        table.insertWithDynamicNameReference(0, "value-2");

        // Then
        assertThat(table.size()).isEqualTo(2);
        var entry = table.getEntry(0);
        assertThat(entry.name()).isEqualTo("x-custom");
        assertThat(entry.value()).isEqualTo("value-2");
    }

    @Test
    void testDuplicate() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header-a", "value-a");
        table.insert("header-b", "value-b");

        // When: duplicate the entry at relative index 1 (header-a)
        table.duplicate(1);

        // Then: header-a is re-inserted at position 0
        assertThat(table.size()).isEqualTo(3);
        assertThat(table.getEntry(0).name()).isEqualTo("header-a");
        assertThat(table.getEntry(0).value()).isEqualTo("value-a");
    }

    @Test
    void testGetEntryAbsolute() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("first", "1");  // absolute index 0
        table.insert("second", "2"); // absolute index 1
        table.insert("third", "3");  // absolute index 2

        // When/Then: absolute indexing (0 = first ever inserted)
        assertThat(table.getEntryAbsolute(0).name()).isEqualTo("first");
        assertThat(table.getEntryAbsolute(1).name()).isEqualTo("second");
        assertThat(table.getEntryAbsolute(2).name()).isEqualTo("third");
    }

    @Test
    void testGetEntryAbsoluteInvalid() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("header", "value");

        // When/Then
        assertThatThrownBy(() -> table.getEntryAbsolute(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetEntryPostBase() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("first", "1");  // absolute 0
        table.insert("second", "2"); // absolute 1
        table.insert("third", "3");  // absolute 2

        // When: post-base index 0 with base 1 means absolute index 1
        var entry = table.getEntryPostBase(0, 1);

        // Then
        assertThat(entry.name()).isEqualTo("second");
    }

    @Test
    void testAcknowledgeSectionForStream() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When
        table.acknowledgeSectionForStream(4L);

        // Then
        assertThat(table.isStreamAcknowledged(4L)).isTrue();
        assertThat(table.isStreamAcknowledged(8L)).isFalse();
    }

    @Test
    void testCancelStream() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.addStreamReference(4L);
        table.acknowledgeSectionForStream(4L);

        // When
        table.cancelStream(4L);

        // Then
        assertThat(table.getStreamReferenceCount(4L)).isEqualTo(0);
        assertThat(table.isStreamAcknowledged(4L)).isFalse();
    }

    @Test
    void testIncrementKnownReceivedCount() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("h1", "v1");
        table.insert("h2", "v2");

        // When
        table.incrementKnownReceivedCount(2);

        // Then
        assertThat(table.getKnownReceivedCount()).isEqualTo(2);
    }

    @Test
    void testIncrementKnownReceivedCountExceedsInsertCount() {
        // Given
        var table = new QpackDynamicTable(4096);
        table.insert("h1", "v1");

        // When/Then: increment beyond insert count should throw
        assertThatThrownBy(() -> table.incrementKnownReceivedCount(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIncrementKnownReceivedCountNegative() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When/Then
        assertThatThrownBy(() -> table.incrementKnownReceivedCount(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testComputeRequiredInsertCount() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When/Then
        assertThat(table.computeRequiredInsertCount(-1)).isEqualTo(0);
        assertThat(table.computeRequiredInsertCount(0)).isEqualTo(1);
        assertThat(table.computeRequiredInsertCount(3)).isEqualTo(4);
    }

    @Test
    void testEncodeDecodeRequiredInsertCountZero() {
        // Given
        var table = new QpackDynamicTable(4096);

        // When/Then
        assertThat(table.encodeRequiredInsertCount(0)).isEqualTo(0);
        assertThat(table.decodeRequiredInsertCount(0)).isEqualTo(0);
    }

    @Test
    void testDroppedCountTracked() {
        // Given: small capacity causing eviction
        var table = new QpackDynamicTable(64);

        // When
        table.insert("a", "1"); // 34 bytes, fits
        table.insert("b", "2"); // 34 bytes, total 68 > 64, evicts "a"

        // Then
        assertThat(table.getDroppedCount()).isEqualTo(1);
        assertThat(table.size()).isEqualTo(1);
    }

    @Test
    void testAbsoluteIndexAfterEviction() {
        // Given: capacity for exactly 1 entry
        var table = new QpackDynamicTable(64);
        table.insert("a", "1"); // absolute 0, 34 bytes
        table.insert("b", "2"); // absolute 1, evicts "a"

        // When/Then: absolute index 0 is evicted and inaccessible
        assertThatThrownBy(() -> table.getEntryAbsolute(0))
                .isInstanceOf(IllegalArgumentException.class);

        // But absolute index 1 (latest) is accessible
        var entry = table.getEntryAbsolute(1);
        assertThat(entry.name()).isEqualTo("b");
    }
}
