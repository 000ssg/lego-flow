package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * StopReplica request (API key 5).
 *
 * <p>Sent by the controller to stop replicating partitions on a broker.
 *
 * @param controllerId         the controller's broker ID
 * @param controllerEpoch      the controller epoch
 * @param brokerEpoch          the broker epoch (-1 if absent, v1+)
 * @param isKRaftController    true if KRaft controller ID is used (v4+)
 * @param deletePartitions     whether to delete the partition data (v0-v2)
 * @param partitions           per-partition data (v0 only)
 * @param topics               per-topic data (v1-v2)
 * @param topicStates          per-topic states (v3+)
 * @since 0.1.0
 */
public record StopReplicaRequest(
        int controllerId,
        int controllerEpoch,
        long brokerEpoch,
        boolean isKRaftController,
        boolean deletePartitions,
        List<TopicPartitionData> partitions,
        List<TopicData> topics,
        List<TopicState> topicStates) {

    /** Default broker epoch value. */
    public static final long DEFAULT_BROKER_EPOCH = -1L;
    /** Default isKRaftController value. */
    public static final boolean DEFAULT_IS_KRAFT_CONTROLLER = false;

    /**
     * Per-partition data (v0).
     *
     * @param topic     the topic name
     * @param partition the partition index
     */
    public record TopicPartitionData(String topic, int partition) {
    }

    /**
     * Per-topic data (v1-v2).
     *
     * @param topicName        the topic name
     * @param partitionIndexes the partition indexes
     */
    public record TopicData(String topicName, List<Integer> partitionIndexes) {
    }

    /**
     * Per-topic state (v3+).
     *
     * @param topicName       the topic name
     * @param partitionStates per-partition states
     */
    public record TopicState(String topicName, List<PartitionState> partitionStates) {
    }

    /**
     * Per-partition state (v3+).
     *
     * @param partition      the partition index
     * @param leaderEpoch    the leader epoch (-1 if absent)
     * @param deletePartition whether to delete the partition
     */
    public record PartitionState(int partition, int leaderEpoch, boolean deletePartition) {
        /** Default leader epoch value. */
        public static final int DEFAULT_LEADER_EPOCH = -1;
    }
}
