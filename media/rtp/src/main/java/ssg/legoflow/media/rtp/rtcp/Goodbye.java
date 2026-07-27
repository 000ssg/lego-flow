package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RTCP Goodbye (BYE) packet (RFC 3550 Section 6.6).
 *
 * <p>Indicates that one or more sources are no longer active.
 * An optional reason string may be included.
 *
 * @param ssrcList the list of SSRC/CSRC identifiers leaving the session
 * @param reason   the optional reason for leaving
 * @since 1.0.0
 */
public record Goodbye(List<Long> ssrcList, Optional<String> reason) implements RtcpPacket {

    /**
     * Creates a goodbye packet with validation.
     */
    public Goodbye {
        Objects.requireNonNull(ssrcList, "ssrcList");
        Objects.requireNonNull(reason, "reason");
        ssrcList = List.copyOf(ssrcList);
        if (ssrcList.isEmpty()) {
            throw new IllegalArgumentException("BYE must contain at least one SSRC");
        }
        if (ssrcList.size() > 31) {
            throw new IllegalArgumentException("Maximum 31 SSRCs in BYE: " + ssrcList.size());
        }
        reason.ifPresent(r -> {
            if (r.length() > 255) {
                throw new IllegalArgumentException("BYE reason must be <= 255 bytes: " + r.length());
            }
        });
    }

    @Override
    public int packetType() {
        return PT_BYE;
    }

    @Override
    public long ssrc() {
        return ssrcList.getFirst();
    }

    /**
     * Returns the source count.
     *
     * @return the number of SSRC/CSRC identifiers
     */
    public int sourceCount() {
        return ssrcList.size();
    }
}
