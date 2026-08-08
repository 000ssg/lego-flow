package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AddPartitionsToTxn response (API key 24).
 *
 * @param topics the per-topic responses
 * @since 0.1.0
 */
public record AddPartitionsToTxnResponse(List<TopicResponse> topics) {

    /**
     * Per-topic response.
     *
     * @param name       the topic name
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition response.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     */
    public record PartitionResponse(int partitionIndex, short errorCode) {
    }
}
