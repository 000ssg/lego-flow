package ssg.legoflow.messaging.kafka.broker.storage;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class InMemoryLogStorageTest {

    @Test
    void testAppendAndFetch() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 3, new byte[]{1, 2, 3}, 1000L);
        storage.append(3, 2, new byte[]{4, 5}, 2000L);

        List<StoredBatch> fetched = storage.fetch(0, 1024);
        assertThat(fetched).hasSize(2);
        assertThat(fetched.get(0).baseOffset()).isZero();
        assertThat(fetched.get(0).recordCount()).isEqualTo(3);
        assertThat(fetched.get(0).data()).containsExactly(1, 2, 3);
        assertThat(fetched.get(1).baseOffset()).isEqualTo(3);
    }

    @Test
    void testFetchFromOffset() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 2, new byte[]{1, 2}, 1000L);
        storage.append(2, 2, new byte[]{3, 4}, 2000L);
        storage.append(4, 2, new byte[]{5, 6}, 3000L);

        List<StoredBatch> fetched = storage.fetch(3, 1024);
        assertThat(fetched).hasSize(2);
        assertThat(fetched.get(0).baseOffset()).isEqualTo(2);
        assertThat(fetched.get(1).baseOffset()).isEqualTo(4);
    }

    @Test
    void testFetchMaxBytes() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 1, new byte[10], 1000L);
        storage.append(1, 1, new byte[10], 2000L);
        storage.append(2, 1, new byte[10], 3000L);

        // maxBytes=15 should return first batch (10 bytes), then stop before second
        List<StoredBatch> fetched = storage.fetch(0, 15);
        assertThat(fetched).hasSize(1);
    }

    @Test
    void testFetchMaxBytesAtLeastOne() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 1, new byte[100], 1000L);

        // Even with maxBytes=1, should return at least one batch
        List<StoredBatch> fetched = storage.fetch(0, 1);
        assertThat(fetched).hasSize(1);
    }

    @Test
    void testFetchBeyondData() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 2, new byte[]{1, 2}, 1000L);

        List<StoredBatch> fetched = storage.fetch(10, 1024);
        assertThat(fetched).isEmpty();
    }

    @Test
    void testAllBatches() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 1, new byte[]{1}, 1000L);
        storage.append(1, 1, new byte[]{2}, 2000L);

        List<StoredBatch> all = storage.allBatches();
        assertThat(all).hasSize(2);
    }

    @Test
    void testReplaceBatches() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 1, new byte[]{1}, 1000L);
        storage.append(1, 1, new byte[]{2}, 2000L);

        storage.replaceBatches(List.of(
                new StoredBatch(5, 3, new byte[]{10, 20, 30}, 5000L)));

        assertThat(storage.size()).isEqualTo(1);
        assertThat(storage.earliestOffset()).isEqualTo(5);
    }

    @Test
    void testTruncateBefore() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 2, new byte[]{1, 2}, 1000L);
        storage.append(2, 2, new byte[]{3, 4}, 2000L);
        storage.append(4, 2, new byte[]{5, 6}, 3000L);

        storage.truncateBefore(3);
        assertThat(storage.size()).isEqualTo(2);
        assertThat(storage.earliestOffset()).isEqualTo(2);
    }

    @Test
    void testTruncateBeforeAll() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 2, new byte[]{1, 2}, 1000L);

        storage.truncateBefore(5);
        assertThat(storage.isEmpty()).isTrue();
    }

    @Test
    void testIsEmpty() {
        var storage = new InMemoryLogStorage();
        assertThat(storage.isEmpty()).isTrue();

        storage.append(0, 1, new byte[]{1}, 1000L);
        assertThat(storage.isEmpty()).isFalse();
    }

    @Test
    void testEarliestOffsetEmpty() {
        var storage = new InMemoryLogStorage();
        assertThat(storage.earliestOffset()).isEqualTo(-1);
    }

    @Test
    void testSize() {
        var storage = new InMemoryLogStorage();
        assertThat(storage.size()).isZero();

        storage.append(0, 1, new byte[]{1}, 1000L);
        storage.append(1, 1, new byte[]{2}, 2000L);
        assertThat(storage.size()).isEqualTo(2);
    }

    @Test
    void testCloseIsNoOp() {
        var storage = new InMemoryLogStorage();
        storage.append(0, 1, new byte[]{1}, 1000L);
        storage.close(); // should not throw
    }
}
