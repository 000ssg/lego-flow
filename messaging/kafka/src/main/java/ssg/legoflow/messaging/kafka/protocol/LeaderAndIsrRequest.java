package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;
import java.util.UUID;

/**
 * LeaderAndIsr request (API key 4).
 *
 * <p>Sent by the controller to brokers to update partition leadership and ISR sets.
 *
 * @param controllerId         the controller's broker ID
 * @param controllerEpoch      the controller epoch
 * @param brokerEpoch          the broker epoch (-1 if absent, v2+)
 * @param isKRaftController    true if KRaft controller ID is used (v7+)
 * @param type                 topic type (0=all topics, v5+)
 * @param partitionStates      per-partition states (v0-v1 only)
 * @param topicStates          per-topic states (v2+)
 * @param liveLeaders          current live leaders
 * @since 0.1.0
 */
public record LeaderAndIsrRequest(
        int controllerId,
        int controllerEpoch,
        long brokerEpoch,
        boolean isKRaftController,
        int type,
        List<PartitionState> partitionStates,
        List<TopicState> topicStates,
        List<LiveLeader> liveLeaders) {

    /** Default broker epoch value. */
    public static final long DEFAULT_BROKER_EPOCH = -1L;
    /** Default isKRaftController value. */
    public static final boolean DEFAULT_IS_KRAFT_CONTROLLER = false;
    /** Default type value (all topics). */
    public static final int DEFAULT_TYPE = 0;

    /**
     * Per-partition leadership state (v0-v1 ungrouped, v2+ grouped under TopicState).
     *
     * @param topic               topic name (null v2+)
     * @param partition           partition index
     * @param controllerEpoch     controller epoch
     * @param leader              leader broker ID
     * @param leaderEpoch         leader epoch
     * @param isr                 in-sync replica IDs
     * @param partitionEpoch      partition epoch (zkVersion)
     * @param replicas            replica IDs
     * @param isNew               whether replica should have existed (v1+)
     * @param addingReplicas      replicas being added (v3+, nullable)
     * @param removingReplicas    replicas being removed (v3+, nullable)
     * @param leaderRecoveryState unclean recovery state (v6+)
     */
    public record PartitionState(
            String topic,
            int partition,
            int controllerEpoch,
            int leader,
            int leaderEpoch,
            List<Integer> isr,
            int partitionEpoch,
            List<Integer> replicas,
            boolean isNew,
            List<Integer> addingReplicas,
            List<Integer> removingReplicas,
            int leaderRecoveryState) {

        /** Default partition epoch. */
        public static final int DEFAULT_PARTITION_EPOCH = 0;
        /** Default isNew value. */
        public static final boolean DEFAULT_IS_NEW = false;
        /** Default leaderRecoveryState value. */
        public static final int DEFAULT_LEADER_RECOVERY_STATE = 0;
    }

    /**
     * Per-topic state (v2+).
     *
     * @param topicName         topic name
     * @param topicId           topic UUID (null v0-v4)
     * @param partitionStates   per-partition states
     */
    public record TopicState(
            String topicName,
            UUID topicId,
            List<PartitionState> partitionStates) {
    }

    /**
     * Live leader information.
     *
     * @param brokerId  leader broker ID
     * @param hostName  leader hostname
     * @param port      leader port
     */
    public record LiveLeader(
            int brokerId,
            String hostName,
            int port) {
    }
}
