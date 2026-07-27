package ssg.legoflow.http3.qpack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * QPACK dynamic table as defined in RFC 9204.
 *
 * <p>Implements a FIFO ring buffer of header field entries with capacity-based
 * eviction. Unlike HPACK's dynamic table, QPACK supports reference counting
 * per stream to handle blocked streams safely.</p>
 *
 * <p>QPACK uses absolute indices (starting at 0 for the first inserted entry
 * and incrementing with each insertion) and relative indices (0 = most recently
 * inserted). This table supports both addressing modes. The Required Insert Count
 * and Known Received Count track synchronisation between encoder and decoder.</p>
 *
 * <p>This class is thread-safe. All operations are guarded by a read-write lock.</p>
 *
 * @since 1.0.0
 */
public class QpackDynamicTable {

    private static final int ENTRY_OVERHEAD = 32;

    private final Deque<QpackStaticTable.Entry> entries = new ArrayDeque<>();
    private final Map<Long, Integer> streamReferences = new ConcurrentHashMap<>();
    private final Set<Long> acknowledgedStreams = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int maxCapacity;
    private int currentSize;
    private int insertCount;
    private int knownReceivedCount;
    private int droppedCount;

    /**
     * Creates a new dynamic table with the given maximum capacity.
     *
     * @param maxCapacity the maximum table capacity in bytes
     * @since 1.0.0
     */
    public QpackDynamicTable(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    /**
     * Inserts a new header field entry into the table.
     *
     * <p>Entries that exceed the capacity trigger eviction of the oldest
     * entries. If the entry itself is larger than the capacity, the table
     * is cleared but the entry is not added.</p>
     *
     * @param name  the header field name
     * @param value the header field value
     * @since 1.0.0
     */
    public void insert(String name, String value) {
        lock.writeLock().lock();
        try {
            int entrySize = name.length() + value.length() + ENTRY_OVERHEAD;
            while (currentSize + entrySize > maxCapacity && !entries.isEmpty()) {
                evict();
            }
            if (entrySize <= maxCapacity) {
                entries.addFirst(new QpackStaticTable.Entry(name, value));
                currentSize += entrySize;
                insertCount++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the entry at the given relative index.
     *
     * <p>Index 0 refers to the most recently inserted entry.</p>
     *
     * @param index the zero-based relative index
     * @return the entry at the given index
     * @throws IllegalArgumentException if the index is out of range
     * @since 1.0.0
     */
    public QpackStaticTable.Entry getEntry(int index) {
        lock.readLock().lock();
        try {
            if (index < 0 || index >= entries.size()) {
                throw new IllegalArgumentException("Invalid dynamic table index: " + index);
            }
            int i = 0;
            for (var entry : entries) {
                if (i == index) return entry;
                i++;
            }
            throw new IllegalStateException("Unreachable");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of entries currently in the table.
     *
     * @return the entry count
     * @since 1.0.0
     */
    public int size() {
        lock.readLock().lock();
        try {
            return entries.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current byte size of the table.
     *
     * @return the current size in bytes
     * @since 1.0.0
     */
    public int capacity() {
        lock.readLock().lock();
        try {
            return currentSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the maximum capacity of the table.
     *
     * @return the maximum capacity in bytes
     * @since 1.0.0
     */
    public int maxCapacity() {
        return maxCapacity;
    }

    /**
     * Sets the maximum capacity of the table, evicting entries as needed.
     *
     * @param newCapacity the new maximum capacity in bytes
     * @since 1.0.0
     */
    public void setCapacity(int newCapacity) {
        lock.writeLock().lock();
        try {
            this.maxCapacity = newCapacity;
            while (currentSize > maxCapacity && !entries.isEmpty()) {
                evict();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a reference for the given stream ID, incrementing its reference count.
     *
     * @param streamId the QUIC stream ID
     * @since 1.0.0
     */
    public void addStreamReference(long streamId) {
        streamReferences.merge(streamId, 1, Integer::sum);
    }

    /**
     * Removes a reference for the given stream ID, decrementing its reference count.
     *
     * @param streamId the QUIC stream ID
     * @since 1.0.0
     */
    public void removeStreamReference(long streamId) {
        streamReferences.computeIfPresent(streamId, (k, v) -> v <= 1 ? null : v - 1);
    }

    /**
     * Returns the reference count for the given stream ID.
     *
     * @param streamId the QUIC stream ID
     * @return the reference count, or 0 if no references exist
     * @since 1.0.0
     */
    public int getStreamReferenceCount(long streamId) {
        return streamReferences.getOrDefault(streamId, 0);
    }

    /**
     * Returns the total number of active stream references.
     *
     * @return the number of streams with active references
     * @since 1.0.0
     */
    public int getTotalStreamReferences() {
        return streamReferences.size();
    }

    /**
     * Returns the total number of entries inserted since creation.
     *
     * @return the insert count
     * @since 1.0.0
     */
    public int getInsertCount() {
        lock.readLock().lock();
        try {
            return insertCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds the relative index of an entry matching both name and value.
     *
     * @param name  the header field name
     * @param value the header field value
     * @return the relative index, or {@code -1} if not found
     * @since 1.0.0
     */
    public int findEntry(String name, String value) {
        lock.readLock().lock();
        try {
            int i = 0;
            for (var entry : entries) {
                if (entry.name().equals(name) && entry.value().equals(value)) {
                    return i;
                }
                i++;
            }
            return -1;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds the relative index of the first entry matching the given name.
     *
     * @param name the header field name
     * @return the relative index, or {@code -1} if not found
     * @since 1.0.0
     */
    public int findNameIndex(String name) {
        lock.readLock().lock();
        try {
            int i = 0;
            for (var entry : entries) {
                if (entry.name().equals(name)) {
                    return i;
                }
                i++;
            }
            return -1;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all entries from the table.
     *
     * @since 1.0.0
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            entries.clear();
            currentSize = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Inserts a new entry using a name reference from the static table.
     *
     * <p>This corresponds to the QPACK encoder instruction "Insert With Name Reference"
     * (RFC 9204 section 4.3.2) with the static table bit set.</p>
     *
     * @param staticIndex the static table index to reference for the name
     * @param value       the header field value
     * @throws IllegalArgumentException if the static index is out of range
     * @since 1.0.0
     */
    public void insertWithStaticNameReference(int staticIndex, String value) {
        var entry = QpackStaticTable.getEntry(staticIndex);
        insert(entry.name(), value);
    }

    /**
     * Inserts a new entry using a name reference from the dynamic table.
     *
     * <p>This corresponds to the QPACK encoder instruction "Insert With Name Reference"
     * (RFC 9204 section 4.3.2) with the dynamic table bit set.</p>
     *
     * @param relativeIndex the relative index in the dynamic table
     * @param value         the header field value
     * @throws IllegalArgumentException if the index is out of range
     * @since 1.0.0
     */
    public void insertWithDynamicNameReference(int relativeIndex, String value) {
        var existingEntry = getEntry(relativeIndex);
        insert(existingEntry.name(), value);
    }

    /**
     * Duplicates an existing dynamic table entry.
     *
     * <p>This corresponds to the QPACK encoder instruction "Duplicate"
     * (RFC 9204 section 4.3.4). It re-inserts a copy of the entry at the
     * given relative index as the newest entry, which is useful to prevent
     * eviction of frequently used entries.</p>
     *
     * @param relativeIndex the relative index of the entry to duplicate
     * @throws IllegalArgumentException if the index is out of range
     * @since 1.0.0
     */
    public void duplicate(int relativeIndex) {
        var entry = getEntry(relativeIndex);
        insert(entry.name(), entry.value());
    }

    /**
     * Returns the entry at the given absolute index.
     *
     * <p>Absolute index 0 is the first entry ever inserted. Absolute index
     * {@code insertCount - 1} is the most recently inserted entry.</p>
     *
     * @param absoluteIndex the absolute insertion index
     * @return the entry at that absolute index
     * @throws IllegalArgumentException if the index is out of range
     * @since 1.0.0
     */
    public QpackStaticTable.Entry getEntryAbsolute(int absoluteIndex) {
        lock.readLock().lock();
        try {
            int relativeIndex = toRelativeIndex(absoluteIndex);
            return getEntryUnlocked(relativeIndex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the entry at the given post-base index.
     *
     * <p>Post-base indexing is used when the Required Insert Count encoded in
     * the header block prefix is less than the actual insert count. The post-base
     * index {@code 0} refers to the first entry after the base.</p>
     *
     * @param postBaseIndex the post-base index
     * @param base          the base value (Required Insert Count + Delta Base)
     * @return the entry
     * @throws IllegalArgumentException if the index is out of range
     * @since 1.0.0
     */
    public QpackStaticTable.Entry getEntryPostBase(int postBaseIndex, int base) {
        lock.readLock().lock();
        try {
            int absoluteIndex = base + postBaseIndex;
            int relativeIndex = toRelativeIndex(absoluteIndex);
            return getEntryUnlocked(relativeIndex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Acknowledges processing of a header block for the given stream.
     *
     * <p>This corresponds to the QPACK decoder instruction "Section Acknowledgment"
     * (RFC 9204 section 4.4.1). It signals to the encoder that the decoder has
     * successfully processed a header block that referenced dynamic table entries.</p>
     *
     * @param streamId the QUIC stream ID that was processed
     * @since 1.0.0
     */
    public void acknowledgeSectionForStream(long streamId) {
        acknowledgedStreams.add(streamId);
    }

    /**
     * Cancels a stream, removing any pending references.
     *
     * <p>This corresponds to the QPACK decoder instruction "Stream Cancellation"
     * (RFC 9204 section 4.4.2).</p>
     *
     * @param streamId the QUIC stream ID to cancel
     * @since 1.0.0
     */
    public void cancelStream(long streamId) {
        streamReferences.remove(streamId);
        acknowledgedStreams.remove(streamId);
    }

    /**
     * Increments the Known Received Count by the given delta.
     *
     * <p>This corresponds to the QPACK decoder instruction "Insert Count Increment"
     * (RFC 9204 section 4.4.3). The decoder sends this to the encoder to inform it
     * that new dynamic table entries have been received and can be referenced.</p>
     *
     * @param increment the number of new entries received
     * @throws IllegalArgumentException if increment is negative or would exceed insert count
     * @since 1.0.0
     */
    public void incrementKnownReceivedCount(int increment) {
        if (increment < 0) {
            throw new IllegalArgumentException("Increment must be non-negative: " + increment);
        }
        lock.writeLock().lock();
        try {
            int newCount = knownReceivedCount + increment;
            if (newCount > insertCount) {
                throw new IllegalArgumentException(
                        "Known received count " + newCount + " would exceed insert count " + insertCount);
            }
            knownReceivedCount = newCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the Known Received Count — the number of dynamic table entries
     * the decoder has confirmed receiving.
     *
     * @return the known received count
     * @since 1.0.0
     */
    public int getKnownReceivedCount() {
        lock.readLock().lock();
        try {
            return knownReceivedCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns whether the given stream has been acknowledged.
     *
     * @param streamId the QUIC stream ID
     * @return {@code true} if the stream's header block was acknowledged
     * @since 1.0.0
     */
    public boolean isStreamAcknowledged(long streamId) {
        return acknowledgedStreams.contains(streamId);
    }

    /**
     * Computes the Required Insert Count for encoding, which is the insert count
     * needed by the decoder to decode the header block.
     *
     * <p>If no dynamic table references are used, the Required Insert Count is 0.
     * Otherwise, it is the maximum absolute index referenced plus 1.</p>
     *
     * @param maxAbsoluteIndex the maximum absolute index referenced, or -1 if none
     * @return the Required Insert Count value
     * @since 1.0.0
     */
    public int computeRequiredInsertCount(int maxAbsoluteIndex) {
        return maxAbsoluteIndex < 0 ? 0 : maxAbsoluteIndex + 1;
    }

    /**
     * Encodes the Required Insert Count for the header block prefix per RFC 9204 section 4.5.1.
     *
     * @param requiredInsertCount the required insert count
     * @return the encoded value for the wire
     * @since 1.0.0
     */
    public int encodeRequiredInsertCount(int requiredInsertCount) {
        if (requiredInsertCount == 0) {
            return 0;
        }
        int maxEntries = maxCapacity > 0 ? maxCapacity / ENTRY_OVERHEAD : 0;
        if (maxEntries == 0) {
            return requiredInsertCount;
        }
        return (requiredInsertCount % (2 * maxEntries)) + 1;
    }

    /**
     * Decodes the Required Insert Count from the header block prefix per RFC 9204 section 4.5.1.
     *
     * @param encodedValue the encoded value from the wire
     * @return the decoded Required Insert Count
     * @since 1.0.0
     */
    public int decodeRequiredInsertCount(int encodedValue) {
        if (encodedValue == 0) {
            return 0;
        }
        int maxEntries = maxCapacity > 0 ? maxCapacity / ENTRY_OVERHEAD : 0;
        if (maxEntries == 0) {
            return encodedValue;
        }
        int fullRange = 2 * maxEntries;
        int totalInsertCount = insertCount;
        if (encodedValue - 1 >= fullRange) {
            throw new IllegalArgumentException("Invalid encoded Required Insert Count: " + encodedValue);
        }
        int maxValue = totalInsertCount + maxEntries;
        int maxWrapped = (maxValue / fullRange) * fullRange;
        int reqInsCount = maxWrapped + encodedValue - 1;
        if (reqInsCount > maxValue) {
            if (reqInsCount <= fullRange) {
                throw new IllegalArgumentException("Invalid Required Insert Count");
            }
            reqInsCount -= fullRange;
        }
        if (reqInsCount == 0) {
            throw new IllegalArgumentException("Invalid Required Insert Count");
        }
        return reqInsCount;
    }

    /**
     * Returns the number of entries that have been evicted (dropped) since creation.
     *
     * @return the dropped entry count
     * @since 1.0.0
     */
    public int getDroppedCount() {
        lock.readLock().lock();
        try {
            return droppedCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    private int toRelativeIndex(int absoluteIndex) {
        int adjustedAbsolute = absoluteIndex - droppedCount;
        if (adjustedAbsolute < 0 || adjustedAbsolute >= entries.size()) {
            throw new IllegalArgumentException(
                    "Absolute index " + absoluteIndex + " is out of range (dropped=" + droppedCount
                            + ", size=" + entries.size() + ")");
        }
        return entries.size() - 1 - adjustedAbsolute;
    }

    private QpackStaticTable.Entry getEntryUnlocked(int relativeIndex) {
        if (relativeIndex < 0 || relativeIndex >= entries.size()) {
            throw new IllegalArgumentException("Invalid dynamic table index: " + relativeIndex);
        }
        int i = 0;
        for (var entry : entries) {
            if (i == relativeIndex) return entry;
            i++;
        }
        throw new IllegalStateException("Unreachable");
    }

    private void evict() {
        var last = entries.removeLast();
        currentSize -= (last.name().length() + last.value().length() + ENTRY_OVERHEAD);
        droppedCount++;
    }
}
