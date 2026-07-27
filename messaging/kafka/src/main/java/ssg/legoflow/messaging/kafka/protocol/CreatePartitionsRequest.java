package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * CreatePartitions request (API key 37).
 *
 * @param topics    the topics with new partition counts
 * @param timeoutMs the timeout in milliseconds
 * @since 1.0.0
 */
public record CreatePartitionsRequest(List<TopicNewPartitions> topics, int timeoutMs) {

    /**
     * A topic with its desired new partition count.
     *
     * @param name     the topic name
     * @param newCount the new total partition count
     */
    public record TopicNewPartitions(String name, int newCount) {
    }
}
