package ssg.legoflow.network.cluster.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed hierarchy of cluster membership events.
 *
 * Each event subtype carries the source node, a timestamp, and event-specific data.
 */
public sealed interface ClusterEvent
        permits ClusterEvent.NodeJoined,
                ClusterEvent.NodeLeft,
                ClusterEvent.NodeFailed,
                ClusterEvent.NodeRecovered,
                ClusterEvent.LeaderChanged {

    /**
     * The node that triggered this event.
     */
    ClusterNode sourceNode();

    /**
     * The time at which this event occurred.
     */
    Instant timestamp();

    /**
     * A node has joined the cluster.
     */
    record NodeJoined(ClusterNode node, Instant timestamp) implements ClusterEvent {
        public NodeJoined {
            Objects.requireNonNull(node, "node must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        @Override
        public ClusterNode sourceNode() {
            return node;
        }
    }

    /**
     * A node has left the cluster gracefully.
     */
    record NodeLeft(ClusterNode node, Instant timestamp) implements ClusterEvent {
        public NodeLeft {
            Objects.requireNonNull(node, "node must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        @Override
        public ClusterNode sourceNode() {
            return node;
        }
    }

    /**
     * A node has failed (heartbeat timeout or crash).
     */
    record NodeFailed(ClusterNode node, Instant timestamp, String reason) implements ClusterEvent {
        public NodeFailed {
            Objects.requireNonNull(node, "node must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        @Override
        public ClusterNode sourceNode() {
            return node;
        }
    }

    /**
     * A previously failed node has recovered and rejoined.
     */
    record NodeRecovered(ClusterNode node, Instant timestamp) implements ClusterEvent {
        public NodeRecovered {
            Objects.requireNonNull(node, "node must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        @Override
        public ClusterNode sourceNode() {
            return node;
        }
    }

    /**
     * The cluster leader has changed.
     */
    record LeaderChanged(ClusterNode previousLeader, ClusterNode newLeader, Instant timestamp) implements ClusterEvent {
        public LeaderChanged {
            Objects.requireNonNull(previousLeader, "previousLeader must not be null");
            Objects.requireNonNull(newLeader, "newLeader must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }

        @Override
        public ClusterNode sourceNode() {
            return newLeader;
        }
    }
}
