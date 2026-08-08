package ssg.legoflow.database.redis.server;

import java.nio.charset.StandardCharsets;

/**
 * HyperLogLog probabilistic cardinality estimator.
 *
 * <p>Uses 2^14 = 16384 registers (matching Redis standard) with a MurmurHash3-style
 * 64-bit hash function. Provides bias correction using the harmonic mean formula
 * and small range correction (linear counting) when many registers are zero.
 *
 * @since 0.1.0
 */
public final class HyperLogLog {

    /** Number of registers: 2^14 = 16384 (matching Redis). */
    private static final int P = 14;
    private static final int M = 1 << P;
    private static final double ALPHA = 0.7213 / (1.0 + 1.079 / M);

    private final byte[] registers = new byte[M];

    /**
     * Creates a new empty HyperLogLog.
     */
    public HyperLogLog() {
    }

    /**
     * Adds an element to the HyperLogLog.
     *
     * @param element the element bytes
     * @return true if the internal state was modified (cardinality estimate may have changed)
     */
    public boolean add(byte[] element) {
        long hash = murmurHash64(element);
        int index = (int) (hash >>> (64 - P));
        long remaining = hash << P | (1L << (P - 1)); // ensure at least one bit set
        int rank = Long.numberOfLeadingZeros(remaining) + 1;
        if (rank > registers[index]) {
            registers[index] = (byte) rank;
            return true;
        }
        return false;
    }

    /**
     * Returns the estimated cardinality.
     *
     * @return estimated number of distinct elements
     */
    public long count() {
        double sum = 0.0;
        int zeros = 0;
        for (int i = 0; i < M; i++) {
            sum += 1.0 / (1L << registers[i]);
            if (registers[i] == 0) {
                zeros++;
            }
        }

        double estimate = ALPHA * M * M / sum;

        // Small range correction (linear counting)
        if (estimate <= 2.5 * M && zeros > 0) {
            estimate = M * Math.log((double) M / zeros);
        }

        return Math.round(estimate);
    }

    /**
     * Merges another HyperLogLog into this one (union).
     *
     * @param other the other HyperLogLog
     */
    public void merge(HyperLogLog other) {
        for (int i = 0; i < M; i++) {
            if (other.registers[i] > registers[i]) {
                registers[i] = other.registers[i];
            }
        }
    }

    /**
     * Returns the register array (for testing).
     *
     * @return the registers
     */
    byte[] registers() {
        return registers;
    }

    /**
     * MurmurHash3-style 64-bit hash function.
     */
    private static long murmurHash64(byte[] data) {
        long h = 0x9747b28c;
        long k;
        int length = data.length;
        int i = 0;

        // Process 8-byte chunks
        while (i + 8 <= length) {
            k = ((long) data[i] & 0xff)
                    | (((long) data[i + 1] & 0xff) << 8)
                    | (((long) data[i + 2] & 0xff) << 16)
                    | (((long) data[i + 3] & 0xff) << 24)
                    | (((long) data[i + 4] & 0xff) << 32)
                    | (((long) data[i + 5] & 0xff) << 40)
                    | (((long) data[i + 6] & 0xff) << 48)
                    | (((long) data[i + 7] & 0xff) << 56);

            k *= 0x87c37b91114253d5L;
            k = Long.rotateLeft(k, 31);
            k *= 0x4cf5ad432745937fL;
            h ^= k;
            h = Long.rotateLeft(h, 27);
            h = h * 5 + 0x52dce729;
            i += 8;
        }

        // Process remaining bytes
        k = 0;
        for (int shift = 0; i < length; i++, shift += 8) {
            k |= ((long) data[i] & 0xff) << shift;
        }
        if (k != 0) {
            k *= 0x87c37b91114253d5L;
            k = Long.rotateLeft(k, 31);
            k *= 0x4cf5ad432745937fL;
            h ^= k;
        }

        // Finalization
        h ^= length;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;

        return h;
    }
}
