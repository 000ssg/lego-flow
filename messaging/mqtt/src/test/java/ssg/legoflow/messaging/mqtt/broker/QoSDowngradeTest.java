package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
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
 * Tests for MQTT QoS downgrade (Section 4.3).
 *
 * @since 0.2.0
 */
class QoSDowngradeTest {

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
    void testQoS2PublishToQoS0Subscriber() throws Exception {
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qd-sub-0");
             var pub = createClient("qd-pub-0")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/test", QoS.AT_MOST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("downgrade/test", "data".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_MOST_ONCE);
        }
    }

    @Test
    void testQoS2PublishToQoS1Subscriber() throws Exception {
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qd-sub-1");
             var pub = createClient("qd-pub-1")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/qos1", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("downgrade/qos1", "data".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_LEAST_ONCE);
        }
    }

    @Test
    void testQoS1PublishToQoS1SubscriberNoDowngrade() throws Exception {
        var receivedQos = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qd-sub-no");
             var pub = createClient("qd-pub-no")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/same", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                receivedQos.add(q);
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("downgrade/same", "data".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedQos).contains(QoS.AT_LEAST_ONCE);
        }
    }

    @Test
    void testQoS0PublishNoDowngrade() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = createClient("qd-sub-q0");
             var pub = createClient("qd-pub-q0")) {
            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            sub.subscribe("downgrade/zero", QoS.EXACTLY_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("downgrade/zero", "q0-data".getBytes(), QoS.AT_MOST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("q0-data");
        }
    }

    @Test
    void testMultipleSubscribersDifferentQoS() throws Exception {
        var qos0Received = new CopyOnWriteArrayList<QoS>();
        var qos2Received = new CopyOnWriteArrayList<QoS>();
        var latch = new CountDownLatch(2);

        try (var sub0 = createClient("qd-multi-0");
             var sub2 = createClient("qd-multi-2");
             var pub = createClient("qd-multi-pub")) {
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

            pub.publish("downgrade/multi", "multi".getBytes(), QoS.EXACTLY_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(qos0Received).contains(QoS.AT_MOST_ONCE);
            assertThat(qos2Received).contains(QoS.EXACTLY_ONCE);
        }
    }

    private MqttClient createClient(String clientId) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        return new MqttClient(MqttClientConfig.defaults()
                .clientId(clientId).build(), transports[1]);
    }
}
