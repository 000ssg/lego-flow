package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Implements {@link WampTransport} over WebSocket text frames.
 * Uses {@link WampSerializer} to convert between WAMP messages and JSON text,
 * then wraps them in {@link WebSocketFrame} instances.
 *
 * <p>Outgoing WAMP messages are serialized to JSON and forwarded to a configurable
 * frame sink (e.g. the connection layer). Incoming WebSocket text frames are
 * deserialized and queued for consumption via {@link #receive()}.</p>
 *
 * @since 0.1.0
 */
public class WebSocketWampTransport implements WampTransport {

    private final WebSocketSession session;
    private final WampSerializer serializer;
    private final BlockingQueue<WampMessage> incomingQueue = new LinkedBlockingQueue<>();
    private volatile Consumer<WebSocketFrame> frameSink;
    private volatile boolean open = true;

    /**
     * Creates a new transport adapter for the given WebSocket session.
     *
     * @param session    the WebSocket session to wrap
     * @param serializer the WAMP message serializer
     */
    public WebSocketWampTransport(WebSocketSession session, WampSerializer serializer) {
        this.session = session;
        this.serializer = serializer;
        session.onMessage(frame -> {
            if (open) {
                var msg = serializer.deserialize(frame.getPayloadText());
                incomingQueue.offer(msg);
            }
        });
        session.onClose(frame -> open = false);
    }

    /**
     * Registers a consumer that receives outgoing WebSocket frames.
     * This bridges the WAMP transport layer to the underlying WebSocket connection.
     *
     * @param sink the frame consumer
     */
    public void onFrame(Consumer<WebSocketFrame> sink) {
        this.frameSink = sink;
    }

    @Override
    public void send(WampMessage msg) {
        if (!open) throw new IllegalStateException("Transport is closed");
        var json = serializer.serialize(msg);
        var frame = WebSocketFrame.text(json);
        var sink = this.frameSink;
        if (sink != null) {
            sink.accept(frame);
        }
    }

    /**
     * Injects a WebSocket frame into this transport as if it was received from the network.
     * Useful for testing and for connection layers that manage frame I/O externally.
     *
     * @param frame the frame to inject
     */
    public void injectFrame(WebSocketFrame frame) {
        session.handleFrame(frame);
    }

    @Override
    public WampMessage receive() {
        try {
            return incomingQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for WAMP message", e);
        }
    }

    /**
     * Non-blocking receive: returns {@code null} if no message is available.
     *
     * @return the next message, or null
     */
    public WampMessage tryReceive() {
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

    /**
     * Returns the serializer used by this transport.
     *
     * @return the serializer
     */
    public WampSerializer getSerializer() {
        return serializer;
    }
}
