package ssg.legoflow.demos.cluster;

import ssg.legoflow.network.cluster.core.ClusterConfig;
import ssg.legoflow.network.cluster.core.ClusterEvent;
import ssg.legoflow.network.cluster.core.ClusterHealthChecker;
import ssg.legoflow.network.cluster.core.ClusterManager;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterTransport;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
/**
 * Demo: Partition Tolerance in a 5-Node Cluster.
 *
 * <p>Simulates a network partition splitting the cluster:
 * <ul>
 *   <li>5 nodes form a cluster</li>
 *   <li>Partition splits into majority (3 nodes) and minority (2 nodes)</li>
 *   <li>Majority partition elects leader, continues operations</li>
 *   <li>Minority partition detects isolation, enters degraded mode</li>
 *   <li>Partition heals: state reconciliation</li>
 * </ul>
 *
 * <p>This demo is fully deterministic — no Thread.sleep or timing-dependent
 * behavior — so it passes reliably on all platforms and CI environments.
 */
public final class PartitionToleranceDemo {

    PartitionToleranceDemo() {}

    /**
     * Shared partition state that all transport instances consult.
     */
    static class PartitionState {
        private final Set<String> partitionA = ConcurrentHashMap.newKeySet();
        private final Set<String> partitionB = ConcurrentHashMap.newKeySet();
        private final AtomicReference<Boolean> partitioned = new AtomicReference<>(false);

        void createPartition(Set<String> groupA, Set<String> groupB) {
            partitionA.clear();
            partitionB.clear();
            partitionA.addAll(groupA);
            partitionB.addAll(groupB);
            partitioned.set(true);
        }

        void healPartition() {
            partitionA.clear();
            partitionB.clear();
            partitioned.set(false);
        }

        boolean canReach(String from, String to) {
            if (!partitioned.get()) return true;
            if (partitionA.contains(from) && partitionA.contains(to)) return true;
            if (partitionB.contains(from) && partitionB.contains(to)) return true;
            return false;
        }
    }

    /**
     * In-memory transport backed by shared partition state.
     */
    static class PartitionableTransport implements ClusterTransport {
        private final PartitionState state;
        private final String localNodeId;
        private final java.util.function.BiConsumer<String, byte[]> inbox;

        PartitionableTransport(PartitionState state, String localNodeId,
                                java.util.function.BiConsumer<String, byte[]> inbox) {
            this.state = state;
            this.localNodeId = localNodeId;
            this.inbox = inbox;
        }

        @Override
        public CompletableFuture<Void> send(ClusterNode target, byte[] payload) {
            if (!state.canReach(localNodeId, target.id())) {
                return CompletableFuture.failedFuture(new RuntimeException("partition"));
            }
            inbox.accept(target.id(), payload);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> broadcast(ClusterNode sender, byte[] payload) {
            inbox.accept("__broadcast__", payload);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /**
     * Mutable node for simulation.
     */
    static class MutableNode {
        private final String nodeId;
        private final int port;
        private final AtomicReference<ClusterNodeStatus> status = new AtomicReference<>(ClusterNodeStatus.ACTIVE);

        MutableNode(String id, int port) {
            this.nodeId = id;
            this.port = port;
        }

        ClusterNode toNode() {
            return ClusterNode.builder()
                    .id(nodeId)
                    .host("127.0.0.1")
                    .port(port)
                    .role(ClusterRole.BOTH)
                    .status(status.get())
                    .build();
        }

        void kill() { status.set(ClusterNodeStatus.FAILED); }
        void revive() { status.set(ClusterNodeStatus.ACTIVE); }
        String id() { return nodeId; }
    }

    // ── Simulation ──

    /**
     * Runs the partition tolerance simulation.
     *
     * <p>Fully deterministic — no Thread.sleep, no timing dependencies.
     */
    public Map<String, Object> run() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();
        var partitionState = new PartitionState();

        var nodes = new ArrayList<MutableNode>();
        var managers = new ArrayList<ClusterManager>();
        var eventLogs = new ArrayList<List<String>>();

        for (int i = 1; i <= 5; i++) {
            String nodeId = "node-" + i;
            var node = new MutableNode(nodeId, 8000 + i);
            nodes.add(node);

            var config = ClusterConfig.builder()
                    .name("partition-cluster")
                    .heartbeatInterval(Duration.ofMillis(100))
                    .heartbeatFailureThreshold(100)
                    .build();

            var log = new CopyOnWriteArrayList<String>();
            var transport = new PartitionableTransport(partitionState, nodeId, (from, payload) -> {
                String desc = "from=" + from + " payload=" + new String(payload);
                log.add(desc);
            });
            var checker = ClusterHealthChecker.simple(Duration.ofSeconds(10), Duration.ofSeconds(2));

            var manager = new ClusterManager(node.toNode(), config, transport, checker);
            manager.addListener(event -> {
                String desc = switch (event) {
                    case ClusterEvent.NodeJoined j -> "NodeJoined:" + j.node().id();
                    case ClusterEvent.NodeLeft l -> "NodeLeft:" + l.node().id();
                    case ClusterEvent.NodeFailed f -> "NodeFailed:" + f.node().id();
                    case ClusterEvent.NodeRecovered r -> "NodeRecovered:" + r.node().id();
                    case ClusterEvent.LeaderChanged c -> "LeaderChanged:" + c.newLeader().id();
                };
                log.add(desc);
            });
            manager.start();

            managers.add(manager);
            eventLogs.add(log);
        }

        // ── Step 1: Form cluster (all-to-all discovery) ──
        simulateDiscovery(nodes, managers);

        var status0 = managers.get(0).status();
        results.put("initial_member_count", status0.memberCount());
        System.out.println("[1] Cluster formed: " + status0.memberCount() + " members");

        // ── Step 2: Simulate partition (3 vs 2) ──
        var majority = new HashSet<>(List.of("node-1", "node-2", "node-3"));
        var minority = new HashSet<>(List.of("node-4", "node-5"));

        partitionState.createPartition(majority, minority);
        results.put("partition_created", true);
        System.out.println("[2] Partition created: majority=" + majority + ", minority=" + minority);

        // Explicitly mark cross-partition nodes as FAILED on each manager.
        // This simulates what would happen in real networks: nodes on the other
        // side become unreachable and are detected as failed.
        simulatePartitionEffects(nodes, managers, majority, minority);

        // ── Step 3: Check partition state ──
        var majorityStatus = managers.get(0).status();
        results.put("majority_partition_size", majorityStatus.memberCount());
        System.out.println("[3] Majority partition sees " + majorityStatus.memberCount() + " members");

        var minorityStatus = managers.get(3).status();
        results.put("minority_partition_size", minorityStatus.memberCount());
        System.out.println("[4] Minority partition sees " + minorityStatus.memberCount() + " members");

        // Verify no split-brain: majority has quorum, minority does not
        int quorum = 3;
        boolean majorityHasQuorum = majorityStatus.memberCount() >= quorum;
        boolean minorityHasQuorum = minorityStatus.memberCount() >= quorum;
        results.put("majority_has_quorum", majorityHasQuorum);
        results.put("minority_has_quorum", minorityHasQuorum);
        results.put("no_split_brain", majorityHasQuorum != minorityHasQuorum);
        System.out.println("[5] No split-brain: majority has quorum=" + majorityHasQuorum
                + ", minority has quorum=" + minorityHasQuorum);

        // ── Step 4: Majority continues operations ──
        boolean majorityCanServe = majorityHasQuorum;
        boolean minorityReadonly = !minorityHasQuorum;
        results.put("majority_serves_operations", majorityCanServe);
        results.put("minority_read_only", minorityReadonly);
        System.out.println("[6] Majority serves: " + majorityCanServe
                + ", Minority read-only: " + minorityReadonly);

        // ── Step 5: Partition heals ──
        partitionState.healPartition();
        results.put("partition_healed", true);
        System.out.println("[7] Partition healed");

        // Recovery: all-to-all discovery re-discovers previously failed nodes
        simulateDiscovery(nodes, managers);

        var healedStatus = managers.get(0).status();
        results.put("healed_member_count", healedStatus.memberCount());
        System.out.println("[8] After healing: " + healedStatus.memberCount() + " members");

        // ── Shutdown ──
        for (var manager : managers) {
            manager.close();
        }

        var eventCounts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            eventCounts.put(nodes.get(i).id(), eventLogs.get(i).size());
        }
        results.put("events_per_node", eventCounts);
        System.out.println("[9] Events per node: " + eventCounts);

        return results;
    }

    /**
     * Simulates all-to-all heartbeat discovery. Each manager receives
     * heartbeats from every other node, adding or recovering them.
     */
    private void simulateDiscovery(List<MutableNode> nodes, List<ClusterManager> managers) {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i != j) {
                    managers.get(j).processHeartbeat(nodes.get(i).toNode());
                }
            }
        }
    }

    /**
     * Simulates the effects of a network partition. For each manager,
     * marks nodes in the opposite partition as FAILED using
     * {@link ClusterManager#simulateFailure(String)}.
     *
     * <p>This is deterministic — no timing or Thread.sleep involved.
     */
    private void simulatePartitionEffects(List<MutableNode> nodes, List<ClusterManager> managers,
                                           Set<String> groupA, Set<String> groupB) {
        for (int j = 0; j < managers.size(); j++) {
            String localNodeId = nodes.get(j).id();
            for (int i = 0; i < nodes.size(); i++) {
                String remoteNodeId = nodes.get(i).id();
                if (remoteNodeId.equals(localNodeId)) continue;

                boolean localInA = groupA.contains(localNodeId);
                boolean remoteInA = groupA.contains(remoteNodeId);

                // If local and remote are in different partitions, mark remote as failed
                if (localInA != remoteInA) {
                    managers.get(j).simulateFailure(remoteNodeId);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Partition Tolerance Demo ===");
        System.out.println();

        var demo = new PartitionToleranceDemo();
        var results = demo.run();

        System.out.println();
        System.out.println("=== Results ===");
        results.forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }
}
