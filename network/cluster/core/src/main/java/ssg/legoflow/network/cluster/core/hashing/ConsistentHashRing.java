package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
/**
 * Consistent hash ring using the Ketama algorithm.
 *
 * Each physical node is mapped to a configurable number of virtual nodes
 * (replicas) on a 32-bit hash ring. Key lookups find the next virtual node
 * clockwise on the ring.
 *
 * With 160 virtual replicas per node, adding or removing a single node
 * redistributes at most ~1/N of keys (where N is the number of nodes).
 */
public final class ConsistentHashRing {

    private static final int DEFAULT_REPLICAS = 160;
    private static final int MAX_REPLICAS = 1000;

    private final HashFunction hashFunction;
    private final int replicas;
    private final NavigableMap<Long, ClusterNode> ring = new TreeMap<>();
    private final List<KetamaNodeAddress> virtualNodes = new ArrayList<>();

    /**
     * Creates a ring with default settings (160 replicas, MurmurHash3).
     */
    public ConsistentHashRing() {
        this(DEFAULT_REPLICAS, MurmurHash3.INSTANCE);
    }

    /**
     * Creates a ring with the given replica count and hash function.
     *
     * @param replicas     virtual nodes per physical node (10-1000)
     * @param hashFunction the hash function
     */
    public ConsistentHashRing(int replicas, HashFunction hashFunction) {
        if (replicas < 1) {
            throw new IllegalArgumentException("replicas must be >= 1");
        }
        if (replicas > MAX_REPLICAS) {
            throw new IllegalArgumentException("replicas must be <= " + MAX_REPLICAS);
        }
        this.replicas = replicas;
        this.hashFunction = Objects.requireNonNull(hashFunction);
    }

    /**
     * Adds a node to the ring, creating virtual node entries.
     *
     * @param node the node to add
     */
    public void add(ClusterNode node) {
        Objects.requireNonNull(node);
        remove(node); // idempotent

        for (int i = 0; i < replicas; i++) {
            KetamaNodeAddress vNode = new KetamaNodeAddress(node, i, hashFunction);
            ring.put(vNode.hash(), node);
            virtualNodes.add(vNode);
        }
    }

    /**
     * Removes a node from the ring, deleting all its virtual nodes.
     *
     * @param node the node to remove
     * @return true if the node was present
     */
    public boolean remove(ClusterNode node) {
        Objects.requireNonNull(node);
        boolean removed = false;
        Iterator<Map.Entry<Long, ClusterNode>> it = ring.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ClusterNode> entry = it.next();
            if (entry.getValue().id().equals(node.id())) {
                it.remove();
                removed = true;
            }
        }
        virtualNodes.removeIf(v -> v.node().id().equals(node.id()));
        return removed;
    }

    /**
     * Clears all nodes from the ring.
     */
    public void clear() {
        ring.clear();
        virtualNodes.clear();
    }

    /**
     * Finds the node responsible for the given key.
     *
     * @param key the lookup key
     * @return the responsible node, or null if the ring is empty
     */
    public ClusterNode getNode(String key) {
        if (ring.isEmpty()) return null;

        long hash = hashFunction.hash(key);
        NavigableMap<Long, ClusterNode> tail = ring.tailMap(hash, true);

        if (tail.isEmpty()) {
            return ring.firstEntry().getValue();
        }
        return tail.firstEntry().getValue();
    }

    /**
     * Finds the node responsible for the given byte array key.
     *
     * @param key the lookup key
     * @return the responsible node, or null if the ring is empty
     */
    public ClusterNode getNode(byte[] key) {
        if (ring.isEmpty()) return null;

        long hash = hashFunction.hash(key);
        NavigableMap<Long, ClusterNode> tail = ring.tailMap(hash, true);

        if (tail.isEmpty()) {
            return ring.firstEntry().getValue();
        }
        return tail.firstEntry().getValue();
    }

    /**
     * Returns all nodes currently on the ring.
     */
    public List<ClusterNode> getNodes() {
        return new ArrayList<>(ring.values().stream().distinct().toList());
    }

    /**
     * Returns the number of physical nodes on the ring.
     */
    public int nodeCount() {
        return (int) ring.values().stream().distinct().count();
    }

    /**
     * Returns the total number of virtual nodes on the ring.
     */
    public int virtualNodeCount() {
        return virtualNodes.size();
    }

    /**
     * Returns the configured number of virtual replicas per physical node.
     */
    public int replicas() {
        return replicas;
    }

    /**
     * Returns the hash function used by this ring.
     */
    public HashFunction hashFunction() {
        return hashFunction;
    }

    /**
     * Returns an unmodifiable view of the ring.
     */
    public NavigableMap<Long, ClusterNode> ring() {
        return Collections.unmodifiableNavigableMap(ring);
    }

    @Override
    public String toString() {
        return "ConsistentHashRing{nodes=" + nodeCount()
                + ", virtualNodes=" + virtualNodeCount()
                + ", replicas=" + replicas + '}';
    }
}
