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
 * Tests for {@link RetainedMessageDemo} scenarios.
 *
 * <p>All tests run against an in-house {@link MqttBroker} over in-memory transport
 * pairs — no network.</p>
 *
 * @since 0.1.0
 */
class RetainedMessageDemoTest {

    @Test
    void testRetainedMessageDeliveredToLateSubscriber() throws Exception {
        // Given: publisher publishes a retained message
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();

            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-1-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("retained/status", "on".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
            }

            // When: late subscriber connects
            Thread.sleep(200);
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-1-sub").build(), subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("retained/status", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // Then: retained message delivered
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsExactly("on");
            }
        }
    }

    @Test
    void testRetainedMessageUpdatedByNewPublish() throws Exception {
        // Given: retained message "v1" exists
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();

            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-2-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("retained/update", "v1".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("retained/update", "v2".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // When: late subscriber connects, only the latest retained value is delivered
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-2-sub").build(), subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("retained/update", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                // Then: only the latest retained value
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(300);
                assertThat(received).containsExactly("v2");
            }
        }
    }

    @Test
    void testRetainedClearWithEmptyPayload() throws Exception {
        // Given: retained message exists, then cleared with empty payload
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();

            var pubPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(pubPair[0]);
            try (var pub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-3-pub").build(), pubPair[1])) {
                pub.connect().get(5, TimeUnit.SECONDS);
                pub.publish("retained/clear", "value".getBytes(), QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
                pub.publish("retained/clear", new byte[0], QoS.AT_LEAST_ONCE, true)
                        .get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // When: late subscriber connects
            var received = new CopyOnWriteArrayList<byte[]>();
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("ret-3-sub").build(), subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("retained/clear", QoS.AT_LEAST_ONCE, (t, p, q, r) ->
                        received.add(p)).get(5, TimeUnit.SECONDS);

                Thread.sleep(500);
                // Then: no retained message delivered (cleared)
                assertThat(received).isEmpty();
            }
        }
    }
}
