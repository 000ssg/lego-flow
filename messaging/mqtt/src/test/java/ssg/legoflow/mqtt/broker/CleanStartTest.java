package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.ConnAckPacket;
import ssg.legoflow.mqtt.protocol.QoS;
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
 * Tests for MQTT v5.0 Clean Start flag (Section 3.1.2.4).
 *
 * <p>Timing-critical assertions use {@link TestAssertions} with exponential
 * backoff instead of {@code Thread.sleep()} to avoid flaky failures under parallel
 * execution (-T 1C).
 *
 * @since 0.1.0
 */
class CleanStartTest {

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
    void testCleanStartTrueDiscardsSession() throws Exception {
        // Given: persistent session with subscription
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("clean-test")
                .cleanSession(false).build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("clean/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete
        TestAssertions.assertThatCondition(
                "clean-test session exists after disconnect",
                () -> broker.getSessions().containsKey("clean-test"),
                Duration.ofSeconds(3));

        assertThat(broker.getSessions()).containsKey("clean-test");

        // When: reconnect with cleanSession=true
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("clean-test")
                .cleanSession(true).build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: session present = false (old session was discarded)
            assertThat(ack.sessionPresent()).isFalse();
        }
    }

    @Test
    void testCleanStartFalseResumesSession() throws Exception {
        // Given: persistent session with subscription
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("resume-test")
                .cleanSession(false).build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("resume/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete
        TestAssertions.assertThatCondition(
                "resume-test session exists after disconnect",
                () -> broker.getSessions().containsKey("resume-test"),
                Duration.ofSeconds(3));

        // When: reconnect with cleanSession=false
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("resume-test")
                .cleanSession(false).build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: session present = true (session was resumed)
            assertThat(ack.sessionPresent()).isTrue();
        }
    }

    @Test
    void testCleanStartTrueNoExistingSession() throws Exception {
        // Given/When: first connection with cleanSession=true
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("fresh-test")
                .cleanSession(true).build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: session present = false (no prior session)
            assertThat(ack.sessionPresent()).isFalse();
        }
    }

    @Test
    void testCleanStartFalseNoExistingSession() throws Exception {
        // Given/When: first connection with cleanSession=false
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("no-prior-test")
                .cleanSession(false).build())) {
            ConnAckPacket ack = client.connect().get(5, TimeUnit.SECONDS);

            // Then: session present = false (no prior session exists)
            assertThat(ack.sessionPresent()).isFalse();
        }
    }

    @Test
    void testResumedSessionRetainsSubscriptions() throws Exception {
        // Given: persistent session with subscription
        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("resume-subs")
                .cleanSession(false).build())) {
            sub.connect().get(5, TimeUnit.SECONDS);
            sub.subscribe("resume/sub/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            sub.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete
        TestAssertions.assertThatCondition(
                "resume-subs session exists with subscriptions",
                () -> broker.getSessions().containsKey("resume-subs")
                        && broker.getSessions().get("resume-subs").getSubscriptions()
                                .containsKey("resume/sub/topic"),
                Duration.ofSeconds(3));

        // Then: session still has subscriptions
        MqttSession session = broker.getSessions().get("resume-subs");
        assertThat(session).isNotNull();
        assertThat(session.getSubscriptions()).containsKey("resume/sub/topic");

        // When: reconnect with cleanSession=false and receive messages
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        try (var sub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("resume-subs")
                .cleanSession(false).build());
             var pub = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("resume-pub")
                .cleanSession(true).build())) {

            sub.connect().get(5, TimeUnit.SECONDS);
            pub.connect().get(5, TimeUnit.SECONDS);

            // Listener to catch messages on the restored subscription
            sub.subscribe("resume/sub/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);

            pub.publish("resume/sub/topic", "after-resume".getBytes(), QoS.AT_LEAST_ONCE, false)
                    .get(5, TimeUnit.SECONDS);

            // Then: message received (subscription was restored)
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).contains("after-resume");
        }
    }

    @Test
    void testCleanSessionRemoveOnDisconnect() throws Exception {
        // Given: connect with cleanSession=true
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("clean-remove")
                .cleanSession(true).build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete (retry-based)
        TestAssertions.assertThatCondition(
                "clean-remove session removed after disconnect",
                () -> !broker.getSessions().containsKey("clean-remove"),
                Duration.ofSeconds(3));

        // Then: session removed after clean session disconnect
        assertThat(broker.getSessions()).doesNotContainKey("clean-remove");
    }

    @Test
    void testPersistentSessionSurvivesDisconnect() throws Exception {
        // Given: connect with cleanSession=false
        try (var client = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId("persist-survive")
                .cleanSession(false).build())) {
            client.connect().get(5, TimeUnit.SECONDS);
            client.subscribe("persist/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {})
                    .get(5, TimeUnit.SECONDS);
            client.disconnect().get(5, TimeUnit.SECONDS);
        }

        // Allow async disconnect processing to complete
        TestAssertions.assertThatCondition(
                "persist-survive session exists after disconnect",
                () -> broker.getSessions().containsKey("persist-survive"),
                Duration.ofSeconds(3));

        // Then: session still exists
        assertThat(broker.getSessions()).containsKey("persist-survive");
    }
}
