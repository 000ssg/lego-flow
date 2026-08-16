package ssg.legoflow.messaging.nats.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.server.NatsServer;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsClusterHealthBusTest {

    @Test
    void heartbeat_send_and_receive() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            // Create two buses — one publisher, one subscriber
            NatsClusterConfig cfg1 = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .heartbeatInterval(Duration.ofSeconds(1))
                    .build();

            NatsClusterConfig cfg2 = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-B")
                    .heartbeatInterval(Duration.ofSeconds(1))
                    .build();

            try (var client1 = new NatsClient("localhost", port);
                 var client2 = new NatsClient("localhost", port)) {
                client1.connect();
                client2.connect();

                NatsClusterBus bus1 = new NatsClusterBus(cfg1, client1);
                NatsClusterBus bus2 = new NatsClusterBus(cfg2, client2);

                NatsClusterHealthBus healthBus1 = new NatsClusterHealthBus(bus1, Duration.ofSeconds(1), "127.0.0.1", 8080);
                NatsClusterHealthBus healthBus2 = new NatsClusterHealthBus(bus2, Duration.ofSeconds(1), "127.0.0.1", 8081);

                // node-B listens for node-A heartbeats
                AtomicReference<ClusterNode> receivedNode = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                healthBus2.setHealthListener(node -> {
                    receivedNode.set(node);
                    latch.countDown();
                });

                // Allow subscription to propagate
                Thread.sleep(200);

                // node-A sends manual heartbeat
                healthBus1.sendHeartbeat();

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                ClusterNode peer = receivedNode.get();
                assertThat(peer).isNotNull();
                assertThat(peer.id()).isEqualTo("node-A");
                assertThat(peer.host()).isEqualTo("127.0.0.1");
                assertThat(peer.port()).isEqualTo(8080);
                assertThat(peer.role()).isEqualTo(ClusterRole.BOTH);
                assertThat(peer.status()).isEqualTo(ClusterNodeStatus.ACTIVE);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void start_and_stop() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsClusterHealthBus healthBus = new NatsClusterHealthBus(bus, Duration.ofMillis(500), "127.0.0.1", 8080);

                assertThat(healthBus.isRunning()).isFalse();

                healthBus.start();
                assertThat(healthBus.isRunning()).isTrue();

                // Start again — should be idempotent
                healthBus.start();
                assertThat(healthBus.isRunning()).isTrue();

                healthBus.stop();
                assertThat(healthBus.isRunning()).isFalse();

                // Restart after stop
                healthBus.start();
                assertThat(healthBus.isRunning()).isTrue();

                healthBus.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void self_node_info() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("my-node")
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsClusterHealthBus healthBus = new NatsClusterHealthBus(bus, Duration.ofSeconds(5), "192.168.1.10", 9090);

                ClusterNode self = healthBus.self();
                assertThat(self.id()).isEqualTo("my-node");
                assertThat(self.host()).isEqualTo("192.168.1.10");
                assertThat(self.port()).isEqualTo(9090);
                assertThat(self.role()).isEqualTo(ClusterRole.BOTH);
                assertThat(self.status()).isEqualTo(ClusterNodeStatus.ACTIVE);

                healthBus.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void heartbeat_json_format() throws Exception {
        // Verify the heartbeat JSON is well-formed by parsing it on the receiver side
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                // Subscribe to heartbeat subject to capture raw JSON
                AtomicReference<String> rawJson = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                bus.subscribe("heartbeat.>", msg -> {
                    rawJson.set(new String(msg.payload(), java.nio.charset.StandardCharsets.UTF_8));
                    latch.countDown();
                });

                Thread.sleep(100);

                NatsClusterHealthBus healthBus = new NatsClusterHealthBus(bus, Duration.ofSeconds(5), "10.0.0.1", 8080);
                healthBus.sendHeartbeat();

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                String json = rawJson.get();

                // Verify JSON contains all expected fields
                assertThat(json).contains("\"nodeId\":\"node-A\"");
                assertThat(json).contains("\"host\":\"10.0.0.1\"");
                assertThat(json).contains("\"port\":8080");
                assertThat(json).contains("\"timestamp\":");
                assertThat(json).contains("\"status\":\"active\"");
                assertThat(json).startsWith("{");
                assertThat(json).endsWith("}");

                healthBus.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void peer_heartbeat_not_echoed_to_self() throws Exception {
        // When a node receives its own heartbeat, it should be ignored by the health listener
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                NatsClusterHealthBus healthBus = new NatsClusterHealthBus(bus, Duration.ofSeconds(5), "127.0.0.1", 8080);

                // Set listener — should NOT be called for self heartbeats
                AtomicBoolean listenerCalled = new AtomicBoolean(false);
                healthBus.setHealthListener(node -> listenerCalled.set(true));

                // Allow subscription to propagate
                Thread.sleep(200);

                // Send heartbeat — own heartbeat should be ignored by listener
                healthBus.sendHeartbeat();
                Thread.sleep(500);

                // Listener should NOT have been called (self-heartbeat ignored)
                assertThat(listenerCalled.get()).isFalse();

                healthBus.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void close_stops_scheduler() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .heartbeatInterval(Duration.ofMillis(100))
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsClusterHealthBus healthBus = new NatsClusterHealthBus(bus, Duration.ofMillis(100), "127.0.0.1", 8080);

                healthBus.start();
                assertThat(healthBus.isRunning()).isTrue();

                healthBus.close();
                assertThat(healthBus.isRunning()).isFalse();

                // Starting after close should not cause issues
                healthBus.start();
                assertThat(healthBus.isRunning()).isTrue();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void multiple_heartbeats_received() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg1 = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-A")
                    .build();

            NatsClusterConfig cfg2 = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("test")
                    .nodeId("node-B")
                    .build();

            try (var client1 = new NatsClient("localhost", port);
                 var client2 = new NatsClient("localhost", port)) {
                client1.connect();
                client2.connect();

                NatsClusterBus bus1 = new NatsClusterBus(cfg1, client1);
                NatsClusterBus bus2 = new NatsClusterBus(cfg2, client2);

                NatsClusterHealthBus healthBus1 = new NatsClusterHealthBus(bus1, Duration.ofSeconds(1), "127.0.0.1", 8080);
                NatsClusterHealthBus healthBus2 = new NatsClusterHealthBus(bus2, Duration.ofSeconds(1), "127.0.0.1", 8081);

                List<ClusterNode> receivedNodes = new ArrayList<>();
                healthBus2.setHealthListener(receivedNodes::add);

                Thread.sleep(200);

                // Send multiple heartbeats
                healthBus1.sendHeartbeat();
                healthBus1.sendHeartbeat();
                healthBus1.sendHeartbeat();

                Thread.sleep(1000);

                assertThat(receivedNodes).hasSizeGreaterThanOrEqualTo(3);
                for (ClusterNode node : receivedNodes) {
                    assertThat(node.id()).isEqualTo("node-A");
                    assertThat(node.host()).isEqualTo("127.0.0.1");
                    assertThat(node.port()).isEqualTo(8080);
                }

                healthBus1.close();
                healthBus2.close();
            }
        } finally {
            server.close();
        }
    }
}
