package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
class KetamaRedistributionTest {

    private static final int NUM_KEYS = 10_000;
    private static final List<String> KEYS = IntStream.rangeClosed(1, NUM_KEYS)
            .mapToObj(i -> "key-" + i)
            .collect(Collectors.toList());

    @Test
    void addingNodeRedistributesBoundedFraction() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);

        // Start with 3 nodes
        var node1 = ClusterNode.builder().id("n1").build();
        var node2 = ClusterNode.builder().id("n2").build();
        var node3 = ClusterNode.builder().id("n3").build();

        ring.add(node1);
        ring.add(node2);
        ring.add(node3);

        // Record key assignments before adding a 4th node
        var before = assignKeys(ring, KEYS);

        // Add a 4th node
        var node4 = ClusterNode.builder().id("n4").build();
        ring.add(node4);

        // Record assignments after
        var after = assignKeys(ring, KEYS);

        // Count how many keys changed assignment
        int moved = 0;
        for (String key : KEYS) {
            if (!before.get(key).equals(after.get(key))) {
                moved++;
            }
        }

        double fractionMoved = (double) moved / KEYS.size();

        // With 3→4 nodes (25% increase), Ketama should redistribute < 33% of keys
        // Good consistent hashing: ~1/N keys move (N = original node count)
        // For 3 nodes: ~33% max, typically much less with 160 replicas
        assertThat(fractionMoved).isLessThan(0.33);
        assertThat(fractionMoved).isLessThan(0.30); // with 160 replicas, typically ~12-15%
    }

    @Test
    void removingNodeRedistributesBoundedFraction() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);

        // Start with 4 nodes
        var nodes = new ArrayList<ClusterNode>();
        for (int i = 1; i <= 4; i++) {
            var node = ClusterNode.builder().id("n" + i).build();
            ring.add(node);
            nodes.add(node);
        }

        var before = assignKeys(ring, KEYS);

        // Remove one node
        ring.remove(nodes.get(2));

        var after = assignKeys(ring, KEYS);

        int moved = 0;
        for (String key : KEYS) {
            if (!before.get(key).equals(after.get(key))) {
                moved++;
            }
        }

        double fractionMoved = (double) moved / KEYS.size();

        // With 4→3 nodes: ~25% max; with 160 replicas, much less
        assertThat(fractionMoved).isLessThan(0.33);
        assertThat(fractionMoved).isLessThan(0.30);
    }

    @Test
    void redistributionIsProportionalToNodeCount() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);

        // Start with 10 nodes
        var nodes = new ArrayList<ClusterNode>();
        for (int i = 1; i <= 10; i++) {
            var node = ClusterNode.builder().id("n" + i).build();
            ring.add(node);
            nodes.add(node);
        }

        var before = assignKeys(ring, KEYS);

        // Remove one node
        ring.remove(nodes.get(0));

        var after = assignKeys(ring, KEYS);

        int moved = 0;
        for (String key : KEYS) {
            if (!before.get(key).equals(after.get(key))) {
                moved++;
            }
        }

        double fractionMoved = (double) moved / KEYS.size();

        // With 10 nodes, removing 1 should move ~10% of keys (1/N)
        // Allow some tolerance (10-20%)
        assertThat(fractionMoved).isLessThan(0.2);
        assertThat(fractionMoved).isGreaterThan(0.01);
    }

    @Test
    void addingToSmallClusterRedistributesMore() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);

        // 1 node → 2 nodes
        var node1 = ClusterNode.builder().id("n1").build();
        ring.add(node1);

        var before = assignKeys(ring, KEYS);

        var node2 = ClusterNode.builder().id("n2").build();
        ring.add(node2);

        var after = assignKeys(ring, KEYS);

        int moved = 0;
        for (String key : KEYS) {
            if (!before.get(key).equals(after.get(key))) {
                moved++;
            }
        }

        double fractionMoved = (double) moved / KEYS.size();

        // Going from 1→2 nodes: about 50% redistribution
        // With 160 replicas this should be close to 50% ± 10%
        assertThat(fractionMoved).isGreaterThanOrEqualTo(0.35);
        assertThat(fractionMoved).isLessThanOrEqualTo(0.65);
    }

    @Test
    void allKeysMapToRemainingNodesAfterRemoval() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node1 = ClusterNode.builder().id("n1").build();
        var node2 = ClusterNode.builder().id("n2").build();

        ring.add(node1);
        ring.add(node2);

        // Remove node1, all keys should still map to node2
        ring.remove(node1);

        for (String key : KEYS) {
            var mapped = ring.getNode(key);
            assertThat(mapped).isEqualTo(node2);
        }
    }

    @Test
    void keyAssignmentsStableWithoutChanges() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        for (int i = 1; i <= 5; i++) {
            ring.add(ClusterNode.builder().id("n" + i).build());
        }

        var first = assignKeys(ring, KEYS);
        var second = assignKeys(ring, KEYS);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void ketamaVirtualNodePlacement() {
        var node = ClusterNode.builder().id("ketama-test").build();

        // Verify Ketama node address computation
        var vNode0 = new KetamaNodeAddress(node, 0, MurmurHash3.INSTANCE);
        var vNode1 = new KetamaNodeAddress(node, 1, MurmurHash3.INSTANCE);

        assertThat(vNode0.hash()).isNotEqualTo(vNode1.hash());
        assertThat(vNode0.node()).isEqualTo(node);
        assertThat(vNode1.node()).isEqualTo(node);
        assertThat(vNode0.replicaIndex()).isEqualTo(0);
        assertThat(vNode1.replicaIndex()).isEqualTo(1);
    }

    @Test
    void manyNodesGoodBalance() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var nodes = new ArrayList<ClusterNode>();
        for (int i = 1; i <= 20; i++) {
            var node = ClusterNode.builder().id("n" + i).build();
            ring.add(node);
            nodes.add(node);
        }

        var assignments = assignKeys(ring, KEYS);
        var counts = new HashMap<String, Integer>();
        for (var entry : assignments.entrySet()) {
            counts.merge(entry.getValue().id(), 1, Integer::sum);
        }

        double expected = NUM_KEYS / 20.0; // 500 per node
        for (int count : counts.values()) {
            // Allow 40% variance for 20 nodes
            assertThat(count).isBetween((int)(expected * 0.6), (int)(expected * 1.4));
        }
    }

    @Test
    void singleKeyMapsToSameNode() {
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        var node = ClusterNode.builder().id("single").build();
        ring.add(node);

        var key = "consistent-key";
        var result1 = ring.getNode(key);
        var result2 = ring.getNode(key);

        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo(node);
    }

    private Map<String, ClusterNode> assignKeys(ConsistentHashRing ring, List<String> keys) {
        Map<String, ClusterNode> map = new HashMap<>();
        for (String key : keys) {
            map.put(key, ring.getNode(key));
        }
        return map;
    }
}
