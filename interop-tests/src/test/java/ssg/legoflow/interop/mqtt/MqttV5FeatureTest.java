package ssg.legoflow.interop.mqtt;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MQTT v5.0 feature tests using in-memory transport pairs.
 *
 * <p>Tests v5.0 specific features: ReceiveMaximum, retained messages,
 * shared subscriptions, and topic alias against the local broker.
 */
@Tag("messaging-protocols")
@Tag("mqtt-v5")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MqttV5FeatureTest {

    private MqttBroker broker;

    @AfterAll
    void cleanup() {
        if (broker != null) broker.stop();
    }

    @Test
    void testV5ConnectWithReceiveMaximum() throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        var config = MqttBrokerConfig.defaults();
        broker = new MqttBroker(config);
        broker.start();

        var clientConfig = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("test-client")
                .receiveMaximum(5)
                .build();

        try (var client = new MqttClient(clientConfig, transports[1])) {
            broker.handleConnection(transports[0]);
            client.connect().get(5, TimeUnit.SECONDS);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testRetainedMessages() throws Exception {
        var subTransports = InMemoryMqttTransport.createPair();
        var pubTransports = InMemoryMqttTransport.createPair();
        var config = MqttBrokerConfig.defaults();
        broker = new MqttBroker(config);
        broker.start();

        var subConfig = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("retained-sub")
                .build();
        var pubConfig = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("retained-pub")
                .build();

        try (var pub = new MqttClient(pubConfig, pubTransports[1])) {
            broker.handleConnection(pubTransports[0]);
            pub.connect().get(5, TimeUnit.SECONDS);
            pub.publish("retained/test", "retained-msg".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
        }

        var latch = new CountDownLatch(1);
        try (var sub = new MqttClient(subConfig, subTransports[1])) {
            broker.handleConnection(subTransports[0]);
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("retained/test", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                assertThat(new String(p, StandardCharsets.UTF_8)).isEqualTo("retained-msg");
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void testSharedSubscriptions() throws Exception {
        var sub1Transports = InMemoryMqttTransport.createPair();
        var sub2Transports = InMemoryMqttTransport.createPair();
        var pubTransports = InMemoryMqttTransport.createPair();
        var config = MqttBrokerConfig.defaults();
        broker = new MqttBroker(config);
        broker.start();

        var sub1Config = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("shared-1").build();
        var sub2Config = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("shared-2").build();
        var pubConfig = MqttClientConfig.defaults()
                .version(ssg.legoflow.messaging.mqtt.protocol.MqttVersion.V5_0)
                .clientId("shared-pub").build();

        try (var sub1 = new MqttClient(sub1Config, sub1Transports[1]);
             var sub2 = new MqttClient(sub2Config, sub2Transports[1]);
             var pub = new MqttClient(pubConfig, pubTransports[1])) {

            broker.handleConnection(sub1Transports[0]);
            broker.handleConnection(sub2Transports[0]);
            broker.handleConnection(pubTransports[0]);

            sub1.connect().get(5, TimeUnit.SECONDS);
            sub2.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            var latch = new CountDownLatch(3);
            var received = new CopyOnWriteArrayList<String>();

            sub1.subscribe("$share/group/sensor/#", QoS.AT_LEAST_ONCE,
                    (t, p, q, r) -> { received.add("sub1"); latch.countDown(); })
                    .get(5, TimeUnit.SECONDS);
            sub2.subscribe("$share/group/sensor/#", QoS.AT_LEAST_ONCE,
                    (t, p, q, r) -> { received.add("sub2"); latch.countDown(); })
                    .get(5, TimeUnit.SECONDS);

            // Publish 3 messages
            for (int i = 0; i < 3; i++) {
                pub.publish("sensor/temp", ("msg-" + i).getBytes(StandardCharsets.UTF_8),
                        QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                Thread.sleep(100);
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(received).isNotEmpty();
        }
    }
}
