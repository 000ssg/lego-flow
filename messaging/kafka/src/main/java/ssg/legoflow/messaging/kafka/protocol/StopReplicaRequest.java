package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * StopReplica request (API key 5).
 *
 * <p>Sent by the controller to stop replicating partitions on a broker.
 *
 * @param controllerId    the controller's broker ID
 * @param controllerEpoch the controller epoch
 * @param deletePartitions whether to delete the partition data
 * @param partitions       the partitions to stop replicating
 * @since 0.1.0
 */
public record StopReplicaRequest(int controllerId, int controllerEpoch,
                                 boolean deletePartitions, List<TopicPartitionData> partitions) {

    /**
     * Per-partition data.
     *
     * @param topic     the topic name
     * @param partition the partition index
     */
    public record TopicPartitionData(String topic, int partition) {
    }
}
