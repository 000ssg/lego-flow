package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteRecords response (API key 21).
 *
 * @param topics the per-topic results
 * @since 0.1.0
 */
public record DeleteRecordsResponse(List<TopicData> topics) {

    /**
     * Per-topic delete records result.
     *
     * @param name       the topic name
     * @param partitions the per-partition results
     */
    public record TopicData(String name, List<PartitionData> partitions) {
    }

    /**
     * Per-partition delete records result.
     *
     * @param partitionIndex the partition index
     * @param lowWatermark   the new low watermark after deletion
     * @param errorCode      the error code
     */
    public record PartitionData(int partitionIndex, long lowWatermark, short errorCode) {
    }
}
