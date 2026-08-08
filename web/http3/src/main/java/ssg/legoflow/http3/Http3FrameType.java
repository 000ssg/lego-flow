package ssg.legoflow.http3;

/**
 * HTTP/3 frame types as defined in RFC 9114 section 7.2.
 *
 * <p>Each frame type has a variable-length integer code used on the wire.
 * HTTP/3 defines fewer frame types than HTTP/2 because many connection-level
 * concerns are handled by QUIC.</p>
 *
 * @since 0.1.0
 */
public enum Http3FrameType {

    /** Carries request or response body data. */
    DATA(0x00),

    /** Carries a compressed header block. */
    HEADERS(0x01),

    /** Cancels a server push. */
    CANCEL_PUSH(0x03),

    /** Conveys configuration parameters. */
    SETTINGS(0x04),

    /** Initiates a server push. */
    PUSH_PROMISE(0x05),

    /** Initiates graceful shutdown. */
    GOAWAY(0x07),

    /** Sets the maximum push ID. */
    MAX_PUSH_ID(0x0D);

    private final long code;

    Http3FrameType(long code) {
        this.code = code;
    }

    /**
     * Returns the wire-format frame type code.
     *
     * @return the frame type code
     * @since 0.1.0
     */
    public long code() {
        return code;
    }

    /**
     * Resolves a wire-format code to the corresponding frame type.
     *
     * @param code the frame type code
     * @return the matching {@code Http3FrameType}
     * @throws IllegalArgumentException if the code is not recognised
     * @since 0.1.0
     */
    public static Http3FrameType fromCode(long code) {
        for (var type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown HTTP/3 frame type: 0x" + Long.toHexString(code));
    }
}
