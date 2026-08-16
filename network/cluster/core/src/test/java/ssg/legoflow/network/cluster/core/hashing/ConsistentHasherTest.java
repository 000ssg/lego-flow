package ssg.legoflow.network.cluster.core.hashing;

import ssg.legoflow.network.cluster.core.ClusterConfig;
import ssg.legoflow.network.cluster.core.ClusterHealthChecker;
import ssg.legoflow.network.cluster.core.ClusterManager;
import ssg.legoflow.network.cluster.core.ClusterMembership;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.InMemoryClusterTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistentHasherTest {

    @Test
    void constructorSyncsWithMembership() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("hasher-node").build();
        var config = ClusterConfig.defaultsFor("hasher-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var hasher = new ConsistentHasher(manager);

        try {
            assertThat(hasher.nodeCount()).isGreaterThanOrEqualTo(1);
            var node = hasher.getNode("test-key");
            assertThat(node).isNotNull();
        } finally {
            manager.close();
        }
    }

    @Test
    void standaloneHasher() {
        var nodes = List.of(
                ClusterNode.builder().id("n1").build(),
                ClusterNode.builder().id("n2").build(),
                ClusterNode.builder().id("n3").build()
        );

        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        assertThat(hasher.nodeCount()).isEqualTo(3);

        // All keys should map to one of the nodes
        for (int i = 0; i < 100; i++) {
            var node = hasher.getNode("key-" + i);
            assertThat(node).isIn(nodes.toArray(new ClusterNode[0]));
        }
    }

    @Test
    void getNodeReturnsNullWhenEmpty() {
        var nodes = List.<ClusterNode>of();
        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        assertThat(hasher.getNode("key")).isNull();
        assertThat(hasher.getNode("key".getBytes())).isNull();
    }

    @Test
    void getNodeWithByteKey() {
        var nodes = List.of(ClusterNode.builder().id("n1").build());
        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        var node = hasher.getNode("byte-test".getBytes());
        assertThat(node).isNotNull();
        assertThat(node.id()).isEqualTo("n1");
    }

    @Test
    void repartitionMapsKeysToNodes() {
        var nodes = List.of(
                ClusterNode.builder().id("n1").build(),
                ClusterNode.builder().id("n2").build()
        );
        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        var keys = List.of("key-a", "key-b", "key-c", "key-d");
        var assignments = hasher.repartition(keys);

        assertThat(assignments).hasSize(4);
        for (var entry : assignments.entrySet()) {
            assertThat(entry.getValue()).isIn(nodes.toArray(new ClusterNode[0]));
        }
    }

    @Test
    void repartitionAfterNodeRemoval() {
        var nodes = new ArrayList<ClusterNode>();
        nodes.add(ClusterNode.builder().id("n1").build());
        nodes.add(ClusterNode.builder().id("n2").build());
        nodes.add(ClusterNode.builder().id("n3").build());

        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        for (var node : nodes) ring.add(node);

        var hasher = new ConsistentHasher(ring);

        var keys = List.of("k1", "k2", "k3", "k4", "k5");
        var before = hasher.repartition(keys);

        // Remove n2 and rehash
        ring.remove(nodes.get(1));
        hasher.syncMembers(); // sync is a no-op for standalone, but let's clear and re-add

        var ring2 = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        ring2.add(nodes.get(0));
        ring2.add(nodes.get(2));

        var hasher2 = new ConsistentHasher(ring2);
        var after = hasher2.repartition(keys);

        // At least some keys should change assignment
        int moved = 0;
        for (var entry : before.entrySet()) {
            var newAssignment = after.get(entry.getKey());
            if (newAssignment != null && !entry.getValue().equals(newAssignment)) {
                moved++;
            }
        }

        // Some keys that were on n2 must move
        var wasOnRemoved = before.values().stream()
                .filter(n -> n.id().equals("n2"))
                .count();
        assertThat(moved).isGreaterThanOrEqualTo((int)wasOnRemoved);

        // No key should map to the removed node
        assertThat(after.values()).noneMatch(n -> n.id().equals("n2"));
    }

    @Test
    void ringAccessor() {
        var nodes = List.of(ClusterNode.builder().id("n1").build());
        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        var ring = hasher.ring();
        assertThat(ring).isNotNull();
        assertThat(ring.nodeCount()).isEqualTo(1);
    }

    @Test
    void hasherStandaloneWithNullMembership() {
        // Standalone hasher should work without membership
        var nodes = List.of(ClusterNode.builder().id("standalone").build());
        var hasher = ConsistentHasher.standalone(nodes, 100, MurmurHash3.INSTANCE);

        assertThat(hasher.nodeCount()).isEqualTo(1);
        var result = hasher.getNode("test");
        assertThat(result).isNotNull();
    }

    @Test
    void consistentMappings() {
        var nodes = List.of(
                ClusterNode.builder().id("n1").build(),
                ClusterNode.builder().id("n2").build()
        );
        var hasher = ConsistentHasher.standalone(nodes, 160, MurmurHash3.INSTANCE);

        var key = "consistent-key";
        var first = hasher.getNode(key);
        var second = hasher.getNode(key);
        var third = hasher.getNode(key);

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(third);
    }

    @Test
    void syncMembersWithMembership() {
        var transport = new InMemoryClusterTransport();
        var checker = ClusterHealthChecker.simple(
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        var localNode = ClusterNode.builder().id("sync-node").build();
        var config = ClusterConfig.defaultsFor("sync-test");

        var manager = new ClusterManager(localNode, config, transport, checker);
        manager.start();

        var hasher = new ConsistentHasher(manager);

        try {
            var initialCount = hasher.nodeCount();
            assertThat(initialCount).isGreaterThanOrEqualTo(1);

            // Sync again (should be idempotent)
            hasher.syncMembers();
            assertThat(hasher.nodeCount()).isEqualTo(initialCount);
        } finally {
            manager.close();
        }
    }
}
