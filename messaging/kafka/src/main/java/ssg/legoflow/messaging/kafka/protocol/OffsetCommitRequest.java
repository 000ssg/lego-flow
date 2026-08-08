package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetCommit request (API key 8).
 *
 * @param groupId      the consumer group ID
 * @param generationId the group generation ID
 * @param memberId     the member ID
 * @param topics       the topics with offsets to commit
 * @since 0.1.0
 */
public record OffsetCommitRequest(String groupId, int generationId, String memberId,
                                  List<TopicOffsets> topics) {

    /**
     * Per-topic offset commit data.
     *
     * @param name       the topic name
     * @param partitions the partition offsets to commit
     */
    public record TopicOffsets(String name, List<PartitionOffset> partitions) {
    }

    /**
     * Per-partition offset commit data.
     *
     * @param partitionIndex the partition index
     * @param committedOffset the offset to commit
     * @param metadata        the metadata (nullable)
     */
    public record PartitionOffset(int partitionIndex, long committedOffset, String metadata) {
    }
}
