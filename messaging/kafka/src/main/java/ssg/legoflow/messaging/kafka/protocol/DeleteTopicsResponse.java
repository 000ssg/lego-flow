package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteTopics response (API key 20).
 *
 * @param responses the per-topic deletion responses
 * @since 1.0.0
 */
public record DeleteTopicsResponse(List<TopicResult> responses) {

    /**
     * Per-topic deletion result.
     *
     * @param name      the topic name
     * @param errorCode the error code
     */
    public record TopicResult(String name, short errorCode) {
    }
}
