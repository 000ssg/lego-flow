package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * LeaderAndIsr request (API key 4).
 *
 * <p>Sent by the controller to brokers to update partition leadership and ISR sets.
 *
 * @param controllerId     the controller's broker ID
 * @param controllerEpoch  the controller epoch
 * @param partitionStates  per-partition leadership state
 * @since 0.1.0
 */
public record LeaderAndIsrRequest(int controllerId, int controllerEpoch,
                                  List<PartitionState> partitionStates) {

    /**
     * Per-partition leadership state.
     *
     * @param topic       the topic name
     * @param partition   the partition index
     * @param leader      the leader broker ID
     * @param leaderEpoch the leader epoch
     * @param isr         the in-sync replica set
     */
    public record PartitionState(String topic, int partition, int leader,
                                 int leaderEpoch, List<Integer> isr) {
    }
}
