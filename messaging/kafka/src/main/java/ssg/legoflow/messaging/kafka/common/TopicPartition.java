package ssg.legoflow.messaging.kafka.common;

/**
 * Represents a Kafka topic-partition pair.
 *
 * @param topic     the topic name
 * @param partition the partition index
 * @since 0.1.0
 */
public record TopicPartition(String topic, int partition) {

    /**
     * Creates a topic-partition.
     *
     * @param topic     the topic name, must not be null
     * @param partition the partition index, must be non-negative
     */
    public TopicPartition {
        if (topic == null) throw new IllegalArgumentException("topic must not be null");
        if (partition < 0) throw new IllegalArgumentException("partition must be non-negative: " + partition);
    }

    @Override
    public String toString() {
        return topic + "-" + partition;
    }
}
