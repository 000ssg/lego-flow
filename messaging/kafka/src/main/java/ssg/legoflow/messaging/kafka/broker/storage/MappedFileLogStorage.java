package ssg.legoflow.messaging.kafka.broker.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
/**
 * Memory-mapped file {@link LogStorage} implementation with segment-based storage.
 *
 * <p>Uses memory-mapped files for zero-copy reads and OS-managed dirty page writeback.
 * This is optimal for Kafka's access pattern of sequential append and mostly-sequential read.
 *
 * <h2>File Layout</h2>
 * <pre>
 * &lt;logDir&gt;/
 *   segment-&lt;baseOffset&gt;.log    -- segment data files
 *   index.dat                    -- sparse offset-to-position index
 * </pre>
 *
 * <h2>Segment Format</h2>
 * <p>Each segment file stores batches sequentially. Each batch entry is:
 * <pre>
 *   entryLength:4  baseOffset:8  recordCount:4  timestamp:8  dataLen:4  data:N
 * </pre>
 * The {@code entryLength} is {@code 8 + 4 + 8 + 4 + dataLen} (everything after the length prefix).
 *
 * <h2>Index Format</h2>
 * <p>Sparse index with one entry per {@code indexIntervalBytes} of segment data:
 * <pre>
 *   offset:8  segmentBaseOffset:8  positionInSegment:4
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Not thread-safe. Thread safety is provided by the calling {@code PartitionLog}.
 *
 * @since 0.1.0
 */
public final class MappedFileLogStorage implements LogStorage {

    private static final Logger LOG = LoggerFactory.getLogger(MappedFileLogStorage.class);

    /** Default segment size: 1 GB (matching real Kafka). */
    public static final long DEFAULT_SEGMENT_BYTES = 1L * 1024 * 1024 * 1024;

    /** Default index interval: one index entry per 4 KB of segment data. */
    static final int DEFAULT_INDEX_INTERVAL_BYTES = 4096;

    /** Entry header size: baseOffset(8) + recordCount(4) + timestamp(8) + dataLen(4) = 24. */
    private static final int ENTRY_HEADER_SIZE = 24;

    /** Index entry size: offset(8) + segmentBaseOffset(8) + positionInSegment(4) = 20. */

    private final Path logDir;
    private final long segmentBytes;
    private final int indexIntervalBytes;

    /** All segments ordered by base offset. */
    private final List<Segment> segments = new ArrayList<>();

    /** Sparse index entries ordered by offset. */
    private final List<IndexEntry> index = new ArrayList<>();

    /** Bytes written to the active (last) segment since the last index entry. */
    private int bytesSinceLastIndex;

    /**
     * Creates a new mapped-file log storage.
     *
     * <p>On construction, any existing segment files in {@code logDir} are scanned
     * to rebuild the in-memory index and recover state.
     *
     * @param logDir       the directory for segment and index files
     * @param segmentBytes the maximum size of a single segment file in bytes
     * @throws UncheckedIOException if the directory cannot be created or existing files cannot be read
     */
    public MappedFileLogStorage(Path logDir, long segmentBytes) {
        this.logDir = logDir;
        this.segmentBytes = segmentBytes;
        this.indexIntervalBytes = DEFAULT_INDEX_INTERVAL_BYTES;
        try {
            Files.createDirectories(logDir);
            recover();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize log storage at " + logDir, e);
        }
    }

    /**
     * Creates a new mapped-file log storage with the default segment size.
     *
     * @param logDir the directory for segment and index files
     * @throws UncheckedIOException if the directory cannot be created or existing files cannot be read
     */
    public MappedFileLogStorage(Path logDir) {
        this(logDir, DEFAULT_SEGMENT_BYTES);
    }

    @Override
    public void append(long baseOffset, int recordCount, byte[] data, long timestamp) {
        int entrySize = ENTRY_HEADER_SIZE + data.length;

        // Roll to a new segment if needed
        if (segments.isEmpty() || activeSegment().position + entrySize > segmentBytes) {
            rollNewSegment(baseOffset);
        }

        Segment active = activeSegment();
        int position = active.position;

        // Write entry: entryLength(4) + baseOffset(8) + recordCount(4) + timestamp(8) + dataLen(4) + data
        ensureCapacity(active, entrySize + 4);
        ByteBuffer buf = active.buffer;
        buf.position(position);
        buf.putInt(ENTRY_HEADER_SIZE + data.length); // entryLength
        buf.putLong(baseOffset);
        buf.putInt(recordCount);
        buf.putLong(timestamp);
        buf.putInt(data.length);
        buf.put(data);

        active.position = buf.position();
        active.batchCount++;

        // Update sparse index
        bytesSinceLastIndex += entrySize + 4;
        if (bytesSinceLastIndex >= indexIntervalBytes) {
            index.add(new IndexEntry(baseOffset, active.baseOffset, position));
            bytesSinceLastIndex = 0;
        }
    }

    @Override
    public List<StoredBatch> fetch(long fetchOffset, int maxBytes) {
        List<StoredBatch> result = new ArrayList<>();
        int totalBytes = 0;

        // Find the starting segment and position using the index
        int segIdx = 0;
        int posInSeg = 0;
        if (!index.isEmpty()) {
            // Binary search for the highest index entry <= fetchOffset
            int lo = 0, hi = index.size() - 1;
            int best = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (index.get(mid).offset <= fetchOffset) {
                    best = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (best >= 0) {
                IndexEntry ie = index.get(best);
                // Find the segment with this base offset
                for (int i = 0; i < segments.size(); i++) {
                    if (segments.get(i).baseOffset == ie.segmentBaseOffset) {
                        segIdx = i;
                        posInSeg = ie.positionInSegment;
                        break;
                    }
                }
            }
        }

        // Scan from the found position
        for (int si = segIdx; si < segments.size(); si++) {
            Segment seg = segments.get(si);
            ByteBuffer buf = seg.buffer;
            int pos = (si == segIdx) ? posInSeg : 0;

            while (pos < seg.position) {
                buf.position(pos);
                if (buf.remaining() < 4) break;
                int entryLength = buf.getInt();
                if (entryLength <= 0 || pos + 4 + entryLength > seg.position) break;

                long batchBaseOffset = buf.getLong();
                int recordCount = buf.getInt();
                long timestamp = buf.getLong();
                int dataLen = buf.getInt();
                long batchEndOffset = batchBaseOffset + recordCount;

                if (batchEndOffset <= fetchOffset) {
                    pos += 4 + entryLength;
                    continue;
                }

                byte[] data = new byte[dataLen];
                buf.get(data);

                if (totalBytes + data.length > maxBytes && totalBytes > 0) {
                    return result;
                }

                result.add(new StoredBatch(batchBaseOffset, recordCount, data, timestamp));
                totalBytes += data.length;
                pos += 4 + entryLength;
            }
        }
        return result;
    }

    @Override
    public List<StoredBatch> allBatches() {
        List<StoredBatch> all = new ArrayList<>();
        for (Segment seg : segments) {
            readAllFromSegment(seg, all);
        }
        return all;
    }

    @Override
    public void replaceBatches(List<StoredBatch> newBatches) {
        // Close and delete all existing segments
        for (Segment seg : segments) {
            closeSegment(seg);
            try {
                Files.deleteIfExists(seg.path);
            } catch (IOException e) {
                LOG.warn("Failed to delete segment file: {}", seg.path, e);
            }
        }
        segments.clear();
        index.clear();
        bytesSinceLastIndex = 0;

        // Re-append all new batches
        for (StoredBatch sb : newBatches) {
            append(sb.baseOffset(), sb.recordCount(), sb.data(), sb.timestamp());
        }
    }

    @Override
    public void truncateBefore(long offset) {
        // Remove fully-covered segments
        List<Segment> toRemove = new ArrayList<>();
        for (Segment seg : segments) {
            // Check if all batches in this segment are before offset
            if (segmentFullyBefore(seg, offset)) {
                toRemove.add(seg);
            }
        }
        for (Segment seg : toRemove) {
            closeSegment(seg);
            try {
                Files.deleteIfExists(seg.path);
            } catch (IOException e) {
                LOG.warn("Failed to delete segment file: {}", seg.path, e);
            }
            segments.remove(seg);
        }

        // For remaining segments, we can't partially truncate a memory-mapped file easily,
        // so we rebuild: read surviving batches, replace the segment
        if (!segments.isEmpty()) {
            Segment first = segments.getFirst();
            List<StoredBatch> surviving = new ArrayList<>();
            readAllFromSegment(first, surviving);
            List<StoredBatch> filtered = surviving.stream()
                    .filter(sb -> sb.baseOffset() + sb.recordCount() > offset)
                    .toList();

            if (filtered.size() < surviving.size()) {
                // Need to rewrite the first segment
                closeSegment(first);
                try {
                    Files.deleteIfExists(first.path);
                } catch (IOException e) {
                    LOG.warn("Failed to delete segment file: {}", first.path, e);
                }
                segments.removeFirst();

                // Rebuild index from scratch
                rebuildIndex();

                // Re-append the surviving batches from the first segment
                for (StoredBatch sb : filtered) {
                    append(sb.baseOffset(), sb.recordCount(), sb.data(), sb.timestamp());
                }
            }
        }

        // Remove stale index entries
        index.removeIf(ie -> ie.offset < offset);
    }

    @Override
    public boolean isEmpty() {
        return segments.isEmpty() || segments.stream().allMatch(s -> s.batchCount == 0);
    }

    @Override
    public long earliestOffset() {
        for (Segment seg : segments) {
            if (seg.position > 0) {
                ByteBuffer buf = seg.buffer;
                buf.position(0);
                if (buf.remaining() >= 4) {
                    int entryLen = buf.getInt(0);
                    if (entryLen > 0) {
                        return buf.getLong(4); // baseOffset field
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public int size() {
        int total = 0;
        for (Segment seg : segments) {
            total += seg.batchCount;
        }
        return total;
    }

    @Override
    public void close() {
        for (Segment seg : segments) {
            closeSegment(seg);
        }
        segments.clear();
        index.clear();
    }

    // --- Internal types ---

    private static final class Segment {
        final Path path;
        final long baseOffset;
        final FileChannel channel;
        MappedByteBuffer buffer;
        int position; // write position in the buffer
        int batchCount;
        long mappedSize;

        Segment(Path path, long baseOffset, FileChannel channel, MappedByteBuffer buffer, long mappedSize) {
            this.path = path;
            this.baseOffset = baseOffset;
            this.channel = channel;
            this.buffer = buffer;
            this.position = 0;
            this.batchCount = 0;
            this.mappedSize = mappedSize;
        }
    }

    private record IndexEntry(long offset, long segmentBaseOffset, int positionInSegment) {
    }

    // --- Segment management ---

    private Segment activeSegment() {
        return segments.getLast();
    }

    private void rollNewSegment(long baseOffset) {
        try {
            Path segPath = logDir.resolve("segment-" + baseOffset + ".log");
            // Initial mapped size: min(segmentBytes, 16MB) to avoid huge initial allocations
            long initialSize = Math.min(segmentBytes, 16L * 1024 * 1024);
            FileChannel channel = FileChannel.open(segPath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, initialSize);
            segments.add(new Segment(segPath, baseOffset, channel, buffer, initialSize));
            bytesSinceLastIndex = 0;
            LOG.debug("Rolled new segment: {} (baseOffset={})", segPath.getFileName(), baseOffset);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create segment at baseOffset " + baseOffset, e);
        }
    }

    private void ensureCapacity(Segment seg, int needed) {
        if (seg.position + needed <= seg.mappedSize) return;
        // Remap with doubled size, capped at segmentBytes
        long newSize = Math.min(segmentBytes, Math.max(seg.mappedSize * 2, seg.position + needed));
        try {
            // Force any pending writes
            seg.buffer.force();
            seg.buffer = seg.channel.map(FileChannel.MapMode.READ_WRITE, 0, newSize);
            seg.mappedSize = newSize;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remap segment", e);
        }
    }

    private void closeSegment(Segment seg) {
        try {
            if (seg.buffer != null) {
                seg.buffer.force();
            }
            seg.channel.close();
        } catch (IOException e) {
            LOG.warn("Error closing segment: {}", seg.path, e);
        }
    }

    private void readAllFromSegment(Segment seg, List<StoredBatch> out) {
        ByteBuffer buf = seg.buffer;
        int pos = 0;
        while (pos < seg.position) {
            buf.position(pos);
            if (buf.remaining() < 4) break;
            int entryLength = buf.getInt();
            if (entryLength <= 0 || pos + 4 + entryLength > seg.position) break;

            long baseOffset = buf.getLong();
            int recordCount = buf.getInt();
            long timestamp = buf.getLong();
            int dataLen = buf.getInt();
            byte[] data = new byte[dataLen];
            buf.get(data);
            out.add(new StoredBatch(baseOffset, recordCount, data, timestamp));
            pos += 4 + entryLength;
        }
    }

    private boolean segmentFullyBefore(Segment seg, long offset) {
        // Scan all batches in segment, check if all are before offset
        ByteBuffer buf = seg.buffer;
        int pos = 0;
        boolean hasAny = false;
        while (pos < seg.position) {
            buf.position(pos);
            if (buf.remaining() < 4) break;
            int entryLength = buf.getInt();
            if (entryLength <= 0 || pos + 4 + entryLength > seg.position) break;

            long baseOff = buf.getLong();
            int recCount = buf.getInt();
            hasAny = true;
            if (baseOff + recCount > offset) {
                return false; // at least one batch survives
            }
            pos += 4 + entryLength;
        }
        return hasAny;
    }

    // --- Recovery ---

    private void recover() throws IOException {
        if (!Files.exists(logDir)) return;

        List<Path> segmentFiles;
        try (var stream = Files.list(logDir)) {
            segmentFiles = stream
                    .filter(p -> p.getFileName().toString().startsWith("segment-") && p.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparingLong(this::parseSegmentBaseOffset))
                    .toList();
        }

        if (segmentFiles.isEmpty()) return;

        for (Path segPath : segmentFiles) {
            long baseOffset = parseSegmentBaseOffset(segPath);
            long fileSize = Files.size(segPath);
            if (fileSize == 0) {
                Files.deleteIfExists(segPath);
                continue;
            }

            FileChannel channel = FileChannel.open(segPath,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            long mapSize = Math.max(fileSize, Math.min(segmentBytes, 16L * 1024 * 1024));
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, mapSize);
            Segment seg = new Segment(segPath, baseOffset, channel, buffer, mapSize);

            // Scan to find the end position and batch count, and rebuild index
            int pos = 0;
            int batchCount = 0;
            int bytesSinceIdx = 0;
            while (pos + 4 <= fileSize) {
                buffer.position(pos);
                int entryLength = buffer.getInt();
                if (entryLength <= 0 || pos + 4 + entryLength > fileSize) break;

                // Read the baseOffset for index
                long batchOffset = buffer.getLong();
                batchCount++;

                bytesSinceIdx += 4 + entryLength;
                if (bytesSinceIdx >= indexIntervalBytes) {
                    index.add(new IndexEntry(batchOffset, baseOffset, pos));
                    bytesSinceIdx = 0;
                }

                pos += 4 + entryLength;
            }

            seg.position = pos;
            seg.batchCount = batchCount;
            segments.add(seg);
            bytesSinceLastIndex = bytesSinceIdx;
        }

        LOG.debug("Recovered {} segments with {} total batches from {}",
                segments.size(), segments.stream().mapToInt(s -> s.batchCount).sum(), logDir);
    }

    private long parseSegmentBaseOffset(Path segPath) {
        String name = segPath.getFileName().toString();
        // segment-<baseOffset>.log
        return Long.parseLong(name.substring("segment-".length(), name.length() - ".log".length()));
    }

    private void rebuildIndex() {
        index.clear();
        bytesSinceLastIndex = 0;
        for (Segment seg : segments) {
            ByteBuffer buf = seg.buffer;
            int pos = 0;
            int bytesSinceIdx = 0;
            while (pos < seg.position) {
                buf.position(pos);
                if (buf.remaining() < 4) break;
                int entryLength = buf.getInt();
                if (entryLength <= 0 || pos + 4 + entryLength > seg.position) break;

                long batchOffset = buf.getLong();
                bytesSinceIdx += 4 + entryLength;
                if (bytesSinceIdx >= indexIntervalBytes) {
                    index.add(new IndexEntry(batchOffset, seg.baseOffset, pos));
                    bytesSinceIdx = 0;
                }
                pos += 4 + entryLength;
            }
            bytesSinceLastIndex = bytesSinceIdx;
        }
    }
}
