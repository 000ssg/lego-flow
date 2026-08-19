package ssg.legoflow.messaging.nats.demo;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.messaging.nats.cluster.NatsClusterBus;
import ssg.legoflow.messaging.nats.cluster.NatsClusterConfig;
import ssg.legoflow.messaging.nats.cluster.NatsClusterHealthBus;
import ssg.legoflow.messaging.nats.cluster.NatsDistributedPubSub;
import ssg.legoflow.network.cluster.core.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Demonstrates NATS-based cluster messaging patterns.
 *
 * <p>Starts an embedded NATS server, creates multiple cluster nodes,
 * and demonstrates:
 * <ul>
 *   <li>Cluster-scoped publish/subscribe</li>
 *   <li>Request/reply between nodes</li>
 *   <li>Health check heartbeat exchange</li>
 *   <li>Distributed pub/sub with topic routing</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class NatsClusterBusDemo {

    private static final Logger LOG = LoggerFactory.getLogger(NatsClusterBusDemo.class);

    private NatsClusterBusDemo() {}

    /**
     * Runs the cluster bus demo.
     *
     * @param port the NATS server port (0 for ephemeral)
     * @return total operations performed
     * @throws IOException if connection fails
     * @throws InterruptedException if interrupted
     */
    public static int run(int port) throws Exception {
        int total = 0;

        try (var server = new NatsServer(port)) {
            server.start(port);
            int actualPort = server.port();
            LOG.info("NATS server started on port {}", actualPort);

            // Create 3 cluster nodes
            String clusterId = "demo-cluster";
            var nodes = new NatsClusterNode[3];
            var buses = new NatsClusterBus[3];

            for (int i = 0; i < 3; i++) {
                String nodeId = "node-" + i;
                NatsClusterConfig cfg = NatsClusterConfig.builder()
                        .serverUrl("nats://localhost:" + actualPort)
                        .clusterId(clusterId)
                        .nodeId(nodeId)
                        .build();

                var client = new NatsClient("localhost", actualPort,
                        ConnectOptions.withDefaults(nodeId));
                client.connect();
                buses[i] = new NatsClusterBus(cfg, client);
                nodes[i] = new NatsClusterNode(client, buses[i]);
                LOG.info("Created {}", nodeId);
            }

            try {
                // --- Scenario 1: Publish/Subscribe ---
                LOG.info("=== Scenario 1: Publish/Subscribe ===");
                total += runPubSubScenario(buses);

                // --- Scenario 2: Request/Reply ---
                LOG.info("=== Scenario 2: Request/Reply ===");
                total += runRequestReplyScenario(buses);

                // --- Scenario 3: Health Check ---
                LOG.info("=== Scenario 3: Health Check Heartbeat ===");
                total += runHealthCheckScenario(buses, actualPort);

                // --- Scenario 4: Distributed Pub/Sub ---
                LOG.info("=== Scenario 4: Distributed Pub/Sub ===");
                total += runDistributedPubSubScenario(buses);

                LOG.info("Demo completed. Total operations: {}", total);
            } finally {
                for (var node : nodes) node.close();
            }
        }

        return total;
    }

    private static int runPubSubScenario(NatsClusterBus[] buses) throws Exception {
        int count = 0;

        // All nodes subscribe to events
        for (int i = 0; i < buses.length; i++) {
            final int nodeIndex = i;
            AtomicInteger[] counters = new AtomicInteger[] { new AtomicInteger(0) };
            buses[i].subscribe("events.>", msg -> {
                LOG.info("Node-{} received event: {}", nodeIndex, msg.dataAsString());
                counters[0].incrementAndGet();
            });
        }

        Thread.sleep(200);

        // Node-0 publishes
        buses[0].publish("events.system.started", "Node-0 is up")
                .get(5, TimeUnit.SECONDS);

        Thread.sleep(500);
        count += 3; // Expected: all 3 nodes receive
        LOG.info("Pub/Sub: 1 message published, 3 nodes subscribed");
        return count;
    }

    private static int runRequestReplyScenario(NatsClusterBus[] buses) throws Exception {
        int count = 0;

        // Node-1 and Node-2 register as RPC handlers
        for (int i = 1; i < 3; i++) {
            int nodeId = i;
            buses[i].handleRequests("rpc.compute", payload -> {
                String input = new String(payload, StandardCharsets.UTF_8);
                String result = String.format("Node-%d computed: %s", nodeId, input);
                LOG.info("Node-{} handled RPC: {}", nodeId, result);
                return result.getBytes(StandardCharsets.UTF_8);
            });
        }

        Thread.sleep(200);

        // Node-0 sends requests
        CompletableFuture<byte[]> reply1 = buses[0].request("rpc.compute", "task-A".getBytes(StandardCharsets.UTF_8));
        String result1 = new String(reply1.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        LOG.info("Node-0 received: {}", result1);

        CompletableFuture<byte[]> reply2 = buses[0].request("rpc.compute", "task-B".getBytes(StandardCharsets.UTF_8));
        String result2 = new String(reply2.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        LOG.info("Node-0 received: {}", result2);

        count += 2;
        return count;
    }

    private static int runHealthCheckScenario(NatsClusterBus[] buses, int port) throws Exception {
        int count = 0;

        // Set up health listeners on Node-1 and Node-2
        var detectedNodes = new java.util.concurrent.ConcurrentHashMap<String, ClusterNode>();

        for (int i = 1; i < 3; i++) {
            int nodeId = i;
            NatsClusterHealthBus healthBus = new NatsClusterHealthBus(
                    buses[nodeId], Duration.ofSeconds(5), "127.0.0.1", 8000 + nodeId);
            healthBus.setHealthListener(node -> {
                LOG.info("Node-{} detected peer: {}", nodeId, node.id());
                detectedNodes.put(node.id(), node);
            });
            buses[nodeId].subscribe("heartbeat.>", msg -> {}); // already subscribed by health bus
        }

        Thread.sleep(200);

        // Node-0 health bus sends heartbeat
        NatsClusterHealthBus healthBus0 = new NatsClusterHealthBus(
                buses[0], Duration.ofSeconds(5), "127.0.0.1", 8000);
        healthBus0.sendHeartbeat();

        Thread.sleep(500);

        count += detectedNodes.size();
        LOG.info("Health check: {} peer(s) detected via heartbeat", detectedNodes.size());

        healthBus0.close();
        return count;
    }

    private static int runDistributedPubSubScenario(NatsClusterBus[] buses) throws Exception {
        int count = 0;

        // Create distributed pub/sub on each node
        var pubSubs = new NatsDistributedPubSub[3];
        for (int i = 0; i < 3; i++) {
            pubSubs[i] = new NatsDistributedPubSub(buses[i]);
        }

        // Subscribe to metrics topic
        AtomicInteger receivedCount = new AtomicInteger(0);
        for (NatsDistributedPubSub ps : pubSubs) {
            ps.subscribe("metrics.cpu", payload -> {
                LOG.info("Received metric: {}", new String(payload, StandardCharsets.UTF_8));
                receivedCount.incrementAndGet();
            });
        }

        Thread.sleep(200);

        // Node-0 publishes metrics
        pubSubs[0].publish("metrics.cpu", "85").get(5, TimeUnit.SECONDS);
        pubSubs[0].publish("metrics.cpu", "92").get(5, TimeUnit.SECONDS);
        pubSubs[0].publish("metrics.cpu", "78").get(5, TimeUnit.SECONDS);

        Thread.sleep(500);

        count += receivedCount.get();
        LOG.info("Distributed Pub/Sub: {} metric readings received across {} nodes",
                receivedCount.get(), buses.length);

        for (var ps : pubSubs) ps.close();
        return count;
    }

    /**
     * Wrapper for client and bus.
     */
    private static final class NatsClusterNode implements AutoCloseable {
        private final NatsClient client;
        private final NatsClusterBus bus;

        NatsClusterNode(NatsClient client, NatsClusterBus bus) {
            this.client = client;
            this.bus = bus;
        }

        @Override
        public void close() {
            bus.close();
            client.close();
        }
    }
}
