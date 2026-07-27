package ssg.legoflow.messaging.kafka.common;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PartitionerTest {

    @Test
    void testKeyHashDeterministic() {
        Partitioner p = Partitioner.keyHash();
        byte[] key = "test-key".getBytes(StandardCharsets.UTF_8);
        int p1 = p.partition("t", key, null, 10);
        int p2 = p.partition("t", key, null, 10);
        assertThat(p1).isEqualTo(p2);
    }

    @Test
    void testKeyHashDistribution() {
        Partitioner p = Partitioner.keyHash();
        Set<Integer> partitions = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            byte[] key = ("key-" + i).getBytes(StandardCharsets.UTF_8);
            partitions.add(p.partition("t", key, null, 10));
        }
        // Should use multiple partitions
        assertThat(partitions.size()).isGreaterThan(1);
    }

    @Test
    void testKeyHashNullKeyFallback() {
        Partitioner p = Partitioner.keyHash();
        int result = p.partition("t", null, null, 5);
        assertThat(result).isBetween(0, 4);
    }

    @Test
    void testRoundRobinCycles() {
        Partitioner p = Partitioner.roundRobin();
        int p0 = p.partition("t", null, null, 3);
        int p1 = p.partition("t", null, null, 3);
        int p2 = p.partition("t", null, null, 3);
        int p3 = p.partition("t", null, null, 3);
        // Should cycle through 0,1,2,0,...
        assertThat(p0).isBetween(0, 2);
        assertThat(p1).isBetween(0, 2);
        assertThat(p2).isBetween(0, 2);
        assertThat(Set.of(p0, p1, p2)).hasSize(3);
    }

    @Test
    void testRoundRobinAllPartitions() {
        Partitioner p = Partitioner.roundRobin();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            seen.add(p.partition("t", null, null, 3));
        }
        assertThat(seen).containsExactlyInAnyOrder(0, 1, 2);
    }

    @Test
    void testPartitionBounds() {
        Partitioner p = Partitioner.keyHash();
        for (int i = 0; i < 1000; i++) {
            byte[] key = ("k" + i).getBytes(StandardCharsets.UTF_8);
            int result = p.partition("t", key, null, 7);
            assertThat(result).isBetween(0, 6);
        }
    }
}
