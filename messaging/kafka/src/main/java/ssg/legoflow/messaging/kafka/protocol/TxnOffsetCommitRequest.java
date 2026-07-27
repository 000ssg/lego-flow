package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * TxnOffsetCommit request (API key 28).
 *
 * <p>Commits consumer group offsets as part of an ongoing transaction. The offsets become
 * visible to consumers only after the transaction is committed.
 *
 * @param transactionalId the transactional ID
 * @param groupId         the consumer group ID
 * @param producerId      the producer ID
 * @param producerEpoch   the producer epoch
 * @param topics          the topics and partitions with offsets to commit
 * @since 1.0.0
 */
public record TxnOffsetCommitRequest(String transactionalId, String groupId,
                                     long producerId, short producerEpoch,
                                     List<TopicData> topics) {

    /**
     * Per-topic data for transactional offset commit.
     *
     * @param name       the topic name
     * @param partitions the partition data
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition data for transactional offset commit.
     *
     * @param partitionIndex  the partition index
     * @param committedOffset the committed offset
     * @param metadata        optional metadata (nullable)
     */
    public record PartitionData(int partitionIndex, long committedOffset, String metadata) {
    }
}
