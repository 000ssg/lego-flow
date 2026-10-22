package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * UpdateMetadata request (API key 6).
 *
 * <p>Sent by the controller to update the metadata cache on brokers.
 *
 * @param controllerId      the controller's broker ID
 * @param controllerEpoch   the controller epoch
 * @param brokerEpoch       the broker epoch (-1 if absent, v5+)
 * @param isKRaftController true if KRaft controller ID is used (v8+)
 * @param type              update type (0=all topics, v8+)
 * @param partitionStates   per-partition states (v0-v4, flat)
 * @param topicStates       per-topic states (v5+)
 * @param liveBrokers       current live brokers
 * @since 0.1.0
 */
public record UpdateMetadataRequest(
        int controllerId,
        int controllerEpoch,
        long brokerEpoch,
        boolean isKRaftController,
        int type,
        List<PartitionState> partitionStates,
        List<TopicState> topicStates,
        List<BrokerState> liveBrokers) {

    /** Default broker epoch value. */
    public static final long DEFAULT_BROKER_EPOCH = -1L;
    /** Default isKRaftController value. */
    public static final boolean DEFAULT_IS_KRAFT_CONTROLLER = false;
    /** Default type value (all topics). */
    public static final int DEFAULT_TYPE = 0;

    /**
     * Per-partition state (v0-v4 flat, v5+ grouped under TopicState).
     *
     * @param topic           topic name (null v5+)
     * @param partition       partition index
     * @param controllerEpoch controller epoch
     * @param leader          leader broker ID
     * @param leaderEpoch     leader epoch
     * @param isr             in-sync replica IDs
     * @param zkVersion       ZooKeeper version
     * @param replicas        replica IDs
     * @param offlineReplicas offline replica IDs (v4+, nullable)
     */
    public record PartitionState(
            String topic,
            int partition,
            int controllerEpoch,
            int leader,
            int leaderEpoch,
            List<Integer> isr,
            int zkVersion,
            List<Integer> replicas,
            List<Integer> offlineReplicas) {

        /** Default zkVersion. */
        public static final int DEFAULT_ZK_VERSION = 0;
    }

    /**
     * Per-topic state (v5+).
     *
     * @param topicName       topic name
     * @param partitionStates per-partition states
     */
    public record TopicState(
            String topicName,
            List<PartitionState> partitionStates) {
    }

    /**
     * Live broker information.
     *
     * @param brokerId leader broker ID
     * @param host     broker hostname
     * @param port     broker port
     */
    public record BrokerState(
            int brokerId,
            String host,
            int port) {
    }
}
