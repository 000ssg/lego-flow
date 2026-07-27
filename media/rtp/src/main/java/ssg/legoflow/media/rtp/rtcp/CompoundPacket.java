package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;

/**
 * RTCP compound packet containing multiple RTCP packets (RFC 3550 Section 6.1).
 *
 * <p>RTCP packets are almost always sent as compound packets. A compound packet
 * must begin with either a Sender Report or Receiver Report, followed by
 * additional RTCP packets (typically SDES with at least a CNAME item).
 *
 * @param packets the list of RTCP packets in this compound packet
 * @since 1.0.0
 */
public record CompoundPacket(List<RtcpPacket> packets) {

    /**
     * Creates a compound packet with validation.
     */
    public CompoundPacket {
        Objects.requireNonNull(packets, "packets");
        packets = List.copyOf(packets);
        if (packets.isEmpty()) {
            throw new IllegalArgumentException("Compound packet must contain at least one packet");
        }
        var first = packets.getFirst();
        if (!(first instanceof SenderReport) && !(first instanceof ReceiverReport)) {
            throw new IllegalArgumentException(
                    "Compound packet must begin with SR or RR, got: " + first.getClass().getSimpleName());
        }
    }

    /**
     * Returns the number of RTCP packets in this compound packet.
     *
     * @return the packet count
     */
    public int size() {
        return packets.size();
    }

    /**
     * Returns the first packet, which is always an SR or RR.
     *
     * @return the first RTCP packet
     */
    public RtcpPacket first() {
        return packets.getFirst();
    }
}
