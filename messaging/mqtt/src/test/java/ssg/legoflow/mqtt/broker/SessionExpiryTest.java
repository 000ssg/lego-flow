package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MQTT v5.0 Session Expiry Interval (Section 3.1.2.11).
 *
 * @since 1.0.0
 */
class SessionExpiryTest {

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
    void testSessionCreatedAtIsSet() {
        // Given: a new session
        var session = new MqttSession("test", false, 100, 60);

        // Then: createdAt is set to approximately now
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.createdAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void testSessionExpiryIntervalProperty() {
        // Given: session with expiry interval
        var session = new MqttSession("test", false, 100, 300);

        // Then: expiry interval is set
        assertThat(session.sessionExpiryInterval()).isEqualTo(300);

        // When: update expiry
        session.setSessionExpiryInterval(600);

        // Then: updated
        assertThat(session.sessionExpiryInterval()).isEqualTo(600);
    }

    @Test
    void testSessionNotExpiredWhileConnected() {
        // Given: connected session with short expiry
        var session = new MqttSession("test", false, 100, 1);
        session.setConnected(true);

        // Then: not expired while connected
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void testSessionNotExpiredWhenExpiryIntervalIsZero() {
        // Given: disconnected session with no expiry
        var session = new MqttSession("test", false, 100, 0);
        session.setConnected(true);
        session.setConnected(false);

        // Then: not expired (0 = no expiry)
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void testSessionExpiresAfterInterval() throws Exception {
        // Given: session with 1-second expiry
        var session = new MqttSession("test", false, 100, 1);
        session.setConnected(true);
        session.setConnected(false); // sets disconnectedAt

        // When: wait for expiry
        Thread.sleep(1200);

        // Then: expired
        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void testSessionNotExpiredBeforeInterval() {
        // Given: session with long expiry
        var session = new MqttSession("test", false, 100, 3600);
        session.setConnected(true);
        session.setConnected(false);

        // Then: not yet expired
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void testSweepExpiredSessionsRemovesExpired() throws Exception {
        // Given: a client connects with short session expiry
        var clientConfig = MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("expire-test")
                .cleanSession(false).build();

        try (var client = new MqttClient(clientConfig)) {
            client.connect().get(5, TimeUnit.SECONDS);

            // Subscribe to persist session
            client.subscribe("expire/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);

            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        Thread.sleep(200);

        // Then: session exists after disconnect
        assertThat(broker.getSessions()).containsKey("expire-test");

        // When: set expiry to 1 second and wait
        MqttSession session = broker.getSessions().get("expire-test");
        session.setSessionExpiryInterval(1);
        Thread.sleep(1200);

        // When: sweep
        broker.sweepExpiredSessions();

        // Then: session removed
        assertThat(broker.getSessions()).doesNotContainKey("expire-test");
    }

    @Test
    void testSweepKeepsNonExpiredSessions() throws Exception {
        // Given: persistent session
        var clientConfig = MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("keep-test")
                .cleanSession(false).build();

        try (var client = new MqttClient(clientConfig)) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("keep/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        Thread.sleep(200);

        // When: sweep (session has no expiry interval)
        broker.sweepExpiredSessions();

        // Then: session still exists
        assertThat(broker.getSessions()).containsKey("keep-test");
    }

    @Test
    void testDisconnectedAtIsTracked() {
        // Given: session
        var session = new MqttSession("test", false, 100);
        session.setConnected(true);

        // Then: no disconnectedAt while connected
        assertThat(session.disconnectedAt()).isNull();

        // When: disconnect
        session.setConnected(false);

        // Then: disconnectedAt is set
        assertThat(session.disconnectedAt()).isNotNull();
        assertThat(session.disconnectedAt()).isBefore(Instant.now().plusSeconds(1));
    }
}
