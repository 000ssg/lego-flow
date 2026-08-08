package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetFetch request (API key 9).
 *
 * @param groupId the consumer group ID
 * @param topics  the topics and partitions to fetch offsets for
 * @since 0.1.0
 */
public record OffsetFetchRequest(String groupId, List<TopicPartitions> topics) {

    /**
     * Per-topic partition list.
     *
     * @param name             the topic name
     * @param partitionIndexes the partition indexes
     */
    public record TopicPartitions(String name, List<Integer> partitionIndexes) {
    }
}
