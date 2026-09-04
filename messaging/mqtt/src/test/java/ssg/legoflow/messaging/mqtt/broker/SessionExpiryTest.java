package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for MQTT session expiry and cleanup.
 *
 * @since 0.2.0
 */
class SessionExpiryTest {

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
    void testSessionCreatedOnConnect() throws Exception {
        try (var client = createClient("session-1")) {
            client.connect().get(5, TimeUnit.SECONDS);
            assertThat(broker.getSessions()).containsKey("session-1");
        }
    }

    @Test
    void testPersistentSessionSurvivesDisconnect() throws Exception {
        try (var client = createClient("persist-1", false)) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("persist-1"),
                Duration.ofSeconds(3), 50);

        TestAssertions.assertThatCondition(
                "persist-1 session exists after disconnect",
                () -> broker.getSessions().containsKey("persist-1"),
                Duration.ofSeconds(3));
    }

    @Test
    void testCleanSessionRemovedOnDisconnect() throws Exception {
        try (var client = createClient("clean-1", true)) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("clean-1"),
                Duration.ofSeconds(3), 50);

        assertThat(broker.getSessions()).doesNotContainKey("clean-1");
    }

    @Test
    void testSessionExpiryInterval() throws Exception {
        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                true, false, 1, 100, null, null, null);
        broker = new MqttBroker(config);
        broker.start();

        try (var client = createClient("expiry-1", false)) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("expiry-1"),
                Duration.ofSeconds(3), 50);

        // Session should be swept after expiry interval
        Thread.sleep(2000);
        broker.sweepExpiredSessions();
        assertThat(broker.getSessions()).doesNotContainKey("expiry-1");
    }

    @Test
    void testNoExpirySessionNotSwept() throws Exception {
        try (var client = createClient("no-expiry-1", false)) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("no-expiry-1"),
                Duration.ofSeconds(3), 50);

        // Session should survive sweep (no expiry)
        broker.sweepExpiredSessions();
        assertThat(broker.getSessions()).containsKey("no-expiry-1");
    }

    @Test
    void testSessionDisconnectedAtTracked() throws Exception {
        try (var client = createClient("tracked-1", false)) {
            client.connect().get(5, TimeUnit.SECONDS);
            var session = broker.getSessions().get("tracked-1");
            assertThat(session.isConnected()).isTrue();
            assertThat(session.disconnectedAt()).isNull();

            client.disconnect().get(5, TimeUnit.SECONDS);

            TestAssertions.waitForCondition(
                    () -> !broker.getConnectedClients().contains("tracked-1"),
                    Duration.ofSeconds(3), 50);

            session = broker.getSessions().get("tracked-1");
            assertThat(session.isConnected()).isFalse();
            assertThat(session.disconnectedAt()).isNotNull();
        }
    }

    @Test
    void testSessionExpirySweepsMultipleSessions() throws Exception {
        var config = new MqttBrokerConfig("localhost", 0, 10, 65536, 32,
                true, false, 1, 100, null, null, null);
        broker = new MqttBroker(config);
        broker.start();

        try (var c1 = createClient("sweep-1", false);
             var c2 = createClient("sweep-2", false);
             var c3 = createClient("sweep-3", true)) {
            c1.connect().get(5, TimeUnit.SECONDS);
            c2.connect().get(5, TimeUnit.SECONDS);
            c3.connect().get(5, TimeUnit.SECONDS);
            c1.disconnect().get(5, TimeUnit.SECONDS);
            c2.disconnect().get(5, TimeUnit.SECONDS);
            c3.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Wait for cleanup
        Thread.sleep(2500);
        broker.sweepExpiredSessions();

        // sweep-1 and sweep-2 had expiry=1s, should be swept
        assertThat(broker.getSessions()).doesNotContainKeys("sweep-1", "sweep-2");
        // sweep-3 had cleanSession=true, so no session at all
    }

    private MqttClient createClient(String clientId) {
        return createClient(clientId, true);
    }

    private MqttClient createClient(String clientId, boolean cleanSession) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var config = MqttClientConfig.defaults()
                .clientId(clientId)
                .cleanSession(cleanSession)
                .build();
        return new MqttClient(config, transports[1]);
    }
}
