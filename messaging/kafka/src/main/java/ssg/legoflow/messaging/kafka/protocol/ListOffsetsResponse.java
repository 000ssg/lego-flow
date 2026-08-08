package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ListOffsets response (API key 2).
 *
 * @param topics the per-topic offset responses
 * @since 0.1.0
 */
public record ListOffsetsResponse(List<TopicResponse> topics) {

    /**
     * Per-topic offset response.
     *
     * @param name       the topic name
     * @param partitions the per-partition offset responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition offset response.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     * @param timestamp      the timestamp of the offset
     * @param offset         the offset
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long timestamp, long offset) {
    }
}
