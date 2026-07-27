package ssg.legoflow.http3.quic;

/**
 * QUIC transport error codes as defined in RFC 9000 section 20.
 *
 * <p>These error codes are carried in CONNECTION_CLOSE frames to indicate
 * the reason for closing a connection.</p>
 *
 * @since 1.0.0
 */
public enum QuicErrorCode {

    /** No error — used for graceful connection closure. */
    NO_ERROR(0x00),

    /** Implementation encountered an internal error. */
    INTERNAL_ERROR(0x01),

    /** Server refused the connection. */
    CONNECTION_REFUSED(0x02),

    /** Flow control limits were violated. */
    FLOW_CONTROL_ERROR(0x03),

    /** The maximum stream limit was exceeded. */
    STREAM_LIMIT_ERROR(0x04),

    /** A frame was received in an invalid stream state. */
    STREAM_STATE_ERROR(0x05),

    /** The final size of a stream changed. */
    FINAL_SIZE_ERROR(0x06),

    /** A frame was malformed or otherwise invalid. */
    FRAME_ENCODING_ERROR(0x07),

    /** Transport parameters were invalid. */
    TRANSPORT_PARAMETER_ERROR(0x08),

    /** Too many connection IDs were issued. */
    CONNECTION_ID_LIMIT_ERROR(0x09),

    /** A generic protocol violation was detected. */
    PROTOCOL_VIOLATION(0x0a),

    /** A received token was invalid. */
    INVALID_TOKEN(0x0b),

    /** Application-specific error. */
    APPLICATION_ERROR(0x0c),

    /** The CRYPTO frame data buffer overflowed. */
    CRYPTO_BUFFER_EXCEEDED(0x0d),

    /** An error occurred during a key update. */
    KEY_UPDATE_ERROR(0x0e),

    /** The AEAD usage limit was reached. */
    AEAD_LIMIT_REACHED(0x0f),

    /** No viable network path exists. */
    NO_VIABLE_PATH(0x10);

    private final int code;

    QuicErrorCode(int code) {
        this.code = code;
    }

    /**
     * Returns the wire-format error code.
     *
     * @return the error code per RFC 9000
     * @since 1.0.0
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a wire-format error code to the corresponding enum constant.
     *
     * @param code the error code from the wire format
     * @return the matching {@code QuicErrorCode}
     * @throws IllegalArgumentException if the code is not recognised
     * @since 1.0.0
     */
    public static QuicErrorCode fromCode(int code) {
        for (var error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        throw new IllegalArgumentException("Unknown QUIC error code: 0x" + Integer.toHexString(code));
    }
}
