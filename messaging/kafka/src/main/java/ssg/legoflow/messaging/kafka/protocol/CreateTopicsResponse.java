package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * CreateTopics response (API key 19).
 *
 * @param topics the per-topic creation responses
 * @since 0.1.0
 */
public record CreateTopicsResponse(List<TopicResult> topics) {

    /**
     * Per-topic creation result.
     *
     * @param name      the topic name
     * @param errorCode the error code
     */
    public record TopicResult(String name, short errorCode) {
    }
}
