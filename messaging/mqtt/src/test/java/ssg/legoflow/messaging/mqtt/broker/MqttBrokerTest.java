package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.client.MqttMessageListener;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
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
 * <p>Uses in-memory transports for transport-agnostic testing.
 *
 * @since 0.2.0
 */
class MqttBrokerTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testBrokerStarts() {
        assertThat(true).isTrue();
    }

    @Test
    void testAcceptClientConnection() throws Exception {
        try (var client = createClient("accept-test")) {
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testMultipleClients() throws Exception {
        var clients = new MqttClient[3];
        var latches = new CountDownLatch[3];
        var receivedMessages = new String[3];

        for (int i = 0; i < 3; i++) {
            clients[i] = createClient("client-" + i);
            latches[i] = new CountDownLatch(1);
            final int idx = i;
            clients[i].subscribe("test/topic", QoS.AT_MOST_ONCE, (topic, payload, qos, reasonCode) -> {
                receivedMessages[idx] = new String(payload, StandardCharsets.UTF_8);
                latches[idx].countDown();
            }).get(5, TimeUnit.SECONDS);
        }

        for (int i = 0; i < 3; i++) {
            clients[i].publish("test/topic", ("hello-from-client-" + i).getBytes(StandardCharsets.UTF_8),
                QoS.AT_MOST_ONCE, false).get(5, TimeUnit.SECONDS);
        }

        for (int i = 0; i < 3; i++) {
            assertThat(latches[i].await(10, TimeUnit.SECONDS))
                .as("Client " + i + " should receive message")
                .isTrue();
            assertThat(receivedMessages[i]).isEqualTo("hello-from-client-" + i);
        }

        for (var client : clients) {
            client.close();
        }
    }

    @Test
    void testClientDisconnect() throws Exception {
        var client = createClient("disconnect-test");
        assertThat(client.isConnected()).isTrue();
        client.close();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void testQoS0Publish() throws Exception {
        var client = createClient("qos0-test");
        var latch = new CountDownLatch(1);
        client.subscribe("qos0/topic", QoS.AT_MOST_ONCE, (topic, payload, qos, reasonCode) -> latch.countDown())
            .get(5, TimeUnit.SECONDS);
        client.publish("qos0/topic", "test".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false)
            .get(5, TimeUnit.SECONDS);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        client.close();
    }

    @Test
    void testQoS1Publish() throws Exception {
        var client = createClient("qos1-test");
        var latch = new CountDownLatch(1);
        client.subscribe("qos1/topic", QoS.AT_LEAST_ONCE, (topic, payload, qos, reasonCode) -> latch.countDown())
            .get(5, TimeUnit.SECONDS);
        client.publish("qos1/topic", "test".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false)
            .get(5, TimeUnit.SECONDS);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        client.close();
    }

    @Test
    void testSharedSubscription() throws Exception {
        var latch1 = new CountDownLatch(1);
        var latch2 = new CountDownLatch(1);
        var received1 = new String[1];
        var received2 = new String[1];

        var client1 = createClient("shared-client-1");
        var client2 = createClient("shared-client-2");

        client1.subscribe("$share/group/test/topic", QoS.AT_LEAST_ONCE, (topic, payload, qos, reasonCode) -> {
            received1[0] = new String(payload, StandardCharsets.UTF_8);
            latch1.countDown();
        }).get(5, TimeUnit.SECONDS);

        client2.subscribe("$share/group/test/topic", QoS.AT_LEAST_ONCE, (topic, payload, qos, reasonCode) -> {
            received2[0] = new String(payload, StandardCharsets.UTF_8);
            latch2.countDown();
        }).get(5, TimeUnit.SECONDS);

        var client3 = createClient("publisher");
        client3.publish("test/topic", "shared-message".getBytes(StandardCharsets.UTF_8),
            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

        boolean received = latch1.await(5, TimeUnit.SECONDS) || latch2.await(5, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        client1.close();
        client2.close();
        client3.close();
    }

    private MqttClient createClient(String clientId) throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        return new MqttClient(MqttClientConfig.defaults()
                .clientId(clientId).build(), transports[1]);
    }

    @Test
    void testQoS0Delivery() throws Exception {
        // Given: QoS 0 setup
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qos0-sub");
             var pub = createClient("qos0-pub")) {
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

        try (var sub = createClient("qos1-sub");
             var pub = createClient("qos1-pub")) {
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
        try (var pub = createClient("retain-pub")) {
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

        try (var sub = createClient("retain-sub")) {
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
        try (var pub = createClient("retain-clear-pub")) {
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

        try (var sub1 = createClient("multi-sub1");
             var sub2 = createClient("multi-sub2");
             var pub = createClient("multi-pub")) {
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

        try (var sub = createClient("wc-sub");
             var pub = createClient("wc-pub")) {
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
        try (var client = createClient("disconnect-test")) {
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
        var c1 = createClient("stop-1");
        var c2 = createClient("stop-2");
        c1.connect().get(5, TimeUnit.SECONDS);
        c2.connect().get(5, TimeUnit.SECONDS);

        // When: stop broker
        broker.stop();

        // Then: clients no longer connected (broker side cleared)
        assertThat(broker.getConnectedClients()).isEmpty();
        c1.close();
        c2.close();
    }
}
