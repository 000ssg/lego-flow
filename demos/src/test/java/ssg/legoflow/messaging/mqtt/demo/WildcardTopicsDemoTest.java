package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link WildcardTopicsDemo} scenarios.
 *
 * <p>All tests run against an in-house {@link MqttBroker} over in-memory transport
 * pairs — no network.</p>
 *
 * @since 0.1.0
 */
class WildcardTopicsDemoTest {

    @Test
    void testSingleLevelWildcard() throws Exception {
        // Given: subscriber to sensors/+/temp
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("wc1-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("wc1-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(3);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("wc2-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("wc2-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("wc3-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("wc3-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("wc4-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("wc4-pub").build(), pubPair[1])) {
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
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);

            try (var sub = new MqttClient(MqttClientConfig.defaults().clientId("wc5-sub").build(), subPair[1]);
                 var pub = new MqttClient(MqttClientConfig.defaults().clientId("wc5-pub").build(), pubPair[1])) {
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
}
