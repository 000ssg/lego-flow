package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * OffsetForLeaderEpoch response (API key 23).
 *
 * @param errorCode the top-level error code
 * @param topics    the per-topic results
 * @since 0.1.0
 */
public record OffsetForLeaderEpochResponse(short errorCode, List<TopicData> topics) {

    /**
     * Per-topic result data.
     *
     * @param topic          the topic name
     * @param epochEndOffsets the per-partition epoch end offsets
     */
    public record TopicData(String topic, List<EpochEndOffset> epochEndOffsets) {
    }

    /**
     * Per-partition epoch end offset.
     *
     * @param partition   the partition index
     * @param endOffset   the end offset for the requested epoch
     * @param errorCode   the error code for this partition
     */
    public record EpochEndOffset(int partition, long endOffset, short errorCode) {
    }
}