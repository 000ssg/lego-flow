package ssg.legoflow.media.rtp.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.rtp.packet.RtpPacket;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adaptive jitter buffer for reordering RTP packets (RFC 3550).
 *
 * <p>Buffers incoming RTP packets and reorders them by sequence number.
 * Handles out-of-order packets, duplicates, and late arrivals. The playout
 * delay adapts based on observed network jitter.
 *
 * <p>Thread-safe for concurrent producers (network receiver) and a single
 * consumer (playout thread).
 *
 * @since 0.1.0
 */
public final class JitterBuffer {

    private static final Logger LOG = LoggerFactory.getLogger(JitterBuffer.class);

    /** Default minimum playout delay in milliseconds. */
    public static final int DEFAULT_MIN_DELAY_MS = 20;

    /** Default maximum playout delay in milliseconds. */
    public static final int DEFAULT_MAX_DELAY_MS = 200;

    /** Default maximum buffer capacity in packets. */
    public static final int DEFAULT_CAPACITY = 500;

    // Using fixed-size array instead of TreeMap for better performance
    private final RtpPacket[] bufferArray;
    private final int[] seqArray; // To track sequence numbers in the same order as bufferArray
    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;
    private final int minDelayMs;
    private final int maxDelayMs;

    private int nextExpectedSeq = -1;
    private int adaptiveDelayMs;
    private int bufferHead = 0; // head position in circular buffer
    private int bufferTail = 0; // tail position in circular buffer
    private int bufferCount = 0; // current number of elements in buffer

    // Statistics
    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalPlayed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong latePackets = new AtomicLong();
    private final AtomicLong overflows = new AtomicLong();

    /**
     * Creates a jitter buffer with default settings.
     */
    public JitterBuffer() {
        this(DEFAULT_CAPACITY, DEFAULT_MIN_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }

    /**
     * Creates a jitter buffer with custom settings.
     *
     * @param capacity   the maximum number of packets to buffer
     * @param minDelayMs the minimum playout delay in milliseconds
     * @param maxDelayMs the maximum playout delay in milliseconds
     */
    public JitterBuffer(int capacity, int minDelayMs, int maxDelayMs) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        if (minDelayMs < 0) throw new IllegalArgumentException("Min delay must be >= 0: " + minDelayMs);
        if (maxDelayMs < minDelayMs) throw new IllegalArgumentException("Max delay must be >= min delay");
        this.capacity = capacity;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.adaptiveDelayMs = minDelayMs;
        // Pre-size arrays
        this.bufferArray = new RtpPacket[capacity];
        this.seqArray = new int[capacity];
    }

    /**
     * Inserts a packet into the buffer.
     *
     * <p>Handles duplicate detection and capacity overflow. Returns the
     * result of the insertion attempt.
     *
     * @param packet the RTP packet to insert
     * @return the insertion result
     */
    public InsertResult insert(RtpPacket packet) {
        int seq = packet.header().sequenceNumber();
        totalReceived.incrementAndGet();

        lock.lock();
        try {
            // Initialize on first packet
            if (nextExpectedSeq < 0) {
                nextExpectedSeq = seq;
            }

            // Check for duplicate (use circular buffer indexing)
            int duplicateIndex = findPacketIndex(seq);
            if (duplicateIndex >= 0) {
                duplicates.incrementAndGet();
                LOG.trace("Duplicate packet seq={}", seq);
                return InsertResult.DUPLICATE;
            }

            // Check for late packet (already played out)
            if (nextExpectedSeq >= 0 && isLate(seq)) {
                latePackets.incrementAndGet();
                LOG.trace("Late packet seq={}, expected >= {}", seq, nextExpectedSeq);
                return InsertResult.LATE;
            }

            // Check capacity
            if (bufferCount >= capacity) {
                overflows.incrementAndGet();
                // Drop oldest packet to make room - remove from head
                bufferArray[bufferHead] = null;
                bufferHead = (bufferHead + 1) % capacity;
                bufferCount--;
                LOG.trace("Buffer overflow, dropped oldest packet");
            }

            // Insert packet
            int insertIndex = (bufferTail + bufferCount) % capacity;
            bufferArray[insertIndex] = packet;
            seqArray[insertIndex] = seq;
            bufferCount++;

            return InsertResult.ACCEPTED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Takes the next packet for playout, if available and ready.
     *
     * <p>Returns the packet with the next expected sequence number.
     * If the expected packet is missing but later packets are available,
     * skips the gap after a configurable wait.
     *
     * @return the next packet for playout, or empty if none is ready
     */
    public Optional<RtpPacket> poll() {
        lock.lock();
        try {
            if (bufferCount == 0) {
                return Optional.empty();
            }

            // Check if the next expected packet is available
            int expectedIndex = findPacketIndex(nextExpectedSeq);
            if (expectedIndex >= 0) {
                RtpPacket packet = bufferArray[expectedIndex];
                bufferArray[expectedIndex] = null; // Clear reference to help GC
                seqArray[expectedIndex] = 0; // Clear sequence number
                bufferCount--;
                nextExpectedSeq = (nextExpectedSeq + 1) & 0xFFFF;
                // Advance bufferHead to next non-null slot
                while (bufferCount > 0 && bufferArray[bufferHead] == null) {
                    bufferHead = (bufferHead + 1) % capacity;
                }
                totalPlayed.incrementAndGet();
                return Optional.of(packet);
            }

            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Skips to the next available packet, discarding any gap.
     *
     * <p>Use this when waiting for a missing packet has exceeded
     * the playout deadline.
     *
     * @return the next available packet, or empty if the buffer is empty
     */
    public Optional<RtpPacket> skip() {
        lock.lock();
        try {
            if (bufferCount == 0) {
                return Optional.empty();
            }
            
            // Get the first available packet in sequence order
            int firstAvailableIndex = findFirstAvailableIndex();
            if (firstAvailableIndex >= 0) {
                RtpPacket packet = bufferArray[firstAvailableIndex];
                bufferArray[firstAvailableIndex] = null; // Clear reference to help GC
                bufferCount--;
                nextExpectedSeq = (seqArray[firstAvailableIndex] + 1) & 0xFFFF;
                totalPlayed.incrementAndGet();
                return Optional.of(packet);
            }
            
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adapts the playout delay based on observed jitter.
     *
     * <p>Uses an exponentially weighted moving average to smooth
     * delay adjustments.
     *
     * @param observedJitterMs the observed jitter in milliseconds
     */
    public void adaptDelay(double observedJitterMs) {
        lock.lock();
        try {
            // EWMA with alpha = 1/16 (per RFC 3550 A.8)
            double alpha = 1.0 / 16.0;
            double target = adaptiveDelayMs + alpha * (observedJitterMs * 2 - adaptiveDelayMs);
            adaptiveDelayMs = (int) Math.max(minDelayMs, Math.min(maxDelayMs, target));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current adaptive playout delay in milliseconds.
     *
     * @return the playout delay
     */
    public int adaptiveDelayMs() {
        return adaptiveDelayMs;
    }

    /**
     * Returns the current number of packets in the buffer.
     *
     * @return the buffer size
     */
    public int size() {
        lock.lock();
        try {
            return bufferCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the buffer capacity.
     *
     * @return the maximum number of packets
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Clears the buffer and resets state.
     */
    public void clear() {
        lock.lock();
        try {
            for (int i = 0; i < capacity; i++) { bufferArray[i] = null; seqArray[i] = 0; }
            bufferHead = 0;
            bufferTail = 0;
            bufferCount = 0;
            nextExpectedSeq = -1;
        } finally {
            lock.unlock();
        }
    }

    /** @return total packets received */
    public long totalReceived() { return totalReceived.get(); }

    /** @return total packets played out */
    public long totalPlayed() { return totalPlayed.get(); }

    /** @return total duplicate packets detected */
    public long duplicateCount() { return duplicates.get(); }

    /** @return total late packets detected */
    public long latePacketCount() { return latePackets.get(); }

    /** @return total buffer overflows */
    public long overflowCount() { return overflows.get(); }

    /**
     * Finds the index of a packet with the given sequence number in the buffer.
     * 
     * @param seq the sequence number to find
     * @return the index if found, -1 otherwise
     */
    private int findPacketIndex(int seq) {
        // Linear search through the buffer
        for (int i = 0; i < bufferCount; i++) {
            int index = (bufferHead + i) % capacity;
            if (seqArray[index] == seq) {
                return index;
            }
        }
        return -1;
    }
    
    /**
     * Finds the index of the first available packet in sequence order.
     * 
     * @return the index if found, -1 otherwise
     */
    private int findFirstAvailableIndex() {
        // Linear search for the lowest sequence number >= nextExpectedSeq
        int lowestIndex = -1;
        int lowestSeq = Integer.MAX_VALUE;
        
        for (int i = 0; i < bufferCount; i++) {
            int index = (bufferHead + i) % capacity;
            int seq = seqArray[index];
            if (seq >= nextExpectedSeq && seq < lowestSeq) {
                lowestSeq = seq;
                lowestIndex = index;
            }
        }
        
        return lowestIndex;
    }

    /**
     * Checks if a sequence number is "late" relative to the expected sequence.
     * Accounts for sequence number wrap-around.
     */
    private boolean isLate(int seq) {
        return isLate(seq, nextExpectedSeq);
    }

    /**
     * Checks if {@code seq} is older than {@code reference}, accounting for wrap-around.
     */
    private static boolean isLate(int seq, int reference) {
        int diff = (reference - seq) & 0xFFFF;
        return diff > 0 && diff < 0x8000;
    }

    /**
     * Comparator for sequence numbers handling wrap-around.
     */
    static int seqCompare(int a, int b) {
        int diff = (a - b) & 0xFFFF;
        if (diff == 0) return 0;
        return diff < 0x8000 ? 1 : -1;
    }

    /**
     * Result of inserting a packet into the jitter buffer.
     *
     * @since 0.1.0
     */
    public enum InsertResult {
        /** Packet was accepted and buffered. */
        ACCEPTED,
        /** Packet was a duplicate of one already buffered. */
        DUPLICATE,
        /** Packet arrived too late (already played out). */
        LATE
    }
}
