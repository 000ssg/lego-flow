package ssg.legoflow.service.manager;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Per-selector I/O statistics tracking for {@link ServiceGroup}.
 *
 * <p>Collects global and per-selector counters for TCP and UDP traffic,
 * connection counts, key-type processing durations, and selector cycle times.
 * All counters are lock-free atomic operations safe for concurrent updates
 * from multiple selector threads.
 *
 * <p>Each selector thread sets its index via {@link #setSelectorIndex(int)}
 * so that subsequent recording calls auto-route to the correct per-selector
 * bucket.
 *
 * @since 0.1.0
 */
public class ServiceGroupStatistics {

    /** Key type index for OP_ACCEPT. */
    public static final int ACCEPT = 0;
    /** Key type index for OP_CONNECT. */
    public static final int CONNECT = 1;
    /** Key type index for OP_READ. */
    public static final int READ = 2;
    /** Key type index for OP_WRITE. */
    public static final int WRITE = 3;

    private final int selectorCount;
    private final ThreadLocal<Integer> selectorIndex = new ThreadLocal<>();

    // Global counters
    private final AtomicLong connections = new AtomicLong();
    private final AtomicLong[] tcpBytes = {new AtomicLong(), new AtomicLong()};       // [read, written]
    private final AtomicLong[] udpPackets = {new AtomicLong(), new AtomicLong()};      // [read, written]
    private final AtomicLong[] udpBytes = {new AtomicLong(), new AtomicLong()};        // [read, written]

    // Key type counters: ACCEPT=0, CONNECT=1, READ=2, WRITE=3
    private final AtomicLongArray keyCounts = new AtomicLongArray(4);
    private final AtomicLongArray keyDurations = new AtomicLongArray(4);

    // Per-selector counters
    private final AtomicLongArray selectorDurations;
    private final AtomicLongArray selectorReadBytes;
    private final AtomicLongArray selectorWriteBytes;
    private final AtomicLongArray selectorReadDurations;
    private final AtomicLongArray selectorWriteDurations;
    private final AtomicLongArray selectorKeyCounts;

    /**
     * Creates a new statistics tracker for the given number of selectors.
     *
     * @param selectorCount total number of selectors (including connector selector at index 0)
     * @throws IllegalArgumentException if {@code selectorCount} is not positive
     * @since 0.1.0
     */
    public ServiceGroupStatistics(int selectorCount) {
        if (selectorCount <= 0) {
            throw new IllegalArgumentException("selectorCount must be positive: " + selectorCount);
        }
        this.selectorCount = selectorCount;
        this.selectorDurations = new AtomicLongArray(selectorCount);
        this.selectorReadBytes = new AtomicLongArray(selectorCount);
        this.selectorWriteBytes = new AtomicLongArray(selectorCount);
        this.selectorReadDurations = new AtomicLongArray(selectorCount);
        this.selectorWriteDurations = new AtomicLongArray(selectorCount);
        this.selectorKeyCounts = new AtomicLongArray(selectorCount);
    }

    /**
     * Sets the selector index for the current thread.
     *
     * @param index the selector index (0 = connector, 1..N = data selectors)
     * @since 0.1.0
     */
    public void setSelectorIndex(int index) {
        selectorIndex.set(index);
    }

    /**
     * Returns the selector index for the current thread.
     *
     * @return the selector index, or {@code null} if not set
     * @since 0.1.0
     */
    public Integer getSelectorIndex() {
        return selectorIndex.get();
    }

    /**
     * Records a new connection.
     *
     * @since 0.1.0
     */
    public void addConnection() {
        connections.incrementAndGet();
    }

    /**
     * Records a TCP read operation.
     *
     * @param bytes         number of bytes read
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addTcpRead(long bytes, long durationNanos) {
        tcpBytes[0].addAndGet(bytes);
        var idx = selectorIndex.get();
        if (idx != null) {
            selectorReadBytes.addAndGet(idx, bytes);
            selectorReadDurations.addAndGet(idx, durationNanos);
        }
    }

    /**
     * Records a TCP write operation.
     *
     * @param bytes         number of bytes written
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addTcpWrite(long bytes, long durationNanos) {
        tcpBytes[1].addAndGet(bytes);
        var idx = selectorIndex.get();
        if (idx != null) {
            selectorWriteBytes.addAndGet(idx, bytes);
            selectorWriteDurations.addAndGet(idx, durationNanos);
        }
    }

    /**
     * Records a UDP read operation.
     *
     * @param bytes         number of bytes read
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addUdpRead(long bytes, long durationNanos) {
        udpPackets[0].incrementAndGet();
        udpBytes[0].addAndGet(bytes);
        var idx = selectorIndex.get();
        if (idx != null) {
            selectorReadBytes.addAndGet(idx, bytes);
            selectorReadDurations.addAndGet(idx, durationNanos);
        }
    }

    /**
     * Records a UDP write operation.
     *
     * @param bytes         number of bytes written
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addUdpWrite(long bytes, long durationNanos) {
        udpPackets[1].incrementAndGet();
        udpBytes[1].addAndGet(bytes);
        var idx = selectorIndex.get();
        if (idx != null) {
            selectorWriteBytes.addAndGet(idx, bytes);
            selectorWriteDurations.addAndGet(idx, durationNanos);
        }
    }

    /**
     * Records a processed selection key of the given type.
     *
     * @param keyType       the key type index ({@link #ACCEPT}, {@link #CONNECT}, {@link #READ}, {@link #WRITE})
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addKeyProcessed(int keyType, long durationNanos) {
        keyCounts.incrementAndGet(keyType);
        keyDurations.addAndGet(keyType, durationNanos);
        var idx = selectorIndex.get();
        if (idx != null) {
            selectorKeyCounts.incrementAndGet(idx);
        }
    }

    /**
     * Records a selector cycle duration.
     *
     * @param selectorIdx   the selector index
     * @param durationNanos duration in nanoseconds
     * @since 0.1.0
     */
    public void addSelectorDuration(int selectorIdx, long durationNanos) {
        selectorDurations.addAndGet(selectorIdx, durationNanos);
    }

    /**
     * Returns the total number of selectors being tracked.
     *
     * @return the selector count
     * @since 0.1.0
     */
    public int getSelectorCount() {
        return selectorCount;
    }

    /**
     * Returns the total connection count.
     *
     * @return connections recorded
     * @since 0.1.0
     */
    public long getConnections() {
        return connections.get();
    }

    /**
     * Returns the global TCP bytes array (index 0=read, 1=written).
     *
     * @return array of two values
     * @since 0.1.0
     */
    public long[] getTcpBytes() {
        return new long[]{tcpBytes[0].get(), tcpBytes[1].get()};
    }

    /**
     * Returns the global UDP packet counts (index 0=read, 1=written).
     *
     * @return array of two values
     * @since 0.1.0
     */
    public long[] getUdpPackets() {
        return new long[]{udpPackets[0].get(), udpPackets[1].get()};
    }

    /**
     * Returns the global UDP byte counts (index 0=read, 1=written).
     *
     * @return array of two values
     * @since 0.1.0
     */
    public long[] getUdpBytes() {
        return new long[]{udpBytes[0].get(), udpBytes[1].get()};
    }

    /**
     * Returns the key type counts (ACCEPT, CONNECT, READ, WRITE).
     *
     * @return a copy of the key counts array
     * @since 0.1.0
     */
    public long[] getKeyCounts() {
        var result = new long[4];
        for (int i = 0; i < 4; i++) result[i] = keyCounts.get(i);
        return result;
    }

    /**
     * Returns the key type durations (ACCEPT, CONNECT, READ, WRITE).
     *
     * @return a copy of the key durations array
     * @since 0.1.0
     */
    public long[] getKeyDurations() {
        var result = new long[4];
        for (int i = 0; i < 4; i++) result[i] = keyDurations.get(i);
        return result;
    }

    /**
     * Returns the per-selector read bytes.
     *
     * @return a copy of the per-selector read bytes array
     * @since 0.1.0
     */
    public long[] getSelectorReadBytes() {
        var result = new long[selectorCount];
        for (int i = 0; i < selectorCount; i++) result[i] = selectorReadBytes.get(i);
        return result;
    }

    /**
     * Returns the per-selector write bytes.
     *
     * @return a copy of the per-selector write bytes array
     * @since 0.1.0
     */
    public long[] getSelectorWriteBytes() {
        var result = new long[selectorCount];
        for (int i = 0; i < selectorCount; i++) result[i] = selectorWriteBytes.get(i);
        return result;
    }

    /**
     * Returns the per-selector key counts.
     *
     * @return a copy of the per-selector key counts array
     * @since 0.1.0
     */
    public long[] getSelectorKeyCounts() {
        var result = new long[selectorCount];
        for (int i = 0; i < selectorCount; i++) result[i] = selectorKeyCounts.get(i);
        return result;
    }

    /**
     * Returns the per-selector durations.
     *
     * @return a copy of the per-selector durations array
     * @since 0.1.0
     */
    public long[] getSelectorDurations() {
        var result = new long[selectorCount];
        for (int i = 0; i < selectorCount; i++) result[i] = selectorDurations.get(i);
        return result;
    }

    /**
     * Creates an immutable point-in-time snapshot of all statistics.
     *
     * @return a new {@link Snapshot} with all current counter values
     * @since 0.1.0
     */
    public Snapshot snapshot() {
        var selectorReadBytesArr = new long[selectorCount];
        var selectorWriteBytesArr = new long[selectorCount];
        var selectorDurationsArr = new long[selectorCount];
        var selectorReadDurationsArr = new long[selectorCount];
        var selectorWriteDurationsArr = new long[selectorCount];
        var selectorKeyCountsArr = new long[selectorCount];

        for (int i = 0; i < selectorCount; i++) {
            selectorReadBytesArr[i] = selectorReadBytes.get(i);
            selectorWriteBytesArr[i] = selectorWriteBytes.get(i);
            selectorDurationsArr[i] = selectorDurations.get(i);
            selectorReadDurationsArr[i] = selectorReadDurations.get(i);
            selectorWriteDurationsArr[i] = selectorWriteDurations.get(i);
            selectorKeyCountsArr[i] = selectorKeyCounts.get(i);
        }

        var keyCountsArr = new long[4];
        var keyDurationsArr = new long[4];
        for (int i = 0; i < 4; i++) {
            keyCountsArr[i] = keyCounts.get(i);
            keyDurationsArr[i] = keyDurations.get(i);
        }

        return new Snapshot(
                connections.get(),
                new long[]{tcpBytes[0].get(), tcpBytes[1].get()},
                new long[]{udpPackets[0].get(), udpPackets[1].get()},
                new long[]{udpBytes[0].get(), udpBytes[1].get()},
                keyCountsArr,
                keyDurationsArr,
                selectorReadBytesArr,
                selectorWriteBytesArr,
                selectorDurationsArr,
                selectorReadDurationsArr,
                selectorWriteDurationsArr,
                selectorKeyCountsArr
        );
    }

    /**
     * Resets all counters to zero.
     *
     * @since 0.1.0
     */
    public void reset() {
        connections.set(0);
        tcpBytes[0].set(0);
        tcpBytes[1].set(0);
        udpPackets[0].set(0);
        udpPackets[1].set(0);
        udpBytes[0].set(0);
        udpBytes[1].set(0);
        for (int i = 0; i < 4; i++) {
            keyCounts.set(i, 0);
            keyDurations.set(i, 0);
        }
        for (int i = 0; i < selectorCount; i++) {
            selectorDurations.set(i, 0);
            selectorReadBytes.set(i, 0);
            selectorWriteBytes.set(i, 0);
            selectorReadDurations.set(i, 0);
            selectorWriteDurations.set(i, 0);
            selectorKeyCounts.set(i, 0);
        }
    }

    @Override
    public String toString() {
        var snap = snapshot();
        var sb = new StringBuilder();
        sb.append("ServiceGroupStatistics{");
        sb.append("\n  connections=").append(snap.connections());
        sb.append("\n  tcpBytes (read, written)=[").append(snap.tcpBytes()[0]).append(", ").append(snap.tcpBytes()[1]).append(']');
        sb.append("\n  udpPackets (read, written)=[").append(snap.udpPackets()[0]).append(", ").append(snap.udpPackets()[1]).append(']');
        sb.append("\n  udpBytes (read, written)=[").append(snap.udpBytes()[0]).append(", ").append(snap.udpBytes()[1]).append(']');
        sb.append("\n  keyCounts (a,c,r,w)=[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(", ");
            sb.append(snap.keyCounts()[i]);
        }
        sb.append(']');
        sb.append("\n  keyDurations (a,c,r,w)=[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(", ");
            sb.append(toMs(snap.keyDurations()[i]));
        }
        sb.append(']');

        for (int i = 0; i < selectorCount; i++) {
            sb.append("\n  selector[").append(i).append(i == 0 ? "/connector" : "/data").append("]:");
            sb.append(" keys=").append(snap.selectorKeyCounts()[i]);
            sb.append(" read=").append(formatBytes(snap.selectorReadBytes()[i]));
            sb.append(" write=").append(formatBytes(snap.selectorWriteBytes()[i]));
            sb.append(" duration=").append(toMs(snap.selectorDurations()[i]));
            long readBytes = snap.selectorReadBytes()[i];
            long readNanos = snap.selectorReadDurations()[i];
            if (readBytes > 0 && readNanos > 0) {
                sb.append(" readRate=").append(formatRate(readBytes, readNanos));
            }
            long writeBytes = snap.selectorWriteBytes()[i];
            long writeNanos = snap.selectorWriteDurations()[i];
            if (writeBytes > 0 && writeNanos > 0) {
                sb.append(" writeRate=").append(formatRate(writeBytes, writeNanos));
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    private static String toMs(long nanos) {
        return nanos / 1_000_000f + "ms";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    static String formatRate(long bytes, long nanos) {
        if (nanos == 0) return "N/A";
        long rate = Math.round(bytes / (nanos / 1_000_000_000.0));
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;
        if (rate < mb) return String.format("%.2f KB/s", rate / (double) kb);
        if (rate < gb) return String.format("%.2f MB/s", rate / (double) mb);
        return String.format("%.2f GB/s", rate / (double) gb);
    }

    /**
     * Immutable point-in-time snapshot of all statistics counters.
     *
     * @param connections          total connections
     * @param tcpBytes             TCP bytes [read, written]
     * @param udpPackets           UDP packets [read, written]
     * @param udpBytes             UDP bytes [read, written]
     * @param keyCounts            key type counts [ACCEPT, CONNECT, READ, WRITE]
     * @param keyDurations         key type durations [ACCEPT, CONNECT, READ, WRITE]
     * @param selectorReadBytes    per-selector read byte counts
     * @param selectorWriteBytes   per-selector write byte counts
     * @param selectorDurations    per-selector cycle durations
     * @param selectorReadDurations  per-selector read durations
     * @param selectorWriteDurations per-selector write durations
     * @param selectorKeyCounts    per-selector key counts
     * @since 0.1.0
     */
    public record Snapshot(
            long connections,
            long[] tcpBytes,
            long[] udpPackets,
            long[] udpBytes,
            long[] keyCounts,
            long[] keyDurations,
            long[] selectorReadBytes,
            long[] selectorWriteBytes,
            long[] selectorDurations,
            long[] selectorReadDurations,
            long[] selectorWriteDurations,
            long[] selectorKeyCounts
    ) {}
}
