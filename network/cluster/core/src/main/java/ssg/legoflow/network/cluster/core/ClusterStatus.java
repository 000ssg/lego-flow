package ssg.legoflow.network.cluster.core;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only view of the current cluster state.
 *
 * Provides a snapshot of all member nodes, the leader (if elected),
 * and aggregate health metrics.
 */
public final class ClusterStatus {

    private final List<ClusterNode> members;
    private final ClusterNode leader;

    private ClusterStatus(List<ClusterNode> members, ClusterNode leader) {
        this.members = List.copyOf(members);
        this.leader = leader;
    }

    /**
     * Creates a status from the current member list.
     *
     * @param members the current members
     * @return a new ClusterStatus
     */
    public static ClusterStatus of(Collection<ClusterNode> members) {
        return new ClusterStatus(
                members.stream()
                        .filter(n -> n.status() == ClusterNodeStatus.ACTIVE)
                        .toList(),
                null);
    }

    /**
     * Creates a status with an explicit leader.
     *
     * @param members the current members
     * @param leader  the current leader (may be null)
     * @return a new ClusterStatus
     */
    public static ClusterStatus of(Collection<ClusterNode> members, ClusterNode leader) {
        return new ClusterStatus(
                members.stream()
                        .filter(n -> n.status() == ClusterNodeStatus.ACTIVE)
                        .toList(),
                leader);
    }

    /**
     * The total number of active members.
     */
    public int memberCount() {
        return members.size();
    }

    /**
     * All active members.
     */
    public List<ClusterNode> members() {
        return members;
    }

    /**
     * The current leader, or null if no leader has been elected.
     */
    public ClusterNode leader() {
        return leader;
    }

    /**
     * Whether a leader has been elected.
     */
    public boolean hasLeader() {
        return leader != null;
    }

    /**
     * Number of members with SUSPECT status.
     */
    public int suspectCount(Collection<ClusterNode> allNodes) {
        return (int) allNodes.stream()
                .filter(n -> n.status() == ClusterNodeStatus.SUSPECT)
                .count();
    }

    /**
     * Number of members with FAILED status.
     */
    public int failedCount(Collection<ClusterNode> allNodes) {
        return (int) allNodes.stream()
                .filter(n -> n.status() == ClusterNodeStatus.FAILED)
                .count();
    }

    /**
     * Whether all members are healthy (no suspect or failed nodes).
     */
    public boolean isHealthy(Collection<ClusterNode> allNodes) {
        return suspectCount(allNodes) == 0 && failedCount(allNodes) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterStatus that = (ClusterStatus) o;
        return members.equals(that.members)
                && Objects.equals(leader, that.leader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, leader);
    }

    @Override
    public String toString() {
        return "ClusterStatus{members=" + members.size()
                + (leader != null ? ", leader=" + leader.id() : "")
                + '}';
    }
}
