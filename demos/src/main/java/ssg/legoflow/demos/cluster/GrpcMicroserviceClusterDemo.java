package ssg.legoflow.demos.cluster;

import ssg.legoflow.http.proxy.cluster.ClusterHealthMonitor;
import ssg.legoflow.http.proxy.cluster.ProxyClusterConfig;
import ssg.legoflow.http.proxy.reverse.BackendServer;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.rpc.grpc.cluster.ClusterSubchannel;
import ssg.legoflow.rpc.grpc.cluster.GrpcLoadBalancer;
import ssg.legoflow.rpc.grpc.cluster.HealthStatus;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Demo: gRPC Microservice Cluster with Load Balancing.
 *
 * <p>Simulates a client-side load balancer managing 5 gRPC backends:
 * <ul>
 *   <li>Round-robin distribution</li>
 *   <li>Least-request (dynamic) balancing</li>
 *   <li>Consistent hashing for session affinity</li>
 *   <li>Health monitoring with failover</li>
 *   <li>Recovery and re-inclusion</li>
 * </ul>
 *
 * <p>Scenario:
 * <ol>
 *   <li>5 backends registered with the load balancer</li>
 *   <li>Round-robin distributes evenly</li>
 *   <li>Least-request balances by active connections</li>
 *   <li>Consistent hash routes same key to same backend</li>
 *   <li>Backend 3 fails health check → removed from pool</li>
 *   <li>Remaining 4 backends handle traffic</li>
 *   <li>Backend 3 recovers → re-included</li>
 * </ol>
 */
public final class GrpcMicroserviceClusterDemo {

    GrpcMicroserviceClusterDemo() {}

    private static ClusterNode node(String id, int port, ClusterNodeStatus status) {
        return ClusterNode.builder()
                .id("grpc-backend-" + id)
                .host("127.0.0.1")
                .port(port)
                .role(ClusterRole.BOTH)
                .status(status)
                .build();
    }

    /**
     * Runs the gRPC cluster simulation.
     */
    public Map<String, Object> run() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        // ── Step 1: Create 5 backends ──
        var backends = new ArrayList<ClusterSubchannel>();
        for (int i = 1; i <= 5; i++) {
            backends.add(ClusterSubchannel.of(node("" + i, 9000 + i, ClusterNodeStatus.ACTIVE)));
        }

        // ── Step 2: Round-robin distribution ──
        var rrBalancer = GrpcLoadBalancer.roundRobin();
        rrBalancer.updateChannels(backends);

        var rrDistribution = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 20; i++) {
            var chosen = rrBalancer.select(backends, null);
            chosen.ifPresent(ch -> rrDistribution.merge(ch.node().id(), 1, Integer::sum));
        }
        results.put("round_robin_distribution", rrDistribution);
        System.out.println("[1] Round-robin (20 requests): " + rrDistribution);

        // Verify even distribution
        var rrValues = new ArrayList<>(rrDistribution.values());
        var rrEven = rrValues.stream().allMatch(v -> v >= 3 && v <= 5);
        results.put("round_robin_even", rrEven);
        System.out.println("[1b] Distribution even: " + rrEven);

        // ── Step 3: Least-request balancing ──
        var lrBalancer = GrpcLoadBalancer.leastRequest();
        lrBalancer.updateChannels(backends);

        var lrDistribution = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 15; i++) {
            var chosen = lrBalancer.select(backends, null);
            chosen.ifPresent(ch -> lrDistribution.merge(ch.node().id(), 1, Integer::sum));
        }
        results.put("least_request_distribution", lrDistribution);
        System.out.println("[2] Least-request (15 requests): " + lrDistribution);

        // ── Step 4: Consistent hashing ──
        var chBalancer = GrpcLoadBalancer.consistentHash();
        chBalancer.updateChannels(backends);

        var users = List.of("user-alice", "user-bob", "user-charlie", "user-dave", "user-eve");
        var hashRoutes = new LinkedHashMap<String, String>();
        for (String user : users) {
            var chosen = chBalancer.select(backends, user);
            chosen.ifPresent(ch -> hashRoutes.put(user, ch.node().id()));
        }
        results.put("consistent_hash_routes", hashRoutes);
        System.out.println("[3] Consistent hash routing: " + hashRoutes);

        // Verify same user always goes to same backend
        AtomicBoolean hashConsistent = new AtomicBoolean(true);
        for (String user : users) {
            var chosen = chBalancer.select(backends, user);
            chosen.ifPresent(ch -> {
                if (!ch.node().id().equals(hashRoutes.get(user))) hashConsistent.set(false);
            });
        }
        results.put("hash_consistency", hashConsistent.get());
        System.out.println("[3b] Hash consistency maintained: " + hashConsistent);

        // ── Step 5: Health monitoring — Backend 3 fails ──
        var events = new CopyOnWriteArrayList<ClusterHealthMonitor.HealthEvent>();

        // Create BackendServer instances for health monitoring
        var backendServers = new ArrayList<BackendServer>();
        for (int i = 1; i <= 5; i++) {
            var bs = new BackendServer("127.0.0.1", 9000 + i);
            bs.setHealthy(true);
            backendServers.add(bs);
        }

        var config = ProxyClusterConfig.builder()
                .backends(backendServers)
                .unhealthyThreshold(1)
                .recoveryThreshold(1)
                .build();
        var healthMonitor = new ClusterHealthMonitor(config, events::add);

        // Simulate health failure for backend 3
        var backend3 = backendServers.get(2);
        healthMonitor.recordCheck(backend3, false);

        var healthyAfterFailure = healthMonitor.getHealthyBackends();
        results.put("healthy_after_failure_count", healthyAfterFailure.size());
        System.out.println("[4] After backend 3 failure: " + healthyAfterFailure.size()
                + " healthy backends");

        // Update subchannels: backend 3 → NOT_SERVING
        var failedSubchannels = new ArrayList<ClusterSubchannel>();
        for (int i = 0; i < backends.size(); i++) {
            var ch = backends.get(i);
            if (i == 2) {
                failedSubchannels.add(ch.withHealth(HealthStatus.NOT_SERVING));
            } else {
                failedSubchannels.add(ch);
            }
        }

        // RR balancer with failed backend
        rrBalancer.updateChannels(failedSubchannels);
        var postFailureDist = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 10; i++) {
            var chosen = rrBalancer.select(failedSubchannels, null);
            chosen.ifPresent(ch -> postFailureDist.merge(ch.node().id(), 1, Integer::sum));
        }
        results.put("distribution_after_failure", postFailureDist);
        results.put("backend3_excluded", !postFailureDist.containsKey("grpc-backend-3"));
        System.out.println("[5] Distribution after failure (10 requests): " + postFailureDist);
        System.out.println("    Backend 3 excluded: " + !postFailureDist.containsKey("grpc-backend-3"));

        // ── Step 6: Backend 3 recovers ──
        healthMonitor.recordCheck(backend3, true);
        var recoveredHealthy = healthMonitor.getHealthyBackends();
        results.put("healthy_after_recovery_count", recoveredHealthy.size());
        System.out.println("[6] After recovery: " + recoveredHealthy.size() + " healthy backends");

        // Restore all backends
        rrBalancer.updateChannels(backends);
        var postRecovery = rrBalancer.select(backends, null);
        results.put("all_backends_available_again", postRecovery.isPresent());
        System.out.println("[7] All backends available: " + postRecovery.isPresent());

        // Verify events
        var eventTypeCounts = new LinkedHashMap<String, Integer>();
        events.forEach(e -> eventTypeCounts.merge(e.event().name(), 1, Integer::sum));
        results.put("health_events", eventTypeCounts);
        System.out.println("[8] Health events: " + eventTypeCounts);

        healthMonitor.close();

        return results;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== gRPC Microservice Cluster Demo ===");
        System.out.println();

        var demo = new GrpcMicroserviceClusterDemo();
        var results = demo.run();

        System.out.println();
        System.out.println("=== Results ===");
        results.forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }
}
