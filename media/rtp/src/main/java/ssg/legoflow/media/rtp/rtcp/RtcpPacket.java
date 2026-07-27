package ssg.legoflow.media.rtp.rtcp;

/**
 * Sealed interface for all RTCP packet types (RFC 3550 Section 6).
 *
 * <p>RTCP provides out-of-band control information for an RTP session.
 * Packet types include Sender Reports, Receiver Reports, Source Descriptions,
 * Goodbye, and Application-Defined packets.
 *
 * @since 1.0.0
 */
public sealed interface RtcpPacket
        permits SenderReport, ReceiverReport, SourceDescription, Goodbye, ApplicationDefined {

    /** RTCP packet type for Sender Report. */
    int PT_SR = 200;

    /** RTCP packet type for Receiver Report. */
    int PT_RR = 201;

    /** RTCP packet type for Source Description. */
    int PT_SDES = 202;

    /** RTCP packet type for Goodbye. */
    int PT_BYE = 203;

    /** RTCP packet type for Application-Defined. */
    int PT_APP = 204;

    /**
     * Returns the RTCP packet type number.
     *
     * @return the packet type (200-204)
     */
    int packetType();

    /**
     * Returns the SSRC/CSRC of the sender of this packet.
     *
     * @return the SSRC
     */
    long ssrc();
}
