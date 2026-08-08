package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;

/**
 * RTCP Sender Report (SR) packet (RFC 3550 Section 6.4.1).
 *
 * <p>Sent by participants that are active senders. Contains sender info
 * (NTP timestamp, RTP timestamp, packet/octet counts) and reception
 * report blocks for each source from which this sender has received.
 *
 * @param ssrc            the SSRC of the sender
 * @param ntpTimestamp    the NTP timestamp (64-bit wallclock time)
 * @param rtpTimestamp    the RTP timestamp corresponding to the NTP timestamp
 * @param senderPacketCount the total number of RTP data packets sent
 * @param senderOctetCount  the total number of payload octets sent
 * @param reports         the reception report blocks
 * @since 0.1.0
 */
public record SenderReport(
        long ssrc,
        long ntpTimestamp,
        long rtpTimestamp,
        long senderPacketCount,
        long senderOctetCount,
        List<ReceptionReport> reports
) implements RtcpPacket {

    /**
     * Creates a sender report with validation.
     */
    public SenderReport {
        Objects.requireNonNull(reports, "reports");
        reports = List.copyOf(reports);
        if (reports.size() > 31) {
            throw new IllegalArgumentException("Maximum 31 reception reports: " + reports.size());
        }
    }

    @Override
    public int packetType() {
        return PT_SR;
    }

    /**
     * Returns the reception report count.
     *
     * @return the number of reception reports
     */
    public int reportCount() {
        return reports.size();
    }
}
