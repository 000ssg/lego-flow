package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link QoSLevelsDemo} scenarios.
 *
 * @since 0.1.0
 */
class QoSLevelsDemoTest {

    @Test
    void testQoS0FireAndForget() throws Exception {
        // Given: setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "q0-sub");
                 var pub = client(port, "q0-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("qos/0", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("qos/0", "qos0-msg".getBytes(), QoS.AT_MOST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                // Then: delivered (best effort)
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("qos0-msg");
            }
        }
    }

    @Test
    void testQoS1Acknowledged() throws Exception {
        // Given: QoS 1 setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "q1-sub");
                 var pub = client(port, "q1-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("qos/1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("qos/1", "qos1-msg".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("qos1-msg");
            }
        }
    }

    @Test
    void testQoS2ExactlyOnce() throws Exception {
        // Given: QoS 2 setup
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "q2-sub");
                 var pub = client(port, "q2-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("qos/2", QoS.EXACTLY_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("qos/2", "qos2-msg".getBytes(), QoS.EXACTLY_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("qos2-msg");
            }
        }
    }

    @Test
    void testAllQoSLevelsDeliver() throws Exception {
        // Given: setup with all 3 QoS levels
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            try (var sub = client(port, "all-q-sub");
                 var pub = client(port, "all-q-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                for (QoS qos : QoS.values()) {
                    sub.subscribe("qos/all/" + qos.value(), qos, (t, p, q, r) -> {
                        received.add(qos.name());
                        latch.countDown();
                    }).get(5, TimeUnit.SECONDS);
                }

                for (QoS qos : QoS.values()) {
                    pub.publish("qos/all/" + qos.value(), "test".getBytes(), qos, false)
                            .get(5, TimeUnit.SECONDS);
                }

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(3);
            }
        }
    }

    private MqttClient client(int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build());
    }
}
