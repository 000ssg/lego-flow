package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * WriteTxnMarkers request (API key 27).
 *
 * <p>Writes transaction markers (commit/abort) to partition logs.
 *
 * @param markers the transaction markers to write
 * @since 0.1.0
 */
public record WriteTxnMarkersRequest(List<TxnMarker> markers) {

    /**
     * A single transaction marker.
     *
     * @param producerId       the producer ID
     * @param producerEpoch    the producer epoch
     * @param coordinatorEpoch the transaction coordinator epoch
     * @param committed        true if committing, false if aborting
     * @param partitions       the partitions to write the marker to
     */
    public record TxnMarker(long producerId, short producerEpoch, int coordinatorEpoch,
                            boolean committed, List<TopicPartitionData> partitions) {
    }

    /**
     * A topic-partition for the marker.
     *
     * @param topic     the topic name
     * @param partition the partition index
     */
    public record TopicPartitionData(String topic, int partition) {
    }
}
