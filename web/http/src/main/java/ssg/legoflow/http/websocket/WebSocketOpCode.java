package ssg.legoflow.http.websocket;

public enum WebSocketOpCode {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA);

    private final int code;

    WebSocketOpCode(int code) { this.code = code; }

    public int code() { return code; }

    public boolean isControl() { return code >= 0x8; }

    public static WebSocketOpCode fromCode(int code) {
        for (var op : values()) {
            if (op.code == code) return op;
        }
        throw new IllegalArgumentException("Unknown opcode: " + code);
    }
}
