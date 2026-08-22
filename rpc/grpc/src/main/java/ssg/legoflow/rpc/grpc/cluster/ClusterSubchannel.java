package ssg.legoflow.rpc.grpc.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;
/**
 * A subchannel represents a connection to one gRPC backend.
 *
 * <p>Tracks health status, in-flight request count, and the target node.
 *
 * @param node        the backend node
 * @param health      current health status
 * @param inFlight    number of in-flight requests
 * @since 0.2.0
 */
public record ClusterSubchannel(
        ClusterNode node,
        HealthStatus health,
        int inFlight
) {
    /**
     * Creates a new subchannel for the given node.
     *
     * @param node the backend node
     * @return a new subchannel in SERVING state
     */
    public static ClusterSubchannel of(ClusterNode node) {
        return new ClusterSubchannel(node, HealthStatus.SERVING, 0);
    }

    /**
     * Returns a copy with updated health status.
     *
     * @param newHealth the new health status
     * @return updated subchannel
     */
    public ClusterSubchannel withHealth(HealthStatus newHealth) {
        return new ClusterSubchannel(node, newHealth, inFlight);
    }

    /**
     * Returns a copy with in-flight count incremented.
     *
     * @return updated subchannel
     */
    public ClusterSubchannel inFlightInc() {
        return new ClusterSubchannel(node, health, inFlight + 1);
    }

    /**
     * Returns a copy with in-flight count decremented.
     *
     * @return updated subchannel
     */
    public ClusterSubchannel inFlightDec() {
        return new ClusterSubchannel(node, health, Math.max(0, inFlight - 1));
    }

    /**
     * Whether this subchannel is healthy for receiving requests.
     *
     * @return true if SERVING
     */
    public boolean isHealthy() {
        return health == HealthStatus.SERVING;
    }
}
