package ssg.legoflow.messaging.kafka.broker.storage;

import java.util.List;

/**
 * Storage backend for a single partition log.
 *
 * <p>Defines the contract for batch storage operations that {@code PartitionLog} delegates to.
 * Implementations are <strong>not</strong> required to be thread-safe; thread safety is provided
 * by the calling {@code PartitionLog} via its {@code ReentrantReadWriteLock}.
 *
 * <p>Two built-in implementations are provided:
 * <ul>
 *   <li>{@link InMemoryLogStorage} — in-memory {@code ArrayList}-backed (default, volatile)</li>
 *   <li>{@link MappedFileLogStorage} — memory-mapped file-backed (durable, segment-based)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public interface LogStorage extends AutoCloseable {

    /**
     * Appends a batch to the log.
     *
     * @param baseOffset  the base offset assigned to the batch
     * @param recordCount the number of records in the batch
     * @param data        the raw Kafka v2 encoded batch bytes
     * @param timestamp   the wall-clock time of the append (milliseconds since epoch)
     */
    void append(long baseOffset, int recordCount, byte[] data, long timestamp);

    /**
     * Reads batches starting from the given fetch offset, up to a byte limit.
     *
     * <p>Returns batches whose records overlap with or follow the fetch offset.
     * At least one batch is always returned if any batch matches, even if it
     * exceeds {@code maxBytes}.
     *
     * @param fetchOffset the offset to start fetching from
     * @param maxBytes    the maximum total bytes to return
     * @return the list of matching stored batches (may be empty)
     */
    List<StoredBatch> fetch(long fetchOffset, int maxBytes);

    /**
     * Returns all batches in the log as a mutable list.
     *
     * <p>Used by compaction to read all records for deduplication.
     *
     * @return all stored batches
     */
    List<StoredBatch> allBatches();

    /**
     * Replaces all batches in the log with the given list.
     *
     * <p>Used after compaction to swap in the deduplicated result.
     *
     * @param newBatches the replacement batches
     */
    void replaceBatches(List<StoredBatch> newBatches);

    /**
     * Removes all batches whose records are entirely before the given offset.
     *
     * <p>A batch is removed if {@code baseOffset + recordCount <= offset}.
     *
     * @param offset the offset before which records should be deleted
     */
    void truncateBefore(long offset);

    /**
     * Checks whether the log contains any batches.
     *
     * @return {@code true} if the log is empty
     */
    boolean isEmpty();

    /**
     * Returns the base offset of the earliest batch, or {@code -1} if the log is empty.
     *
     * @return the earliest offset, or {@code -1}
     */
    long earliestOffset();

    /**
     * Returns the number of batches in the log.
     *
     * @return the batch count
     */
    int size();

    /**
     * Closes this storage and releases any underlying resources.
     *
     * <p>For in-memory storage this is a no-op. For file-backed storage this
     * closes file channels and releases mapped buffers.
     */
    @Override
    void close();
}
