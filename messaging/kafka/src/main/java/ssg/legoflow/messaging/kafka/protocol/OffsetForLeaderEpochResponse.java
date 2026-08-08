package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetForLeaderEpoch response (API key 23).
 *
 * @param topics the per-topic results
 * @since 0.1.0
 */
public record OffsetForLeaderEpochResponse(List<TopicData> topics) {

    /**
     * Per-topic result data.
     *
     * @param topic      the topic name
     * @param partitions the per-partition results
     */
    public record TopicData(String topic, List<PartitionData> partitions) {
    }

    /**
     * Per-partition result data.
     *
     * @param errorCode   the error code
     * @param partition   the partition index
     * @param leaderEpoch the leader epoch
     * @param endOffset   the end offset for the requested epoch
     */
    public record PartitionData(short errorCode, int partition, int leaderEpoch, long endOffset) {
    }
}
