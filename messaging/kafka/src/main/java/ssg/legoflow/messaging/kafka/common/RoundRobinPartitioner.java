package ssg.legoflow.messaging.kafka.common;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin partitioner that cycles through partitions.
 *
 * @since 0.1.0
 */
final class RoundRobinPartitioner implements Partitioner {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int partition(String topic, byte[] key, byte[] value, int numPartitions) {
        return Math.abs(counter.getAndIncrement() % numPartitions);
    }
}
