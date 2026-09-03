package ssg.legoflow.interop.mqtt;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability test: Lego Flow MQTT client ↔ real Mosquitto broker.
 *
 * <p>Uses the {@link MqttClient} to connect, publish, subscribe, and unsubscribe
 * on a Mosquitto instance running via Docker Compose.
 *
 * <h3>Prerequisites</h3>
 * <pre>{@code
 * docker compose -f interop-tests/docker-compose.yml up -d mosquitto
 * }</pre>
 */
    @Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MqttMosquittoInteropTest {

    private final String host = System.getProperty("interop.mosquitto.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.mosquitto.port", "1883"));

    @Test
    void testConnectAndDisconnect() throws Exception {
        var config = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-interop-connect")
                .build();

        try (var client = new MqttClient(config)) {
            // When: connect to Mosquitto
            client.connect().get(10, TimeUnit.SECONDS);

            // Then: connection established
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testPublishAndSubscribe() throws Exception {
        String topic = "test/interop/publish";
        String payload = "Hello from Lego Flow!";
        var latch = new CountDownLatch(1);
        var received = new CopyOnWriteArrayList<String>();

        // Subscriber
        var subConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-sub-interop")
                .build();

        // Publisher
        var pubConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-pub-interop")
                .build();

        try (var sub = new MqttClient(subConfig);
             var pub = new MqttClient(pubConfig)) {

            sub.connect().get(10, TimeUnit.SECONDS);
            pub.connect().get(10, TimeUnit.SECONDS);

            // Subscribe with callback
            Consumer<String> onMessage = message -> {
                received.add(message);
                latch.countDown();
            };

            sub.subscribe(topic, QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                onMessage.accept(new String(p, StandardCharsets.UTF_8));
            }).get(10, TimeUnit.SECONDS);

            // Publish a message
            pub.publish(topic, payload.getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false)
                    .get(10, TimeUnit.SECONDS);

            // Wait for delivery
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

            // Verify received message
            assertThat(received).contains(payload);
        }
    }

    @Test
    void testWildcardSubscription() throws Exception {
        String topic = "sensor/temperature/kitchen";
        String wildcardFilter = "sensor/+/kitchen";
        var latch = new CountDownLatch(1);

        var subConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-sub-wildcard")
                .build();

        var pubConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-pub-wildcard")
                .build();

        try (var sub = new MqttClient(subConfig);
             var pub = new MqttClient(pubConfig)) {

            sub.connect().get(10, TimeUnit.SECONDS);
            pub.connect().get(10, TimeUnit.SECONDS);

            sub.subscribe(wildcardFilter, QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                latch.countDown();
            }).get(10, TimeUnit.SECONDS);

            // Publish to matching topic
            pub.publish(topic, "23.5".getBytes(StandardCharsets.UTF_8), QoS.AT_LEAST_ONCE, false)
                    .get(10, TimeUnit.SECONDS);

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void testMultipleTopics() throws Exception {
        var latch = new CountDownLatch(2);

        var subConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-sub-multi")
                .build();

        var pubConfig = MqttClientConfig.defaults()
                .host(host).port(port)
                .clientId("lego-flow-pub-multi")
                .build();

        try (var sub = new MqttClient(subConfig);
             var pub = new MqttClient(pubConfig)) {

            sub.connect().get(10, TimeUnit.SECONDS);
            pub.connect().get(10, TimeUnit.SECONDS);

            // Subscribe to two topics
            sub.subscribe("multi/topic/A", QoS.AT_LEAST_ONCE, (t, p, q, r) -> latch.countDown())
                    .get(10, TimeUnit.SECONDS);
            sub.subscribe("multi/topic/B", QoS.AT_LEAST_ONCE, (t, p, q, r) -> latch.countDown())
                    .get(10, TimeUnit.SECONDS);

            // Publish to both topics
            pub.publish("multi/topic/A", "msgA".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(10, TimeUnit.SECONDS);
            pub.publish("multi/topic/B", "msgB".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(10, TimeUnit.SECONDS);

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
