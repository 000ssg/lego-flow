package ssg.legoflow.messaging.nats.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.server.NatsServer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class NatsClusterBusTest {

    private NatsClusterConfig config(int port, String nodeId) {
        return NatsClusterConfig.builder()
                .serverUrl("nats://localhost:" + port)
                .clusterId("test-cluster")
                .nodeId(nodeId)
                .build();
    }

    @Test
    void publish_and_subscribe() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicReference<byte[]> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                bus.subscribe("events.>", msg -> {
                    received.set(msg.payload());
                    latch.countDown();
                });

                // Small delay to let subscription register
                Thread.sleep(100);

                CompletableFuture<Void> pubFuture = bus.publish("events.topic-a",
                        "hello".getBytes(StandardCharsets.UTF_8));
                pubFuture.get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            server.close();
        }
    }

    @Test
    void publish_string_message() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicReference<String> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                bus.subscribe("events.>", msg -> {
                    received.set(new String(msg.payload(), StandardCharsets.UTF_8));
                    latch.countDown();
                });

                Thread.sleep(100);

                bus.publish("events.greeting", "hello world").get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("hello world");
            }
        } finally {
            server.close();
        }
    }

    @Test
    void request_and_reply() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                // Register a handler
                bus.handleRequests("rpc.greet", payload ->
                        ("Hello, " + new String(payload, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));

                // Allow handler registration
                Thread.sleep(200);

                CompletableFuture<byte[]> future = bus.request("rpc.greet",
                        "world".getBytes(StandardCharsets.UTF_8));
                byte[] reply = future.get(5, TimeUnit.SECONDS);
                assertThat(new String(reply, StandardCharsets.UTF_8)).isEqualTo("Hello, world");
            }
        } finally {
            server.close();
        }
    }

    @Test
    void request_string_reply() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                bus.handleRequests("rpc.echo", payload ->
                        ("echo: " + new String(payload, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));

                Thread.sleep(200);

                CompletableFuture<String> future = bus.request("rpc.echo", "test");
                String reply = future.get(5, TimeUnit.SECONDS);
                assertThat(reply).isEqualTo("echo: test");
            }
        } finally {
            server.close();
        }
    }

    @Test
    void broadcast_alias() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicReference<byte[]> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                bus.subscribe("broadcast.>", msg -> {
                    received.set(msg.payload());
                    latch.countDown();
                });

                Thread.sleep(100);

                bus.broadcast("broadcast.msg", "broadcast-data".getBytes(StandardCharsets.UTF_8))
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("broadcast-data".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            server.close();
        }
    }

    @Test
    void cluster_scoping() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("my-cluster")
                    .nodeId("my-node")
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                assertThat(bus.nodeId()).isEqualTo("my-node");
                assertThat(bus.clusterId()).isEqualTo("my-cluster");
                assertThat(bus.clusterPrefix()).isEqualTo("my-cluster");
                assertThat(bus.client()).isSameAs(client);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void cluster_subject_prefix() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = NatsClusterConfig.builder()
                    .serverUrl("nats://localhost:" + port)
                    .clusterId("prod-cluster")
                    .nodeId("node-A")
                    .build();

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicReference<String> receivedSubject = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                bus.subscribe("events.>", msg -> {
                    receivedSubject.set(msg.subject());
                    latch.countDown();
                });

                Thread.sleep(100);

                bus.publish("events.test", "data".getBytes(StandardCharsets.UTF_8))
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(receivedSubject.get()).isEqualTo("prod-cluster.events.test");
            }
        } finally {
            server.close();
        }
    }

    @Test
    void multiple_subscriptions() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicInteger countA = new AtomicInteger(0);
                AtomicInteger countB = new AtomicInteger(0);
                CountDownLatch latch = new CountDownLatch(2);

                bus.subscribe("channel-a", msg -> { countA.incrementAndGet(); latch.countDown(); });
                bus.subscribe("channel-b", msg -> { countB.incrementAndGet(); latch.countDown(); });

                Thread.sleep(100);

                bus.publish("channel-a", "msg-a".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);
                bus.publish("channel-b", "msg-b".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(countA.get()).isEqualTo(1);
                assertThat(countB.get()).isEqualTo(1);
                assertThat(bus.subscriptions()).hasSize(2);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void unsubscribe() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                AtomicInteger count = new AtomicInteger(0);
                bus.subscribe("test", msg -> count.incrementAndGet());
                assertThat(bus.subscriptions()).hasSize(1);

                boolean removed = bus.unsubscribe("test-cluster.test");
                assertThat(removed).isTrue();
                assertThat(bus.subscriptions()).isEmpty();

                // Now publish to the unsubscribed subject — handler should not be called
                bus.publish("test", "data".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);
                Thread.sleep(200);
                assertThat(count.get()).isEqualTo(0);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void close_clears_subscriptions() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                bus.subscribe("a", msg -> {});
                bus.subscribe("b", msg -> {});
                assertThat(bus.subscriptions()).hasSize(2);

                bus.close();
                assertThat(bus.subscriptions()).isEmpty();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void publish_with_null_subject_throws() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                assertThatThrownBy(() -> bus.publish(null, "data".getBytes(StandardCharsets.UTF_8)))
                        .isInstanceOf(NullPointerException.class);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void publish_with_null_payload_throws() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                assertThatThrownBy(() -> bus.publish("subject", (byte[]) null))
                        .isInstanceOf(NullPointerException.class);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void null_config_throws() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                assertThatThrownBy(() -> new NatsClusterBus(null, client))
                        .isInstanceOf(NullPointerException.class);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void null_client_throws() {
        NatsClusterConfig cfg = config(4222, "node-1");
        assertThatThrownBy(() -> new NatsClusterBus(cfg, null))
                .isInstanceOf(NullPointerException.class);
    }
}
