package ssg.legoflow.messaging.kafka.common;

/**
 * Strategy for selecting a partition for a record.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface Partitioner {

    /**
     * Compute the partition for the given record.
     *
     * @param topic          the topic name
     * @param key            the record key (may be null)
     * @param value          the record value (may be null)
     * @param numPartitions  the total number of partitions
     * @return the partition index (0-based)
     */
    int partition(String topic, byte[] key, byte[] value, int numPartitions);

    /**
     * Returns a key-hash partitioner that distributes by key hash.
     * If key is null, falls back to round-robin.
     *
     * @return a key-hash partitioner
     */
    static Partitioner keyHash() {
        return new KeyHashPartitioner();
    }

    /**
     * Returns a round-robin partitioner.
     *
     * @return a round-robin partitioner
     */
    static Partitioner roundRobin() {
        return new RoundRobinPartitioner();
    }
}
