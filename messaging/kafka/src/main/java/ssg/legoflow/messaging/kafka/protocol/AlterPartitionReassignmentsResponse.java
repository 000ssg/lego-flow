package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AlterPartitionReassignments response (API key 45).
 *
 * @param errorCode the top-level error code
 * @param topics    per-topic results
 * @since 1.0.0
 */
public record AlterPartitionReassignmentsResponse(short errorCode, List<TopicResult> topics) {

    /**
     * Per-topic result.
     *
     * @param topic      the topic name
     * @param partitions per-partition results
     */
    public record TopicResult(String topic, List<PartitionResult> partitions) {
    }

    /**
     * Per-partition result.
     *
     * @param partition the partition index
     * @param errorCode the partition-level error code
     */
    public record PartitionResult(int partition, short errorCode) {
    }
}
