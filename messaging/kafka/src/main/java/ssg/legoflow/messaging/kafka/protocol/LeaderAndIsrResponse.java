package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;
import java.util.UUID;

/**
 * LeaderAndIsr response (API key 4).
 *
 * @param errorCode         top-level error code
 * @param partitionErrors   per-partition errors (v0-v4, flat)
 * @param topics            per-topic errors (v5+, keyed by TopicId)
 * @since 0.1.0
 */
public record LeaderAndIsrResponse(
        short errorCode,
        List<PartitionError> partitionErrors,
        List<TopicError> topics) {

    /**
     * Per-partition error (v0-v4 flat, or nested under TopicError v5+).
     *
     * @param topicName      topic name (null v5+)
     * @param partitionIndex partition index
     * @param errorCode      partition error code
     */
    public record PartitionError(
            String topicName,
            int partitionIndex,
            short errorCode) {
    }

    /**
     * Per-topic error container (v5+).
     *
     * @param topicId           topic UUID
     * @param partitionErrors   per-partition errors
     */
    public record TopicError(
            UUID topicId,
            List<PartitionError> partitionErrors) {
    }
}
