package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.QoS;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimplePubSubDemo} scenarios.
 *
 * @since 1.0.0
 */
class SimplePubSubDemoTest {

    @Test
    void testSingleMessageDelivery() throws Exception {
        // Given: broker and two clients
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "simple-sub");
                 var pub = client(port, "simple-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("demo/simple", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // When: publish
                pub.publish("demo/simple", "hello-world".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                // Then: received
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactly("hello-world");
            }
        }
    }

    @Test
    void testMultipleMessages() throws Exception {
        // Given: setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            try (var sub = client(port, "multi-msg-sub");
                 var pub = client(port, "multi-msg-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("demo/multi", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // When: publish 3 messages
                for (int i = 0; i < 3; i++) {
                    pub.publish("demo/multi", ("msg-" + i).getBytes(), QoS.AT_LEAST_ONCE, false)
                            .get(5, TimeUnit.SECONDS);
                }

                // Then: all received
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(3);
            }
        }
    }

    @Test
    void testEmptyPayload() throws Exception {
        // Given: setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<byte[]>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "empty-sub");
                 var pub = client(port, "empty-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("demo/empty", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(p);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // When: publish empty
                pub.publish("demo/empty", new byte[0], QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                // Then: empty payload received
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received.get(0)).isEmpty();
            }
        }
    }

    @Test
    void testDifferentTopicsIsolated() throws Exception {
        // Given: setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();

            try (var sub = client(port, "iso-sub");
                 var pub = client(port, "iso-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("demo/topicA", QoS.AT_LEAST_ONCE, (t, p, q, r) ->
                        received.add(new String(p, StandardCharsets.UTF_8))).get(5, TimeUnit.SECONDS);

                // When: publish to different topic
                pub.publish("demo/topicB", "wrong".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
                Thread.sleep(500);

                // Then: nothing received
                assertThat(received).isEmpty();
            }
        }
    }

    private MqttClient client(int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build());
    }
}
