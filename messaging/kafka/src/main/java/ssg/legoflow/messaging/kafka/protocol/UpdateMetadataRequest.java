package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * UpdateMetadata request (API key 6).
 *
 * <p>Sent by the controller to update the metadata cache on brokers.
 *
 * @param controllerId    the controller's broker ID
 * @param controllerEpoch the controller epoch
 * @param liveBrokers     the set of live brokers
 * @param partitionStates the current partition assignments
 * @since 1.0.0
 */
public record UpdateMetadataRequest(int controllerId, int controllerEpoch,
                                    List<BrokerState> liveBrokers,
                                    List<PartitionState> partitionStates) {

    /**
     * Broker endpoint information.
     *
     * @param brokerId the broker ID
     * @param host     the broker host
     * @param port     the broker port
     */
    public record BrokerState(int brokerId, String host, int port) {
    }

    /**
     * Per-partition assignment state.
     *
     * @param topic       the topic name
     * @param partition   the partition index
     * @param leader      the leader broker ID
     * @param leaderEpoch the leader epoch
     * @param isr         the in-sync replica set
     * @param replicas    the full replica set
     */
    public record PartitionState(String topic, int partition, int leader,
                                 int leaderEpoch, List<Integer> isr, List<Integer> replicas) {
    }
}
