package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-broker replica state manager.
 *
 * <p>Tracks partition leadership, ISR sets, leader epochs, and watermarks
 * for all replicas hosted on a single broker.
 *
 * @since 1.0.0
 */
public final class ReplicaManager {

    /**
     * The state of a single replica on this broker.
     *
     * @param leaderBrokerId the current leader broker ID
     * @param leaderEpoch    the current leader epoch
     * @param highWatermark  the high watermark offset
     * @param logEndOffset   the log end offset
     * @param isr            the in-sync replica set
     */
    public record ReplicaState(int leaderBrokerId, int leaderEpoch, long highWatermark,
                               long logEndOffset, List<Integer> isr) {
    }

    private final int brokerId;
    private final Map<TopicPartition, ReplicaState> replicaStates = new ConcurrentHashMap<>();

    /**
     * Creates a new replica manager for the given broker.
     *
     * @param brokerId the broker ID
     */
    public ReplicaManager(int brokerId) {
        this.brokerId = brokerId;
    }

    /**
     * Returns the broker ID.
     *
     * @return the broker ID
     */
    public int brokerId() {
        return brokerId;
    }

    /**
     * Updates the leader and ISR for a partition.
     *
     * @param tp          the topic-partition
     * @param leaderId    the new leader broker ID
     * @param leaderEpoch the new leader epoch
     * @param isr         the new in-sync replica set
     */
    public void updateLeaderAndIsr(TopicPartition tp, int leaderId, int leaderEpoch, List<Integer> isr) {
        ReplicaState existing = replicaStates.get(tp);
        long hw = existing != null ? existing.highWatermark() : 0;
        long leo = existing != null ? existing.logEndOffset() : 0;
        replicaStates.put(tp, new ReplicaState(leaderId, leaderEpoch, hw, leo, List.copyOf(isr)));
    }

    /**
     * Stops replicating a partition, optionally removing its state entirely.
     *
     * @param tp              the topic-partition
     * @param deletePartition whether to delete the partition state
     */
    public void stopReplica(TopicPartition tp, boolean deletePartition) {
        if (deletePartition) {
            replicaStates.remove(tp);
        }
    }

    /**
     * Returns the replica state for a partition.
     *
     * @param tp the topic-partition
     * @return the replica state, or null if not tracked
     */
    public ReplicaState getReplicaState(TopicPartition tp) {
        return replicaStates.get(tp);
    }

    /**
     * Returns whether this broker is the leader for the given partition.
     *
     * @param tp the topic-partition
     * @return true if this broker is the leader
     */
    public boolean isLeader(TopicPartition tp) {
        ReplicaState state = replicaStates.get(tp);
        return state != null && state.leaderBrokerId() == brokerId;
    }

    /**
     * Returns the leader epoch for a partition.
     *
     * @param tp the topic-partition
     * @return the leader epoch, or -1 if not tracked
     */
    public int leaderEpoch(TopicPartition tp) {
        ReplicaState state = replicaStates.get(tp);
        return state != null ? state.leaderEpoch() : -1;
    }

    /**
     * Returns all tracked replica states.
     *
     * @return an unmodifiable view of all replica states
     */
    public Map<TopicPartition, ReplicaState> allReplicas() {
        return Collections.unmodifiableMap(replicaStates);
    }

    /**
     * Returns the log end offset for a given leader epoch.
     *
     * <p>In this simplified implementation, if the requested epoch matches
     * or is less than the current epoch, the current log end offset is returned.
     * If the epoch is unknown, returns -1.
     *
     * @param tp             the topic-partition
     * @param requestedEpoch the leader epoch to query
     * @return the log end offset at that epoch, or -1 if unknown
     */
    public long offsetForLeaderEpoch(TopicPartition tp, int requestedEpoch) {
        ReplicaState state = replicaStates.get(tp);
        if (state == null) return -1;
        if (requestedEpoch <= state.leaderEpoch()) {
            return state.logEndOffset();
        }
        return -1;
    }

    /**
     * Updates the log end offset and high watermark for a partition.
     *
     * @param tp            the topic-partition
     * @param logEndOffset  the new log end offset
     * @param highWatermark the new high watermark
     */
    public void updateOffsets(TopicPartition tp, long logEndOffset, long highWatermark) {
        ReplicaState existing = replicaStates.get(tp);
        if (existing != null) {
            replicaStates.put(tp, new ReplicaState(
                    existing.leaderBrokerId(), existing.leaderEpoch(),
                    highWatermark, logEndOffset, existing.isr()));
        }
    }
}
