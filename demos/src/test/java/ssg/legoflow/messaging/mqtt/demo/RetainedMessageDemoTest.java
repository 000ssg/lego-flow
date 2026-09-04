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
 * Tests for {@link RetainedMessageDemo} scenarios.
 *
 * @since 0.1.0
 */
class RetainedMessageDemoTest {

    @Test
    void testLateSubscriberReceivesRetained() throws Exception {
        // Given: retained message published before subscriber connects
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var pub = client(port, "ret-pub")) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("status/device", "online".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
                pub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // When: late subscriber
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "ret-sub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("status/device", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // Then: receives retained
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("online");
            }
        }
    }

    @Test
    void testRetainedMessageUpdate() throws Exception {
        // Given: retained message updated
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var pub = client(port, "ret-upd-pub")) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("status/x", "v1".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("status/x", "v2".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var sub = client(port, "ret-upd-sub")) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("status/x", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // Then: receives latest value
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("v2");
            }
        }
    }

    @Test
    void testClearRetainedWithEmptyPayload() throws Exception {
        // Given: retained message cleared
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var pub = client(port, "ret-clr-pub")) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("status/y", "value".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("status/y", new byte[0], QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Then: broker has no retained message
            assertThat(broker.getRetainStore().get("status/y")).isNull();
        }
    }

    @Test
    void testNoRetainedForNonRetainedPublish() throws Exception {
        // Given: non-retained publish
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var pub = client(port, "no-ret-pub")) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("normal/topic", "data".getBytes(), QoS.AT_LEAST_ONCE, false)
                        .get(5, TimeUnit.SECONDS);
            }

            // Then: no retained message stored
            assertThat(broker.getRetainStore().get("normal/topic")).isNull();
        }
    }

    private MqttClient client(int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build());
    }
}
