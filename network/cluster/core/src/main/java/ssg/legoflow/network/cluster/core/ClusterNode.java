package ssg.legoflow.network.cluster.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable descriptor of a cluster node.
 *
 * A ClusterNode uniquely identifies a member of the cluster with a stable ID,
 * network address, role, status, and arbitrary metadata.
 */
public final class ClusterNode {

    private final String id;
    private final String host;
    private final int port;
    private final ClusterRole role;
    private final ClusterNodeStatus status;
    private final Map<String, String> metadata;

    private ClusterNode(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.host = Objects.requireNonNull(builder.host, "host must not be null");
        this.port = builder.port;
        this.role = Objects.requireNonNull(builder.role, "role must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.metadata = Collections.unmodifiableMap(Map.copyOf(builder.metadata));
    }

    /**
     * Creates a builder with a random UUID and default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a node from an InetAddress with auto-generated ID.
     *
     * @param address the node's network address
     * @param port    the node's port
     * @param role    the node's role
     * @return a new ClusterNode
     * @throws NullPointerException if address or role is null
     */
    public static ClusterNode fromAddress(InetAddress address, int port, ClusterRole role) {
        return new Builder()
                .id(UUID.randomUUID().toString())
                .host(address.getHostAddress())
                .port(port)
                .role(role)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    public String id() {
        return id;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public ClusterRole role() {
        return role;
    }

    public ClusterNodeStatus status() {
        return status;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Returns a new node with the updated status.
     *
     * @param newStatus the new status
     * @return a new ClusterNode with the updated status
     * @throws IllegalArgumentException if the transition is invalid
     */
    public ClusterNode withStatus(ClusterNodeStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid transition: " + this.status + " -> " + newStatus);
        }
        return new Builder(this)
                .status(newStatus)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterNode that = (ClusterNode) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ClusterNode{id='" + id + "', host='" + host + "', port=" + port
                + ", role=" + role + ", status=" + status + '}';
    }

    /**
     * Builder for ClusterNode.
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String host = "127.0.0.1";
        private int port = 0;
        private ClusterRole role = ClusterRole.BOTH;
        private ClusterNodeStatus status = ClusterNodeStatus.ACTIVE;
        private final java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();

        Builder() {}

        Builder(ClusterNode source) {
            this.id = source.id;
            this.host = source.host;
            this.port = source.port;
            this.role = source.role;
            this.status = source.status;
            this.metadata.putAll(source.metadata);
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder host(InetAddress address) throws UnknownHostException {
            this.host = address.getHostAddress();
            return this;
        }

        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535");
            }
            this.port = port;
            return this;
        }

        public Builder role(ClusterRole role) {
            this.role = role;
            return this;
        }

        public Builder status(ClusterNodeStatus status) {
            this.status = status;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public ClusterNode build() {
            return new ClusterNode(this);
        }
    }
}
