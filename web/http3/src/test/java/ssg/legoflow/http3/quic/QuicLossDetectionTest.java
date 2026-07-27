package ssg.legoflow.http3.quic;

import ssg.legoflow.http3.quic.QuicLossDetection.PacketNumberSpace;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class QuicLossDetectionTest {

    @Test
    void testInitialRttValues() {
        var ld = new QuicLossDetection();
        assertThat(ld.smoothedRtt()).isEqualTo(QuicLossDetection.INITIAL_RTT_MS);
        assertThat(ld.rttVariance()).isEqualTo(QuicLossDetection.INITIAL_RTT_MS / 2);
        assertThat(ld.minRtt()).isEqualTo(Long.MAX_VALUE);
        assertThat(ld.latestRtt()).isEqualTo(0);
        assertThat(ld.ptoCount()).isEqualTo(0);
    }

    @Test
    void testOnPacketSentTracksPackets() {
        var ld = new QuicLossDetection();
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 0, true, 1200);
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 1, true, 1200);
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 2, false, 100);

        assertThat(ld.trackedPacketCount(PacketNumberSpace.APPLICATION_DATA)).isEqualTo(3);
    }

    @Test
    void testOnPacketSentDifferentSpaces() {
        var ld = new QuicLossDetection();
        ld.onPacketSent(PacketNumberSpace.INITIAL, 0, true, 500);
        ld.onPacketSent(PacketNumberSpace.HANDSHAKE, 0, true, 600);
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 0, true, 1200);

        assertThat(ld.trackedPacketCount(PacketNumberSpace.INITIAL)).isEqualTo(1);
        assertThat(ld.trackedPacketCount(PacketNumberSpace.HANDSHAKE)).isEqualTo(1);
        assertThat(ld.trackedPacketCount(PacketNumberSpace.APPLICATION_DATA)).isEqualTo(1);
    }

    @Test
    void testOnAckReceivedRemovesAckedPackets() {
        var ld = new QuicLossDetection();
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 0, true, 1200);
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 1, true, 1200);
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 2, true, 1200);

        ld.onAckReceived(PacketNumberSpace.APPLICATION_DATA, 1, Set.of(0L, 1L), 0);

        // Packet 0 and 1 acked, packet 2 still tracked
        assertThat(ld.trackedPacketCount(PacketNumberSpace.APPLICATION_DATA)).isLessThanOrEqualTo(2);
    }

    @Test
    void testOnAckReceivedResetsPtoCount() {
        var ld = new QuicLossDetection();
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 0, true, 1200);
        ld.onPtoExpired();
        ld.onPtoExpired();
        assertThat(ld.ptoCount()).isEqualTo(2);

        ld.onAckReceived(PacketNumberSpace.APPLICATION_DATA, 0, Set.of(0L), 0);
        assertThat(ld.ptoCount()).isEqualTo(0);
    }

    @Test
    void testPacketThresholdLossDetection() {
        var ld = new QuicLossDetection();
        // Send packets 0..5
        for (long i = 0; i <= 5; i++) {
            ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, i, true, 1200);
        }

        // ACK packet 5 — packets 0, 1, 2 are more than K_PACKET_THRESHOLD below
        var lost = ld.onAckReceived(PacketNumberSpace.APPLICATION_DATA, 5,
                Set.of(5L), 0);

        // Packets 0, 1, 2 should be detected as lost (5-0=5 >= 3, 5-1=4 >= 3, 5-2=3 >= 3)
        assertThat(lost).contains(0L, 1L, 2L);
        // Packets 3, 4 should NOT be lost (5-3=2 < 3, 5-4=1 < 3)
        assertThat(lost).doesNotContain(3L, 4L);
    }

    @Test
    void testDetectLostPacketsNoLargestAcked() {
        var ld = new QuicLossDetection();
        ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, 0, true, 1200);

        var lost = ld.detectLostPackets(PacketNumberSpace.APPLICATION_DATA);
        assertThat(lost).isEmpty();
    }

    @Test
    void testComputePtoDefault() {
        var ld = new QuicLossDetection();
        long pto = ld.computePto(25);

        // PTO = smoothedRtt + max(4*rttVar, granularity) + maxAckDelay
        // = 333 + max(4*166, 1) + 25 = 333 + 664 + 25 = 1022
        assertThat(pto).isEqualTo(1022);
    }

    @Test
    void testComputePtoWithBackoff() {
        var ld = new QuicLossDetection();
        long pto0 = ld.computePto(25);

        ld.onPtoExpired();
        long pto1 = ld.computePto(25);
        assertThat(pto1).isEqualTo(pto0 * 2);

        ld.onPtoExpired();
        long pto2 = ld.computePto(25);
        assertThat(pto2).isEqualTo(pto0 * 4);
    }

    @Test
    void testPtoCountIncrements() {
        var ld = new QuicLossDetection();
        assertThat(ld.ptoCount()).isEqualTo(0);

        ld.onPtoExpired();
        assertThat(ld.ptoCount()).isEqualTo(1);

        ld.onPtoExpired();
        assertThat(ld.ptoCount()).isEqualTo(2);
    }

    @Test
    void testConstants() {
        assertThat(QuicLossDetection.K_PACKET_THRESHOLD).isEqualTo(3);
        assertThat(QuicLossDetection.K_TIME_THRESHOLD).isEqualTo(9.0 / 8.0);
        assertThat(QuicLossDetection.INITIAL_RTT_MS).isEqualTo(333);
        assertThat(QuicLossDetection.K_GRANULARITY_MS).isEqualTo(1);
    }

    @Test
    void testPacketNumberSpaceValues() {
        assertThat(PacketNumberSpace.values()).hasSize(3);
        assertThat(PacketNumberSpace.INITIAL).isNotNull();
        assertThat(PacketNumberSpace.HANDSHAKE).isNotNull();
        assertThat(PacketNumberSpace.APPLICATION_DATA).isNotNull();
    }

    @Test
    void testLostPacketsRemovedFromTracking() {
        var ld = new QuicLossDetection();
        for (long i = 0; i <= 5; i++) {
            ld.onPacketSent(PacketNumberSpace.APPLICATION_DATA, i, true, 1200);
        }

        ld.onAckReceived(PacketNumberSpace.APPLICATION_DATA, 5, Set.of(5L), 0);

        // After loss detection, lost packets should be removed
        // Only packets 3, 4 remain (5 was acked, 0-2 lost)
        assertThat(ld.trackedPacketCount(PacketNumberSpace.APPLICATION_DATA)).isEqualTo(2);
    }
}
