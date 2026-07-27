package ssg.legoflow.media.rtp.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtcpIntervalCalculator}.
 */
class RtcpIntervalCalculatorTest {

    @Test
    void testDeterministicIntervalMinimum() {
        // Very high bandwidth -> should hit minimum interval
        var calc = new RtcpIntervalCalculator(10_000_000); // 10 Mbps
        double interval = calc.computeDeterministicInterval(2, 1, false);
        // Initial minimum is 2.5 seconds
        assertThat(interval).isGreaterThanOrEqualTo(RtcpIntervalCalculator.INITIAL_MIN_INTERVAL_SEC);
    }

    @Test
    void testDeterministicIntervalAfterInitial() {
        var calc = new RtcpIntervalCalculator(10_000_000);
        calc.markInitialSent();
        double interval = calc.computeDeterministicInterval(2, 1, false);
        // After initial, minimum is 5 seconds
        assertThat(interval).isGreaterThanOrEqualTo(RtcpIntervalCalculator.MIN_INTERVAL_SEC);
    }

    @Test
    void testDeterministicIntervalScalesWithParticipants() {
        var calc = new RtcpIntervalCalculator(64_000); // 64 kbps
        calc.markInitialSent();

        double interval2 = calc.computeDeterministicInterval(2, 1, false);
        double interval10 = calc.computeDeterministicInterval(10, 1, false);
        double interval100 = calc.computeDeterministicInterval(100, 1, false);

        // More participants -> longer intervals
        assertThat(interval10).isGreaterThanOrEqualTo(interval2);
        assertThat(interval100).isGreaterThanOrEqualTo(interval10);
    }

    @Test
    void testSenderFractionBandwidthAllocation() {
        var calc = new RtcpIntervalCalculator(64_000);
        calc.markInitialSent();

        // When senders <= 25% of members, they share 25% of RTCP bandwidth
        double senderInterval = calc.computeDeterministicInterval(10, 2, true);
        double receiverInterval = calc.computeDeterministicInterval(10, 2, false);

        // Both should be >= minimum
        assertThat(senderInterval).isGreaterThanOrEqualTo(RtcpIntervalCalculator.MIN_INTERVAL_SEC);
        assertThat(receiverInterval).isGreaterThanOrEqualTo(RtcpIntervalCalculator.MIN_INTERVAL_SEC);
    }

    @Test
    void testRandomizedIntervalBounds() {
        var calc = new RtcpIntervalCalculator(64_000);
        double deterministic = calc.computeDeterministicInterval(5, 1, false);

        // Run multiple times and check bounds
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int i = 0; i < 1000; i++) {
            double randomized = calc.computeRandomizedInterval(5, 1, false);
            min = Math.min(min, randomized);
            max = Math.max(max, randomized);
        }

        // Randomized should be within [Td*0.5/comp, Td*1.5/comp]
        double comp = RtcpIntervalCalculator.COMPENSATION;
        assertThat(min).isGreaterThanOrEqualTo(deterministic * 0.5 / comp - 0.01);
        assertThat(max).isLessThanOrEqualTo(deterministic * 1.5 / comp + 0.01);
    }

    @Test
    void testUpdateAvgPacketSize() {
        var calc = new RtcpIntervalCalculator(64_000);
        double initial = calc.avgPacketSize();

        calc.updateAvgPacketSize(256);
        assertThat(calc.avgPacketSize()).isGreaterThan(initial);

        // After many updates, should converge
        for (int i = 0; i < 100; i++) {
            calc.updateAvgPacketSize(256);
        }
        assertThat(calc.avgPacketSize()).isCloseTo(256.0, within(1.0));
    }

    @Test
    void testInitialFlag() {
        var calc = new RtcpIntervalCalculator(64_000);
        assertThat(calc.isInitial()).isTrue();
        calc.markInitialSent();
        assertThat(calc.isInitial()).isFalse();
    }

    @Test
    void testZeroParticipants() {
        var calc = new RtcpIntervalCalculator(64_000);
        double interval = calc.computeDeterministicInterval(0, 0, false);
        assertThat(interval).isEqualTo(RtcpIntervalCalculator.MIN_INTERVAL_SEC);
    }
}
