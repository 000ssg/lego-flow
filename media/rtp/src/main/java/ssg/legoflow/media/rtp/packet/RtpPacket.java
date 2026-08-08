package ssg.legoflow.media.rtp.packet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete RTP packet consisting of a header and payload (RFC 3550 Section 5.1).
 *
 * <p>An RTP packet carries a single media frame or fragment. The header
 * identifies the payload type, sequence number, timestamp, and source.
 * The payload contains the actual media data.
 *
 * @param header  the RTP header
 * @param payload the media payload data
 * @since 0.1.0
 */
public record RtpPacket(RtpHeader header, byte[] payload) {

    /**
     * Creates an RTP packet with validation.
     */
    public RtpPacket {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    /**
     * Creates an RTP packet with minimal parameters.
     *
     * @param payloadType    the payload type number
     * @param sequenceNumber the sequence number
     * @param timestamp      the RTP timestamp
     * @param ssrc           the synchronization source
     * @param payload        the media payload
     * @return the RTP packet
     */
    public static RtpPacket of(int payloadType, int sequenceNumber, long timestamp,
                                long ssrc, byte[] payload) {
        var header = new RtpHeader(
                RtpHeader.VERSION_2, false, false, false,
                payloadType, sequenceNumber, timestamp, ssrc,
                List.of(), Optional.empty()
        );
        return new RtpPacket(header, payload);
    }

    /**
     * Creates an RTP packet with the marker bit set.
     *
     * @param payloadType    the payload type number
     * @param sequenceNumber the sequence number
     * @param timestamp      the RTP timestamp
     * @param ssrc           the synchronization source
     * @param payload        the media payload
     * @return the RTP packet with marker set
     */
    public static RtpPacket withMarker(int payloadType, int sequenceNumber, long timestamp,
                                        long ssrc, byte[] payload) {
        var header = new RtpHeader(
                RtpHeader.VERSION_2, false, false, true,
                payloadType, sequenceNumber, timestamp, ssrc,
                List.of(), Optional.empty()
        );
        return new RtpPacket(header, payload);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Returns the total packet size in bytes (header + payload).
     *
     * @return the total size
     */
    public int totalSize() {
        return header.totalSize() + payload.length;
    }

    /**
     * Returns the payload size in bytes.
     *
     * @return the payload size
     */
    public int payloadSize() {
        return payload.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RtpPacket that)) return false;
        return header.equals(that.header) && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return 31 * header.hashCode() + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "RtpPacket[pt=%d, seq=%d, ts=%d, ssrc=0x%08X, payload=%d bytes]"
                .formatted(header.payloadType(), header.sequenceNumber(),
                        header.timestamp(), header.ssrc(), payload.length);
    }
}
