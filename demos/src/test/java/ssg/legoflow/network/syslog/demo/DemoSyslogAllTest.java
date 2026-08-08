package ssg.legoflow.network.syslog.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive syslog demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house syslog sender and collector. To test against
 * an external rsyslog/syslog-ng, set {@code DemoSyslogAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 *
 * @since 0.1.0
 */
class DemoSyslogAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSyslogAll.runAll();

        assertThat(results.messageEncoding())
                .as("RFC 5424 encoding produces valid output with PRI, hostname, and message")
                .isTrue();

        assertThat(results.messageDecoding())
                .as("RFC 5424 decoding recovers all message fields")
                .isTrue();

        assertThat(results.structuredData())
                .as("Structured data encoding includes SD-PARAMs")
                .isGreaterThanOrEqualTo(4);

        assertThat(results.facilityCodes())
                .as("All 24 facility codes are valid")
                .isEqualTo(24);

        assertThat(results.severityLevels())
                .as("All 8 severity levels are valid")
                .isEqualTo(8);

        assertThat(results.udpTransport())
                .as("UDP transport sends and receives messages over loopback")
                .isTrue();

        assertThat(results.messageBuilder())
                .as("Message builder produces valid message with all fields")
                .isTrue();

        assertThat(results.codecRoundTrip())
                .as("Codec round-trip preserves all message fields")
                .isTrue();

        assertThat(results.highLevelSender())
                .as("High-level SyslogSender transmits messages successfully")
                .isTrue();

        assertThat(results.highLevelCollector())
                .as("High-level SyslogCollector receives messages")
                .isTrue();
    }
}
