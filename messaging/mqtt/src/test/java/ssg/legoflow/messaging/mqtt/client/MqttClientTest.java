package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttClient} using in-memory transports.
 *
 * @since 0.2.0
 */
class MqttClientTest {

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
    void testConnectAndDisconnect() throws Exception {
        try (var client = createClient("test-connect")) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);
            assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            assertThat(client.isConnected()).isTrue();

            client.disconnect().get(5, TimeUnit.SECONDS);
            assertThat(client.isConnected()).isFalse();
        }
    }

    @Test
    void testPublishQoS0() throws Exception {
        try (var client = createClient("pub-qos0")) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.publish("qos0/topic", "hello".getBytes(), QoS.AT_MOST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSubscribeAndReceive() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("sub-1");
             var pub = createClient("pub-1")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("test/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("test/topic", "hello".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("hello");
        }
    }

    @Test
    void testSubscribeMultipleTopics() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        try (var sub = createClient("multi-sub");
             var pub = createClient("multi-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("multi/a", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            sub.subscribe("multi/b", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(t);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("multi/a", "1".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
            pub.publish("multi/b", "2".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).containsExactlyInAnyOrder("multi/a", "multi/b");
        }
    }

    @Test
    void testUnsubscribe() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("unsub-sub");
             var pub = createClient("unsub-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("unsub/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // Unsubscribe
            sub.unsubscribe("unsub/topic").get(5, TimeUnit.SECONDS);

            // Publish — should NOT be received
            pub.publish("unsub/topic", "after-unsub".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            Thread.sleep(500);
            assertThat(received).isEmpty();
        }
    }

    @Test
    void testCallbackOnMessage() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var client = createClient("callback-client");
             var pub = createClient("callback-pub")) {

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

            pub.publish("cb/topic", "cb-msg".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("cb-msg");
        }
    }

    @Test
    void testIsConnectedInitiallyFalse() {
        var client = createClient("not-connected");
        assertThat(client.isConnected()).isFalse();
        client.close();
    }

    @Test
    void testPublishQoS1() throws Exception {
        var latch = new CountDownLatch(1);
        var received = new CopyOnWriteArrayList<String>();

        try (var sub = createClient("qos1-sub");
             var pub = createClient("qos1-pub")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("qos1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("qos1", "ack-me".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("ack-me");
        }
    }

    @Test
    void testKeepAlive() throws Exception {
        try (var client = createClient("keepalive-test")) {
            client.connect().get(5, TimeUnit.SECONDS);
            Thread.sleep(1500);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testMultipleClientsOnBroker() throws Exception {
        try (var c1 = createClient("multi-1");
             var c2 = createClient("multi-2");
             var c3 = createClient("multi-3")) {
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            c3.connect().get(5, TimeUnit.SECONDS);

            assertThat(broker.getConnectedClients()).contains("multi-1", "multi-2", "multi-3");
        }
    }

    private MqttClient createClient(String clientId) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var config = MqttClientConfig.defaults()
                .clientId(clientId).build();
        return new MqttClient(config, transports[1]);
    }
}
