package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.broker.storage.InMemoryLogStorage;
import ssg.legoflow.messaging.kafka.broker.storage.LogStorage;
import ssg.legoflow.messaging.kafka.broker.storage.StoredBatch;
import ssg.legoflow.messaging.kafka.record.Record;
import ssg.legoflow.messaging.kafka.record.RecordBatch;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Append-only log for a single partition.
 *
 * <p>Stores record batches sequentially and tracks the high watermark (next offset).
 * Thread-safe via read-write locking. Delegates all batch storage operations to a
 * {@link LogStorage} backend.
 *
 * <p>The default constructor uses {@link InMemoryLogStorage} (volatile, in-memory).
 * To use durable storage, pass a {@link LogStorage} instance to the three-argument constructor.
 *
 * @since 0.1.0
 */
public final class PartitionLog {

    private final String topic;
    private final int partition;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LogStorage storage;
    private long nextOffset = 0;

    /**
     * Creates a new partition log with the given storage backend.
     *
     * @param topic     the topic name
     * @param partition the partition index
     * @param storage   the storage backend for batch persistence
     */
    public PartitionLog(String topic, int partition, LogStorage storage) {
        this.topic = topic;
        this.partition = partition;
        this.storage = storage;
        // Recover nextOffset from existing storage
        if (!storage.isEmpty()) {
            List<StoredBatch> all = storage.allBatches();
            StoredBatch last = all.getLast();
            this.nextOffset = last.baseOffset() + last.recordCount();
        }
    }

    /**
     * Creates a new partition log with in-memory storage (default).
     *
     * @param topic     the topic name
     * @param partition the partition index
     */
    public PartitionLog(String topic, int partition) {
        this(topic, partition, new InMemoryLogStorage());
    }

    /**
     * Appends a record batch and returns the base offset assigned.
     *
     * @param batchData the raw record batch bytes
     * @return the base offset of the appended batch
     */
    public long append(byte[] batchData) {
        lock.writeLock().lock();
        try {
            long baseOffset = nextOffset;
            // Decode to get record count and adjust offsets
            RecordBatch batch = RecordBatch.decode(batchData);
            int recordCount = batch.records().size();

            // Re-encode with correct base offset
            batch.baseOffset(baseOffset);
            batch.lastOffsetDelta(recordCount - 1);
            byte[] correctedData = batch.encode();

            storage.append(baseOffset, recordCount, correctedData, System.currentTimeMillis());
            nextOffset += recordCount;
            return baseOffset;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Fetches record batches starting from the given offset.
     *
     * @param fetchOffset the offset to start fetching from
     * @param maxBytes    the maximum bytes to return
     * @return the raw bytes of matching record batches
     */
    public byte[] fetch(long fetchOffset, int maxBytes) {
        lock.readLock().lock();
        try {
            List<StoredBatch> batches = storage.fetch(fetchOffset, maxBytes);
            var out = new java.io.ByteArrayOutputStream();
            for (StoredBatch sb : batches) {
                out.writeBytes(sb.data());
            }
            return out.toByteArray();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the high watermark (next offset to be assigned).
     *
     * @return the high watermark
     */
    public long highWatermark() {
        lock.readLock().lock();
        try {
            return nextOffset;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the earliest available offset.
     *
     * @return the earliest offset (0 if log is empty or contains data from offset 0)
     */
    public long earliestOffset() {
        lock.readLock().lock();
        try {
            if (storage.isEmpty()) return 0;
            long earliest = storage.earliestOffset();
            return earliest >= 0 ? earliest : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds the offset for a given timestamp.
     *
     * @param timestamp the target timestamp (-1=latest, -2=earliest)
     * @return the offset
     */
    public long offsetForTimestamp(long timestamp) {
        if (timestamp == -1L) return highWatermark();
        if (timestamp == -2L) return earliestOffset();
        lock.readLock().lock();
        try {
            for (StoredBatch sb : storage.allBatches()) {
                if (sb.timestamp() >= timestamp) return sb.baseOffset();
            }
            return nextOffset;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Truncates (removes) all record batches whose records are entirely before the given offset.
     * Returns the new low watermark (earliest available offset after truncation).
     *
     * @param offset the offset before which records should be deleted
     * @return the new low watermark
     */
    public long truncateBefore(long offset) {
        lock.writeLock().lock();
        try {
            storage.truncateBefore(offset);
            if (storage.isEmpty()) return nextOffset;
            long earliest = storage.earliestOffset();
            return earliest >= 0 ? earliest : nextOffset;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Compacts the partition log by deduplicating records by key.
     * For each key, only the record with the highest offset is retained.
     * Tombstones (records with null value) remove the key entry.
     * Records without keys are always retained.
     * High watermark and nextOffset are unchanged.
     *
     * @return the number of records removed during compaction
     */
    public int compact() {
        lock.writeLock().lock();
        try {
            List<StoredBatch> batches = storage.allBatches();
            if (batches.isEmpty()) return 0;

            // 1. Decode all batches and collect records with absolute offsets
            List<Record> nullKeyRecords = new ArrayList<>();
            LinkedHashMap<ByteBuffer, Record> keyedRecords = new LinkedHashMap<>();
            int totalOriginal = 0;

            for (StoredBatch sb : batches) {
                RecordBatch batch = RecordBatch.decode(sb.data());
                for (Record rec : batch.records()) {
                    totalOriginal++;
                    long absoluteOffset = sb.baseOffset() + rec.offsetDelta();

                    if (rec.key() == null) {
                        // Null-key records are always kept, preserve their absolute offset
                        nullKeyRecords.add(new Record((int) absoluteOffset, rec.timestampDelta(),
                                rec.key(), rec.value(), rec.headers()));
                    } else {
                        ByteBuffer keyBuf = ByteBuffer.wrap(rec.key());
                        if (rec.value() == null) {
                            // Tombstone: remove the key
                            keyedRecords.remove(keyBuf);
                        } else {
                            // Keep latest: store with absolute offset
                            keyedRecords.put(keyBuf, new Record((int) absoluteOffset, rec.timestampDelta(),
                                    rec.key(), rec.value(), rec.headers()));
                        }
                    }
                }
            }

            // 2. Combine surviving records sorted by their absolute offset
            List<Record> surviving = new ArrayList<>(nullKeyRecords.size() + keyedRecords.size());
            surviving.addAll(nullKeyRecords);
            surviving.addAll(keyedRecords.values());
            surviving.sort((a, b) -> Integer.compare(a.offsetDelta(), b.offsetDelta()));

            int removed = totalOriginal - surviving.size();
            if (removed == 0) return 0;

            // 3. Re-encode into a single batch if there are surviving records
            List<StoredBatch> newBatches = new ArrayList<>();
            if (!surviving.isEmpty()) {
                long baseOffset = surviving.getFirst().offsetDelta();
                List<Record> batchRecords = new ArrayList<>(surviving.size());
                for (Record rec : surviving) {
                    batchRecords.add(new Record(
                            (int) (rec.offsetDelta() - baseOffset),
                            rec.timestampDelta(), rec.key(), rec.value(), rec.headers()));
                }

                RecordBatch newBatch = new RecordBatch()
                        .baseOffset(baseOffset)
                        .lastOffsetDelta(batchRecords.getLast().offsetDelta())
                        .baseTimestamp(System.currentTimeMillis())
                        .maxTimestamp(System.currentTimeMillis())
                        .records(batchRecords);

                byte[] encoded = newBatch.encode();
                newBatches.add(new StoredBatch(baseOffset, surviving.size(), encoded, System.currentTimeMillis()));
            }

            // 4. Replace internal batch list
            storage.replaceBatches(newBatches);

            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the topic name.
     *
     * @return the topic name
     */
    public String topic() { return topic; }

    /**
     * Returns the partition index.
     *
     * @return the partition index
     */
    public int partition() { return partition; }
}
