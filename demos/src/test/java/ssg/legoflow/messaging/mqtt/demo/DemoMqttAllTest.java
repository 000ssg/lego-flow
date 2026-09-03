package ssg.legoflow.messaging.mqtt.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive MQTT demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code MqttBroker}. To test against
 * an external Mosquitto/HiveMQ/EMQX, set {@code DemoMqttAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoMqttAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoMqttAll.runAll();

        assertThat(results.pubSubQoS0())
                .as("QoS 0 fire-and-forget delivery")
                .isTrue();

        assertThat(results.pubSubQoS1())
                .as("QoS 1 at-least-once delivery")
                .isTrue();

        assertThat(results.pubSubQoS2())
                .as("QoS 2 exactly-once delivery")
                .isTrue();

        assertThat(results.wildcardSingle())
                .as("Single-level wildcard (+) matches exactly 2 topics")
                .isEqualTo(2);

        assertThat(results.wildcardMulti())
                .as("Multi-level wildcard (#) matches all 3 topics")
                .isEqualTo(3);

        assertThat(results.retainedReceived())
                .as("Late subscriber received retained message")
                .isTrue();

        assertThat(results.sessionPersist())
                .as("Persistent session restores subscription and delivers messages after reconnect")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.willDelivered())
                .as("Last will and testament delivered on ungraceful disconnect")
                .isTrue();

        assertThat(results.keepAliveOk())
                .as("Keep-alive configuration accepted")
                .isTrue();

        assertThat(results.topicTreeMatches())
                .as("Topic tree routes messages to matching subscriptions")
                .isGreaterThanOrEqualTo(5);
    }
}
