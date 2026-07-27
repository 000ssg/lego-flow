package ssg.legoflow.media.common.sdp;

/**
 * SDP transport protocols as defined in RFC 4566 and related RFCs.
 *
 * @since 1.0.0
 */
public enum TransportProtocol {

    /** RTP/AVP — RTP Profile for Audio and Video (RFC 3551). */
    RTP_AVP("RTP/AVP"),

    /** RTP/SAVP — Secure RTP Profile (RFC 3711). */
    RTP_SAVP("RTP/SAVP"),

    /** RTP/AVPF — Extended RTP Profile with RTCP-based Feedback (RFC 4585). */
    RTP_AVPF("RTP/AVPF"),

    /** RTP/SAVPF — Extended Secure RTP Profile (RFC 5124). */
    RTP_SAVPF("RTP/SAVPF"),

    /** UDP transport. */
    UDP("udp"),

    /** TCP transport. */
    TCP("TCP"),

    /** TCP/RTP/AVP — RTP over TCP. */
    TCP_RTP_AVP("TCP/RTP/AVP");

    private final String token;

    TransportProtocol(String token) {
        this.token = token;
    }

    /**
     * Returns the SDP token for this transport protocol.
     *
     * @return the SDP protocol token
     */
    public String token() {
        return token;
    }

    /**
     * Parses a transport protocol token (case-insensitive).
     *
     * @param token the SDP transport protocol token
     * @return the matching transport protocol
     * @throws IllegalArgumentException if the token is unknown
     */
    public static TransportProtocol fromToken(String token) {
        for (TransportProtocol tp : values()) {
            if (tp.token.equalsIgnoreCase(token)) {
                return tp;
            }
        }
        throw new IllegalArgumentException("Unknown transport protocol: " + token);
    }

    @Override
    public String toString() {
        return token;
    }
}
