package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AlterPartitionReassignments request (API key 45).
 *
 * <p>Reassign partition replicas to different brokers.
 *
 * @param timeoutMs the timeout in milliseconds
 * @param topics    the per-topic reassignment data
 * @since 1.0.0
 */
public record AlterPartitionReassignmentsRequest(int timeoutMs, List<TopicReassignment> topics) {

    /**
     * Per-topic reassignment data.
     *
     * @param topic      the topic name
     * @param partitions the per-partition reassignment data
     */
    public record TopicReassignment(String topic, List<PartitionReassignment> partitions) {
    }

    /**
     * Per-partition reassignment data.
     *
     * @param partition the partition index
     * @param replicas  the target replica set (null to cancel reassignment)
     */
    public record PartitionReassignment(int partition, List<Integer> replicas) {
    }
}
