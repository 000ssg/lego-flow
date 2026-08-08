package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetDelete response (API key 47).
 *
 * @param errorCode the top-level error code
 * @param topics    the per-topic results
 * @since 0.1.0
 */
public record OffsetDeleteResponse(short errorCode, List<TopicData> topics) {

    /**
     * Per-topic offset delete result.
     *
     * @param name       the topic name
     * @param partitions the per-partition results
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition offset delete result.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     */
    public record PartitionData(int partitionIndex, short errorCode) {
    }
}
