package ssg.legoflow.media.rtp.session;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a participant in an RTP session identified by SSRC.
 *
 * <p>Tracks sender and receiver statistics for this participant including
 * packet counts, byte counts, jitter, and last activity time.
 *
 * @since 0.1.0
 */
public final class RtpParticipant {

    private final long ssrc;
    private final AtomicReference<String> cname = new AtomicReference<>();
    private final AtomicLong packetsReceived = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong packetsSent = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong packetsLost = new AtomicLong();
    private final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastSRReceived = new AtomicReference<>();
    private volatile long lastSequenceNumber;
    private volatile long highestSequenceNumber;
    private volatile long jitter;
    private volatile boolean sender;

    /**
     * Creates a new participant.
     *
     * @param ssrc the synchronization source identifier
     */
    public RtpParticipant(long ssrc) {
        this.ssrc = ssrc;
    }

    /**
     * Returns the SSRC identifier.
     *
     * @return the SSRC
     */
    public long ssrc() {
        return ssrc;
    }

    /**
     * Returns the canonical name (CNAME) if known.
     *
     * @return the CNAME
     */
    public Optional<String> cname() {
        return Optional.ofNullable(cname.get());
    }

    /**
     * Sets the canonical name.
     *
     * @param cname the CNAME value
     */
    public void setCname(String cname) {
        this.cname.set(cname);
    }

    /**
     * Records a received packet from this participant.
     *
     * @param sequenceNumber the packet sequence number
     * @param payloadSize    the payload size in bytes
     */
    public void recordReceived(int sequenceNumber, int payloadSize) {
        packetsReceived.incrementAndGet();
        bytesReceived.addAndGet(payloadSize);
        lastSequenceNumber = sequenceNumber;
        if (sequenceNumber > highestSequenceNumber || highestSequenceNumber - sequenceNumber > 0x8000) {
            highestSequenceNumber = sequenceNumber;
        }
        lastActivity.set(Instant.now());
    }

    /**
     * Records a sent packet.
     *
     * @param payloadSize the payload size in bytes
     */
    public void recordSent(int payloadSize) {
        packetsSent.incrementAndGet();
        bytesSent.addAndGet(payloadSize);
        sender = true;
        lastActivity.set(Instant.now());
    }

    /**
     * Updates the interarrival jitter estimate (RFC 3550 A.8).
     *
     * @param jitter the new jitter value
     */
    public void updateJitter(long jitter) {
        this.jitter = jitter;
    }

    /** @return the total packets received from this participant */
    public long packetsReceived() { return packetsReceived.get(); }

    /** @return the total bytes received from this participant */
    public long bytesReceived() { return bytesReceived.get(); }

    /** @return the total packets sent by this participant */
    public long packetsSent() { return packetsSent.get(); }

    /** @return the total bytes sent by this participant */
    public long bytesSent() { return bytesSent.get(); }

    /** @return the total packets lost */
    public long packetsLost() { return packetsLost.get(); }

    /** @return the current interarrival jitter estimate */
    public long jitter() { return jitter; }

    /** @return the highest sequence number received */
    public long highestSequenceNumber() { return highestSequenceNumber; }

    /** @return the last activity time */
    public Instant lastActivity() { return lastActivity.get(); }

    /** @return true if this participant is a sender */
    public boolean isSender() { return sender; }

    /**
     * Records the time an SR was received from this participant.
     *
     * @param time the time the SR was received
     */
    public void setLastSRReceived(Instant time) {
        lastSRReceived.set(time);
    }

    /**
     * Returns the time the last SR was received from this participant.
     *
     * @return the time, or empty if no SR has been received
     */
    public Optional<Instant> lastSRReceived() {
        return Optional.ofNullable(lastSRReceived.get());
    }

    /**
     * Increments the lost packet counter.
     *
     * @param count the number of packets lost
     */
    public void addLost(long count) {
        packetsLost.addAndGet(count);
    }

    @Override
    public String toString() {
        return "RtpParticipant[ssrc=0x%08X, cname=%s, recv=%d, sent=%d]"
                .formatted(ssrc, cname.get(), packetsReceived.get(), packetsSent.get());
    }
}
