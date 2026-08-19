package ssg.legoflow.messaging.stomp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.messaging.stomp.core.StompCodec;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
/**
 * STOMP transport over WebSocket text frames.
 *
 * <p>Each WebSocket text frame carries exactly one STOMP frame (the NULL terminator
 * may be omitted as WebSocket provides its own message boundaries). This adapter
 * serializes outgoing STOMP frames to text and deserializes incoming text frames.
 *
 * @since 0.1.0
 */
public class WebSocketStompTransport implements StompTransport {

    private final WebSocketSession session;
    private final BlockingQueue<StompFrame> incomingQueue = new LinkedBlockingQueue<>();
    private volatile Consumer<WebSocketFrame> frameSink;
    private volatile boolean open = true;

    /**
     * Creates a new STOMP transport adapter for the given WebSocket session.
     *
     * @param session the WebSocket session
     */
    public WebSocketStompTransport(WebSocketSession session) {
        this.session = session;
        session.onMessage(frame -> {
            if (open) {
                var stompFrame = StompCodec.decodeFromString(frame.getPayloadText());
                incomingQueue.offer(stompFrame);
            }
        });
        session.onClose(frame -> open = false);
    }

    /**
     * Registers a consumer that receives outgoing WebSocket frames.
     *
     * @param sink the frame consumer
     */
    public void onFrame(Consumer<WebSocketFrame> sink) {
        this.frameSink = sink;
    }

    @Override
    public void send(StompFrame frame) {
        if (!open) throw new IllegalStateException("Transport is closed");
        var text = StompCodec.encodeToString(frame);
        var wsFrame = WebSocketFrame.text(text);
        var sink = this.frameSink;
        if (sink != null) {
            sink.accept(wsFrame);
        }
    }

    /**
     * Injects a WebSocket frame as if it was received from the network.
     *
     * @param frame the frame to inject
     */
    public void injectFrame(WebSocketFrame frame) {
        session.handleFrame(frame);
    }

    @Override
    public StompFrame receive() {
        try {
            return incomingQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for STOMP frame", e);
        }
    }

    /**
     * Non-blocking receive: returns {@code null} if no frame is available.
     *
     * @return the next frame, or null
     */
    public StompFrame tryReceive() {
        return incomingQueue.poll();
    }

    @Override
    public void close() {
        open = false;
        session.close();
    }

    @Override
    public boolean isOpen() {
        return open && session.isOpen();
    }

    /**
     * Returns the underlying WebSocket session.
     *
     * @return the session
     */
    public WebSocketSession getSession() {
        return session;
    }
}
