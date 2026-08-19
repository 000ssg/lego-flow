package ssg.legoflow.messaging.kafka.common;

import java.util.concurrent.atomic.AtomicInteger;
/**
 * Partitioner that uses murmur2 hash of the key.
 * Falls back to round-robin if key is null.
 *
 * @since 0.1.0
 */
final class KeyHashPartitioner implements Partitioner {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int partition(String topic, byte[] key, byte[] value, int numPartitions) {
        if (key == null) {
            return Math.abs(counter.getAndIncrement() % numPartitions);
        }
        return Math.abs(murmur2(key) % numPartitions);
    }

    /**
     * Murmur2 hash (same as Kafka's default partitioner).
     */
    static int murmur2(byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c;
        int m = 0x5bd1e995;
        int r = 24;
        int h = seed ^ length;
        int lengthFour = length >> 2;

        for (int i = 0; i < lengthFour; i++) {
            int iF = i << 2;
            int k = (data[iF] & 0xff)
                    | ((data[iF + 1] & 0xff) << 8)
                    | ((data[iF + 2] & 0xff) << 16)
                    | ((data[iF + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int lenM = lengthFour << 2;
        int left = length - lenM;
        if (left >= 3) h ^= (data[lenM + 2] & 0xff) << 16;
        if (left >= 2) h ^= (data[lenM + 1] & 0xff) << 8;
        if (left >= 1) {
            h ^= (data[lenM] & 0xff);
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }
}
