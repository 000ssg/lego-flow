package ssg.legoflow.http.websocket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WebSocketFrame {

    private final boolean fin;
    private final WebSocketOpCode opCode;
    private final boolean masked;
    private final ByteBuffer payload;
    private final ByteBuffer maskKey;

    public WebSocketFrame(boolean fin, WebSocketOpCode opCode, boolean masked, ByteBuffer payload, ByteBuffer maskKey) {
        this.fin = fin;
        this.opCode = opCode;
        this.masked = masked;
        this.payload = payload != null ? copyBuffer(payload) : ByteBuffer.allocate(0);
        this.maskKey = maskKey != null ? copyBuffer(maskKey) : null;
    }

    private static ByteBuffer copyBuffer(ByteBuffer src) {
        var dup = src.duplicate();
        var copy = ByteBuffer.allocate(dup.remaining());
        copy.put(dup);
        copy.flip();
        return copy;
    }

    public static WebSocketFrame text(String text) {
        return new WebSocketFrame(true, WebSocketOpCode.TEXT, false,
                ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)), null);
    }

    public static WebSocketFrame binary(byte[] data) {
        return new WebSocketFrame(true, WebSocketOpCode.BINARY, false,
                ByteBuffer.wrap(data), null);
    }

    public static WebSocketFrame binary(ByteBuffer data) {
        return new WebSocketFrame(true, WebSocketOpCode.BINARY, false, data, null);
    }

    public static WebSocketFrame close() {
        return new WebSocketFrame(true, WebSocketOpCode.CLOSE, false,
                ByteBuffer.allocate(0), null);
    }

    /**
     * Creates a close frame with a status code per RFC 6455 §7.4.
     *
     * @param code   the close status code
     * @param reason the close reason string
     * @return a close frame with encoded status code and reason
     * @since 1.0.0
     */
    public static WebSocketFrame close(int code, String reason) {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        ByteBuffer payload = ByteBuffer.allocate(2 + reasonBytes.length);
        payload.putShort((short) code);
        payload.put(reasonBytes);
        payload.flip();
        return new WebSocketFrame(true, WebSocketOpCode.CLOSE, false, payload, null);
    }

    /**
     * Creates a close frame with a WebSocketCloseCode.
     *
     * @param closeCode the close code enum
     * @return a close frame with encoded status code and default reason
     * @since 1.0.0
     */
    public static WebSocketFrame close(WebSocketCloseCode closeCode) {
        return close(closeCode.code(), closeCode.reason());
    }

    /**
     * Extracts the close status code from a close frame payload.
     *
     * @return the close code, or -1 if the payload is too short
     * @since 1.0.0
     */
    public int getCloseCode() {
        if (opCode != WebSocketOpCode.CLOSE || payload.remaining() < 2) {
            return -1;
        }
        var dup = payload.duplicate();
        return dup.getShort() & 0xFFFF;
    }

    /**
     * Extracts the close reason string from a close frame payload.
     *
     * @return the close reason, or empty string if no reason
     * @since 1.0.0
     */
    public String getCloseReason() {
        if (opCode != WebSocketOpCode.CLOSE || payload.remaining() <= 2) {
            return "";
        }
        var dup = payload.duplicate();
        dup.getShort(); // skip code
        byte[] reasonBytes = new byte[dup.remaining()];
        dup.get(reasonBytes);
        return new String(reasonBytes, StandardCharsets.UTF_8);
    }

    public static WebSocketFrame ping(byte[] data) {
        return new WebSocketFrame(true, WebSocketOpCode.PING, false,
                ByteBuffer.wrap(data), null);
    }

    public static WebSocketFrame pong(byte[] data) {
        return new WebSocketFrame(true, WebSocketOpCode.PONG, false,
                ByteBuffer.wrap(data), null);
    }

    public boolean isFin() { return fin; }
    public WebSocketOpCode getOpCode() { return opCode; }
    public boolean isMasked() { return masked; }

    public ByteBuffer getPayload() { return payload.asReadOnlyBuffer(); }

    public ByteBuffer getMaskKey() { return maskKey != null ? maskKey.asReadOnlyBuffer() : null; }

    public String getPayloadText() {
        var buf = payload.duplicate();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int getPayloadLength() { return payload.remaining(); }
}
