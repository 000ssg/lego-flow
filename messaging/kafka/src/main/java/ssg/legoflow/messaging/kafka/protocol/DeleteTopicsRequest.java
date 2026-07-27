package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteTopics request (API key 20).
 *
 * @param topicNames the topic names to delete
 * @param timeoutMs  the timeout in milliseconds
 * @since 1.0.0
 */
public record DeleteTopicsRequest(List<String> topicNames, int timeoutMs) {
}
