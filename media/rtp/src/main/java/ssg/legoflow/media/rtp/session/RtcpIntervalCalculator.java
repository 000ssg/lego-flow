package ssg.legoflow.media.rtp.session;

import java.util.concurrent.ThreadLocalRandom;

/**
 * RTCP transmission interval calculator (RFC 3550 Section 6.3).
 *
 * <p>Computes the deterministic and randomized intervals between RTCP
 * transmissions based on session bandwidth, number of participants,
 * and whether the local participant is a sender.
 *
 * <p>Key parameters from RFC 3550:
 * <ul>
 *   <li>RTCP bandwidth fraction: 5% of session bandwidth</li>
 *   <li>Sender fraction: 25% of RTCP bandwidth to senders</li>
 *   <li>Minimum interval: 5 seconds (reduced to 2.5 for initial)</li>
 *   <li>Randomization: 0.5 to 1.5 times the deterministic interval</li>
 *   <li>Compensation factor: e / (e-1) = 1.21828...</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class RtcpIntervalCalculator {

    /** Default RTCP bandwidth fraction (5% of session bandwidth). */
    public static final double RTCP_BW_FRACTION = 0.05;

    /** Fraction of RTCP bandwidth allocated to senders. */
    public static final double SENDER_BW_FRACTION = 0.25;

    /** Minimum RTCP interval in seconds. */
    public static final double MIN_INTERVAL_SEC = 5.0;

    /** Reduced minimum for initial report. */
    public static final double INITIAL_MIN_INTERVAL_SEC = 2.5;

    /** Compensation factor: e/(e-1). */
    public static final double COMPENSATION = Math.E / (Math.E - 1.0);

    /** Average RTCP packet size in bytes (initial estimate). */
    private static final int INITIAL_AVG_PACKET_SIZE = 128;

    private double sessionBandwidthBps;
    private double avgPacketSize;
    private boolean initial;

    /**
     * Creates a calculator with the specified session bandwidth.
     *
     * @param sessionBandwidthBps the total session bandwidth in bits per second
     */
    public RtcpIntervalCalculator(double sessionBandwidthBps) {
        this.sessionBandwidthBps = sessionBandwidthBps;
        this.avgPacketSize = INITIAL_AVG_PACKET_SIZE;
        this.initial = true;
    }

    /**
     * Computes the deterministic RTCP interval in seconds (RFC 3550 Section 6.3.1).
     *
     * @param participantCount the total number of session members
     * @param senderCount      the number of senders
     * @param isSender         whether the local participant is a sender
     * @return the deterministic interval in seconds
     */
    public double computeDeterministicInterval(int participantCount, int senderCount,
                                                boolean isSender) {
        double rtcpBw = sessionBandwidthBps * RTCP_BW_FRACTION;

        // Determine effective number of members and bandwidth
        int n; // number of members for computation
        double bw; // bandwidth for this group

        if (senderCount <= (int) (participantCount * SENDER_BW_FRACTION)) {
            // Senders are <= 25% of members
            if (isSender) {
                n = senderCount;
                bw = rtcpBw * SENDER_BW_FRACTION;
            } else {
                n = participantCount - senderCount;
                bw = rtcpBw * (1.0 - SENDER_BW_FRACTION);
            }
        } else {
            // Senders are > 25% of members
            n = participantCount;
            bw = rtcpBw;
        }

        if (n == 0 || bw <= 0) {
            return MIN_INTERVAL_SEC;
        }

        // T = max(Tmin, n * avgSize * 8 / bw)
        double minInterval = initial ? INITIAL_MIN_INTERVAL_SEC : MIN_INTERVAL_SEC;
        double interval = (n * avgPacketSize * 8.0) / bw;
        interval = Math.max(minInterval, interval);

        return interval;
    }

    /**
     * Computes the randomized RTCP interval in seconds.
     *
     * <p>Applies the randomization factor (0.5 to 1.5) and the
     * compensation factor e/(e-1) as specified in RFC 3550.
     *
     * @param participantCount the total number of session members
     * @param senderCount      the number of senders
     * @param isSender         whether the local participant is a sender
     * @return the randomized interval in seconds
     */
    public double computeRandomizedInterval(int participantCount, int senderCount,
                                             boolean isSender) {
        double td = computeDeterministicInterval(participantCount, senderCount, isSender);
        double randomFactor = 0.5 + ThreadLocalRandom.current().nextDouble();
        return (td * randomFactor) / COMPENSATION;
    }

    /**
     * Updates the average RTCP packet size using an exponentially
     * weighted moving average.
     *
     * @param packetSize the size of the latest RTCP packet in bytes
     */
    public void updateAvgPacketSize(int packetSize) {
        // EWMA: avg = (1/16) * packet_size + (15/16) * avg
        avgPacketSize = (1.0 / 16.0) * packetSize + (15.0 / 16.0) * avgPacketSize;
    }

    /**
     * Returns the current average RTCP packet size estimate.
     *
     * @return the average packet size in bytes
     */
    public double avgPacketSize() {
        return avgPacketSize;
    }

    /**
     * Marks that the initial RTCP packet has been sent.
     * Subsequent intervals use the full minimum interval.
     */
    public void markInitialSent() {
        initial = false;
    }

    /**
     * Returns whether this is the initial transmission.
     *
     * @return true if no RTCP packet has been sent yet
     */
    public boolean isInitial() {
        return initial;
    }

    /**
     * Sets the session bandwidth.
     *
     * @param sessionBandwidthBps the session bandwidth in bits per second
     */
    public void setSessionBandwidth(double sessionBandwidthBps) {
        this.sessionBandwidthBps = sessionBandwidthBps;
    }
}
