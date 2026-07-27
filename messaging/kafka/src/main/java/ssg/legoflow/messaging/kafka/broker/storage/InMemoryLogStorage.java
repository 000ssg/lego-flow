package ssg.legoflow.messaging.kafka.broker.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link LogStorage} implementation backed by an {@code ArrayList}.
 *
 * <p>This is the default storage backend. All data is volatile and lost on close.
 * Provides the same behavior as the original {@code PartitionLog} internal storage.
 *
 * <p>Not thread-safe. Thread safety is provided by the calling {@code PartitionLog}.
 *
 * @since 1.0.0
 */
public final class InMemoryLogStorage implements LogStorage {

    private final List<StoredBatch> batches = new ArrayList<>();

    /**
     * Creates a new in-memory log storage.
     */
    public InMemoryLogStorage() {
    }

    @Override
    public void append(long baseOffset, int recordCount, byte[] data, long timestamp) {
        batches.add(new StoredBatch(baseOffset, recordCount, data, timestamp));
    }

    @Override
    public List<StoredBatch> fetch(long fetchOffset, int maxBytes) {
        List<StoredBatch> result = new ArrayList<>();
        int totalBytes = 0;
        for (StoredBatch sb : batches) {
            long batchEndOffset = sb.baseOffset() + sb.recordCount();
            if (batchEndOffset <= fetchOffset) continue;
            if (totalBytes + sb.data().length > maxBytes && totalBytes > 0) break;
            result.add(sb);
            totalBytes += sb.data().length;
        }
        return result;
    }

    @Override
    public List<StoredBatch> allBatches() {
        return batches;
    }

    @Override
    public void replaceBatches(List<StoredBatch> newBatches) {
        batches.clear();
        batches.addAll(newBatches);
    }

    @Override
    public void truncateBefore(long offset) {
        batches.removeIf(sb -> sb.baseOffset() + sb.recordCount() <= offset);
    }

    @Override
    public boolean isEmpty() {
        return batches.isEmpty();
    }

    @Override
    public long earliestOffset() {
        return batches.isEmpty() ? -1 : batches.getFirst().baseOffset();
    }

    @Override
    public int size() {
        return batches.size();
    }

    @Override
    public void close() {
        // no-op for in-memory storage
    }
}
