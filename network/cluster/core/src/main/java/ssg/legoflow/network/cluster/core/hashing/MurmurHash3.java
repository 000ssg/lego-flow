package ssg.legoflow.network.cluster.core.hashing;

/**
 * MurmurHash3 32-bit hash function.
 *
 * Reference: https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp
 * Produces consistent 32-bit unsigned hash values.
 */
public final class MurmurHash3 implements HashFunction {

    public static final MurmurHash3 INSTANCE = new MurmurHash3();

    private MurmurHash3() {}

    @Override
    public long hash(byte[] data) {
        return hash32(data, 0, data.length, 0);
    }

    @Override
    public String name() {
        return "MurmurHash3";
    }

    /**
     * MurmurHash3 x86 32-bit implementation.
     *
     * @param data   the input data
     * @param offset the offset into the data
     * @param length the number of bytes to hash
     * @param seed   the seed value
     * @return an unsigned 32-bit hash
     */
    public static long hash32(byte[] data, int offset, int length, int seed) {
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int r1 = 15;
        int r2 = 13;
        int mask = 0xff;

        int h1 = seed;
        int roundedEnd = offset + (length & ~3);

        for (int i = offset; i < roundedEnd; i += 4) {
            int k1 = (data[i] & mask)
                    | ((data[i + 1] & mask) << 8)
                    | ((data[i + 2] & mask) << 16)
                    | ((data[i + 3] & mask) << 24);

            k1 *= c1;
            k1 = (k1 << r1) | (k1 >>> (32 - r1));
            k1 *= c2;
            h1 ^= k1;
        }

        int tailOffset = roundedEnd;
        int k1 = 0;
        int tail = length & 3;

        if (tail >= 3) k1 ^= (data[roundedEnd + 2] & mask) << 16;
        if (tail >= 2) k1 ^= (data[roundedEnd + 1] & mask) << 8;
        if (tail >= 1) {
            k1 ^= (data[roundedEnd] & mask);
            k1 *= c1;
            k1 = (k1 << r1) | (k1 >>> (32 - r1));
            k1 *= c2;
            h1 ^= k1;
        }

        // Finalization
        h1 ^= length;
        h1 = fmMix(h1);

        // Return as unsigned long
        return h1 & 0xFFFFFFFFL;
    }

    private static int fmMix(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
