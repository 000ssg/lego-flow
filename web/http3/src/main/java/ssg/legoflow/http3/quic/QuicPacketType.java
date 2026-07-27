package ssg.legoflow.http3.quic;

/**
 * QUIC packet types as defined in RFC 9000.
 *
 * <p>Long header packets use {@link #INITIAL}, {@link #ZERO_RTT},
 * {@link #HANDSHAKE}, and {@link #RETRY}. Short header packets
 * use {@link #ONE_RTT} after the handshake completes.</p>
 *
 * @since 1.0.0
 */
public enum QuicPacketType {

    /** Initial packet — used to establish a QUIC connection. */
    INITIAL,

    /** 0-RTT packet — carries early data before handshake completion. */
    ZERO_RTT,

    /** Handshake packet — carries handshake messages. */
    HANDSHAKE,

    /** Retry packet — sent by the server for address validation. */
    RETRY,

    /** 1-RTT packet — short header, used after handshake completion. */
    ONE_RTT
}
