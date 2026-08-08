package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link HeartbeatMonitor}.
 *
 * @since 0.1.0
 */
class HeartbeatMonitorTest {

    @Test
    void testParseHeartbeat() {
        int[] hb = HeartbeatMonitor.parseHeartbeat("10000,20000");
        assertThat(hb[0]).isEqualTo(10000);
        assertThat(hb[1]).isEqualTo(20000);
    }

    @Test
    void testParseHeartbeatZeros() {
        int[] hb = HeartbeatMonitor.parseHeartbeat("0,0");
        assertThat(hb[0]).isZero();
        assertThat(hb[1]).isZero();
    }

    @Test
    void testParseHeartbeatNull() {
        int[] hb = HeartbeatMonitor.parseHeartbeat(null);
        assertThat(hb[0]).isZero();
        assertThat(hb[1]).isZero();
    }

    @Test
    void testParseHeartbeatBlank() {
        int[] hb = HeartbeatMonitor.parseHeartbeat("  ");
        assertThat(hb[0]).isZero();
        assertThat(hb[1]).isZero();
    }

    @Test
    void testParseHeartbeatInvalid() {
        assertThatThrownBy(() -> HeartbeatMonitor.parseHeartbeat("invalid"))
                .isInstanceOf(StompProtocolException.class);
    }

    @Test
    void testParseHeartbeatNegative() {
        assertThatThrownBy(() -> HeartbeatMonitor.parseHeartbeat("-1,0"))
                .isInstanceOf(StompProtocolException.class);
    }

    @Test
    void testFormatHeartbeat() {
        assertThat(HeartbeatMonitor.formatHeartbeat(10000, 20000)).isEqualTo("10000,20000");
    }

    @Test
    void testNegotiateBothEnabled() {
        // Client can send at 5000ms, wants receive at 10000ms
        // Server can send at 8000ms, wants receive at 3000ms
        int[] result = HeartbeatMonitor.negotiate(5000, 10000, 8000, 3000);
        // Client sends at MAX(5000, 3000) = 5000
        assertThat(result[0]).isEqualTo(5000);
        // Client receives at MAX(8000, 10000) = 10000
        assertThat(result[1]).isEqualTo(10000);
    }

    @Test
    void testNegotiateClientCannotSend() {
        int[] result = HeartbeatMonitor.negotiate(0, 10000, 5000, 5000);
        assertThat(result[0]).isZero(); // Cannot send
        assertThat(result[1]).isEqualTo(10000); // MAX(5000, 10000)
    }

    @Test
    void testNegotiateServerDoesNotWantReceive() {
        int[] result = HeartbeatMonitor.negotiate(5000, 10000, 5000, 0);
        assertThat(result[0]).isZero(); // Server doesn't want
        assertThat(result[1]).isEqualTo(10000); // MAX(5000, 10000)
    }

    @Test
    void testNegotiateAllDisabled() {
        int[] result = HeartbeatMonitor.negotiate(0, 0, 0, 0);
        assertThat(result[0]).isZero();
        assertThat(result[1]).isZero();
    }

    @Test
    void testMonitorInitiallyInactive() {
        var monitor = new HeartbeatMonitor();
        assertThat(monitor.isActive()).isFalse();
        assertThat(monitor.shouldSendHeartbeat()).isFalse();
        assertThat(monitor.isReceiveTimedOut()).isFalse();
    }

    @Test
    void testMonitorStartStop() {
        var monitor = new HeartbeatMonitor();
        monitor.start(1000, 2000);
        assertThat(monitor.isActive()).isTrue();
        assertThat(monitor.getSendInterval()).isEqualTo(1000);
        assertThat(monitor.getReceiveInterval()).isEqualTo(2000);

        monitor.stop();
        assertThat(monitor.isActive()).isFalse();
    }

    @Test
    void testMonitorDisabledSend() {
        var monitor = new HeartbeatMonitor();
        monitor.start(0, 1000);
        assertThat(monitor.shouldSendHeartbeat()).isFalse();
    }

    @Test
    void testMonitorDisabledReceive() {
        var monitor = new HeartbeatMonitor();
        monitor.start(1000, 0);
        assertThat(monitor.isReceiveTimedOut()).isFalse();
    }

    @Test
    void testMarkSentResetsTimer() {
        var monitor = new HeartbeatMonitor();
        monitor.start(100, 0);
        monitor.markSent();
        assertThat(monitor.timeSinceLastSend()).isLessThan(100);
    }

    @Test
    void testMarkReceivedResetsTimer() {
        var monitor = new HeartbeatMonitor();
        monitor.start(0, 100);
        monitor.markReceived();
        assertThat(monitor.timeSinceLastReceive()).isLessThan(100);
    }
}
