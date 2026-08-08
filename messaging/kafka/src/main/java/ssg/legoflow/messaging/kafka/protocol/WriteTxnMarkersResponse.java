package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * WriteTxnMarkers response (API key 27).
 *
 * @param markers per-marker results
 * @since 0.1.0
 */
public record WriteTxnMarkersResponse(List<MarkerResult> markers) {

    /**
     * Per-marker result.
     *
     * @param producerId the producer ID
     * @param topics     per-topic partition results
     */
    public record MarkerResult(long producerId, List<TopicResult> topics) {
    }

    /**
     * Per-topic result within a marker.
     *
     * @param topic      the topic name
     * @param partitions per-partition error codes
     */
    public record TopicResult(String topic, List<PartitionResult> partitions) {
    }

    /**
     * Per-partition result.
     *
     * @param partition the partition index
     * @param errorCode the error code
     */
    public record PartitionResult(int partition, short errorCode) {
    }
}
