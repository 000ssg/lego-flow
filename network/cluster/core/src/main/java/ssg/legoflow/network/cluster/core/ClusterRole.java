package ssg.legoflow.network.cluster.core;

/**
 * Role of a node within the cluster.
 */
public enum ClusterRole {

    /**
     * Node serves cluster traffic (e.g., HTTP server, gRPC backend).
     */
    SERVER,

    /**
     * Node consumes cluster services but does not serve traffic.
     */
    CLIENT,

    /**
     * Node both serves and consumes cluster services.
     */
    BOTH
}
