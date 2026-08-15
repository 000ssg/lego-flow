package ssg.legoflow.network.cluster.core;

import java.util.concurrent.CompletableFuture;

/**
 * Service Provider Interface for cluster membership management.
 *
 * Implementations provide node discovery, membership tracking, and
 * lifecycle management for a specific discovery protocol
 * (e.g., DNS-SD, etcd, gRPC resolver).
 */
public interface ClusterMembership extends AutoCloseable {

    /**
     * Returns the local node that this membership represents.
     */
    ClusterNode localNode();

    /**
     * Returns a read-only view of the current cluster state.
     */
    ClusterStatus status();

    /**
     * Registers a listener for cluster events.
     *
     * @param listener the listener to register
     */
    void addListener(ClusterEventListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(ClusterEventListener listener);

    /**
     * Initiates a graceful leave from the cluster.
     * Broadcasts a goodbye message and notifies listeners.
     */
    void leave();

    /**
     * Initiates a graceful leave and returns a future completed
     * when the leave is acknowledged.
     *
     * @return a future completed when the leave is complete
     */
    default CompletableFuture<Void> leaveAsync() {
        leave();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Notifies all registered listeners of an event.
     * Implementations call this when membership changes occur.
     *
     * @param event the event to broadcast
     */
    default void fireEvent(ClusterEvent event) {
        // Overridden by implementations
    }

    @Override
    void close();
}
