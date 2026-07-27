package ssg.legoflow.http2.frame;

public enum Http2ErrorCode {

    NO_ERROR(0x0),
    PROTOCOL_ERROR(0x1),
    INTERNAL_ERROR(0x2),
    FLOW_CONTROL_ERROR(0x3),
    SETTINGS_TIMEOUT(0x4),
    STREAM_CLOSED(0x5),
    FRAME_SIZE_ERROR(0x6),
    REFUSED_STREAM(0x7),
    CANCEL(0x8),
    COMPRESSION_ERROR(0x9),
    CONNECT_ERROR(0xa),
    ENHANCE_YOUR_CALM(0xb),
    INADEQUATE_SECURITY(0xc),
    HTTP_1_1_REQUIRED(0xd);

    private final int code;

    Http2ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Http2ErrorCode fromCode(int code) {
        for (Http2ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException("Unknown error code: 0x" + Integer.toHexString(code));
    }
}
