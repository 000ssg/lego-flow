package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;

/**
 * RTCP Receiver Report (RR) packet (RFC 3550 Section 6.4.2).
 *
 * <p>Sent by participants that are not active senders. Contains reception
 * report blocks for each source from which this receiver has received.
 *
 * @param ssrc    the SSRC of the receiver generating this report
 * @param reports the reception report blocks
 * @since 0.1.0
 */
public record ReceiverReport(
        long ssrc,
        List<ReceptionReport> reports
) implements RtcpPacket {

    /**
     * Creates a receiver report with validation.
     */
    public ReceiverReport {
        Objects.requireNonNull(reports, "reports");
        reports = List.copyOf(reports);
        if (reports.size() > 31) {
            throw new IllegalArgumentException("Maximum 31 reception reports: " + reports.size());
        }
    }

    @Override
    public int packetType() {
        return PT_RR;
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
