package ssg.legoflow.http3;

/**
 * HTTP/3 error codes as defined in RFC 9114 section 8 and RFC 9204.
 *
 * <p>These error codes are used in QUIC CONNECTION_CLOSE and
 * RESET_STREAM frames to indicate HTTP/3-specific errors.</p>
 *
 * @since 0.1.0
 */
public enum Http3ErrorCode {

    /** No error; graceful shutdown. */
    H3_NO_ERROR(0x100),

    /** Peer violated protocol requirements. */
    H3_GENERAL_PROTOCOL_ERROR(0x101),

    /** An internal error occurred. */
    H3_INTERNAL_ERROR(0x102),

    /** A stream was created in violation of limits. */
    H3_STREAM_CREATION_ERROR(0x103),

    /** A required critical stream was closed. */
    H3_CLOSED_CRITICAL_STREAM(0x104),

    /** A frame was received on a stream where it is not permitted. */
    H3_FRAME_UNEXPECTED(0x105),

    /** A frame failed to satisfy layout or size requirements. */
    H3_FRAME_ERROR(0x106),

    /** The peer generated excessive load. */
    H3_EXCESSIVE_LOAD(0x107),

    /** A stream ID was used incorrectly. */
    H3_ID_ERROR(0x108),

    /** A SETTINGS frame contained an invalid value. */
    H3_SETTINGS_ERROR(0x109),

    /** No SETTINGS frame was received at the beginning of the control stream. */
    H3_MISSING_SETTINGS(0x10A),

    /** A request was rejected without processing. */
    H3_REQUEST_REJECTED(0x10B),

    /** A request was cancelled. */
    H3_REQUEST_CANCELLED(0x10C),

    /** The request stream was terminated before completion. */
    H3_REQUEST_INCOMPLETE(0x10D),

    /** An HTTP message was malformed. */
    H3_MESSAGE_ERROR(0x10E),

    /** The TCP connection established in response to CONNECT failed. */
    H3_CONNECT_ERROR(0x10F),

    /** The requested operation requires HTTP version fallback. */
    H3_VERSION_FALLBACK(0x110),

    /** QPACK decompression of a header block failed. */
    QPACK_DECOMPRESSION_FAILED(0x200),

    /** An error occurred on the QPACK encoder stream. */
    QPACK_ENCODER_STREAM_ERROR(0x201),

    /** An error occurred on the QPACK decoder stream. */
    QPACK_DECODER_STREAM_ERROR(0x202);

    private final long code;

    Http3ErrorCode(long code) {
        this.code = code;
    }

    /**
     * Returns the wire-format error code.
     *
     * @return the error code
     * @since 0.1.0
     */
    public long code() {
        return code;
    }

    /**
     * Resolves a wire-format error code to the corresponding enum constant.
     *
     * @param code the error code from the wire format
     * @return the matching {@code Http3ErrorCode}
     * @throws IllegalArgumentException if the code is not recognised
     * @since 0.1.0
     */
    public static Http3ErrorCode fromCode(long code) {
        for (var error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        throw new IllegalArgumentException("Unknown HTTP/3 error code: 0x" + Long.toHexString(code));
    }
}
