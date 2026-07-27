package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.QoS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MQTT QoS downgrade (Section 4.3).
 *
 * <p>When a subscriber's maximum QoS is lower than the published message QoS,
 * the broker should deliver at the subscriber's max QoS.
 *
 * @since 1.0.0
 */
class QoSDowngradeTest {

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
    void testQoS2PublishToQoS0Subscriber() throws Exception {
        // Given: subscriber subscribed at QoS 0
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qd-sub-0"));
             var pub = new MqttClient(config("qd-pub-0"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/test", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish at QoS 2
            pub.publish("downgrade/test", "data".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered at QoS 0 (downgraded)
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_MOST_ONCE);
        }
    }

    @Test
    void testQoS2PublishToQoS1Subscriber() throws Exception {
        // Given: subscriber subscribed at QoS 1
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qd-sub-1"));
             var pub = new MqttClient(config("qd-pub-1"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/qos1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish at QoS 2
            pub.publish("downgrade/qos1", "data".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered at QoS 1 (downgraded from 2 to 1)
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_LEAST_ONCE);
        }
    }

    @Test
    void testQoS1PublishToQoS1SubscriberNoDowngrade() throws Exception {
        // Given: subscriber subscribed at QoS 1
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qd-sub-no"));
             var pub = new MqttClient(config("qd-pub-no"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/same", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish at QoS 1
            pub.publish("downgrade/same", "data".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered at QoS 1 (no downgrade needed)
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_LEAST_ONCE);
        }
    }

    @Test
    void testQoS0PublishNoDowngrade() throws Exception {
        // Given: subscriber at QoS 2
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(config("qd-sub-q0"));
             var pub = new MqttClient(config("qd-pub-q0"))) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/zero", QoS.EXACTLY_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish at QoS 0
            pub.publish("downgrade/zero", "q0-data".getBytes(), QoS.AT_MOST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: delivered at QoS 0 (publish QoS is already lowest)
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("q0-data");
        }
    }

    @Test
    void testMultipleSubscribersDifferentQoS() throws Exception {
        // Given: two subscribers at different QoS levels
        var qos0Received = new CopyOnWriteArrayList<QoS>();
        var qos2Received = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(2);

        try (var sub0 = new MqttClient(config("qd-multi-0"));
             var sub2 = new MqttClient(config("qd-multi-2"));
             var pub = new MqttClient(config("qd-multi-pub"))) {
            sub0.connect().get(5, TimeUnit.SECONDS);
            sub2.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub0.subscribe("downgrade/multi", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                qos0Received.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            sub2.subscribe("downgrade/multi", QoS.EXACTLY_ONCE, (t, p, q, r) -> {
                qos2Received.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            // When: publish at QoS 2
            pub.publish("downgrade/multi", "multi".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: QoS 0 subscriber gets QoS 0, QoS 2 subscriber gets QoS 2
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(qos0Received).contains(QoS.AT_MOST_ONCE);
            assertThat(qos2Received).contains(QoS.EXACTLY_ONCE);
        }
    }

    private MqttClientConfig config(String clientId) {
        return MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build();
    }
}
