package ssg.legoflow.demos.cluster;

import ssg.legoflow.http.cluster.CacheCoherenceConfig;
import ssg.legoflow.http.cluster.CacheCoherenceFeature;
import ssg.legoflow.http.cluster.HttpCacheInvalidator;
import ssg.legoflow.http.cluster.SessionAffinityConfig;
import ssg.legoflow.http.cluster.StickySessionHasher;
import ssg.legoflow.http.cluster.StickySessionRouter;
import ssg.legoflow.http.caching.ResponseCache;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.network.cluster.core.ClusterConfig;
import ssg.legoflow.network.cluster.core.ClusterEvent;
import ssg.legoflow.network.cluster.core.ClusterHealthChecker;
import ssg.legoflow.network.cluster.core.ClusterManager;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterRole;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterTransport;
import ssg.legoflow.network.cluster.core.hashing.ConsistentHashRing;
import ssg.legoflow.network.cluster.core.hashing.MurmurHash3;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
/**
 * Demo: Auto-Discovering Web Server Cluster.
 *
 * <p>Simulates a 3-node web cluster with:
 * <ul>
 *   <li>Cluster membership via simulated in-memory transport</li>
 *   <li>Sticky sessions for stateful client routing</li>
 *   <li>Consistent hashing for data partitioning</li>
 *   <li>Cache coherence via publish-subscribe invalidation</li>
 * </ul>
 *
 * <p>Scenario:
 * <ol>
 *   <li>3 nodes boot and join the cluster</li>
 *   <li>Client sends GET requests; sticky sessions route consistently</li>
 *   <li>Data is partitioned across nodes via consistent hashing</li>
 *   <li>Node A receives PUT → publishes cache invalidation</li>
 *   <li>Nodes B and C receive invalidation → purge local cache</li>
 *   <li>Node B crashes → traffic rerouted to remaining nodes</li>
 *   <li>Node B recovers → re-joins, keys redistributed</li>
 * </ol>
 */
public final class AutoDiscoveringWebClusterDemo {

    AutoDiscoveringWebClusterDemo() {}

    /**
     * Simulated cache backed by a ConcurrentHashMap.
     */
    static class SimCache implements ResponseCache {
        private final Map<String, CachedResponse> store = new ConcurrentHashMap<>();

        @Override public Optional<CachedResponse> get(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override public void put(String key, CachedResponse value) {
            store.put(key, value);
        }

        @Override public void remove(String key) {
            store.remove(key);
        }

        @Override public void invalidate(String pattern) {
            store.keySet().removeIf(key -> matches(key, pattern));
        }

        @Override public int size() {
            return store.size();
        }

        Set<String> keySet() {
            return Set.copyOf(store.keySet());
        }

        @Override public void clear() {
            store.clear();
        }

        private static boolean matches(String key, String pattern) {
            if (pattern.endsWith("*")) {
                return key.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return key.equals(pattern);
        }
    }

    /**
     * In-memory transport for simulation (one instance per node).
     */
    static class SimTransport implements ClusterTransport {
        private final String localNodeId;
        private final java.util.function.Consumer<byte[]> inbox;
        private volatile boolean available = true;

        SimTransport(String localNodeId, java.util.function.Consumer<byte[]> inbox) {
            this.localNodeId = localNodeId;
            this.inbox = inbox;
        }

        void setAvailable(boolean available) {
            this.available = available;
        }

        @Override
        public CompletableFuture<Void> send(ClusterNode target, byte[] payload) {
            if (!available) return CompletableFuture.failedFuture(new RuntimeException("transport down"));
            inbox.accept(payload);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> broadcast(ClusterNode sender, byte[] payload) {
            if (!available) return CompletableFuture.failedFuture(new RuntimeException("transport down"));
            inbox.accept(payload);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void close() {
            this.available = false;
        }
    }

    /**
     * Simulates a web server node in the cluster.
     */
    static class WebNode {
        private final String nodeId;
        private final ClusterNode clusterNode;
        private final ClusterManager manager;
        private final SimCache cache = new SimCache();
        private final List<String> eventLog = new CopyOnWriteArrayList<>();

        WebNode(String nodeId, int port) {
            this.nodeId = nodeId;
            this.clusterNode = ClusterNode.builder()
                    .id(nodeId)
                    .host("127.0.0.1")
                    .port(port)
                    .role(ClusterRole.BOTH)
                    .status(ClusterNodeStatus.ACTIVE)
                    .build();

            var config = ClusterConfig.builder()
                    .name("web-cluster")
                    .heartbeatInterval(Duration.ofMillis(100))
                    .heartbeatFailureThreshold(100)
                    .build();

            var transport = new SimTransport(nodeId, payload -> {});
            var checker = ClusterHealthChecker.simple(Duration.ofSeconds(10), Duration.ofSeconds(2));
            this.manager = new ClusterManager(clusterNode, config, transport, checker);

            this.manager.addListener(event -> {
                String desc = switch (event) {
                    case ClusterEvent.NodeJoined j -> "NodeJoined:" + j.node().id();
                    case ClusterEvent.NodeLeft l -> "NodeLeft:" + l.node().id();
                    case ClusterEvent.NodeFailed f -> "NodeFailed:" + f.node().id();
                    case ClusterEvent.NodeRecovered r -> "NodeRecovered:" + r.node().id();
                    case ClusterEvent.LeaderChanged c -> "LeaderChanged:" + c.newLeader().id();
                };
                eventLog.add(desc);
            });
        }

        void start() { manager.start(); }
        void stop() { manager.close(); }
        String nodeId() { return nodeId; }
        ClusterNode clusterNode() { return clusterNode; }
        ClusterManager manager() { return manager; }
        SimCache cache() { return cache; }
        List<String> eventLog() { return eventLog; }
    }

    // ── Demo execution ──

    /**
     * Runs the full web cluster simulation.
     */
    public Map<String, Object> run() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        // ── Step 1: 3 nodes boot ──
        var nodeA = new WebNode("node-A", 8080);
        var nodeB = new WebNode("node-B", 8081);
        var nodeC = new WebNode("node-C", 8082);
        nodeA.start();
        nodeB.start();
        nodeC.start();

        // Simulate mutual discovery
        simulateDiscovery(nodeA, nodeB, nodeC);
        Thread.sleep(300);

        var status = nodeA.manager().status();
        results.put("cluster_size", status.memberCount());
        System.out.println("[1] Cluster formed: " + status.memberCount() + " members");

        // ── Step 2: Sticky session routing ──
        var config = SessionAffinityConfig.builder()
                .cookieName("SESSIONID")
                .build();
        var hasher = new StickySessionHasher();
        var router = new StickySessionRouter(config, hasher);
        var allNodes = List.of(nodeA.clusterNode(), nodeB.clusterNode(), nodeC.clusterNode());
        router.updateNodes(allNodes);

        var request1 = HttpRequest.of(HttpMethod.GET, "/api/users/1");
        var routed1 = router.route(request1);
        results.put("first_request_route", routed1.id());
        System.out.println("[2a] First request routed to: " + routed1.id());

        var cookie = router.buildCookie(routed1);
        results.put("session_server", routed1.id());
        var request2 = HttpRequest.of(HttpMethod.GET, "/api/orders/1");
        request2.getHeaders().set("Cookie", cookie);
        var routed2 = router.route(request2);
        boolean sameNode = routed1.id().equals(routed2.id());
        results.put("sticky_session_consistent", sameNode);
        System.out.println("[2b] Sticky session consistent: " + sameNode);

        // ── Step 3: Consistent hashing for data partitioning ──
        var ring = new ConsistentHashRing(160, MurmurHash3.INSTANCE);
        ring.add(nodeA.clusterNode());
        ring.add(nodeB.clusterNode());
        ring.add(nodeC.clusterNode());

        var keys = List.of("/api/users/1", "/api/users/2", "/api/items/1",
                "/api/items/2", "/api/orders/1");
        var dataPartition = new LinkedHashMap<String, Integer>();
        for (String key : keys) {
            var assigned = ring.getNode(key);
            dataPartition.merge(assigned.id(), 1, Integer::sum);
        }
        results.put("data_partition", dataPartition);
        System.out.println("[3] Data partition: " + dataPartition);

        // ── Step 4: Warm up cache on all nodes ──
        simulateCacheWarmup(nodeA, nodeB, nodeC);
        int cacheA_before = nodeA.cache().size();
        int cacheB_before = nodeB.cache().size();
        int cacheC_before = nodeC.cache().size();
        results.put("cache_A_before", cacheA_before);
        results.put("cache_B_before", cacheB_before);
        results.put("cache_C_before", cacheC_before);
        System.out.println("[4] Cache warmup: A=" + cacheA_before
                + ", B=" + cacheB_before + ", C=" + cacheC_before);

        // ── Step 5: Cache coherence ──
        var invalidationQueue = new LinkedBlockingQueue<HttpCacheInvalidator.CacheInvalidationEvent>();
        var coherenceConfig = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PREFIX)
                .build();

        var featureA = new CacheCoherenceFeature(coherenceConfig);
        featureA.initialize(nodeA.cache(), "node-A", invalidationQueue::offer);

        var writeRequest = HttpRequest.of(HttpMethod.PUT, "/api/users/1");
        featureA.handleWrite(writeRequest).join();

        var event = invalidationQueue.poll(1, TimeUnit.SECONDS);
        results.put("invalidation_published", event != null);
        if (event != null) {
            results.put("invalidation_paths", event.paths());
            results.put("invalidation_source", event.sourceNode());
            System.out.println("[5a] Invalidation published: source=" + event.sourceNode()
                    + ", paths=" + event.paths());
        }

        // Nodes B and C process the invalidation
        if (event != null) {
            var featureB = new CacheCoherenceFeature(coherenceConfig);
            var featureC = new CacheCoherenceFeature(coherenceConfig);
            featureB.initialize(nodeB.cache(), "node-B", e -> {});
            featureC.initialize(nodeC.cache(), "node-C", e -> {});
            featureB.handleInvalidationEvent(event);
            featureC.handleInvalidationEvent(event);
        }

        int cacheA_after = nodeA.cache().size();
        int cacheB_after = nodeB.cache().size();
        int cacheC_after = nodeC.cache().size();
        results.put("cache_A_after_invalidation", cacheA_after);
        results.put("cache_B_after_invalidation", cacheB_after);
        results.put("cache_C_after_invalidation", cacheC_after);
        System.out.println("[5b] After invalidation: A=" + cacheA_after
                + ", B=" + cacheB_after + ", C=" + cacheC_after);

        // ── Step 6: Node B crashes ──
        var nodeBFailed = ClusterNode.builder()
                .id("node-B")
                .host("127.0.0.1")
                .port(8081)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.FAILED)
                .build();
        var activeNodes = List.of(nodeA.clusterNode(), nodeC.clusterNode());
        router.updateNodes(activeNodes);

        var afterCrash = router.route(HttpRequest.of(HttpMethod.GET, "/api/users/1"));
        results.put("routing_after_crash", afterCrash.id());
        results.put("node_B_excluded_from_routing", !afterCrash.id().equals("node-B"));
        System.out.println("[6] After node B crash: routing to " + afterCrash.id()
                + " (node-B excluded: " + !afterCrash.id().equals("node-B") + ")");

        // ── Step 7: Node B recovers ──
        router.updateNodes(allNodes);

        var afterRecovery = router.route(HttpRequest.of(HttpMethod.GET, "/api/users/1"));
        results.put("routing_after_recovery", afterRecovery.id());
        System.out.println("[7] After node B recovery: routing to " + afterRecovery.id());

        // ── Shutdown ──
        nodeA.stop();
        nodeB.stop();
        nodeC.stop();

        results.put("total_events_A", nodeA.eventLog().size());
        results.put("total_events_B", nodeB.eventLog().size());
        results.put("total_events_C", nodeC.eventLog().size());

        return results;
    }

    private void simulateDiscovery(WebNode... nodes) {
        for (var from : nodes) {
            for (var to : nodes) {
                if (!from.nodeId().equals(to.nodeId())) {
                    to.manager().processHeartbeat(from.clusterNode());
                }
            }
        }
    }

    private void simulateCacheWarmup(WebNode... nodes) {
        var dummy = HttpResponse.of(HttpStatus.OK, "cached");
        for (var node : nodes) {
            node.cache().put("/api/users/1", new ResponseCache.CachedResponse(
                    dummy, System.currentTimeMillis(), 60));
            node.cache().put("/api/items/1", new ResponseCache.CachedResponse(
                    dummy, System.currentTimeMillis(), 60));
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Auto-Discovering Web Cluster Demo ===");
        System.out.println();

        var demo = new AutoDiscoveringWebClusterDemo();
        var results = demo.run();

        System.out.println();
        System.out.println("=== Results ===");
        results.forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }
}
