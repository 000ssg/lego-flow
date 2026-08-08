package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MqttBroker}.
 *
 * <p>Timing-critical assertions use {@link TestAssertions} with exponential
 * backoff instead of {@code Thread.sleep()} to avoid flaky failures under parallel
 * execution (-T 1C).
 *
 * @since 0.1.0
 */
class MqttBrokerTest {

    private MqttBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", 0);
        port = broker.getPort();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testBrokerStartsAndBinds() {
        // Given/When: broker started in setUp

        // Then: bound to a port
        assertThat(port).isGreaterThan(0);
    }

    @Test
    void testAcceptClientConnection() throws Exception {
        // Given: a client
        try (var client = new MqttClient(config("accept-test"))) {
            // When: connect
            client.connect().get(5, TimeUnit.SECONDS);

            // Then: broker sees the client
            assertThat(broker.getConnectedClients()).contains("accept-test");
        }
    }

    @Test
    void testRouteMessageBetweenClients() throws Exception {
        // Given: publisher and subscriber
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("route-sub"));
             var pub = new MqttClient(config("route-pub"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("route/test", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish
            pub.publish("route/test", "routed".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: message routed to subscriber
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("routed");
        }
    }

    @Test
    void testQoS0Delivery() throws Exception {
        // Given: QoS 0 setup
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qos0-sub"));
             var pub = new MqttClient(config("qos0-pub"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("qos0", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish QoS 0
            pub.publish("qos0", "fire-forget".getBytes(), QoS.AT_MOST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("fire-forget");
        }
    }

    @Test
    void testQoS1Delivery() throws Exception {
        // Given: QoS 1 setup
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qos1-sub"));
             var pub = new MqttClient(config("qos1-pub"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("qos1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish QoS 1
            pub.publish("qos1", "ack-me".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("ack-me");
        }
    }

    @Test
    void testRetainedMessageDelivery() throws Exception {
        // Given: publish retained message
        try (var pub = new MqttClient(config("retain-pub"))) {
            pub.connect().get(5, TimeUnit.SECONDS);
            pub.publish("retain/test", "retained-value".getBytes(),
                    QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
            pub.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete (retry-based)
        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("retain-pub"),
                Duration.ofSeconds(3), 50);

        // When: new subscriber connects
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("retain-sub"))) {
            sub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("retain/test", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // Then: receives retained message
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("retained-value");
        }
    }

    @Test
    void testRetainStoreClears() throws Exception {
        // Given: retained message
        try (var pub = new MqttClient(config("retain-clear-pub"))) {
            pub.connect().get(5, TimeUnit.SECONDS);
            pub.publish("retain/clear", "value".getBytes(), QoS.AT_LEAST_ONCE, true)
                    .get(5, TimeUnit.SECONDS);

            // When: publish empty retained (clears)
            pub.publish("retain/clear", new byte[0], QoS.AT_LEAST_ONCE, true)
                    .get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete (retry-based)
        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("retain-clear-pub"),
                Duration.ofSeconds(3), 50);

        // Then: retain store is empty for that topic
        assertThat(broker.getRetainStore().get("retain/clear")).isNull();
    }

    @Test
    void testMultipleSubscribersReceiveMessage() throws Exception {
        // Given: two subscribers
        var received1 = new CopyOnWriteArrayList<String>();
        var received2 = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub1 = new MqttClient(config("multi-sub1"));
             var sub2 = new MqttClient(config("multi-sub2"));
             var pub = new MqttClient(config("multi-pub"))) {
            sub1.connect().get(5, TimeUnit.SECONDS);
            sub2.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub1.subscribe("multi/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received1.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            sub2.subscribe("multi/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received2.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish
            pub.publish("multi/topic", "broadcast".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: both receive
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received1).contains("broadcast");
            assertThat(received2).contains("broadcast");
        }
    }

    @Test
    void testWildcardSubscriptionRouting() throws Exception {
        // Given: wildcard subscriber
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub = new MqttClient(config("wc-sub"));
             var pub = new MqttClient(config("wc-pub"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("wc/+/data", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish to matching topics
            pub.publish("wc/a/data", "1".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);
            pub.publish("wc/b/data", "2".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: both match
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).containsExactlyInAnyOrder("wc/a/data", "wc/b/data");
        }
    }

    @Test
    void testClientDisconnectRemovesFromList() throws Exception {
        // Given: connected client
        try (var client = new MqttClient(config("disconnect-test"))) {
            client.connect().get(5, TimeUnit.SECONDS);
            assertThat(broker.getConnectedClients()).contains("disconnect-test");

            // When: disconnect
            client.disconnect().get(5, TimeUnit.SECONDS);

            // Then: removed from connected list (retry-based)
            TestAssertions.assertThatCondition(
                    "client removed from connected list after disconnect",
                    () -> !broker.getConnectedClients().contains("disconnect-test"),
                    Duration.ofSeconds(3));
        }
    }

    @Test
    void testBrokerStopDisconnectsAll() throws Exception {
        // Given: connected clients
        var c1 = new MqttClient(config("stop-1"));
        var c2 = new MqttClient(config("stop-2"));
        c1.connect().get(5, TimeUnit.SECONDS);
        c2.connect().get(5, TimeUnit.SECONDS);

        // When: stop broker
        broker.stop();

        // Then: clients no longer connected (broker side cleared)
        assertThat(broker.getConnectedClients()).isEmpty();
        c1.close();
        c2.close();
    }

    private MqttClientConfig config(String clientId) {
        return MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build();
    }
}
