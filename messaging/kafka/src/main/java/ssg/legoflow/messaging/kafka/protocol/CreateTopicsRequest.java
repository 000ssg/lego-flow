package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;
import java.util.Map;

/**
 * CreateTopics request (API key 19).
 *
 * @param topics    the topics to create
 * @param timeoutMs the timeout in milliseconds
 * @since 0.1.0
 */
public record CreateTopicsRequest(List<TopicCreate> topics, int timeoutMs) {

    /**
     * A topic creation specification.
     *
     * @param name              the topic name
     * @param numPartitions     the number of partitions (-1 for default)
     * @param replicationFactor the replication factor (-1 for default)
     * @param assignments       the manual partition-to-broker assignments (empty for automatic
     *                          assignment)
     * @param configs           the topic configuration overrides
     */
    public record TopicCreate(String name, int numPartitions, short replicationFactor,
                              List<Assignment> assignments, Map<String, String> configs) {

        /**
         * A manual partition-to-broker assignment for one partition of the topic.
         *
         * @param partitionIndex the partition index
         * @param brokerIds      the broker ids to place the partition on
         */
        public record Assignment(int partitionIndex, List<Integer> brokerIds) {
        }

        /**
         * Compat constructor that defaults {@code assignments} to an empty list (automatic
         * assignment), so callers that do not pin partitions to brokers keep compiling.
         */
        public TopicCreate(String name, int numPartitions, short replicationFactor,
                           Map<String, String> configs) {
            this(name, numPartitions, replicationFactor, List.of(), configs);
        }
    }
}
