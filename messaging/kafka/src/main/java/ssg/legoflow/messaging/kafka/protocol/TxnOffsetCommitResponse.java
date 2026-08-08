package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * TxnOffsetCommit response (API key 28).
 *
 * @param topics the per-topic results
 * @since 0.1.0
 */
public record TxnOffsetCommitResponse(List<TopicData> topics) {

    /**
     * Per-topic response for transactional offset commit.
     *
     * @param name       the topic name
     * @param partitions the partition results
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition response for transactional offset commit.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     */
    public record PartitionData(int partitionIndex, short errorCode) {
    }
}
