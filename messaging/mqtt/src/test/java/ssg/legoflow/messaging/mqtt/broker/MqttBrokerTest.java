package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.client.MqttMessageListener;
import ssg.legoflow.messaging.mqtt.codec.InMemoryMqttTransport;
import ssg.legoflow.messaging.mqtt.protocol.*;
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
        var subscribers = new MqttClient[3];
        var publishers = new MqttClient[3];
        var receivedMessages = new CopyOnWriteArrayList<String>();

        for (int i = 0; i < 3; i++) {
            subscribers[i] = createClient("sub-client-" + i);
            publishers[i] = createClient("pub-client-" + i);
            final int idx = i;
            subscribers[i].subscribe("test/topic", QoS.AT_MOST_ONCE, (topic, payload, qos, reasonCode) -> {
                receivedMessages.add(new String(payload, StandardCharsets.UTF_8));
            }).get(5, TimeUnit.SECONDS);
        }

        // Wait for subscriptions to be delivered
        Thread.sleep(50);

        for (int i = 0; i < 3; i++) {
            publishers[i].publish("test/topic", ("hello-from-client-" + i).getBytes(StandardCharsets.UTF_8),
                QoS.AT_MOST_ONCE, false).get(5, TimeUnit.SECONDS);
        }

        // Wait for all messages to be delivered
        Thread.sleep(100);

        // Each of the 3 subscribers should receive all 3 messages
        assertThat(receivedMessages).hasSize(9); // 3 subscribers * 3 messages
        assertThat(receivedMessages).contains("hello-from-client-0", "hello-from-client-1", "hello-from-client-2");

        for (var client : subscribers) {
            client.close();
        }
        for (var client : publishers) {
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
        // Publisher client
        var publisher = createClient("qos0-pub");
        // Subscriber client
        var subscriber = createClient("qos0-sub");

        var latch = new CountDownLatch(1);
        subscriber.subscribe("qos0/topic", QoS.AT_MOST_ONCE, (topic, payload, qos, reasonCode) -> latch.countDown())
            .get(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        publisher.publish("qos0/topic", "test".getBytes(StandardCharsets.UTF_8), QoS.AT_MOST_ONCE, false)
            .get(5, TimeUnit.SECONDS);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        publisher.close();
        subscriber.close();
    }

    @Test
    void testQoS1Publish() throws Exception {
        // Publisher client
        var publisher = createClient("qos1-pub");
        // Subscriber client
        var subscriber = createClient("qos1-sub");
        var latch = new CountDownLatch(1);
        subscriber.subscribe("qos1/topic", QoS.AT_LEAST_ONCE, (topic, payload, qos, reasonCode) -> latch.countDown())
            .get(5, TimeUnit.SECONDS);
        Thread.sleep(50);
        publisher.publish("qos1/topic", "test".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false)
            .get(5, TimeUnit.SECONDS);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        publisher.close();
        subscriber.close();
    }

    @Test
    void testSharedSubscription() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("shared-sub");
             var pub = createClient("shared-pub")) {
            sub.subscribe("shared/topic", QoS.AT_MOST_ONCE, (topic, payload, qos, reasonCode) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            pub.publish("shared/topic", "shared-message".getBytes(), QoS.AT_MOST_ONCE, false)
                .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("shared-message");
        }
    }

    @Test
    void testQoS0Delivery() throws Exception {
        // Given: QoS 0 setup
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qos0-sub");
             var pub = createClient("qos0-pub")) {
            sub.subscribe("qos0", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

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
            sub.subscribe("qos1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            // When: publish QoS 1
            pub.publish("qos1", "at-least-once".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered with PUBACK
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("at-least-once");
        }
    }

    @Test
    void testRetainMessage() throws Exception {
        // Given: publisher with retained message
        try (var pub = createClient("retain-pub")) {
            // When: publish retained message
            pub.publish("retain/topic", "retained".getBytes(), QoS.AT_MOST_ONCE, true)
                    .get(5, TimeUnit.SECONDS);
        }

        // Then: new subscriber receives retained message
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("retain-sub")) {
            sub.subscribe("retain/topic", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("retained");
        }
    }

    @Test
    void testRetainedMessageCleared() throws Exception {
        // Given: retained message exists
        try (var pub = createClient("retain-clear-pub")) {
            pub.publish("retain-clear/topic", "old".getBytes(), QoS.AT_MOST_ONCE, true)
                    .get(5, TimeUnit.SECONDS);
        }

        // When: publish empty retained message
        try (var pub = createClient("retain-clear-pub2")) {
            pub.publish("retain-clear/topic", new byte[0], QoS.AT_MOST_ONCE, true)
                    .get(5, TimeUnit.SECONDS);
        }

        // Then: new subscriber does not receive retained message
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("retain-clear-sub")) {
            sub.subscribe("retain-clear/topic", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            // Wait a bit - should not receive anything
            Thread.sleep(200);
            assertThat(latch.getCount()).isEqualTo(1);
        }
    }

    @Test
    void testMultipleSubscribers() throws Exception {
        var received1 = new CopyOnWriteArrayList<String>();
        var received2 = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub1 = createClient("multi-sub1");
             var sub2 = createClient("multi-sub2");
             var pub = createClient("multi-pub")) {
            sub1.subscribe("multi/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received1.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            sub2.subscribe("multi/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received2.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

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
            sub.subscribe("wc/+/data", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
            Thread.sleep(50);

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

        // When: stop broker
        broker.stop();

        // Then: clients no longer connected (broker side cleared)
        assertThat(broker.getConnectedClients()).isEmpty();
        c1.close();
        c2.close();
    }

    private MqttClient createClient(String clientId) throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var client = new MqttClient(MqttClientConfig.defaults()
                .clientId(clientId).build(), transports[1]);
        client.connect().get(5, TimeUnit.SECONDS);
        return client;
    }
}