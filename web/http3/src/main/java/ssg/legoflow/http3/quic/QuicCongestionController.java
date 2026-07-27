package ssg.legoflow.http3.quic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QUIC congestion control per RFC 9002 Section 7.
 *
 * <p>Implements a Reno-like congestion controller with three phases:
 * <ul>
 *   <li><b>Slow Start</b> — cwnd grows by the number of acknowledged bytes
 *       (exponential growth) until ssthresh is reached or loss occurs</li>
 *   <li><b>Congestion Avoidance</b> — cwnd grows by approximately one MSS per
 *       round-trip (additive increase) until loss occurs</li>
 *   <li><b>Recovery</b> — entered on packet loss; cwnd is halved (multiplicative
 *       decrease) and the controller remains in recovery until all packets
 *       sent before the loss event are acknowledged</li>
 * </ul>
 *
 * <p>The implementation tracks bytes in flight to enforce the congestion window.
 *
 * @since 1.0.0
 */
public final class QuicCongestionController {

    private static final Logger LOG = LoggerFactory.getLogger(QuicCongestionController.class);

    /** Default maximum segment size (RFC 9002 Section 7.2). */
    public static final int DEFAULT_MAX_DATAGRAM_SIZE = 1200;

    /** Initial congestion window: 10 * max_datagram_size (RFC 9002 Section 7.2). */
    public static final int INITIAL_WINDOW_PACKETS = 10;

    /** Minimum congestion window: 2 * max_datagram_size (RFC 9002 Section 7.2). */
    public static final int MINIMUM_WINDOW_PACKETS = 2;

    /** Multiplicative decrease factor for loss events (RFC 9002 Section 7.3.2). */
    public static final double LOSS_REDUCTION_FACTOR = 0.5;

    /**
     * Congestion control phases per RFC 9002 Section 7.3.
     *
     * @since 1.0.0
     */
    public enum Phase {
        /** Exponential growth — cwnd += acked_bytes per ACK. */
        SLOW_START,
        /** Additive increase — cwnd += MSS * acked_bytes / cwnd per ACK. */
        CONGESTION_AVOIDANCE,
        /** Loss recovery — cwnd is frozen until recovery completes. */
        RECOVERY
    }

    private final int maxDatagramSize;
    private long congestionWindow;
    private long slowStartThreshold;
    private long bytesInFlight;
    private Phase phase;
    private long recoveryStartTime;

    /**
     * Creates a congestion controller with default parameters.
     *
     * @since 1.0.0
     */
    public QuicCongestionController() {
        this(DEFAULT_MAX_DATAGRAM_SIZE);
    }

    /**
     * Creates a congestion controller with a custom max datagram size.
     *
     * @param maxDatagramSize the maximum datagram size in bytes
     * @since 1.0.0
     */
    public QuicCongestionController(int maxDatagramSize) {
        this.maxDatagramSize = maxDatagramSize;
        this.congestionWindow = (long) INITIAL_WINDOW_PACKETS * maxDatagramSize;
        this.slowStartThreshold = Long.MAX_VALUE;
        this.bytesInFlight = 0;
        this.phase = Phase.SLOW_START;
        this.recoveryStartTime = 0;
    }

    /**
     * Records bytes sent, adding them to the bytes-in-flight counter.
     *
     * @param sentBytes the number of bytes sent
     * @since 1.0.0
     */
    public void onPacketSent(int sentBytes) {
        bytesInFlight += sentBytes;
    }

    /**
     * Processes an acknowledgment, growing the congestion window.
     *
     * <p>In slow start, cwnd grows by the number of acknowledged bytes.
     * In congestion avoidance, cwnd grows by approximately one MSS per RTT
     * using the formula: cwnd += maxDatagramSize * ackedBytes / cwnd.
     *
     * <p>If in recovery and the acknowledged packet was sent before recovery
     * started, the window is not grown.
     *
     * @param ackedBytes the number of acknowledged bytes
     * @param sentTime   the time the acknowledged packet was sent (System.nanoTime())
     * @since 1.0.0
     */
    public void onPacketAcked(int ackedBytes, long sentTime) {
        bytesInFlight = Math.max(0, bytesInFlight - ackedBytes);

        // If in recovery, do not grow cwnd for packets sent before recovery
        if (phase == Phase.RECOVERY && sentTime <= recoveryStartTime) {
            return;
        }

        // Exit recovery if we've acked everything from before recovery started
        if (phase == Phase.RECOVERY) {
            phase = congestionWindow < slowStartThreshold
                    ? Phase.SLOW_START : Phase.CONGESTION_AVOIDANCE;
            LOG.debug("Exiting recovery, phase={}, cwnd={}", phase, congestionWindow);
        }

        switch (phase) {
            case SLOW_START -> {
                congestionWindow += ackedBytes;
                if (congestionWindow >= slowStartThreshold) {
                    phase = Phase.CONGESTION_AVOIDANCE;
                    LOG.debug("Slow start -> congestion avoidance, cwnd={}, ssthresh={}",
                            congestionWindow, slowStartThreshold);
                }
            }
            case CONGESTION_AVOIDANCE -> {
                // Additive increase: roughly 1 MSS per RTT
                congestionWindow += (long) maxDatagramSize * ackedBytes / congestionWindow;
            }
            case RECOVERY -> {
                // Should not reach here, handled above
            }
        }
    }

    /**
     * Handles a packet loss event, entering recovery if not already in recovery.
     *
     * <p>Per RFC 9002 Section 7.3.2:
     * <ul>
     *   <li>ssthresh = max(cwnd * LOSS_REDUCTION_FACTOR, kMinimumWindow)</li>
     *   <li>cwnd = ssthresh</li>
     * </ul>
     *
     * <p>Multiple losses in the same recovery period do not further reduce the window.
     *
     * @param sentTime the time the lost packet was sent (System.nanoTime())
     * @since 1.0.0
     */
    public void onPacketLost(int lostBytes, long sentTime) {
        bytesInFlight = Math.max(0, bytesInFlight - lostBytes);

        // If this loss is from a packet sent during the current recovery period, ignore
        if (phase == Phase.RECOVERY && sentTime <= recoveryStartTime) {
            return;
        }

        // Enter recovery
        long minimumWindow = (long) MINIMUM_WINDOW_PACKETS * maxDatagramSize;
        slowStartThreshold = Math.max((long) (congestionWindow * LOSS_REDUCTION_FACTOR), minimumWindow);
        congestionWindow = slowStartThreshold;
        phase = Phase.RECOVERY;
        recoveryStartTime = sentTime;

        LOG.debug("Entering recovery: cwnd={}, ssthresh={}, bytesInFlight={}",
                congestionWindow, slowStartThreshold, bytesInFlight);
    }

    /**
     * Handles a persistent congestion event (RFC 9002 Section 7.6.2).
     *
     * <p>Persistent congestion means packets spanning an entire PTO period
     * are all lost. The congestion window is collapsed to the minimum.
     *
     * @since 1.0.0
     */
    public void onPersistentCongestion() {
        long minimumWindow = (long) MINIMUM_WINDOW_PACKETS * maxDatagramSize;
        congestionWindow = minimumWindow;
        slowStartThreshold = minimumWindow;
        phase = Phase.SLOW_START;
        LOG.debug("Persistent congestion — cwnd collapsed to {}", minimumWindow);
    }

    /**
     * Returns whether the sender is allowed to send more data.
     *
     * @return {@code true} if bytesInFlight is less than the congestion window
     * @since 1.0.0
     */
    public boolean canSend() {
        return bytesInFlight < congestionWindow;
    }

    /**
     * Returns the number of bytes available to send within the congestion window.
     *
     * @return the available bytes, or 0 if the window is full
     * @since 1.0.0
     */
    public long availableBytes() {
        return Math.max(0, congestionWindow - bytesInFlight);
    }

    // ---- Accessors ----

    /** Returns the current congestion window in bytes. */
    public long congestionWindow() { return congestionWindow; }
    /** Returns the slow start threshold in bytes. */
    public long slowStartThreshold() { return slowStartThreshold; }
    /** Returns the current bytes in flight. */
    public long bytesInFlight() { return bytesInFlight; }
    /** Returns the current congestion control phase. */
    public Phase phase() { return phase; }
    /** Returns the max datagram size. */
    public int maxDatagramSize() { return maxDatagramSize; }
    /** Returns the recovery start time. */
    public long recoveryStartTime() { return recoveryStartTime; }
}
