package ssg.legoflow.http.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.hashing.ConsistentHashRing;
import ssg.legoflow.network.cluster.core.hashing.MurmurHash3;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Consistent-hash based router for sticky session fallback.
 *
 * <p>Wraps a {@link ConsistentHashRing} and provides a simple
 * API for updating nodes and looking up the target node for a key.
 *
 * @since 0.2.0
 */
public final class StickySessionHasher {

    private final ConsistentHashRing ring;
    private final int replicas;

    /**
     * Creates a hasher with the default replica count (160).
     */
    public StickySessionHasher() {
        this(160);
    }

    /**
     * Creates a hasher with the given replica count.
     *
     * @param replicas virtual nodes per physical node (1-1000)
     */
    public StickySessionHasher(int replicas) {
        this.replicas = replicas;
        this.ring = new ConsistentHashRing(replicas, MurmurHash3.INSTANCE);
    }

    /**
     * Updates the hash ring with the current node set.
     *
     * @param nodes the available nodes
     */
    public void updateNodes(Collection<ClusterNode> nodes) {
        Objects.requireNonNull(nodes);
        ring.clear();
        for (ClusterNode node : nodes) {
            ring.add(node);
        }
    }

    /**
     * Finds the node responsible for the given key.
     *
     * @param key the lookup key (e.g., request URI or user ID)
     * @return the responsible node, or null if no nodes
     */
    public ClusterNode getNode(String key) {
        Objects.requireNonNull(key);
        return ring.getNode(key);
    }

    /**
     * Finds the node responsible for the given key bytes.
     *
     * @param key the lookup key
     * @return the responsible node, or null if no nodes
     */
    public ClusterNode getNode(byte[] key) {
        Objects.requireNonNull(key);
        return ring.getNode(key);
    }

    /**
     * Returns all nodes on the ring.
     */
    public List<ClusterNode> getNodes() {
        return ring.getNodes();
    }

    /**
     * Returns the number of physical nodes on the ring.
     */
    public int nodeCount() {
        return ring.nodeCount();
    }

    /**
     * Returns the underlying hash ring.
     */
    public ConsistentHashRing ring() {
        return ring;
    }
}
