package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StreamStore}.
 */
class StreamStoreTest {

    private StreamStore createStore(long maxMsgs, long maxBytes) {
        var config = StreamConfig.builder("TEST")
                .subjects("test.>")
                .maxMsgs(maxMsgs)
                .maxBytes(maxBytes)
                .build();
        return new StreamStore(config);
    }

    private StreamStore createStore() {
        return createStore(-1, -1);
    }

    @Test
    void testStoreAndRetrieve() {
        var store = createStore();
        long seq = store.store("test.a", null, "hello".getBytes());

        assertThat(seq).isEqualTo(1);
        assertThat(store.messageCount()).isEqualTo(1);

        var msg = store.get(1);
        assertThat(msg).isNotNull();
        assertThat(msg.subject()).isEqualTo("test.a");
        assertThat(new String(msg.payload())).isEqualTo("hello");
    }

    @Test
    void testSequenceIncrementing() {
        var store = createStore();
        assertThat(store.store("a", null, "1".getBytes())).isEqualTo(1);
        assertThat(store.store("b", null, "2".getBytes())).isEqualTo(2);
        assertThat(store.store("c", null, "3".getBytes())).isEqualTo(3);
        assertThat(store.currentSequence()).isEqualTo(3);
    }

    @Test
    void testFetch() {
        var store = createStore();
        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());
        store.store("c", null, "3".getBytes());

        var msgs = store.fetch(1, 10);
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).sequence()).isEqualTo(1);
        assertThat(msgs.get(2).sequence()).isEqualTo(3);
    }

    @Test
    void testFetchFromSequence() {
        var store = createStore();
        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());
        store.store("c", null, "3".getBytes());

        var msgs = store.fetch(2, 10);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).sequence()).isEqualTo(2);
    }

    @Test
    void testFetchWithMaxCount() {
        var store = createStore();
        for (int i = 0; i < 10; i++) {
            store.store("t", null, String.valueOf(i).getBytes());
        }

        var msgs = store.fetch(1, 3);
        assertThat(msgs).hasSize(3);
    }

    @Test
    void testFetchBySubject() {
        var store = createStore();
        store.store("orders.new", null, "o1".getBytes());
        store.store("orders.cancel", null, "o2".getBytes());
        store.store("orders.new", null, "o3".getBytes());

        var msgs = store.fetch("orders.new", 1, 10);
        assertThat(msgs).hasSize(2);
        assertThat(new String(msgs.get(0).payload())).isEqualTo("o1");
        assertThat(new String(msgs.get(1).payload())).isEqualTo("o3");
    }

    @Test
    void testFirstAndLastMessage() {
        var store = createStore();
        assertThat(store.firstMessage()).isNull();
        assertThat(store.lastMessage()).isNull();

        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());

        assertThat(store.firstMessage().sequence()).isEqualTo(1);
        assertThat(store.lastMessage().sequence()).isEqualTo(2);
    }

    @Test
    void testTotalBytes() {
        var store = createStore();
        store.store("a", null, "hello".getBytes());   // 5 bytes
        store.store("b", null, "world!".getBytes());   // 6 bytes

        assertThat(store.totalBytes()).isEqualTo(11);
    }

    @Test
    void testRemove() {
        var store = createStore();
        store.store("a", null, "data".getBytes());
        store.store("b", null, "more".getBytes());

        assertThat(store.remove(1)).isTrue();
        assertThat(store.messageCount()).isEqualTo(1);
        assertThat(store.get(1)).isNull();
        assertThat(store.get(2)).isNotNull();
    }

    @Test
    void testRemoveNonExistent() {
        var store = createStore();
        assertThat(store.remove(999)).isFalse();
    }

    @Test
    void testPurge() {
        var store = createStore();
        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());

        int purged = store.purge();
        assertThat(purged).isEqualTo(2);
        assertThat(store.messageCount()).isEqualTo(0);
        assertThat(store.totalBytes()).isEqualTo(0);
    }

    // --- Retention enforcement ---

    @Test
    void testMaxMsgsRetention() {
        var store = createStore(3, -1);
        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());
        store.store("c", null, "3".getBytes());
        store.store("d", null, "4".getBytes());

        assertThat(store.messageCount()).isEqualTo(3);
        assertThat(store.firstMessage().sequence()).isEqualTo(2); // oldest removed
    }

    @Test
    void testMaxBytesRetention() {
        var store = createStore(-1, 10);
        store.store("a", null, "12345".getBytes());   // 5 bytes
        store.store("b", null, "12345".getBytes());   // 5 bytes = 10 total
        store.store("c", null, "12345".getBytes());   // exceeds 10 bytes

        assertThat(store.messageCount()).isEqualTo(2);
        assertThat(store.totalBytes()).isLessThanOrEqualTo(10);
    }

    @Test
    void testDiscardNewPolicy() {
        var config = StreamConfig.builder("TEST")
                .subjects("test.>")
                .maxMsgs(2)
                .discardPolicy(StreamConfig.DiscardPolicy.NEW)
                .build();
        var store = new StreamStore(config);

        store.store("a", null, "1".getBytes());
        store.store("b", null, "2".getBytes());
        long seq = store.store("c", null, "3".getBytes()); // should be rejected

        assertThat(seq).isEqualTo(-1);
        assertThat(store.messageCount()).isEqualTo(2);
    }

    @Test
    void testGetNonExistentSequence() {
        var store = createStore();
        assertThat(store.get(999)).isNull();
    }

    @Test
    void testEmptyFetch() {
        var store = createStore();
        assertThat(store.fetch(1, 10)).isEmpty();
    }
}
