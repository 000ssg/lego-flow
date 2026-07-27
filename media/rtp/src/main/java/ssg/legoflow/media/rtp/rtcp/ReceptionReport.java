package ssg.legoflow.media.rtp.rtcp;

/**
 * RTCP reception report block (RFC 3550 Section 6.4.1).
 *
 * <p>Contains statistics about data received from a single synchronization source.
 *
 * @param ssrc             the SSRC identifier of the source
 * @param fractionLost     the fraction of packets lost since last report (0-255, scaled by 256)
 * @param cumulativeLost   cumulative number of packets lost (24-bit signed)
 * @param highestSeqReceived the extended highest sequence number received
 * @param jitter           the interarrival jitter estimate
 * @param lastSR           the middle 32 bits of the NTP timestamp from the most recent SR
 * @param delaySinceLastSR the delay since last SR, in units of 1/65536 seconds
 * @since 1.0.0
 */
public record ReceptionReport(
        long ssrc,
        int fractionLost,
        int cumulativeLost,
        long highestSeqReceived,
        long jitter,
        long lastSR,
        long delaySinceLastSR
) {

    /**
     * Creates a reception report with validation.
     */
    public ReceptionReport {
        if (fractionLost < 0 || fractionLost > 255) {
            throw new IllegalArgumentException("Fraction lost must be 0-255: " + fractionLost);
        }
        if (cumulativeLost < -0x800000 || cumulativeLost > 0x7FFFFF) {
            throw new IllegalArgumentException("Cumulative lost must fit in 24-bit signed: " + cumulativeLost);
        }
    }

    /** Reception report block size in bytes. */
    public static final int SIZE = 24;
}
