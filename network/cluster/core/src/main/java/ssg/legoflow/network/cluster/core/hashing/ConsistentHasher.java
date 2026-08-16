package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterMembership;
import ssg.legoflow.network.cluster.core.ClusterStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Consistent hashing utility that maps keys to cluster nodes.
 *
 * Integrates with ClusterMembership to maintain an up-to-date hash ring
 * and rehash keys when membership changes.
 */
public final class ConsistentHasher {

    private final ConsistentHashRing ring;
    private final ClusterMembership membership;
    private final Map<String, ClusterNode> keyAssignments = new ConcurrentHashMap<>();

    /**
     * Creates a hasher backed by a ring with default settings.
     *
     * @param membership the cluster membership to sync with
     */
    public ConsistentHasher(ClusterMembership membership) {
        this(membership, 160, MurmurHash3.INSTANCE);
    }

    /**
     * Creates a hasher with the given replica count and hash function.
     *
     * @param membership   the cluster membership to sync with
     * @param replicas     virtual nodes per physical node
     * @param hashFunction the hash function to use
     */
    public ConsistentHasher(ClusterMembership membership, int replicas, HashFunction hashFunction) {
        this.membership = membership;
        this.ring = new ConsistentHashRing(replicas, hashFunction);
        syncMembers();
    }

    /**
     * Creates a standalone hasher (not linked to membership).
     *
     * @param nodes        the initial nodes
     * @param replicas     virtual nodes per physical node
     * @param hashFunction the hash function
     */
    public static ConsistentHasher standalone(Collection<ClusterNode> nodes,
                                               int replicas, HashFunction hashFunction) {
        ConsistentHashRing ring = new ConsistentHashRing(replicas, hashFunction);
        for (ClusterNode node : nodes) {
            ring.add(node);
        }
        return new ConsistentHasher(ring);
    }

    ConsistentHasher(ConsistentHashRing ring) {
        this.ring = ring;
        this.membership = null;
    }

    /**
     * Finds the node responsible for the given key.
     *
     * @param key the lookup key
     * @return the responsible node, or null if no nodes available
     */
    public ClusterNode getNode(String key) {
        return ring.getNode(key);
    }

    /**
     * Finds the node responsible for the given byte array key.
     *
     * @param key the lookup key
     * @return the responsible node, or null if no nodes available
     */
    public ClusterNode getNode(byte[] key) {
        return ring.getNode(key);
    }

    /**
     * Syncs the hash ring with current membership.
     * Called automatically when membership events are received.
     */
    public void syncMembers() {
        if (membership != null) {
            ClusterStatus status = membership.status();
            ring.clear();
            for (ClusterNode node : status.members()) {
                ring.add(node);
            }
        }
    }

    /**
     * Repartitions all tracked keys after membership change.
     * Returns a map of keys to their new assigned nodes.
     *
     * @param keys the keys to repartition
     * @return a map of key -> new node
     */
    public Map<String, ClusterNode> repartition(Collection<String> keys) {
        Map<String, ClusterNode> assignments = new ConcurrentHashMap<>();
        for (String key : keys) {
            ClusterNode node = getNode(key);
            if (node != null) {
                assignments.put(key, node);
            }
        }
        return assignments;
    }

    /**
     * Returns the underlying hash ring.
     */
    public ConsistentHashRing ring() {
        return ring;
    }

    /**
     * Returns the number of nodes on the ring.
     */
    public int nodeCount() {
        return ring.nodeCount();
    }
}
