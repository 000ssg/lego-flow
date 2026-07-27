package ssg.legoflow.http2.frame;

public enum Http2FrameType {

    DATA(0x0),
    HEADERS(0x1),
    PRIORITY(0x2),
    RST_STREAM(0x3),
    SETTINGS(0x4),
    PUSH_PROMISE(0x5),
    PING(0x6),
    GOAWAY(0x7),
    WINDOW_UPDATE(0x8),
    CONTINUATION(0x9);

    private final int code;

    Http2FrameType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Http2FrameType fromCode(int code) {
        return switch (code) {
            case 0x0 -> DATA;
            case 0x1 -> HEADERS;
            case 0x2 -> PRIORITY;
            case 0x3 -> RST_STREAM;
            case 0x4 -> SETTINGS;
            case 0x5 -> PUSH_PROMISE;
            case 0x6 -> PING;
            case 0x7 -> GOAWAY;
            case 0x8 -> WINDOW_UPDATE;
            case 0x9 -> CONTINUATION;
            default -> throw new IllegalArgumentException("Unknown frame type: 0x" + Integer.toHexString(code));
        };
    }
}
