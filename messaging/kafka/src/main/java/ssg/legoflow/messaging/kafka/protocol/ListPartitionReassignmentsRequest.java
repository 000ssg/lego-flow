package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ListPartitionReassignments request (API key 46).
 *
 * <p>Lists ongoing partition reassignments.
 *
 * @param timeoutMs the timeout in milliseconds
 * @param topics    the topics to list reassignments for (null for all)
 * @since 1.0.0
 */
public record ListPartitionReassignmentsRequest(int timeoutMs, List<TopicData> topics) {

    /**
     * Per-topic data.
     *
     * @param topic      the topic name
     * @param partitions the partition indices to query
     */
    public record TopicData(String topic, List<Integer> partitions) {
    }
}
