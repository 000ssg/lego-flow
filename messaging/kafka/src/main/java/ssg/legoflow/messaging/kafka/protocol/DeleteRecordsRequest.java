package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteRecords request (API key 21).
 *
 * @param topics    the topics with partition offsets before which to delete
 * @param timeoutMs the timeout in milliseconds
 * @since 0.1.0
 */
public record DeleteRecordsRequest(List<TopicData> topics, int timeoutMs) {

    /**
     * Per-topic data for record deletion.
     *
     * @param name       the topic name
     * @param partitions the per-partition data
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition data for record deletion.
     *
     * @param partitionIndex the partition index
     * @param offset         the offset before which records should be deleted
     */
    public record PartitionData(int partitionIndex, long offset) {
    }
}
