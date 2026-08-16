package ssg.legoflow.messaging.nats.cluster;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.Subscription;
import ssg.legoflow.messaging.nats.server.NatsServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsDistributedPubSubTest {

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
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                AtomicReference<byte[]> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                pubSub.subscribe("orders", payload -> {
                    received.set(payload);
                    latch.countDown();
                });

                Thread.sleep(100);

                pubSub.publish("orders", "order-123".getBytes(StandardCharsets.UTF_8))
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("order-123".getBytes(StandardCharsets.UTF_8));

                pubSub.close();
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
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                AtomicReference<String> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);

                pubSub.subscribe("notifications", payload -> {
                    received.set(new String(payload, StandardCharsets.UTF_8));
                    latch.countDown();
                });

                Thread.sleep(100);

                pubSub.publish("notifications", "alert: high cpu")
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("alert: high cpu");

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void wildcard_subscription() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                List<String> received = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(3);

                pubSub.subscribeWildcard("metrics.*", payload -> {
                    received.add(new String(payload, StandardCharsets.UTF_8));
                    latch.countDown();
                });

                Thread.sleep(100);

                pubSub.publish("metrics.cpu", "85").get(5, TimeUnit.SECONDS);
                pubSub.publish("metrics.memory", "72").get(5, TimeUnit.SECONDS);
                pubSub.publish("metrics.disk", "45").get(5, TimeUnit.SECONDS);

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactlyInAnyOrder("85", "72", "45");

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void multi_token_wildcard_subscription() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                List<String> received = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(3);

                pubSub.subscribeWildcard("events.>", payload -> {
                    received.add(new String(payload, StandardCharsets.UTF_8));
                    latch.countDown();
                });

                Thread.sleep(100);

                pubSub.publish("events.user.created", "user-1").get(5, TimeUnit.SECONDS);
                pubSub.publish("events.order.placed", "order-1").get(5, TimeUnit.SECONDS);
                pubSub.publish("events.system.alert", "disk-full").get(5, TimeUnit.SECONDS);

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(3);

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void subscribeOthers_receives_peer_messages() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            // Two nodes in the same cluster
            NatsClusterConfig cfg1 = config(port, "node-A");
            NatsClusterConfig cfg2 = config(port, "node-B");

            try (var client1 = new NatsClient("localhost", port);
                 var client2 = new NatsClient("localhost", port)) {
                client1.connect();
                client2.connect();

                NatsClusterBus bus1 = new NatsClusterBus(cfg1, client1);
                NatsClusterBus bus2 = new NatsClusterBus(cfg2, client2);

                NatsDistributedPubSub pubSub1 = new NatsDistributedPubSub(bus1);
                NatsDistributedPubSub pubSub2 = new NatsDistributedPubSub(bus2);

                // node-B subscribes to others
                AtomicReference<byte[]> received = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                pubSub2.subscribeOthers("shared", payload -> {
                    received.set(payload);
                    latch.countDown();
                });

                Thread.sleep(200);

                // node-A publishes to shared topic
                pubSub1.publish("shared", "from-A".getBytes(StandardCharsets.UTF_8))
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get()).isEqualTo("from-A".getBytes(StandardCharsets.UTF_8));

                pubSub1.close();
                pubSub2.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void multiple_topics() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                AtomicInteger countA = new AtomicInteger(0);
                AtomicInteger countB = new AtomicInteger(0);
                CountDownLatch latch = new CountDownLatch(2);

                pubSub.subscribe("topic-a", payload -> { countA.incrementAndGet(); latch.countDown(); });
                pubSub.subscribe("topic-b", payload -> { countB.incrementAndGet(); latch.countDown(); });

                Thread.sleep(100);

                pubSub.publish("topic-a", "msg-a".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);
                pubSub.publish("topic-b", "msg-b".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(countA.get()).isEqualTo(1);
                assertThat(countB.get()).isEqualTo(1);

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void subscription_count() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                assertThat(pubSub.subscriptionCount()).isZero();

                pubSub.subscribe("topic-a", payload -> {});
                assertThat(pubSub.subscriptionCount()).isEqualTo(1);

                pubSub.subscribe("topic-b", payload -> {});
                assertThat(pubSub.subscriptionCount()).isEqualTo(2);

                pubSub.subscribeOthers("topic-c", payload -> {});
                assertThat(pubSub.subscriptionCount()).isEqualTo(3);

                pubSub.close();
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
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                pubSub.subscribe("a", payload -> {});
                pubSub.subscribe("b", payload -> {});
                assertThat(pubSub.subscriptions()).hasSize(2);

                pubSub.close();
                assertThat(pubSub.subscriptions()).isEmpty();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void returns_bus_reference() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                assertThat(pubSub.bus()).isSameAs(bus);

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void null_bus_throws() {
        assertThatThrownBy(() -> new NatsDistributedPubSub(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_topic_throws() throws Exception {
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);
                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);

                assertThatThrownBy(() -> pubSub.publish(null, "data".getBytes(StandardCharsets.UTF_8)))
                        .isInstanceOf(NullPointerException.class);
                assertThatThrownBy(() -> pubSub.publish("topic", (byte[]) null))
                        .isInstanceOf(NullPointerException.class);
                assertThatThrownBy(() -> pubSub.subscribe(null, payload -> {}))
                        .isInstanceOf(NullPointerException.class);

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }

    @Test
    void topic_subject_prefix() throws Exception {
        // Verify that topics are prefixed with "events."
        NatsServer server = new NatsServer();
        server.start(0);
        int port = server.port();

        try {
            NatsClusterConfig cfg = config(port, "node-1");

            try (var client = new NatsClient("localhost", port)) {
                client.connect();
                NatsClusterBus bus = new NatsClusterBus(cfg, client);

                // Subscribe at the bus level to see the full subject
                AtomicReference<String> fullSubject = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                bus.subscribe("events.>", msg -> {
                    fullSubject.set(msg.subject());
                    latch.countDown();
                });

                NatsDistributedPubSub pubSub = new NatsDistributedPubSub(bus);
                pubSub.subscribe("orders", payload -> {});

                Thread.sleep(100);

                pubSub.publish("orders", "data".getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS);

                assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(fullSubject.get()).isEqualTo("test-cluster.events.orders");

                pubSub.close();
            }
        } finally {
            server.close();
        }
    }
}
