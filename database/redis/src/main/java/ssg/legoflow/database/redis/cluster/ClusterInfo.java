package ssg.legoflow.database.redis.cluster;

import java.util.List;
import java.util.Objects;

/**
 * Cluster topology information for a Redis node.
 *
 * <p>In single-node mode (the default for this implementation),
 * one node owns all 16384 slots. This class provides the data
 * structures needed for clients to handle MOVED/ASK redirects.
 *
 * @since 0.1.0
 */
public final class ClusterInfo {

    /**
     * A cluster node descriptor.
     *
     * @param id    the node ID
     * @param host  the host address
     * @param port  the port
     * @param role  "master" or "replica"
     * @param slots slot ranges owned by this node
     */
    public record Node(String id, String host, int port, String role, List<SlotRange> slots) {}

    /**
     * A contiguous range of hash slots.
     *
     * @param start first slot (inclusive)
     * @param end   last slot (inclusive)
     */
    public record SlotRange(int start, int end) {
        /**
         * Returns whether the given slot is in this range.
         *
         * @param slot the slot number
         * @return true if contained
         */
        public boolean contains(int slot) {
            return slot >= start && slot <= end;
        }
    }

    /**
     * A MOVED or ASK redirect response.
     *
     * @param type  "MOVED" or "ASK"
     * @param slot  the hash slot
     * @param host  the target host
     * @param port  the target port
     */
    public record Redirect(String type, int slot, String host, int port) {
        /**
         * Parses a redirect from a RESP error message.
         *
         * @param errorMessage e.g. "MOVED 3999 127.0.0.1:6380"
         * @return the parsed redirect, or null if not a redirect
         */
        public static Redirect parse(String errorMessage) {
            if (errorMessage == null) return null;
            String[] parts = errorMessage.split("\\s+");
            if (parts.length < 3) return null;
            String type = parts[0];
            if (!"MOVED".equals(type) && !"ASK".equals(type)) return null;
            int slot = Integer.parseInt(parts[1]);
            String[] hostPort = parts[2].split(":");
            return new Redirect(type, slot, hostPort[0], Integer.parseInt(hostPort[1]));
        }
    }

    /**
     * Creates a single-node cluster info.
     *
     * @param host the node host
     * @param port the node port
     * @return cluster info with one master owning all slots
     */
    public static ClusterInfo singleNode(String host, int port) {
        Node node = new Node("legoflow-node-0001", host, port, "master",
                List.of(new SlotRange(0, HashSlot.TOTAL_SLOTS - 1)));
        return new ClusterInfo(List.of(node));
    }

    private final List<Node> nodes;

    /**
     * Creates cluster info with the given nodes.
     *
     * @param nodes the cluster nodes
     */
    public ClusterInfo(List<Node> nodes) {
        this.nodes = Objects.requireNonNull(nodes);
    }

    /**
     * Returns all nodes in the cluster.
     *
     * @return node list
     */
    public List<Node> nodes() {
        return nodes;
    }

    /**
     * Finds the node that owns the given slot.
     *
     * @param slot the hash slot
     * @return the owning node, or null
     */
    public Node nodeForSlot(int slot) {
        for (Node node : nodes) {
            for (SlotRange range : node.slots()) {
                if (range.contains(slot)) {
                    return node;
                }
            }
        }
        return null;
    }
}
