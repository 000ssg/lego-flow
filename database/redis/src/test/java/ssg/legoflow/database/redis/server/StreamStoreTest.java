package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class StreamStoreTest {

    @Test void testAddWithAutoId() {
        var store = new StreamStore();
        String id = store.add("*", Map.of("key", "value"));
        assertThat(id).isNotNull().contains("-");
        assertThat(store.length()).isEqualTo(1);
    }

    @Test void testAddExplicitId() {
        var store = new StreamStore();
        String id = store.add("1000-0", Map.of("f1", "v1"));
        assertThat(id).isEqualTo("1000-0");
    }

    @Test void testLength() {
        var store = new StreamStore();
        assertThat(store.length()).isEqualTo(0);
        store.add("*", Map.of("a", "1"));
        assertThat(store.length()).isEqualTo(1);
    }

    @Test void testTrim() {
        var store = new StreamStore();
        for (int i = 0; i < 10; i++) store.add("*", Map.of("i", String.valueOf(i)));
        
        long removed = store.trim(5);
        assertThat(removed).isEqualTo(5);
        assertThat(store.length()).isEqualTo(5);
    }

    @Test void testTrimNoOp() {
        var store = new StreamStore();
        store.add("*", Map.of("a", "1"));
        assertThat(store.trim(100)).isEqualTo(0);
    }

    @Test void testCreateGroup() {
        var store = new StreamStore();
        store.add("*", Map.of("data", "val"));
        
        boolean created = store.createGroup("mygroup", "0");
        assertThat(created).isTrue();
        assertThat(store.createGroup("mygroup", "0")).isFalse();
    }

    @Test void testCreateGroupWithDollarStart() {
        var store = new StreamStore();
        store.add("*", Map.of("a", "1"));
        store.createGroup("g", "$");
        assertThat(store.getGroup("g")).isNotNull();
    }

    @Test void testGetGroup() {
        var store = new StreamStore();
        store.createGroup("g", "0");
        
        var group = store.getGroup("g");
        assertThat(group).isNotNull();
        assertThat(group.name()).isEqualTo("g");
    }

    // === ConsumerGroup tests ===
    @Test void testConsumerGroupProperties() {
        var group = new StreamStore.ConsumerGroup("g1", "0-0");
        assertThat(group.name()).isEqualTo("g1");
        assertThat(group.lastDeliveredId()).isEqualTo("0-0");
        assertThat(group.consumers()).isEmpty();
        assertThat(group.pending()).isEmpty();
        
        group.setLastDeliveredId("1000-0");
        assertThat(group.lastDeliveredId()).isEqualTo("1000-0");
    }

    // === PendingEntry tests ===
    @Test void testPendingEntryRecord() {
        var entry = new StreamStore.PendingEntry("1000-0", "c1", 12345L, 3);
        assertThat(entry.id()).isEqualTo("1000-0");
        assertThat(entry.consumer()).isEqualTo("c1");
        assertThat(entry.deliveryTime()).isEqualTo(12345L);
        assertThat(entry.deliveryCount()).isEqualTo(3);
    }

    // === StreamEntry tests ===
    @Test void testStreamEntryRecord() {
        var entry = new StreamStore.StreamEntry("1000-0", Map.of("key", "val"));
        assertThat(entry.id()).isEqualTo("1000-0");
        assertThat(entry.fields().get("key")).isEqualTo("val");
    }

    @Test void testRangeEmptyStream() {
        var store = new StreamStore();
        // Empty stream should return empty list for any range query
        List<StreamStore.StreamEntry> result = store.range("0-0", "99999999999999-99999999999999", 0);
        assertThat(result).isEmpty();
    }

    @Test void testReadEmptyStream() {
        var store = new StreamStore();
        assertThat(store.read("0-0", 0)).isEmpty();
    }

    @Test void testRevRangeEmptyStream() {
        var store = new StreamStore();
        assertThat(store.revRange("99999999999999-99999999999999", "0-0", 0)).isEmpty();
    }
}
