package ssg.legoflow.network.cluster.core;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of a cluster node.
 *
 * Transitions:
 * ACTIVE -> SUSPECT (heartbeat miss)
 * SUSPECT -> ACTIVE (heartbeat received)
 * SUSPECT -> FAILED (heartbeat timeout)
 * ACTIVE -> LEAVING (graceful leave initiated)
 * LEAVING -> FAILED (leave timeout)
 * FAILED -> ACTIVE (rejoin after recovery)
 */
public enum ClusterNodeStatus {

    /**
     * Node is healthy and participating in the cluster.
     */
    ACTIVE,

    /**
     * Node has missed heartbeats; failure is suspected but not confirmed.
     */
    SUSPECT,

    /**
     * Node has failed (heartbeat timeout, crash, or partition).
     */
    FAILED,

    /**
     * Node is in the process of leaving gracefully.
     */
    LEAVING;

    private static final Map<ClusterNodeStatus, Set<ClusterNodeStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(SUSPECT, FAILED, LEAVING),
            SUSPECT, EnumSet.of(ACTIVE, FAILED),
            FAILED, EnumSet.of(ACTIVE),
            LEAVING, EnumSet.of(FAILED)
    );

    /**
     * Checks whether a transition from this status to the target is valid.
     *
     * @param target the target status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(ClusterNodeStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
