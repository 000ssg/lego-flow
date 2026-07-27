package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link QuicFlowControl} — window consumption, MAX_DATA updates,
 * and blocked detection.
 *
 * @since 1.0.0
 */
class QuicFlowControlTest {

    @Test
    void testInitialLimits() {
        var fc = new QuicFlowControl(1_048_576);

        assertThat(fc.connectionSendLimit()).isEqualTo(1_048_576);
        assertThat(fc.connectionReceiveLimit()).isEqualTo(1_048_576);
        assertThat(fc.connectionSendUsed()).isEqualTo(0);
        assertThat(fc.connectionReceiveUsed()).isEqualTo(0);
    }

    @Test
    void testConsumeSendWindow() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 5000, 5000);

        boolean result = fc.consumeSendWindow(0, 1000);

        assertThat(result).isTrue();
        assertThat(fc.connectionSendUsed()).isEqualTo(1000);
    }

    @Test
    void testConsumeSendWindowExceedsConnection() {
        var fc = new QuicFlowControl(500);
        fc.registerStream(0, 5000, 5000);

        boolean result = fc.consumeSendWindow(0, 1000);

        assertThat(result).isFalse();
        assertThat(fc.connectionSendUsed()).isEqualTo(0);
    }

    @Test
    void testConsumeSendWindowExceedsStream() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 500, 5000);

        boolean result = fc.consumeSendWindow(0, 1000);

        assertThat(result).isFalse();
    }

    @Test
    void testConsumeSendWindowUnregisteredStream() {
        var fc = new QuicFlowControl(10_000);

        boolean result = fc.consumeSendWindow(99, 100);

        assertThat(result).isFalse();
    }

    @Test
    void testConsumeReceiveWindow() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 5000, 5000);

        boolean result = fc.consumeReceiveWindow(0, 1000);

        assertThat(result).isTrue();
        assertThat(fc.connectionReceiveUsed()).isEqualTo(1000);
    }

    @Test
    void testConsumeReceiveWindowExceedsConnection() {
        var fc = new QuicFlowControl(500);
        fc.registerStream(0, 5000, 5000);

        boolean result = fc.consumeReceiveWindow(0, 1000);

        assertThat(result).isFalse();
    }

    @Test
    void testUpdateMaxData() {
        var fc = new QuicFlowControl(1000);

        fc.updateMaxData(2000);

        assertThat(fc.connectionSendLimit()).isEqualTo(2000);
    }

    @Test
    void testUpdateMaxDataDoesNotDecrease() {
        var fc = new QuicFlowControl(2000);

        fc.updateMaxData(1000);

        assertThat(fc.connectionSendLimit()).isEqualTo(2000);
    }

    @Test
    void testUpdateMaxStreamData() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 1000, 1000);

        fc.updateMaxStreamData(0, 5000);

        assertThat(fc.getAvailableSendWindow(0)).isEqualTo(5000);
    }

    @Test
    void testShouldSendMaxData() {
        var fc = new QuicFlowControl(1000);
        fc.registerStream(0, 1000, 1000);

        assertThat(fc.shouldSendMaxData()).isFalse();

        fc.consumeReceiveWindow(0, 600);
        assertThat(fc.shouldSendMaxData()).isTrue();
    }

    @Test
    void testShouldSendMaxStreamData() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 1000, 1000);

        assertThat(fc.shouldSendMaxStreamData(0)).isFalse();

        fc.consumeReceiveWindow(0, 600);
        assertThat(fc.shouldSendMaxStreamData(0)).isTrue();
    }

    @Test
    void testShouldSendMaxStreamDataUnregistered() {
        var fc = new QuicFlowControl(10_000);

        assertThat(fc.shouldSendMaxStreamData(99)).isFalse();
    }

    @Test
    void testGetAvailableSendWindow() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 5000, 5000);

        assertThat(fc.getAvailableSendWindow(0)).isEqualTo(5000);

        fc.consumeSendWindow(0, 2000);
        assertThat(fc.getAvailableSendWindow(0)).isEqualTo(3000);
    }

    @Test
    void testGetAvailableSendWindowConnectionLimited() {
        var fc = new QuicFlowControl(1000);
        fc.registerStream(0, 5000, 5000);

        // Connection limit (1000) < stream limit (5000)
        assertThat(fc.getAvailableSendWindow(0)).isEqualTo(1000);
    }

    @Test
    void testGetAvailableReceiveWindow() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 5000, 5000);

        assertThat(fc.getAvailableReceiveWindow(0)).isEqualTo(5000);

        fc.consumeReceiveWindow(0, 1500);
        assertThat(fc.getAvailableReceiveWindow(0)).isEqualTo(3500);
    }

    @Test
    void testGetAvailableWindowUnregistered() {
        var fc = new QuicFlowControl(10_000);

        assertThat(fc.getAvailableSendWindow(99)).isEqualTo(0);
        assertThat(fc.getAvailableReceiveWindow(99)).isEqualTo(0);
    }

    @Test
    void testMultipleStreams() {
        var fc = new QuicFlowControl(10_000);
        fc.registerStream(0, 3000, 3000);
        fc.registerStream(4, 4000, 4000);

        fc.consumeSendWindow(0, 1000);
        fc.consumeSendWindow(4, 2000);

        assertThat(fc.connectionSendUsed()).isEqualTo(3000);
        assertThat(fc.getAvailableSendWindow(0)).isEqualTo(2000);
        assertThat(fc.getAvailableSendWindow(4)).isEqualTo(2000);
    }
}
