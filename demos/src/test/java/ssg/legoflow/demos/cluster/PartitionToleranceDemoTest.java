package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link PartitionToleranceDemo}.
 *
 * Verifies the partition tolerance simulation:
 * - 5 nodes form a cluster initially
 * - Partition splits into majority (3) and minority (2) groups
 * - Majority partition has quorum, minority does not
 * - No split-brain: only majority serves operations
 * - Partition heals and cluster recovers to 5 members
 * - Events are tracked per node
 */
class PartitionToleranceDemoTest {

    @Test
    void testDemoRunsSuccessfully() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void testInitialClusterHasFiveNodes() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        int members = (int) result.get("initial_member_count");
        assertThat(members).isEqualTo(5);
    }

    @Test
    void testPartitionCreated() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        boolean created = (boolean) result.get("partition_created");
        assertThat(created).isTrue();
    }

    @Test
    void testMajorityPartitionHasQuorum() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        int majoritySize = (int) result.get("majority_partition_size");
        boolean hasQuorum = (boolean) result.get("majority_has_quorum");
        assertThat(majoritySize).isEqualTo(3);
        assertThat(hasQuorum).isTrue();
    }

    @Test
    void testMinorityPartitionHasNoQuorum() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        int minoritySize = (int) result.get("minority_partition_size");
        boolean hasQuorum = (boolean) result.get("minority_has_quorum");
        assertThat(minoritySize).isEqualTo(2);
        assertThat(hasQuorum).isFalse();
    }

    @Test
    void testNoSplitBrain() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        boolean noSplitBrain = (boolean) result.get("no_split_brain");
        assertThat(noSplitBrain).isTrue();
    }

    @Test
    void testMajorityServesOperations() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        boolean serves = (boolean) result.get("majority_serves_operations");
        boolean minorityReadOnly = (boolean) result.get("minority_read_only");
        assertThat(serves).isTrue();
        assertThat(minorityReadOnly).isTrue();
    }

    @Test
    void testPartitionHeals() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        boolean healed = (boolean) result.get("partition_healed");
        int memberCount = (int) result.get("healed_member_count");
        assertThat(healed).isTrue();
        assertThat(memberCount).isEqualTo(5);
    }

    @Test
    void testEventsTrackedPerNode() throws Exception {
        var demo = new PartitionToleranceDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, Integer> events = (Map<String, Integer>) result.get("events_per_node");
        assertThat(events).hasSize(5);
        for (String nodeId : events.keySet()) {
            assertThat(nodeId).startsWith("node-");
            assertThat(events.get(nodeId)).isGreaterThanOrEqualTo(0);
        }
    }
}
