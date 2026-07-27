package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketFrameCodec;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * DataFilter that encodes WAMP messages into WebSocket frame bytes or decodes
 * WebSocket frame bytes into WAMP-ready JSON text.
 *
 * <p>In {@link Mode#ENCODE} mode, input ByteBuffers contain JSON-serialized WAMP messages
 * which are wrapped into WebSocket text frames. In {@link Mode#DECODE} mode, input ByteBuffers
 * contain raw WebSocket frame bytes which are decoded to extract the JSON payload.</p>
 *
 * @since 1.0.0
 */
public class WampWebSocketFilter extends AbstractDataFilter<ByteBuffer> {

    private final Mode mode;
    private final WebSocketFrameCodec codec;

    /**
     * Operating mode for the filter.
     */
    public enum Mode {
        /** Wraps WAMP JSON payloads into WebSocket text frame bytes. */
        ENCODE,
        /** Extracts WAMP JSON payloads from WebSocket frame bytes. */
        DECODE
    }

    /**
     * Creates a new filter with the given mode.
     *
     * @param mode encode or decode
     */
    public WampWebSocketFilter(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
        this.codec = new WebSocketFrameCodec(
                mode == Mode.ENCODE
                        ? WebSocketFrameCodec.Mode.ENCODE
                        : WebSocketFrameCodec.Mode.DECODE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        var results = new ByteBuffer[data.length];
        for (int i = 0; i < data.length; i++) {
            results[i] = switch (mode) {
                case ENCODE -> {
                    var payload = data[i].duplicate();
                    var bytes = new byte[payload.remaining()];
                    payload.get(bytes);
                    var frame = WebSocketFrame.text(new String(bytes, StandardCharsets.UTF_8));
                    yield codec.encodeFrame(frame);
                }
                case DECODE -> {
                    var frame = codec.decodeFrame(data[i].duplicate());
                    var text = frame.getPayloadText();
                    yield ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
        return results;
    }

    /**
     * Returns the operating mode.
     *
     * @return the mode
     */
    public Mode getMode() {
        return mode;
    }
}
