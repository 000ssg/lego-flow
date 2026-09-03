package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link SessionPersistenceDemo} scenarios.
 *
 * @since 0.1.0
 */
class SessionPersistenceDemoTest {

    @Test
    void testCleanSessionDoesNotPersist() throws Exception {
        // Given: clean session subscriber disconnects
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("clean-client")
                    .cleanSession(true).build())) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("clean/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            // Then: session removed after clean disconnect
            Thread.sleep(200);
            // Broker should have cleaned up the session
            assertThat(broker.getConnectedClients()).doesNotContain("clean-client");
        }
    }

    @Test
    void testPersistentSessionSurvivesDisconnect() throws Exception {
        // Given: persistent session subscriber subscribes and disconnects
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            var config = MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("persistent-client")
                    .cleanSession(false).build();

            // Subscribe with persistent session and disconnect
            try (var sub = new MqttClient(config)) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("persist/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // When: reconnect with same persistent session
            try (var sub2 = new MqttClient(config)) {
                var ack = sub2.connect().get(5, TimeUnit.SECONDS);

                // Then: session present flag indicates persistent session survived
                assertThat(ack.sessionPresent()).isTrue();
                assertThat(ack.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
            }
        }
    }

    @Test
    void testSessionPresentFlagOnReconnect() throws Exception {
        // Given: persistent session
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            var config = MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("sp-client")
                    .cleanSession(false).build();

            // First connection
            try (var sub = new MqttClient(config)) {
                var ack = sub.connect().get(5, TimeUnit.SECONDS);
                assertThat(ack.sessionPresent()).isFalse(); // First time
                sub.subscribe("sp/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Reconnect
            try (var sub2 = new MqttClient(config)) {
                var ack2 = sub2.connect().get(5, TimeUnit.SECONDS);
                // Then: session present flag is true
                assertThat(ack2.sessionPresent()).isTrue();
            }
        }
    }

    @Test
    void testCleanSessionClearsPrevious() throws Exception {
        // Given: persistent session with subscriptions
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();

            // Create persistent session
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("clear-prev")
                    .cleanSession(false).build())) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("clear/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Reconnect with clean session
            try (var sub2 = new MqttClient(MqttClientConfig.defaults()
                    .host("localhost").port(port).clientId("clear-prev")
                    .cleanSession(true).build())) {
                var ack = sub2.connect().get(5, TimeUnit.SECONDS);

                // Then: session not present (cleaned)
                assertThat(ack.sessionPresent()).isFalse();
            }
        }
    }
}
