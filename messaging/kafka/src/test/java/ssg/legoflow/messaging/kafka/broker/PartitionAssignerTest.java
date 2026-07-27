package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PartitionAssignerTest {

    @Test
    void testRangeAssignerEvenDistribution() {
        var assigner = new RangeAssigner();
        assertThat(assigner.name()).isEqualTo("range");

        var members = List.of("m-1", "m-2");
        var partitions = List.of(
                new TopicPartition("t", 0),
                new TopicPartition("t", 1),
                new TopicPartition("t", 2),
                new TopicPartition("t", 3));

        var result = assigner.assign(members, partitions, Map.of());

        // 4 partitions across 2 members: 2 each
        assertThat(result).hasSize(2);
        assertThat(result.get("m-1")).hasSize(2);
        assertThat(result.get("m-2")).hasSize(2);

        // All partitions assigned
        var all = new ArrayList<TopicPartition>();
        result.values().forEach(all::addAll);
        assertThat(all).containsExactlyInAnyOrderElementsOf(partitions);
    }

    @Test
    void testRangeAssignerUnevenDistribution() {
        var assigner = new RangeAssigner();
        var members = List.of("m-1", "m-2", "m-3");
        var partitions = List.of(
                new TopicPartition("t", 0),
                new TopicPartition("t", 1),
                new TopicPartition("t", 2),
                new TopicPartition("t", 3),
                new TopicPartition("t", 4));

        var result = assigner.assign(members, partitions, Map.of());

        // 5 partitions across 3 members: 2, 2, 1
        assertThat(result).hasSize(3);
        int total = result.values().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(5);

        // Max difference between any two members is 1
        int max = result.values().stream().mapToInt(List::size).max().orElse(0);
        int min = result.values().stream().mapToInt(List::size).min().orElse(0);
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void testStickyAssignerRetainsExistingAssignments() {
        var assigner = new StickyAssigner();
        assertThat(assigner.name()).isEqualTo("sticky");

        var tp0 = new TopicPartition("t", 0);
        var tp1 = new TopicPartition("t", 1);
        var tp2 = new TopicPartition("t", 2);
        var tp3 = new TopicPartition("t", 3);

        var members = List.of("m-1", "m-2");
        var partitions = List.of(tp0, tp1, tp2, tp3);

        // Current assignment: m-1 has tp0 and tp1, m-2 has tp2 and tp3
        var current = Map.of(
                "m-1", List.of(tp0, tp1),
                "m-2", List.of(tp2, tp3));

        var result = assigner.assign(members, partitions, current);

        // Should retain existing assignments
        assertThat(result.get("m-1")).containsExactlyInAnyOrder(tp0, tp1);
        assertThat(result.get("m-2")).containsExactlyInAnyOrder(tp2, tp3);
    }

    @Test
    void testStickyAssignerAssignsUnassignedToLeastLoaded() {
        var assigner = new StickyAssigner();

        var tp0 = new TopicPartition("t", 0);
        var tp1 = new TopicPartition("t", 1);
        var tp2 = new TopicPartition("t", 2);
        var tp3 = new TopicPartition("t", 3);
        var tp4 = new TopicPartition("t", 4); // new partition

        var members = List.of("m-1", "m-2");
        var partitions = List.of(tp0, tp1, tp2, tp3, tp4);

        // Current: m-1 has 1 partition, m-2 has 3 partitions
        var current = Map.of(
                "m-1", List.of(tp0),
                "m-2", List.of(tp1, tp2, tp3));

        var result = assigner.assign(members, partitions, current);

        // m-1 should get the new partition (tp4) since it's least loaded
        assertThat(result.get("m-1")).contains(tp0, tp4);
        assertThat(result.get("m-2")).containsExactlyInAnyOrder(tp1, tp2, tp3);
    }

    @Test
    void testStickyAssignerHandlesRemovedMember() {
        var assigner = new StickyAssigner();

        var tp0 = new TopicPartition("t", 0);
        var tp1 = new TopicPartition("t", 1);
        var tp2 = new TopicPartition("t", 2);

        // m-3 left the group, so its partitions become unassigned
        var members = List.of("m-1", "m-2");
        var partitions = List.of(tp0, tp1, tp2);
        var current = Map.of(
                "m-1", List.of(tp0),
                "m-2", List.of(tp1),
                "m-3", List.of(tp2)); // m-3 is gone

        var result = assigner.assign(members, partitions, current);

        // tp0 stays with m-1, tp1 stays with m-2, tp2 goes to least-loaded
        assertThat(result.get("m-1")).contains(tp0);
        assertThat(result.get("m-2")).contains(tp1);
        // tp2 should be assigned to one of the members
        var all = new ArrayList<TopicPartition>();
        result.values().forEach(all::addAll);
        assertThat(all).containsExactlyInAnyOrder(tp0, tp1, tp2);
    }

    @Test
    void testStickyAssignerEmptyCurrentAssignment() {
        var assigner = new StickyAssigner();
        var members = List.of("m-1", "m-2");
        var partitions = List.of(
                new TopicPartition("t", 0),
                new TopicPartition("t", 1),
                new TopicPartition("t", 2),
                new TopicPartition("t", 3));

        var result = assigner.assign(members, partitions, Map.of());

        // Should distribute evenly like range when no previous assignment
        assertThat(result).hasSize(2);
        int total = result.values().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(4);
        assertThat(result.get("m-1")).hasSize(2);
        assertThat(result.get("m-2")).hasSize(2);
    }
}
