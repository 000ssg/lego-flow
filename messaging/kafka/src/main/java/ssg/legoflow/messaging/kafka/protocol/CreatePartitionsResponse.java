package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * CreatePartitions response (API key 37).
 *
 * @param results the per-topic results
 * @since 1.0.0
 */
public record CreatePartitionsResponse(List<TopicResult> results) {

    /**
     * Per-topic create partitions result.
     *
     * @param name      the topic name
     * @param errorCode the error code
     */
    public record TopicResult(String name, short errorCode) {
    }
}
