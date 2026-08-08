package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetCommit response (API key 8).
 *
 * @param topics the per-topic commit responses
 * @since 0.1.0
 */
public record OffsetCommitResponse(List<TopicResponse> topics) {

    /**
     * Per-topic commit response.
     *
     * @param name       the topic name
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition commit response.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     */
    public record PartitionResponse(int partitionIndex, short errorCode) {
    }
}
