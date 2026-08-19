package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterNode;
import java.util.Objects;
/**
 * A virtual node on the consistent hash ring (Ketama-style).
 *
 * Each physical node is mapped to N virtual nodes by hashing
 * "nodeName:replicaIndex". Virtual nodes are placed on the ring
 * at positions determined by their hash value.
 */
public final class KetamaNodeAddress {

    private final ClusterNode node;
    private final int replicaIndex;
    private final long hash;

    /**
     * Creates a virtual node address.
     *
     * @param node         the physical node
     * @param replicaIndex the replica index (0..N-1)
     * @param hashFunction the hash function
     */
    public KetamaNodeAddress(ClusterNode node, int replicaIndex, HashFunction hashFunction) {
        this.node = Objects.requireNonNull(node);
        this.replicaIndex = replicaIndex;
        this.hash = hashFunction.hash(node.id() + ":" + replicaIndex);
    }

    /**
     * The physical node this virtual node represents.
     */
    public ClusterNode node() {
        return node;
    }

    /**
     * The replica index on the ring.
     */
    public int replicaIndex() {
        return replicaIndex;
    }

    /**
     * The hash position on the 32-bit ring.
     */
    public long hash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KetamaNodeAddress that = (KetamaNodeAddress) o;
        return hash == that.hash;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }

    @Override
    public String toString() {
        return "KetamaNodeAddress{node=" + node.id() + ", replica=" + replicaIndex + ", hash=" + hash + '}';
    }
}
