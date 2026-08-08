package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Fetch response (API key 1).
 *
 * @param throttleTimeMs the throttle time in milliseconds
 * @param topics         the per-topic responses
 * @since 0.1.0
 */
public record FetchResponse(int throttleTimeMs, List<TopicResponse> topics) {

    /**
     * Per-topic fetch response.
     *
     * @param name       the topic name
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition fetch response.
     *
     * @param partitionIndex    the partition index
     * @param errorCode         the error code
     * @param highWatermark     the high watermark offset
     * @param records           the raw record batch bytes (may be empty)
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long highWatermark,
                                    byte[] records) {
    }
}
