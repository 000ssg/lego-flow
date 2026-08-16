package ssg.legoflow.http.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StickySessionHasherTest {

    private ClusterNode node(String id, int port) {
        return ClusterNode.builder()
                .id(id)
                .host("127.0.0.1")
                .port(port)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    @Test
    void empty_ring_returns_null() {
        StickySessionHasher hasher = new StickySessionHasher();
        assertThat(hasher.getNode("/test")).isNull();
    }

    @Test
    void single_node_handles_all_keys() {
        StickySessionHasher hasher = new StickySessionHasher();
        hasher.updateNodes(List.of(node("node-1", 8080)));

        for (int i = 0; i < 50; i++) {
            ClusterNode target = hasher.getNode("/api/item-" + i);
            assertThat(target).isNotNull();
            assertThat(target.id()).isEqualTo("node-1");
        }
    }

    @Test
    void multiple_nodes_distribute_keys() {
        StickySessionHasher hasher = new StickySessionHasher(160);
        hasher.updateNodes(List.of(
                node("node-A", 8080),
                node("node-B", 8081),
                node("node-C", 8082)
        ));

        int[] counts = new int[3];
        for (int i = 0; i < 300; i++) {
            ClusterNode target = hasher.getNode("/data/" + i);
            assertThat(target).isNotNull();
            if (target.id().equals("node-A")) counts[0]++;
            else if (target.id().equals("node-B")) counts[1]++;
            else counts[2]++;
        }

        // Each node should get roughly 1/3 of keys (within 30% tolerance)
        for (int count : counts) {
            assertThat(count).isBetween(60, 140);
        }
    }

    @Test
    void consistent_hash_same_key_same_node() {
        StickySessionHasher hasher = new StickySessionHasher();
        hasher.updateNodes(List.of(
                node("node-1", 8080),
                node("node-2", 8081)
        ));

        String key = "/api/users/123";
        ClusterNode first = hasher.getNode(key);
        for (int i = 0; i < 10; i++) {
            ClusterNode result = hasher.getNode(key);
            assertThat(result).isEqualTo(first);
        }
    }

    @Test
    void rebalance_on_node_removal() {
        StickySessionHasher hasher = new StickySessionHasher();
        var nodes = List.of(
                node("node-1", 8080),
                node("node-2", 8081),
                node("node-3", 8082)
        );
        hasher.updateNodes(nodes);

        // Get assignments with 3 nodes
        var key1Node = hasher.getNode("/key-1");
        var key2Node = hasher.getNode("/key-2");

        // Remove node-2
        nodes = List.of(
                node("node-1", 8080),
                node("node-3", 8082)
        );
        hasher.updateNodes(nodes);

        // Keys previously on node-2 should now map to node-1 or node-3
        var newKey1Node = hasher.getNode("/key-1");
        var newKey2Node = hasher.getNode("/key-2");

        assertThat(newKey1Node).isNotNull();
        assertThat(newKey2Node).isNotNull();
        assertThat(newKey1Node.id()).isNotEqualTo("node-2");
        assertThat(newKey2Node.id()).isNotEqualTo("node-2");
    }

    @Test
    void rebalance_on_node_addition() {
        StickySessionHasher hasher = new StickySessionHasher();
        hasher.updateNodes(List.of(node("node-1", 8080)));

        var nodesBefore = hasher.getNodes();
        assertThat(nodesBefore).hasSize(1);

        hasher.updateNodes(List.of(
                node("node-1", 8080),
                node("node-2", 8081)
        ));

        var nodesAfter = hasher.getNodes();
        assertThat(nodesAfter).hasSize(2);
        assertThat(hasher.nodeCount()).isEqualTo(2);
    }

    @Test
    void getNode_count() {
        StickySessionHasher hasher = new StickySessionHasher();
        assertThat(hasher.nodeCount()).isZero();

        hasher.updateNodes(List.of(
                node("n1", 8080),
                node("n2", 8081)
        ));
        assertThat(hasher.nodeCount()).isEqualTo(2);
    }

    @Test
    void byte_array_key() {
        StickySessionHasher hasher = new StickySessionHasher();
        hasher.updateNodes(List.of(node("node-1", 8080)));

        byte[] key = "test-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClusterNode node = hasher.getNode(key);
        assertThat(node).isNotNull();
        assertThat(node.id()).isEqualTo("node-1");
    }

    @Test
    void ring_exposed() {
        StickySessionHasher hasher = new StickySessionHasher();
        assertThat(hasher.ring()).isNotNull();
    }

    @Test
    void null_key_throws() {
        StickySessionHasher hasher = new StickySessionHasher();
        assertThatThrownBy(() -> hasher.getNode((String) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> hasher.getNode((byte[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_nodes_throws() {
        StickySessionHasher hasher = new StickySessionHasher();
        assertThatThrownBy(() -> hasher.updateNodes(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void custom_replica_count() {
        StickySessionHasher hasher = new StickySessionHasher(50);
        assertThat(hasher.ring().replicas()).isEqualTo(50);
    }
}
