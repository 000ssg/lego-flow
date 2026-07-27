package ssg.legoflow.http3.quic;

import ssg.legoflow.http3.quic.QuicCongestionController.Phase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QuicCongestionControllerTest {

    @Test
    void testInitialState() {
        var cc = new QuicCongestionController();
        long expectedCwnd = (long) QuicCongestionController.INITIAL_WINDOW_PACKETS
                * QuicCongestionController.DEFAULT_MAX_DATAGRAM_SIZE;

        assertThat(cc.congestionWindow()).isEqualTo(expectedCwnd);
        assertThat(cc.slowStartThreshold()).isEqualTo(Long.MAX_VALUE);
        assertThat(cc.bytesInFlight()).isEqualTo(0);
        assertThat(cc.phase()).isEqualTo(Phase.SLOW_START);
        assertThat(cc.maxDatagramSize()).isEqualTo(QuicCongestionController.DEFAULT_MAX_DATAGRAM_SIZE);
    }

    @Test
    void testCustomMaxDatagramSize() {
        var cc = new QuicCongestionController(1400);
        assertThat(cc.maxDatagramSize()).isEqualTo(1400);
        assertThat(cc.congestionWindow()).isEqualTo(14_000L);
    }

    @Test
    void testOnPacketSentTracksBytesInFlight() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(1200);
        assertThat(cc.bytesInFlight()).isEqualTo(1200);

        cc.onPacketSent(800);
        assertThat(cc.bytesInFlight()).isEqualTo(2000);
    }

    @Test
    void testCanSendWhenWindowAvailable() {
        var cc = new QuicCongestionController();
        assertThat(cc.canSend()).isTrue();
        assertThat(cc.availableBytes()).isEqualTo(cc.congestionWindow());
    }

    @Test
    void testCanSendBlockedWhenWindowFull() {
        var cc = new QuicCongestionController();
        long cwnd = cc.congestionWindow();
        cc.onPacketSent((int) cwnd);

        assertThat(cc.canSend()).isFalse();
        assertThat(cc.availableBytes()).isEqualTo(0);
    }

    @Test
    void testSlowStartGrowth() {
        var cc = new QuicCongestionController();
        long initialCwnd = cc.congestionWindow();

        cc.onPacketSent(1200);
        long sentTime = System.nanoTime();
        cc.onPacketAcked(1200, sentTime);

        // In slow start, cwnd grows by acked bytes
        assertThat(cc.congestionWindow()).isEqualTo(initialCwnd + 1200);
        assertThat(cc.phase()).isEqualTo(Phase.SLOW_START);
    }

    @Test
    void testSlowStartTransitionToCongestionAvoidance() {
        var cc = new QuicCongestionController();
        // Set a low ssthresh to force transition
        // First, cause a loss to set ssthresh
        long cwnd = cc.congestionWindow(); // 12000
        cc.onPacketSent((int) cwnd);
        long sentTime = System.nanoTime();
        cc.onPacketLost((int) cwnd, sentTime);

        // Now ssthresh = cwnd * 0.5 = 6000, and cwnd = ssthresh = 6000, phase = RECOVERY
        long ssthresh = cc.slowStartThreshold();
        assertThat(ssthresh).isEqualTo((long) (cwnd * QuicCongestionController.LOSS_REDUCTION_FACTOR));

        // ACK a packet sent after recovery to exit recovery
        cc.onPacketSent(1200);
        long postRecoverySentTime = System.nanoTime();
        cc.onPacketAcked(1200, postRecoverySentTime);

        // cwnd < ssthresh is no longer true (cwnd == ssthresh), so should go to CONGESTION_AVOIDANCE
        assertThat(cc.phase()).isEqualTo(Phase.CONGESTION_AVOIDANCE);
    }

    @Test
    void testCongestionAvoidanceLinearGrowth() {
        var cc = new QuicCongestionController();
        // Force into congestion avoidance by causing loss first
        cc.onPacketSent(12000);
        long lossTime = System.nanoTime();
        cc.onPacketLost(12000, lossTime);

        // Exit recovery
        cc.onPacketSent(1200);
        long postTime = System.nanoTime();
        cc.onPacketAcked(1200, postTime);
        assertThat(cc.phase()).isEqualTo(Phase.CONGESTION_AVOIDANCE);

        long cwndBefore = cc.congestionWindow();
        cc.onPacketSent(1200);
        long ackTime = System.nanoTime();
        cc.onPacketAcked(1200, ackTime);

        // In congestion avoidance: cwnd += MSS * ackedBytes / cwnd
        // Growth should be much smaller than in slow start
        long growth = cc.congestionWindow() - cwndBefore;
        assertThat(growth).isLessThan(1200); // Less than full MSS
        assertThat(growth).isGreaterThan(0);
    }

    @Test
    void testLossEntersRecovery() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(1200);
        long sentTime = System.nanoTime();

        long cwndBefore = cc.congestionWindow();
        cc.onPacketLost(1200, sentTime);

        assertThat(cc.phase()).isEqualTo(Phase.RECOVERY);
        assertThat(cc.congestionWindow()).isEqualTo(
                (long) (cwndBefore * QuicCongestionController.LOSS_REDUCTION_FACTOR));
        assertThat(cc.slowStartThreshold()).isEqualTo(cc.congestionWindow());
        assertThat(cc.bytesInFlight()).isEqualTo(0);
    }

    @Test
    void testMultipleLossesInRecoveryDoNotReduceFurther() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(1200);
        cc.onPacketSent(1200);
        long sentTime = System.nanoTime();

        cc.onPacketLost(1200, sentTime);
        long cwndAfterFirstLoss = cc.congestionWindow();

        // Second loss from same recovery period should not reduce further
        cc.onPacketLost(1200, sentTime);
        assertThat(cc.congestionWindow()).isEqualTo(cwndAfterFirstLoss);
    }

    @Test
    void testRecoveryDoesNotGrowForOldPackets() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(1200);
        long beforeRecovery = System.nanoTime();

        cc.onPacketLost(600, beforeRecovery);
        long cwndInRecovery = cc.congestionWindow();

        // ACK a packet that was sent before recovery started
        cc.onPacketAcked(600, beforeRecovery);
        assertThat(cc.congestionWindow()).isEqualTo(cwndInRecovery);
    }

    @Test
    void testPersistentCongestion() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(1200);

        cc.onPersistentCongestion();

        long minimumWindow = (long) QuicCongestionController.MINIMUM_WINDOW_PACKETS
                * QuicCongestionController.DEFAULT_MAX_DATAGRAM_SIZE;
        assertThat(cc.congestionWindow()).isEqualTo(minimumWindow);
        assertThat(cc.slowStartThreshold()).isEqualTo(minimumWindow);
        assertThat(cc.phase()).isEqualTo(Phase.SLOW_START);
    }

    @Test
    void testMinimumWindowOnLoss() {
        var cc = new QuicCongestionController();
        long minimumWindow = (long) QuicCongestionController.MINIMUM_WINDOW_PACKETS
                * QuicCongestionController.DEFAULT_MAX_DATAGRAM_SIZE;

        // Cause repeated losses to drive cwnd down
        for (int i = 0; i < 20; i++) {
            cc.onPacketSent(1200);
            long t = System.nanoTime();
            // exit recovery first
            cc.onPacketSent(1200);
            cc.onPacketAcked(1200, System.nanoTime());
            cc.onPacketLost(1200, System.nanoTime());
        }

        assertThat(cc.congestionWindow()).isGreaterThanOrEqualTo(minimumWindow);
    }

    @Test
    void testBytesInFlightNeverNegative() {
        var cc = new QuicCongestionController();
        cc.onPacketSent(100);
        cc.onPacketAcked(200, System.nanoTime()); // More than sent

        assertThat(cc.bytesInFlight()).isEqualTo(0);
    }

    @Test
    void testAvailableBytesDecreasesWithFlight() {
        var cc = new QuicCongestionController();
        long total = cc.availableBytes();

        cc.onPacketSent(1200);
        assertThat(cc.availableBytes()).isEqualTo(total - 1200);
    }

    @Test
    void testConstants() {
        assertThat(QuicCongestionController.DEFAULT_MAX_DATAGRAM_SIZE).isEqualTo(1200);
        assertThat(QuicCongestionController.INITIAL_WINDOW_PACKETS).isEqualTo(10);
        assertThat(QuicCongestionController.MINIMUM_WINDOW_PACKETS).isEqualTo(2);
        assertThat(QuicCongestionController.LOSS_REDUCTION_FACTOR).isEqualTo(0.5);
    }

    @Test
    void testPhaseValues() {
        assertThat(Phase.values()).hasSize(3);
        assertThat(Phase.SLOW_START).isNotNull();
        assertThat(Phase.CONGESTION_AVOIDANCE).isNotNull();
        assertThat(Phase.RECOVERY).isNotNull();
    }
}
