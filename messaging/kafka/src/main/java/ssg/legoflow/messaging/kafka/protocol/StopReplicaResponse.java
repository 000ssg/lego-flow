package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * StopReplica response (API key 5).
 *
 * @param errorCode  the top-level error code
 * @param partitions per-partition results
 * @since 0.1.0
 */
public record StopReplicaResponse(short errorCode, List<PartitionResult> partitions) {

    /**
     * Per-partition result.
     *
     * @param topic     the topic name
     * @param partition the partition index
     * @param errorCode the partition-level error code
     */
    public record PartitionResult(String topic, int partition, short errorCode) {
    }
}
