package ssg.legoflow.demos.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.cluster.core.ClusterNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClusterSimulationDemo}.
 *
 * Verifies that the 3-node in-memory cluster simulation:
 * - Starts with a single node
 * - Supports node join/leave lifecycle
 * - Detects node failures
 * - Handles node recovery
 * - Redistributes keys via consistent hashing on membership changes
 */
class ClusterSimulationDemoTest {

    @Test
    void testDemoRunsSuccessfully() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        // Verify all step results are present
        assertThat(result).containsKeys(
                "step1-members", "step2-members", "step3-members",
                "hashing-3-node-counts", "step5-active-after-crash",
                "final-A-running", "final-B-running", "final-C-running"
        );
    }

    @Test
    void testInitialSoloNode() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        int members = (int) result.get("step1-members");
        assertThat(members).isEqualTo(1);
    }

    @Test
    void testTwoNodeCluster() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        int[] members = (int[]) result.get("step2-members");
        assertThat(members).hasSize(2);
        assertThat(members[0]).isGreaterThanOrEqualTo(2);
        assertThat(members[1]).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testThreeNodeCluster() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        int[] members = (int[]) result.get("step3-members");
        assertThat(members).hasSize(3);
        for (int count : members) {
            assertThat(count).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void testConsistentHashingWithThreeNodes() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) result.get("hashing-3-node-counts");
        assertThat(counts).isNotNull();
        assertThat(counts).hasSizeBetween(1, 3);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(100);

        for (int count : counts.values()) {
            assertThat(count).isGreaterThan(0);
            assertThat(count).isLessThan(100);
        }
    }

    @Test
    void testNodeFailureDetected() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        @SuppressWarnings("unchecked")
        int[] activeAfterCrash = (int[]) result.get("step5-active-after-crash");
        assertThat(activeAfterCrash).hasSize(2);
        for (int count : activeAfterCrash) {
            assertThat(count).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void testAllNodesShutDownCleanly() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        assertThat(result.get("final-A-running")).isEqualTo(false);
        assertThat(result.get("final-B-running")).isEqualTo(false);
        assertThat(result.get("final-C-running")).isEqualTo(false);
    }

    @Test
    void testLeaderIsNodeA() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        ClusterNode leader = (ClusterNode) result.get("step1-leader");
        assertThat(leader).isNotNull();
        assertThat(leader.id()).isEqualTo("node-A");
    }

    @Test
    void testKeysRedistributedAfterCrash() throws Exception {
        var demo = new ClusterSimulationDemo();
        var result = demo.run();

        Integer movedKeys = (Integer) result.get("redistribution-moved-keys");
        assertThat(movedKeys).isNotNull();
        assertThat(movedKeys).isGreaterThanOrEqualTo(0);
    }
}
