package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link SessionPersistenceDemo} scenarios.
 *
 * <p>All tests run against an in-house {@link MqttBroker} over in-memory transport
 * pairs — no network.</p>
 *
 * @since 0.1.0
 */
class SessionPersistenceDemoTest {

    @Test
    void testCleanSessionDoesNotPersist() throws Exception {
        // Given: clean session subscriber disconnects
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();

            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("clean-client").cleanSession(true).build(), subPair[1])) {
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
            broker.start();

            var config = MqttClientConfig.defaults()
                    .clientId("persistent-client").cleanSession(false).build();

            // Subscribe with persistent session and disconnect
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(config, subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("persist/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // When: reconnect with same persistent session
            var sub2Pair = InMemoryMqttTransport.createPair();
            broker.handleConnection(sub2Pair[0]);
            try (var sub2 = new MqttClient(config, sub2Pair[1])) {
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
            broker.start();

            var config = MqttClientConfig.defaults()
                    .clientId("sp-client").cleanSession(false).build();

            // First connection
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(config, subPair[1])) {
                var ack = sub.connect().get(5, TimeUnit.SECONDS);
                assertThat(ack.sessionPresent()).isFalse(); // First time
                sub.subscribe("sp/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Reconnect
            var sub2Pair = InMemoryMqttTransport.createPair();
            broker.handleConnection(sub2Pair[0]);
            try (var sub2 = new MqttClient(config, sub2Pair[1])) {
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
            broker.start();

            // Create persistent session
            var subPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(subPair[0]);
            try (var sub = new MqttClient(MqttClientConfig.defaults()
                    .clientId("clear-prev").cleanSession(false).build(), subPair[1])) {
                sub.connect().get(5, TimeUnit.SECONDS);
                sub.subscribe("clear/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                        .get(5, TimeUnit.SECONDS);
                sub.disconnect().get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            // Reconnect with clean session
            var sub2Pair = InMemoryMqttTransport.createPair();
            broker.handleConnection(sub2Pair[0]);
            try (var sub2 = new MqttClient(MqttClientConfig.defaults()
                    .clientId("clear-prev").cleanSession(true).build(), sub2Pair[1])) {
                var ack = sub2.connect().get(5, TimeUnit.SECONDS);

                // Then: session not present (cleaned)
                assertThat(ack.sessionPresent()).isFalse();
            }
        }
    }
}
