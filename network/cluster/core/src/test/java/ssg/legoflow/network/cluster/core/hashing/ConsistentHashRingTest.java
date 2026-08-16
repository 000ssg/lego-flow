package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistentHashRingTest {

    @Test
    void defaultConstructorUsesDefaultReplicas() {
        var ring = new ConsistentHashRing();
        assertThat(ring.replicas()).isEqualTo(160);
    }

    @Test
    void customReplicasAndHashFunction() {
        var ring = new ConsistentHashRing(200, MurmurHash3.INSTANCE);
        assertThat(ring.replicas()).isEqualTo(200);
        assertThat(ring.hashFunction()).isSameAs(MurmurHash3.INSTANCE);
    }

    @Test
    void zeroReplicasRejected() {
        assertThatThrownBy(() -> new ConsistentHashRing(0, MurmurHash3.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replicas must be >= 1");
    }

    @Test
    void tooManyReplicasRejected() {
        assertThatThrownBy(() -> new ConsistentHashRing(1001, MurmurHash3.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replicas must be <= 1000");
    }

    @Test
    void nullHashFunctionRejected() {
        assertThatThrownBy(() -> new ConsistentHashRing(160, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyRingReturnsNull() {
        var ring = new ConsistentHashRing();
        assertThat(ring.getNode("any-key")).isNull();
        assertThat(ring.getNode("x".getBytes())).isNull();
    }

    @Test
    void addNodeCreatesVirtualNodes() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("node-1").build();

        ring.add(node);

        assertThat(ring.nodeCount()).isEqualTo(1);
        assertThat(ring.virtualNodeCount()).isEqualTo(160);
    }

    @Test
    void lookupReturnsNodeAfterAdd() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("node-1").build();
        ring.add(node);

        assertThat(ring.getNode("any-key")).isEqualTo(node);
        assertThat(ring.getNode("another-key")).isEqualTo(node);
    }

    @Test
    void addIsIdempotent() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("idempotent").build();

        ring.add(node);
        ring.add(node);

        assertThat(ring.nodeCount()).isEqualTo(1);
        assertThat(ring.virtualNodeCount()).isEqualTo(160);
    }

    @Test
    void removeNodeDeletesVirtualNodes() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("to-remove").build();

        ring.add(node);
        assertThat(ring.nodeCount()).isEqualTo(1);

        var removed = ring.remove(node);
        assertThat(removed).isTrue();
        assertThat(ring.nodeCount()).isZero();
        assertThat(ring.virtualNodeCount()).isZero();
    }

    @Test
    void removeNonExistentReturnsFalse() {
        var ring = new ConsistentHashRing();
        var node = ClusterNode.builder().id("not-in-ring").build();

        assertThat(ring.remove(node)).isFalse();
    }

    @Test
    void removeIsNullSafe() {
        var ring = new ConsistentHashRing();
        assertThatThrownBy(() -> ring.remove(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addIsNullSafe() {
        var ring = new ConsistentHashRing();
        assertThatThrownBy(() -> ring.add(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void multipleNodesDistributeKeys() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node1 = ClusterNode.builder().id("node-1").build();
        var node2 = ClusterNode.builder().id("node-2").build();
        var node3 = ClusterNode.builder().id("node-3").build();

        ring.add(node1);
        ring.add(node2);
        ring.add(node3);

        assertThat(ring.nodeCount()).isEqualTo(3);

        // All keys should map to one of the three nodes
        for (int i = 0; i < 100; i++) {
            var node = ring.getNode("key-" + i);
            assertThat(node).isIn(node1, node2, node3);
        }
    }

    @Test
    void keyDistributionIsBalanced() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node1 = ClusterNode.builder().id("n1").build();
        var node2 = ClusterNode.builder().id("n2").build();
        var node3 = ClusterNode.builder().id("n3").build();

        ring.add(node1);
        ring.add(node2);
        ring.add(node3);

        int count1 = 0, count2 = 0, count3 = 0;
        for (int i = 0; i < 10000; i++) {
            var node = ring.getNode("distribution-key-" + i);
            if (node == node1) count1++;
            else if (node == node2) count2++;
            else count3++;
        }

        // Each node should get roughly 1/3 of keys (allow 40% variance)
        var expected = 10000.0 / 3.0;
        assertThat(count1).isBetween((int)(expected * 0.6), (int)(expected * 1.4));
        assertThat(count2).isBetween((int)(expected * 0.6), (int)(expected * 1.4));
        assertThat(count3).isBetween((int)(expected * 0.6), (int)(expected * 1.4));
    }

    @Test
    void keysRemainStableAfterLookup() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node1 = ClusterNode.builder().id("n1").build();
        var node2 = ClusterNode.builder().id("n2").build();

        ring.add(node1);
        ring.add(node2);

        var first = ring.getNode("stable-key");
        var second = ring.getNode("stable-key");
        var third = ring.getNode("stable-key");

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(third);
    }

    @Test
    void clearRemovesAllNodes() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        ring.add(ClusterNode.builder().id("a").build());
        ring.add(ClusterNode.builder().id("b").build());

        ring.clear();

        assertThat(ring.nodeCount()).isZero();
        assertThat(ring.virtualNodeCount()).isZero();
        assertThat(ring.getNode("any")).isNull();
    }

    @Test
    void getNodeReturnsList() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node1 = ClusterNode.builder().id("n1").build();
        var node2 = ClusterNode.builder().id("n2").build();

        ring.add(node1);
        ring.add(node2);

        var nodes = ring.getNodes();
        assertThat(nodes).containsExactlyInAnyOrder(node1, node2);

        // Modifying the returned list should not affect the ring
        nodes.add(ClusterNode.builder().id("hack").build());
        assertThat(ring.nodeCount()).isEqualTo(2);
    }

    @Test
    void ringReturnsUnmodifiableView() {
        var ring = new ConsistentHashRing();
        ring.add(ClusterNode.builder().id("n1").build());

        var ringView = ring.ring();
        assertThat(ringView).isNotEmpty();
        assertThatThrownBy(() -> ringView.put(1L, ClusterNode.builder().id("hack").build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toStringContainsNodeCount() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        ring.add(ClusterNode.builder().id("n1").build());

        var str = ring.toString();
        assertThat(str).contains("nodes=1");
        assertThat(str).contains("virtualNodes=160");
        assertThat(str).contains("replicas=160");
    }

    @Test
    void byteKeyLookup() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("byte-key").build();
        ring.add(node);

        var result = ring.getNode("test".getBytes());
        assertThat(result).isEqualTo(node);
    }

    @Test
    void replicaCountValidation() {
        // Minimum valid
        var minRing = new ConsistentHashRing(1, MurmurHash3.INSTANCE);
        assertThat(minRing.replicas()).isEqualTo(1);

        // Maximum valid
        var maxRing = new ConsistentHashRing(1000, MurmurHash3.INSTANCE);
        assertThat(maxRing.replicas()).isEqualTo(1000);
    }
}
