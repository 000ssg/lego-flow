package ssg.legoflow.network.cluster.core;

/**
 * Functional interface for receiving cluster membership events.
 *
 * Implementations are notified when nodes join, leave, fail, recover,
 * or when leadership changes.
 */
@FunctionalInterface
public interface ClusterEventListener {

    /**
     * Called when a cluster event occurs.
     *
     * @param event the cluster event
     */
    void onEvent(ClusterEvent event);

    /**
     * Convenience: creates a listener that only handles NodeJoined events.
     *
     * @param handler the handler for joined nodes
     * @return a listener for NodeJoined events
     */
    static ClusterEventListener onJoin(java.util.function.Consumer<ClusterNode> handler) {
        return event -> {
            if (event instanceof ClusterEvent.NodeJoined joined) {
                handler.accept(joined.node());
            }
        };
    }

    /**
     * Convenience: creates a listener that only handles NodeLeft events.
     *
     * @param handler the handler for left nodes
     * @return a listener for NodeLeft events
     */
    static ClusterEventListener onLeave(java.util.function.Consumer<ClusterNode> handler) {
        return event -> {
            if (event instanceof ClusterEvent.NodeLeft left) {
                handler.accept(left.node());
            }
        };
    }

    /**
     * Convenience: creates a listener that only handles NodeFailed events.
     *
     * @param handler the handler for failed nodes
     * @return a listener for NodeFailed events
     */
    static ClusterEventListener onFailure(java.util.function.Consumer<ClusterNode> handler) {
        return event -> {
            if (event instanceof ClusterEvent.NodeFailed failed) {
                handler.accept(failed.node());
            }
        };
    }
}
