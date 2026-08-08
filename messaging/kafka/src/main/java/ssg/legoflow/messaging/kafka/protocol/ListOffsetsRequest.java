package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ListOffsets request (API key 2) for finding offsets by timestamp.
 *
 * @param topics the topics and partitions to query
 * @since 0.1.0
 */
public record ListOffsetsRequest(List<TopicOffsets> topics) {

    /** Timestamp constant for latest offset. */
    public static final long LATEST_TIMESTAMP = -1L;
    /** Timestamp constant for earliest offset. */
    public static final long EARLIEST_TIMESTAMP = -2L;

    /**
     * Per-topic offset request.
     *
     * @param name       the topic name
     * @param partitions the partition offset queries
     */
    public record TopicOffsets(String name, List<PartitionOffsets> partitions) {
    }

    /**
     * Per-partition offset request.
     *
     * @param partitionIndex the partition index
     * @param timestamp      the target timestamp (-1=latest, -2=earliest)
     */
    public record PartitionOffsets(int partitionIndex, long timestamp) {
    }
}
