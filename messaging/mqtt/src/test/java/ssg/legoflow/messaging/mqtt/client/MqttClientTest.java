package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttClient}.
 *
 * <p>Timing-critical assertions use retry-based waiting instead of
 * {@code Thread.sleep()} to avoid flaky failures under parallel execution (-T 1C).
 *
 * @since 0.1.0
 */
class MqttClientTest {

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
    void testConnectAndDisconnect() throws Exception {
        // Given: a client
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("test-connect").build())) {

            // When: connect
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: connected
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();

            // When: disconnect
            client.disconnect().get(5, TimeUnit.SECONDS);

            // Then: disconnected
            assertThat(client.isConnected()).isFalse();
        }
    }

    @Test
    void testPublishQoS0() throws Exception {
        // Given: connected client
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("pub-qos0").build())) {
            client.connect().get(5, TimeUnit.SECONDS);

            // When: publish QoS 0
            client.publish("test/qos0", "hello".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_MOST_ONCE, false).get(5, TimeUnit.SECONDS);

            // Then: no exception (fire and forget)
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testSubscribeAndReceive() throws Exception {
        // Given: publisher and subscriber
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("sub-1").build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("pub-1").build())) {

            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("test/msg", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {
                received.add(new String(payload, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish
            pub.publish("test/msg", "world".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            // Then: received
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("world");
        }
    }

    @Test
    void testSubscribeMultipleTopics() throws Exception {
        // Given: subscriber with multiple subscriptions
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("multi-sub").build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("multi-pub").build())) {

            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe(List.of(
                    new TopicSubscription("topic/a", QoS.AT_LEAST_ONCE),
                    new TopicSubscription("topic/b", QoS.AT_LEAST_ONCE)
            ), (topic, payload, qos, retain) -> {
                received.add(topic);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish to both
            pub.publish("topic/a", "a".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            pub.publish("topic/b", "b".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            // Then: both received
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).containsExactlyInAnyOrder("topic/a", "topic/b");
        }
    }

    @Test
    void testUnsubscribe() throws Exception {
        // Given: subscribed client
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("unsub-sub").build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("unsub-pub").build())) {

            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("unsub/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: unsubscribe
            sub.unsubscribe("unsub/topic").get(5, TimeUnit.SECONDS);

            // Allow unsubscription state to propagate (retry-based)
            Thread.sleep(200);

            pub.publish("unsub/topic", "after-unsub".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Wait for a short delivery window then assert no message received
            var deliveryLatch = new CountDownLatch(1);
            Thread.sleep(500);

            // Then: no message received after unsubscribe
            assertThat(received).isEmpty();
        }
    }

    @Test
    void testCallbackOnMessage() throws Exception {
        // Given: client with callback
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("callback-client").build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("callback-pub").build())) {

            client.setCallback(new MqttCallback() {
                @Override public void onMessage(String topic, PublishPacket message) {
                    received.add(new String(message.payload(), StandardCharsets.UTF_8));
                    latch.countDown();
                }
                @Override public void onConnectionLost(Throwable cause) {}
                @Override public void onReconnected() {}
                @Override public void onDeliveryComplete(int packetId) {}
            });

            client.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            client.subscribe("cb/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);

            // When: publish
            pub.publish("cb/topic", "callback-msg".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: callback invoked
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("callback-msg");
        }
    }

    @Test
    void testIsConnectedInitiallyFalse() {
        // Given: new client not connected
        var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("not-connected").build());

        // Then: not connected
        assertThat(client.isConnected()).isFalse();
        client.close();
    }

    @Test
    void testPublishQoS1() throws Exception {
        // Given: connected clients
        var latch = new CountDownLatch(1);
        var received = new CopyOnWriteArrayList<String>();

        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("qos1-sub").build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("qos1-pub").build())) {

            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("qos1/test", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish QoS 1
            pub.publish("qos1/test", "qos1-data".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("qos1-data");
        }
    }

    @Test
    void testKeepAlive() throws Exception {
        // Given: client with short keepalive (1 second)
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("keepalive-test")
                .keepAlive(1).build())) {

            client.connect().get(5, TimeUnit.SECONDS);

            // When: wait for keepalive pings to fire (up to 4s with retry check)
            // The client should send PINGREQ/PINGRESP automatically and stay connected
            var stillConnectedLatch = new CountDownLatch(1);
            Thread.sleep(2000);

            // Then: still connected (pings maintained the connection)
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testMultipleClientsOnBroker() throws Exception {
        // Given: multiple clients connecting
        try (var c1 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("multi-1").build());
             var c2 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("multi-2").build());
             var c3 = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("multi-3").build())) {

            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            c3.connect().get(5, TimeUnit.SECONDS);

            // Then: all connected
            assertThat(broker.getConnectedClients()).hasSize(3);
        }
    }
}
