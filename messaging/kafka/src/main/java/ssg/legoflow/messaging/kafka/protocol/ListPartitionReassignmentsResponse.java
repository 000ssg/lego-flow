package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ListPartitionReassignments response (API key 46).
 *
 * @param errorCode the top-level error code
 * @param topics    per-topic reassignment info
 * @since 1.0.0
 */
public record ListPartitionReassignmentsResponse(short errorCode, List<TopicResult> topics) {

    /**
     * Per-topic reassignment information.
     *
     * @param topic      the topic name
     * @param partitions per-partition reassignment details
     */
    public record TopicResult(String topic, List<PartitionResult> partitions) {
    }

    /**
     * Per-partition reassignment details.
     *
     * @param partition        the partition index
     * @param replicas         the current replica set
     * @param addingReplicas   replicas being added
     * @param removingReplicas replicas being removed
     */
    public record PartitionResult(int partition, List<Integer> replicas,
                                  List<Integer> addingReplicas, List<Integer> removingReplicas) {
    }
}
