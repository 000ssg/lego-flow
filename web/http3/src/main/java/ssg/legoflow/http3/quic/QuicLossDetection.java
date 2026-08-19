package ssg.legoflow.http3.quic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * QUIC loss detection per RFC 9002 Sections 5-6.
 *
 * <p>Implements ACK-based loss detection with:
 * <ul>
 *   <li>RTT estimation (smoothed RTT, RTT variance, min RTT)</li>
 *   <li>Packet threshold-based detection (kPacketThreshold = 3)</li>
 *   <li>Time threshold-based detection (9/8 * max(SRTT, latest_rtt))</li>
 *   <li>Probe Timeout (PTO) computation per packet number space</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class QuicLossDetection {

    private static final Logger LOG = LoggerFactory.getLogger(QuicLossDetection.class);

    /** Packet reordering threshold (RFC 9002 Section 6.1.1). */
    public static final int K_PACKET_THRESHOLD = 3;

    /** Time reordering factor (RFC 9002 Section 6.1.2): 9/8 as a multiplier. */
    public static final double K_TIME_THRESHOLD = 9.0 / 8.0;

    /** Initial RTT estimate (RFC 9002 Section 6.2.2): 333ms. */
    public static final long INITIAL_RTT_MS = 333;

    /** Granularity timer floor (RFC 9002 Section 6.1.2): 1ms. */
    public static final long K_GRANULARITY_MS = 1;

    // ---- RTT state ----
    private long smoothedRtt = INITIAL_RTT_MS;
    private long rttVariance = INITIAL_RTT_MS / 2;
    private long minRtt = Long.MAX_VALUE;
    private long latestRtt = 0;
    private boolean firstRttSample = true;

    // ---- Sent packet tracking per packet number space ----
    private final Map<PacketNumberSpace, Map<Long, SentPacketInfo>> sentPackets = new ConcurrentHashMap<>();
    private final Map<PacketNumberSpace, Long> largestAckedPacket = new ConcurrentHashMap<>();
    private final Map<PacketNumberSpace, Long> lossTime = new ConcurrentHashMap<>();

    // ---- PTO state ----
    private int ptoCount = 0;

    /**
     * Packet number spaces per RFC 9002 Section 4.
     *
     * @since 0.1.0
     */
    public enum PacketNumberSpace {
        /** Initial packets (client/server Initial). */
        INITIAL,
        /** Handshake packets. */
        HANDSHAKE,
        /** Application data (1-RTT) packets. */
        APPLICATION_DATA
    }

    /**
     * Information about a sent packet used for loss detection.
     *
     * @param packetNumber the packet number
     * @param ackEliciting whether this packet elicits an ACK
     * @param sentBytes    the number of bytes in the packet
     * @param timeSent     the time the packet was sent (System.nanoTime())
     * @since 0.1.0
     */
    public record SentPacketInfo(long packetNumber, boolean ackEliciting,
                                  int sentBytes, long timeSent) {
    }

    /**
     * Creates a new loss detection instance.
     *
     * @since 0.1.0
     */
    public QuicLossDetection() {
        for (var space : PacketNumberSpace.values()) {
            sentPackets.put(space, new ConcurrentHashMap<>());
            largestAckedPacket.put(space, -1L);
            lossTime.put(space, 0L);
        }
    }

    /**
     * Records a sent packet for loss tracking.
     *
     * @param space        the packet number space
     * @param packetNumber the packet number
     * @param ackEliciting whether this packet elicits an ACK
     * @param sentBytes    the number of bytes
     * @since 0.1.0
     */
    public void onPacketSent(PacketNumberSpace space, long packetNumber,
                             boolean ackEliciting, int sentBytes) {
        var info = new SentPacketInfo(packetNumber, ackEliciting, sentBytes, System.nanoTime());
        sentPackets.get(space).put(packetNumber, info);
    }

    /**
     * Processes an ACK frame, updating RTT estimates and detecting losses.
     *
     * <p>Per RFC 9002 Section 5.1 (RTT estimation) and Section 6.1 (loss detection).
     *
     * @param space         the packet number space
     * @param largestAcked  the largest acknowledged packet number
     * @param ackedPackets  the set of acknowledged packet numbers
     * @param ackDelay      the ACK delay reported by the peer (microseconds)
     * @return the list of packet numbers detected as lost
     * @since 0.1.0
     */
    public List<Long> onAckReceived(PacketNumberSpace space, long largestAcked,
                                     Set<Long> ackedPackets, long ackDelay) {
        // Update largest acked
        long prevLargest = largestAckedPacket.get(space);
        if (largestAcked > prevLargest) {
            largestAckedPacket.put(space, largestAcked);
        }

        // Update RTT from the largest newly acknowledged packet
        var largestAckedInfo = sentPackets.get(space).get(largestAcked);
        if (largestAckedInfo != null && largestAcked > prevLargest) {
            long now = System.nanoTime();
            long rttSampleNs = now - largestAckedInfo.timeSent();
            long rttSampleMs = Math.max(1, rttSampleNs / 1_000_000);
            updateRtt(rttSampleMs, ackDelay / 1000); // Convert ack_delay from us to ms
        }

        // Remove acknowledged packets from tracking
        for (long pn : ackedPackets) {
            sentPackets.get(space).remove(pn);
        }

        // Reset PTO count on receiving an ACK
        ptoCount = 0;

        // Detect lost packets
        return detectLostPackets(space);
    }

    /**
     * Detects lost packets in the given packet number space.
     *
     * <p>A packet is considered lost if:
     * <ul>
     *   <li>Its packet number is more than {@link #K_PACKET_THRESHOLD} below
     *       the largest acknowledged, OR</li>
     *   <li>It was sent more than the time threshold before the largest acknowledged
     *       packet's send time</li>
     * </ul>
     *
     * @param space the packet number space
     * @return the list of lost packet numbers
     * @since 0.1.0
     */
    public List<Long> detectLostPackets(PacketNumberSpace space) {
        long largest = largestAckedPacket.get(space);
        if (largest < 0) return List.of();

        long timeThresholdMs = (long) (K_TIME_THRESHOLD * Math.max(smoothedRtt, latestRtt));
        if (timeThresholdMs < K_GRANULARITY_MS) {
            timeThresholdMs = K_GRANULARITY_MS;
        }

        var lost = new ArrayList<Long>();
        long now = System.nanoTime();
        long timeThresholdNs = timeThresholdMs * 1_000_000;
        long earliestLossTime = Long.MAX_VALUE;

        var tracked = sentPackets.get(space);
        for (var entry : tracked.entrySet()) {
            var info = entry.getValue();
            if (info.packetNumber() > largest) continue;

            // Packet threshold
            if (largest - info.packetNumber() >= K_PACKET_THRESHOLD) {
                lost.add(info.packetNumber());
                continue;
            }

            // Time threshold
            long elapsed = now - info.timeSent();
            if (elapsed >= timeThresholdNs) {
                lost.add(info.packetNumber());
            } else {
                long lossTimeCandidate = info.timeSent() + timeThresholdNs;
                if (lossTimeCandidate < earliestLossTime) {
                    earliestLossTime = lossTimeCandidate;
                }
            }
        }

        // Remove lost packets from tracking
        for (long pn : lost) {
            tracked.remove(pn);
        }

        // Set loss time for this space
        lossTime.put(space, earliestLossTime == Long.MAX_VALUE ? 0L : earliestLossTime);

        if (!lost.isEmpty()) {
            LOG.debug("Detected {} lost packets in {} space", lost.size(), space);
        }

        return lost;
    }

    /**
     * Computes the Probe Timeout (PTO) per RFC 9002 Section 6.2.
     *
     * <p>PTO = smoothedRtt + max(4 * rttVariance, kGranularity) + maxAckDelay
     *
     * @param maxAckDelayMs the peer's maximum ACK delay in milliseconds
     * @return the PTO in milliseconds
     * @since 0.1.0
     */
    public long computePto(long maxAckDelayMs) {
        long pto = smoothedRtt + Math.max(4 * rttVariance, K_GRANULARITY_MS) + maxAckDelayMs;
        // Exponential backoff
        pto *= (1L << ptoCount);
        return pto;
    }

    /**
     * Increments the PTO count (used for exponential backoff).
     *
     * @since 0.1.0
     */
    public void onPtoExpired() {
        ptoCount++;
        LOG.debug("PTO expired, count={}", ptoCount);
    }

    /**
     * Updates RTT estimates per RFC 9002 Section 5.3.
     *
     * @param rttSampleMs the latest RTT sample in milliseconds
     * @param ackDelayMs  the peer-reported ACK delay in milliseconds
     */
    private void updateRtt(long rttSampleMs, long ackDelayMs) {
        latestRtt = rttSampleMs;

        if (rttSampleMs < minRtt) {
            minRtt = rttSampleMs;
        }

        if (firstRttSample) {
            smoothedRtt = rttSampleMs;
            rttVariance = rttSampleMs / 2;
            firstRttSample = false;
            return;
        }

        // Adjust for ACK delay (only for application data)
        long adjustedRtt = rttSampleMs;
        if (adjustedRtt > minRtt + ackDelayMs) {
            adjustedRtt -= ackDelayMs;
        }

        // RFC 9002 Section 5.3:
        // rttvar = 3/4 * rttvar + 1/4 * abs(smoothed_rtt - adjusted_rtt)
        rttVariance = (3 * rttVariance + Math.abs(smoothedRtt - adjustedRtt)) / 4;
        // smoothed_rtt = 7/8 * smoothed_rtt + 1/8 * adjusted_rtt
        smoothedRtt = (7 * smoothedRtt + adjustedRtt) / 8;
    }

    // ---- Accessors ----

    /** Returns the smoothed RTT estimate in milliseconds. */
    public long smoothedRtt() { return smoothedRtt; }
    /** Returns the RTT variance in milliseconds. */
    public long rttVariance() { return rttVariance; }
    /** Returns the minimum RTT observed in milliseconds. */
    public long minRtt() { return minRtt; }
    /** Returns the latest RTT sample in milliseconds. */
    public long latestRtt() { return latestRtt; }
    /** Returns the current PTO count (0 = no PTO expired). */
    public int ptoCount() { return ptoCount; }

    /** Returns the number of tracked sent packets in a space. */
    public int trackedPacketCount(PacketNumberSpace space) {
        return sentPackets.get(space).size();
    }
}
