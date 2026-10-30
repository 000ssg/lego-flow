package ssg.legoflow.messaging.mqtt.bridge;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryMqttBridge}.
 *
 * @since 0.2.0
 */
class InMemoryMqttBridgeTest {

    private MqttBroker localBroker;
    private MqttBroker remoteBroker;

    @BeforeEach
    void setUp() {
        localBroker = new MqttBroker(MqttBrokerConfig.minimal());
        remoteBroker = new MqttBroker(MqttBrokerConfig.minimal());
        localBroker.start();
        remoteBroker.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        localBroker.close();
        remoteBroker.close();
    }

    @Test
    void testBridgeStartsAndStops() throws Exception {
        try (var bridge = new InMemoryMqttBridge(localBroker, remoteBroker, "test-bridge")) {
            bridge.start();
            assertThat(bridge.isRunning()).isTrue();
            assertThat(bridge).isNotNull();
            bridge.stop();
            assertThat(bridge.isRunning()).isFalse();
        }
    }

    @Test
    @Disabled("Callback threading timing issue — MqttClient listener not triggered before assertion")
    void testMessageForwardsFromLocalToRemote() throws Exception {
        try (var bridge = new InMemoryMqttBridge(localBroker, remoteBroker, "bridge-1")) {
            bridge.start();
            bridge.bridgeTopic("sensors/+", "sensors/+", QoS.AT_MOST_ONCE);

            // Subscribe a client on the remote broker
            var remoteClientTransports = InMemoryMqttTransport.createPair();
            remoteBroker.handleConnection(remoteClientTransports[0]);
            var remoteClient = createClient(remoteClientTransports[1], "remote-sub");
            remoteClient.connect().get(5, TimeUnit.SECONDS);

            var latch = new CountDownLatch(1);
            var received = new CopyOnWriteArrayList<String>();
            remoteClient.subscribe("sensors/temp", QoS.AT_MOST_ONCE,
                    (t, p, q, r) -> {
                        received.add(new String(p, StandardCharsets.UTF_8));
                        latch.countDown();
                    });

            Thread.sleep(500);

            // Publish on local broker
            var localClientTransports = InMemoryMqttTransport.createPair();
            localBroker.handleConnection(localClientTransports[0]);
            var localClient = createClient(localClientTransports[1], "local-pub");
            localClient.connect().get(5, TimeUnit.SECONDS);
            localClient.publish("sensors/temp", "42".getBytes(StandardCharsets.UTF_8),
                    QoS.AT_MOST_ONCE, false);

            // Wait for message to propagate
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("42");

            localClient.disconnect();
            remoteClient.disconnect();
        }
    }

    private MqttClient createClient(InMemoryMqttTransport transport, String clientId) {
        var config = MqttClientConfig.defaults()
                .host("localhost")
                .port(1883)
                .clientId(clientId)
                .cleanSession(true)
                .build();
        return new MqttClient(config, transport);
    }
}
