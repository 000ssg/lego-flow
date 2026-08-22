package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.record.Record;
import ssg.legoflow.messaging.kafka.record.RecordBatch;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class PartitionLogTest {

    private byte[] makeBatch(int recordCount) {
        List<Record> records = new java.util.ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            records.add(new Record(i, 0L, ("k" + i).getBytes(), ("v" + i).getBytes(), List.of()));
        }
        return new RecordBatch()
                .baseOffset(0)
                .lastOffsetDelta(recordCount - 1)
                .baseTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(records)
                .encode();
    }

    @Test
    void testAppendAndHighWatermark() {
        var log = new PartitionLog("test", 0);
        assertThat(log.highWatermark()).isZero();

        long offset = log.append(makeBatch(3));
        assertThat(offset).isZero();
        assertThat(log.highWatermark()).isEqualTo(3);
    }

    @Test
    void testMultipleAppends() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(2));
        long offset2 = log.append(makeBatch(3));
        assertThat(offset2).isEqualTo(2);
        assertThat(log.highWatermark()).isEqualTo(5);
    }

    @Test
    void testFetchFromBeginning() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(3));

        byte[] fetched = log.fetch(0, 1048576);
        assertThat(fetched).isNotEmpty();

        RecordBatch batch = RecordBatch.decode(fetched);
        assertThat(batch.records()).hasSize(3);
    }

    @Test
    void testFetchFromOffset() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(2)); // offsets 0-1
        log.append(makeBatch(2)); // offsets 2-3

        byte[] fetched = log.fetch(2, 1048576);
        assertThat(fetched).isNotEmpty();

        RecordBatch batch = RecordBatch.decode(fetched);
        assertThat(batch.baseOffset()).isEqualTo(2);
    }

    @Test
    void testFetchBeyondHighWatermark() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(2));

        byte[] fetched = log.fetch(10, 1048576);
        assertThat(fetched).isEmpty();
    }

    @Test
    void testFetchMaxBytes() {
        var log = new PartitionLog("test", 0);
        for (int i = 0; i < 10; i++) {
            log.append(makeBatch(1));
        }
        // Fetch with very small max bytes — should still get at least one batch
        byte[] fetched = log.fetch(0, 1);
        assertThat(fetched).isNotEmpty();
    }

    @Test
    void testEarliestOffset() {
        var log = new PartitionLog("test", 0);
        assertThat(log.earliestOffset()).isZero();

        log.append(makeBatch(2));
        assertThat(log.earliestOffset()).isZero();
    }

    @Test
    void testOffsetForTimestampLatest() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(3));
        assertThat(log.offsetForTimestamp(-1L)).isEqualTo(3);
    }

    @Test
    void testOffsetForTimestampEarliest() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(3));
        assertThat(log.offsetForTimestamp(-2L)).isZero();
    }

    @Test
    void testTopicAndPartition() {
        var log = new PartitionLog("orders", 2);
        assertThat(log.topic()).isEqualTo("orders");
        assertThat(log.partition()).isEqualTo(2);
    }

    @Test
    void testTruncateBefore() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(2)); // offsets 0-1
        log.append(makeBatch(2)); // offsets 2-3
        log.append(makeBatch(2)); // offsets 4-5

        long lowWatermark = log.truncateBefore(2);
        assertThat(lowWatermark).isEqualTo(2);
        assertThat(log.highWatermark()).isEqualTo(6);

        // Fetching from offset 0 should only return batches starting at offset 2
        byte[] fetched = log.fetch(0, 1048576);
        RecordBatch batch = RecordBatch.decode(fetched);
        assertThat(batch.baseOffset()).isEqualTo(2);
    }

    @Test
    void testTruncateBeforeAll() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(2)); // offsets 0-1
        log.append(makeBatch(2)); // offsets 2-3

        long lowWatermark = log.truncateBefore(4);
        assertThat(lowWatermark).isEqualTo(4); // returns nextOffset when all batches removed
        assertThat(log.fetch(0, 1048576)).isEmpty();
    }

    @Test
    void testTruncateBeforeNone() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatch(3)); // offsets 0-2

        long lowWatermark = log.truncateBefore(0);
        assertThat(lowWatermark).isZero();
        assertThat(log.fetch(0, 1048576)).isNotEmpty();
    }

    private byte[] makeBatchWithKeyValue(String key, String value) {
        Record record = new Record(
                0, 0L,
                key != null ? key.getBytes() : null,
                value != null ? value.getBytes() : null,
                List.of());
        return new RecordBatch()
                .baseOffset(0)
                .lastOffsetDelta(0)
                .baseTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(List.of(record))
                .encode();
    }

    @Test
    void testCompactDeduplicatesByKey() {
        var log = new PartitionLog("test", 0);
        // Append 3 records with the same key "k", different values
        log.append(makeBatchWithKeyValue("k", "v1"));
        log.append(makeBatchWithKeyValue("k", "v2"));
        log.append(makeBatchWithKeyValue("k", "v3"));
        assertThat(log.highWatermark()).isEqualTo(3);

        int removed = log.compact();
        assertThat(removed).isEqualTo(2);
        assertThat(log.highWatermark()).isEqualTo(3); // unchanged

        // Fetch and verify only latest value retained
        byte[] fetched = log.fetch(0, 1048576);
        assertThat(fetched).isNotEmpty();
        RecordBatch batch = RecordBatch.decode(fetched);
        assertThat(batch.records()).hasSize(1);
        assertThat(batch.records().getFirst().value()).isEqualTo("v3".getBytes());
    }

    @Test
    void testCompactTombstoneRemovesKey() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatchWithKeyValue("k", "v1"));
        log.append(makeBatchWithKeyValue("k", null)); // tombstone
        assertThat(log.highWatermark()).isEqualTo(2);

        int removed = log.compact();
        assertThat(removed).isEqualTo(2); // both removed (key entry deleted by tombstone)
        assertThat(log.highWatermark()).isEqualTo(2); // unchanged

        byte[] fetched = log.fetch(0, 1048576);
        assertThat(fetched).isEmpty();
    }

    @Test
    void testCompactRetainsNullKeyRecords() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatchWithKeyValue(null, "v1"));
        log.append(makeBatchWithKeyValue(null, "v2"));
        log.append(makeBatchWithKeyValue(null, "v3"));
        assertThat(log.highWatermark()).isEqualTo(3);

        int removed = log.compact();
        assertThat(removed).isZero(); // all null-key records kept
    }

    @Test
    void testCompactEmptyLog() {
        var log = new PartitionLog("test", 0);
        int removed = log.compact();
        assertThat(removed).isZero();
    }

    @Test
    void testCompactNoCompactionNeeded() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatchWithKeyValue("k1", "v1"));
        log.append(makeBatchWithKeyValue("k2", "v2"));
        log.append(makeBatchWithKeyValue("k3", "v3"));

        int removed = log.compact();
        assertThat(removed).isZero();
    }

    @Test
    void testCompactPreservesHighWatermark() {
        var log = new PartitionLog("test", 0);
        log.append(makeBatchWithKeyValue("k", "v1"));
        log.append(makeBatchWithKeyValue("k", "v2"));
        log.append(makeBatchWithKeyValue("other", "val"));
        long hwBefore = log.highWatermark();

        log.compact();

        assertThat(log.highWatermark()).isEqualTo(hwBefore);
    }

    @Test
    void testConcurrentAppends() throws Exception {
        var log = new PartitionLog("test", 0);
        int threads = 10;
        int recordsPerThread = 100;
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var latch = new java.util.concurrent.CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    log.append(makeBatch(1));
                }
                latch.countDown();
            });
        }
        latch.await();
        executor.close();

        assertThat(log.highWatermark()).isEqualTo(threads * recordsPerThread);
    }
}
