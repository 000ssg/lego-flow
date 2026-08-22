package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.QoS;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link WildcardTopicsDemo} scenarios.
 *
 * @since 0.1.0
 */
class WildcardTopicsDemoTest {

    @Test
    void testSingleLevelWildcard() throws Exception {
        // Given: subscriber to sensors/+/temp
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            try (var sub = client(port, "wc1-sub");
                 var pub = client(port, "wc1-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("sensors/+/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("sensors/r1/temp", "22".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("sensors/r2/temp", "23".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactlyInAnyOrder("sensors/r1/temp", "sensors/r2/temp");
            }
        }
    }

    @Test
    void testMultiLevelWildcard() throws Exception {
        // Given: subscriber to sensors/#
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            try (var sub = client(port, "wc2-sub");
                 var pub = client(port, "wc2-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("sensors/a", "1".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("sensors/b/c", "2".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                pub.publish("sensors/d/e/f", "3".getBytes(), QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(3);
            }
        }
    }

    @Test
    void testWildcardDoesNotMatchDifferentPrefix() throws Exception {
        // Given: subscriber to sensors/+/temp
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();

            try (var sub = client(port, "wc3-sub");
                 var pub = client(port, "wc3-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("sensors/+/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) ->
                        received.add(t)).get(5, TimeUnit.SECONDS);

                pub.publish("actuators/motor/temp", "30".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
                Thread.sleep(500);

                assertThat(received).isEmpty();
            }
        }
    }

    @Test
    void testPlusDoesNotMatchMultipleLevels() throws Exception {
        // Given: + only matches single level
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();

            try (var sub = client(port, "wc4-sub");
                 var pub = client(port, "wc4-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("a/+/c", QoS.AT_LEAST_ONCE, (t, p, q, r) ->
                        received.add(t)).get(5, TimeUnit.SECONDS);

                pub.publish("a/b/x/c", "deep".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
                Thread.sleep(500);

                assertThat(received).isEmpty();
            }
        }
    }

    @Test
    void testHashAloneMatchesAll() throws Exception {
        // Given: subscriber to #
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            try (var sub = client(port, "wc5-sub");
                 var pub = client(port, "wc5-pub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                pub.connect().get(5, TimeUnit.SECONDS);

                sub.subscribe("#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                pub.publish("any/topic", "1".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("different", "2".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(2);
            }
        }
    }

    private MqttClient client(int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build());
    }
}
