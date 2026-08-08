package ssg.legoflow.http3.quic;

/**
 * QUIC frame types with their type codes as defined in RFC 9000.
 *
 * <p>Each frame type has a unique code used in the wire format.
 * The {@link #fromCode(int)} method resolves a type code back to
 * the corresponding enum constant.</p>
 *
 * @since 0.1.0
 */
public enum QuicFrameType {

    /** Padding frame — has no semantic value. */
    PADDING(0x00),

    /** Ping frame — tests reachability and keeps the connection alive. */
    PING(0x01),

    /** ACK frame — acknowledges received packets. */
    ACK(0x02),

    /** Reset Stream frame — abruptly terminates the sending part of a stream. */
    RESET_STREAM(0x04),

    /** Stop Sending frame — requests that a peer cease transmission on a stream. */
    STOP_SENDING(0x05),

    /** Crypto frame — carries cryptographic handshake messages. */
    CRYPTO(0x06),

    /** New Token frame — provides a token for future connection attempts. */
    NEW_TOKEN(0x07),

    /** Stream frame — carries stream data. */
    STREAM(0x08),

    /** Max Data frame — indicates the maximum amount of data that can be sent on the connection. */
    MAX_DATA(0x10),

    /** Max Stream Data frame — indicates the maximum data for a specific stream. */
    MAX_STREAM_DATA(0x11),

    /** Max Streams (Bidirectional) frame — limits the number of bidirectional streams. */
    MAX_STREAMS_BIDI(0x12),

    /** Max Streams (Unidirectional) frame — limits the number of unidirectional streams. */
    MAX_STREAMS_UNI(0x13),

    /** Data Blocked frame — indicates the connection is blocked by flow control. */
    DATA_BLOCKED(0x14),

    /** Stream Data Blocked frame — indicates a stream is blocked by flow control. */
    STREAM_DATA_BLOCKED(0x15),

    /** Streams Blocked (Bidirectional) frame — indicates the bidirectional stream limit was reached. */
    STREAMS_BLOCKED_BIDI(0x16),

    /** Streams Blocked (Unidirectional) frame — indicates the unidirectional stream limit was reached. */
    STREAMS_BLOCKED_UNI(0x17),

    /** New Connection ID frame — provides alternative connection IDs. */
    NEW_CONNECTION_ID(0x18),

    /** Retire Connection ID frame — requests retirement of a connection ID. */
    RETIRE_CONNECTION_ID(0x19),

    /** Path Challenge frame — checks reachability of a path. */
    PATH_CHALLENGE(0x1a),

    /** Path Response frame — responds to a path challenge. */
    PATH_RESPONSE(0x1b),

    /** Connection Close frame — signals that the connection is being closed. */
    CONNECTION_CLOSE(0x1c),

    /** Handshake Done frame — signals handshake completion (server to client). */
    HANDSHAKE_DONE(0x1e);

    private final int code;

    QuicFrameType(int code) {
        this.code = code;
    }

    /**
     * Returns the wire-format type code for this frame type.
     *
     * @return the type code per RFC 9000
     * @since 0.1.0
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a wire-format type code to the corresponding enum constant.
     *
     * @param code the type code from the wire format
     * @return the matching {@code QuicFrameType}
     * @throws IllegalArgumentException if the code is not recognised
     * @since 0.1.0
     */
    public static QuicFrameType fromCode(int code) {
        for (var type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown QUIC frame type code: 0x" + Integer.toHexString(code));
    }
}
