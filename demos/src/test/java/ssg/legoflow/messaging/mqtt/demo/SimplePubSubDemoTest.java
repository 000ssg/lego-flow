package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link SimplePubSubDemo} scenarios.
 *
 * <p>All tests run against an in-house {@link MqttBroker} over in-memory transport
 * pairs — no network.</p>
 *
 * @since 0.1.0
 */
class SimplePubSubDemoTest {

    @Test
    void testSingleMessageDelivery() throws Exception {
        // Given: broker and two clients
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("simple-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("simple-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("multi-msg-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("multi-msg-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<byte[]>();
            var latch = new CountDownLatch(1);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("empty-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("empty-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("iso-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("iso-pub").build(), pubPair[1])) {
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
}
