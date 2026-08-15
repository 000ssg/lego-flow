package ssg.legoflow.demos.cluster;

import ssg.legoflow.network.cluster.core.*;
import ssg.legoflow.network.cluster.core.hashing.ConsistentHasher;
import ssg.legoflow.network.cluster.core.hashing.ConsistentHashRing;
import ssg.legoflow.network.cluster.core.hashing.MurmurHash3;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulation demo: 3-node in-memory cluster with consistent hashing.
 *
 * Scenario:
 * 1. Node A starts as solo cluster
 * 2. Node B joins → heartbeats exchanged
 * 3. Node C joins → 3-node cluster
 * 4. Node B crashes → A and C mark B as FAILED
 * 5. Node B recovers → rejoins, keys redistributed
 * 6. All nodes leave → clean shutdown
 *
 * Demonstrates: membership, events, consistent hashing, failure detection, recovery.
 */
public class ClusterSimulationDemo {

    private static final String CLUSTER_NAME = "demo-cluster";
    private static final Duration HB_INTERVAL = Duration.ofMillis(200);

    // Shared transport (in-memory, no network)
    private final Map<String, ClusterNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> eventLogs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> broadcastMessages = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean transportAvailable = new AtomicBoolean(true);

    /**
     * Runs the full cluster simulation.
     */
    public Map<String, Object> run() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        // ── Step 1: Node A starts ──
        var nodeA = createNode("node-A", 8080);
        var managerA = createManager("node-A", nodeA);
        managerA.addListener(event -> logEvent("node-A", event));
        managerA.start();
        results.put("step1-members", managerA.status().memberCount());
        results.put("step1-leader", managerA.getLeader());
        System.out.println("[1] Node A started. Members: " + managerA.status().memberCount());

        // ── Step 2: Node B joins ──
        var nodeB = createNode("node-B", 8081);
        var managerB = createManager("node-B", nodeB);
        managerB.addListener(event -> logEvent("node-B", event));
        managerB.start();

        // Simulate B sending heartbeat to A (join signal)
        simulateHeartbeat(managerA, nodeB);
        simulateHeartbeat(managerB, nodeA);

        sleep(300);
        results.put("step2-members", new int[]{managerA.status().memberCount(), managerB.status().memberCount()});
        System.out.println("[2] Node B joined. A sees " + managerA.status().memberCount()
                + " members, B sees " + managerB.status().memberCount() + " members");

        // ── Step 3: Node C joins ──
        var nodeC = createNode("node-C", 8082);
        var managerC = createManager("node-C", nodeC);
        managerC.addListener(event -> logEvent("node-C", event));
        managerC.start();

        simulateHeartbeat(managerA, nodeC);
        simulateHeartbeat(managerB, nodeC);
        simulateHeartbeat(managerC, nodeA);
        simulateHeartbeat(managerC, nodeB);

        sleep(300);
        results.put("step3-members", new int[]{managerA.status().memberCount(),
                managerB.status().memberCount(), managerC.status().memberCount()});
        System.out.println("[3] Node C joined. 3-node cluster formed");

        // ── Step 4: Consistent hashing with 3 nodes ──
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        ring.add(nodeA);
        ring.add(nodeB);
        ring.add(nodeC);

        var assignments3 = new HashMap<String, String>();
        for (int i = 0; i < 100; i++) {
            String key = "data-" + i;
            var assigned = ring.getNode(key);
            assignments3.put(key, assigned.id());
        }
        results.put("hashing-3-node-counts", countAssignments(assignments3));
        System.out.println("[4] 100 keys distributed: " + countAssignments(assignments3));

        // ── Step 5: Node B crashes ──
        System.out.println("[5] Node B crashes...");

        // Mark B as suspect/failed on A and C
        managerA.simulateFailure("node-B");
        managerC.simulateFailure("node-B");

        sleep(300);

        // Verify B is failed
        var aMembersAfterCrash = managerA.status().members().stream()
                .filter(n -> n.status() == ClusterNodeStatus.ACTIVE)
                .count();
        var cMembersAfterCrash = managerC.status().members().stream()
                .filter(n -> n.status() == ClusterNodeStatus.ACTIVE)
                .count();
        results.put("step5-active-after-crash", new int[]{(int) aMembersAfterCrash, (int) cMembersAfterCrash});
        System.out.println("[5] After crash: A has " + aMembersAfterCrash
                + " active, C has " + cMembersAfterCrash + " active");

        // ── Step 6: Rehash after B is removed ──
        ring.remove(nodeB);
        var assignments2 = new HashMap<String, String>();
        for (int i = 0; i < 100; i++) {
            String key = "data-" + i;
            var assigned = ring.getNode(key);
            assignments2.put(key, assigned.id());
        }

        int moved = 0;
        for (String key : assignments3.keySet()) {
            if (!assignments3.get(key).equals(assignments2.get(key))) {
                moved++;
            }
        }
        results.put("redistribution-moved-keys", moved);
        System.out.println("[6] After B removed: " + moved + "/100 keys redistributed ("
                + (moved * 100 / 100.0) + "%)");
        System.out.println("    Distribution: " + countAssignments(assignments2));

        // ── Step 7: Node B recovers ──
        System.out.println("[7] Node B recovers...");
        simulateHeartbeat(managerA, nodeB);
        simulateHeartbeat(managerC, nodeB);

        sleep(300);
        var recoveryEventsA = eventLogs.getOrDefault("node-A", Collections.emptyList());
        var recoveryEventsC = eventLogs.getOrDefault("node-C", Collections.emptyList());
        results.put("recovery-events-A", recoveryEventsA.size());
        results.put("recovery-events-C", recoveryEventsC.size());
        System.out.println("[7] A logged " + recoveryEventsA.size() + " events total");

        // ── Step 8: Leader election ──
        managerA.setLeader(nodeA);
        var leaderBefore = managerA.getLeader();
        System.out.println("[8] Leader elected: " + leaderBefore.id());

        // Simulate leader change
        managerA.setLeader(nodeC);
        var leaderAfter = managerA.getLeader();
        results.put("leader-before", leaderBefore.id());
        results.put("leader-after", leaderAfter.id());
        System.out.println("[8] Leader changed: " + leaderBefore.id() + " → " + leaderAfter.id());

        // ── Step 9: Graceful shutdown ──
        System.out.println("[9] Graceful shutdown...");
        managerA.leave();
        managerB.leave();
        managerC.leave();

        sleep(300);
        managerA.close();
        managerB.close();
        managerC.close();

        results.put("final-A-running", managerA.isRunning());
        results.put("final-B-running", managerB.isRunning());
        results.put("final-C-running", managerC.isRunning());
        System.out.println("[9] All nodes shut down cleanly");

        return results;
    }

    private ClusterNode createNode(String id, int port) {
        return ClusterNode.builder()
                .id(id)
                .host("127.0.0.1")
                .port(port)
                .role(ClusterRole.BOTH)
                .addMetadata("datacenter", "local")
                .build();
    }

    private ClusterManager createManager(String name, ClusterNode node) {
        var config = ClusterConfig.builder()
                .name(CLUSTER_NAME)
                .heartbeatInterval(HB_INTERVAL)
                .heartbeatFailureThreshold(3)
                .joinTimeout(Duration.ofSeconds(5))
                .leaveTimeout(Duration.ofSeconds(3))
                .build();

        var transport = new SimulatedTransport(name);
        var checker = ClusterHealthChecker.simple(Duration.ofSeconds(10), Duration.ofSeconds(2));

        return new ClusterManager(node, config, transport, checker);
    }

    private void simulateHeartbeat(ClusterManager manager, ClusterNode remoteNode) {
        manager.processHeartbeat(remoteNode);
    }

    private void logEvent(String nodeId, ClusterEvent event) {
        var log = eventLogs.computeIfAbsent(nodeId, k -> Collections.synchronizedList(new ArrayList<>()));
        String description = switch (event) {
            case ClusterEvent.NodeJoined j -> "NodeJoined:" + j.node().id();
            case ClusterEvent.NodeLeft l -> "NodeLeft:" + l.node().id();
            case ClusterEvent.NodeFailed f -> "NodeFailed:" + f.node().id();
            case ClusterEvent.NodeRecovered r -> "NodeRecovered:" + r.node().id();
            case ClusterEvent.LeaderChanged c -> "LeaderChanged:" + c.newLeader().id();
        };
        log.add(description);
    }

    private Map<String, Integer> countAssignments(Map<String, String> assignments) {
        Map<String, Integer> counts = new HashMap<>();
        for (String node : assignments.values()) {
            counts.merge(node, 1, Integer::sum);
        }
        return counts;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulated in-memory transport.
     */
    private class SimulatedTransport implements ClusterTransport {
        private final String name;

        SimulatedTransport(String name) {
            this.name = name;
        }

        @Override
        public CompletableFuture<Void> send(ClusterNode target, byte[] payload) {
            if (!transportAvailable.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Transport down"));
            }
            broadcastMessages.add(name + " → " + target.id() + ": " + new String(payload));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> broadcast(ClusterNode sender, byte[] payload) {
            if (!transportAvailable.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Transport down"));
            }
            broadcastMessages.add(sender.id() + " (via " + name + ") → all: " + new String(payload));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isAvailable() {
            return transportAvailable.get();
        }

        @Override
        public void close() {
            // no-op for simulation
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Lego Flow Cluster Simulation Demo ===\n");

        var demo = new ClusterSimulationDemo();
        var results = demo.run();

        System.out.println("\n=== Results ===");
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            System.out.printf("  %-30s = %s%n", entry.getKey(), formatValue(entry.getValue()));
        }

        System.out.println("\n=== Broadcast Messages ===");
        // Print sample messages
        var messages = demo.broadcastMessages;
        int count = 0;
        for (var msg : messages) {
            System.out.println("  " + msg);
            if (++count >= 20) {
                System.out.println("  ... (" + (messages.size() - 20) + " more)");
                break;
            }
        }

        System.out.println("\nDemo completed successfully.");
    }

    private static String formatValue(Object value) {
        if (value instanceof int[] arr) {
            return Arrays.toString(arr);
        }
        if (value instanceof Map) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}
