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
     * @param configs           the topic configuration overrides
     */
    public record TopicCreate(String name, int numPartitions, short replicationFactor,
                              Map<String, String> configs) {
    }
}
