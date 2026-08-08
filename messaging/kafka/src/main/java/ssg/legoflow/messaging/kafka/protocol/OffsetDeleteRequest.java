package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetDelete request (API key 47).
 *
 * @param groupId the consumer group ID
 * @param topics  the topics with partitions whose offsets to delete
 * @since 0.1.0
 */
public record OffsetDeleteRequest(String groupId, List<TopicData> topics) {

    /**
     * Per-topic data for offset deletion.
     *
     * @param name       the topic name
     * @param partitions the per-partition data
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition data for offset deletion.
     *
     * @param partitionIndex the partition index
     */
    public record PartitionData(int partitionIndex) {
    }
}
