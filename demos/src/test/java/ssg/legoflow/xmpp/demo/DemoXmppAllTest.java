package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive XMPP demo and verifies all feature sections.
 *
 * <p>By default, uses in-memory transport. To test against an external
 * XMPP server (Prosody), set {@code DemoXmppAll.USE_EXTERNAL = true}
 * and configure the domain before running.</p>
 *
 * @since 0.1.0
 */
class DemoXmppAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoXmppAll.runAll();

        assertThat(results.messagesDelivered())
                .as("Chat messages delivered between Alice and Bob")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.presenceStatesSet())
                .as("Presence states demonstrated (available, away, dnd, chat, unavailable)")
                .isEqualTo(5);

        assertThat(results.rosterSize())
                .as("Roster has 2 contacts after add 3, remove 1")
                .isEqualTo(2);

        assertThat(results.sensorFieldCount())
                .as("Sensor fields read from temperature and humidity sensors")
                .isGreaterThanOrEqualTo(4);

        assertThat(results.controlSuccess())
                .as("IoT control commands (temperature, mode, enable) all succeeded")
                .isTrue();

        assertThat(results.discoveryHits())
                .as("IoT discovery finds sensors and actuators")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.automationTriggered())
                .as("Smart home automation triggered at least 2 notifications")
                .isTrue();

        assertThat(results.pubsubItemCount())
                .as("PubSub published 3 items")
                .isEqualTo(3);
    }
}
