package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetFetch response (API key 9).
 *
 * @param topics the per-topic offset responses
 * @since 0.1.0
 */
public record OffsetFetchResponse(List<TopicResponse> topics) {

    /**
     * Per-topic offset response.
     *
     * @param name       the topic name
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition offset response.
     *
     * @param partitionIndex  the partition index
     * @param committedOffset the committed offset (-1 if none)
     * @param metadata        the metadata (nullable)
     * @param errorCode       the error code
     */
    public record PartitionResponse(int partitionIndex, long committedOffset, String metadata,
                                    short errorCode) {
    }
}
