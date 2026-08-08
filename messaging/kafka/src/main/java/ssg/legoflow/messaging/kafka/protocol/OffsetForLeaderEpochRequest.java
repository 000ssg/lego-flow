package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetForLeaderEpoch request (API key 23).
 *
 * <p>Finds the log offset for a given leader epoch.
 *
 * @param topics the topics and partitions to query
 * @since 0.1.0
 */
public record OffsetForLeaderEpochRequest(List<TopicData> topics) {

    /**
     * Per-topic data.
     *
     * @param topic      the topic name
     * @param partitions the per-partition data
     */
    public record TopicData(String topic, List<PartitionData> partitions) {
    }

    /**
     * Per-partition data.
     *
     * @param partition   the partition index
     * @param leaderEpoch the leader epoch to look up
     */
    public record PartitionData(int partition, int leaderEpoch) {
    }
}
