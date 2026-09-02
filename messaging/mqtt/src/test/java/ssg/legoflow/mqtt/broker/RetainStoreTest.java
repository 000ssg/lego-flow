package ssg.legoflow.mqtt.broker;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link RetainStore}.
 *
 * @since 0.1.0
 */
class RetainStoreTest {

    @Test
    void testPutAndGet() {
        // Given: store with a retained message
        var store = new RetainStore();
        store.put("sensor/temp", "22.5".getBytes());

        // When: get
        var packet = store.get("sensor/temp");

        // Then: message present
        assertThat(packet).isNotNull();
        assertThat(new String(packet.payload())).isEqualTo("22.5");
    }

    @Test
    void testGetNonExistent() {
        // Given: empty store
        var store = new RetainStore();

        // When/Then: returns null
        assertThat(store.get("none")).isNull();
    }

    @Test
    void testRemove() {
        // Given: store with message
        var store = new RetainStore();
        store.put("a/b", "data".getBytes());

        // When: remove
        store.remove("a/b");

        // Then: gone
        assertThat(store.get("a/b")).isNull();
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void testEmptyPayloadRemoves() {
        // Given: store with message
        var store = new RetainStore();
        store.put("a", "value".getBytes());

        // When: put with empty payload
        store.put("a", new byte[0]);

        // Then: removed
        assertThat(store.get("a")).isNull();
    }

    @Test
    void testGetMatchingWithWildcard() {
        // Given: store with multiple topics
        var store = new RetainStore();
        store.put("sensors/room1/temp", "22".getBytes());
        store.put("sensors/room2/temp", "23".getBytes());
        store.put("sensors/room1/humidity", "45".getBytes());
        store.put("other/topic", "x".getBytes());

        // When: wildcard query
        var matches = store.getMatching("sensors/+/temp");

        // Then: two matches
        assertThat(matches).hasSize(2);
    }

    @Test
    void testClearAll() {
        // Given: store with messages
        var store = new RetainStore();
        store.put("a", "1".getBytes());
        store.put("b", "2".getBytes());

        // When: clear
        store.clear();

        // Then: empty
        assertThat(store.size()).isEqualTo(0);
    }
}
