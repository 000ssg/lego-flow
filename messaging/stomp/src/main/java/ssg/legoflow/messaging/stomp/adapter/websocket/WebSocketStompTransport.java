package ssg.legoflow.messaging.stomp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.messaging.stomp.core.StompCodec;
import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.transport.StompTransport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * STOMP transport over WebSocket text frames.
 *
 * <p>Implements byte-level {@link StompTransport} backed by WebSocket text frames.
 * Each WebSocket text frame carries raw STOMP frame bytes. The caller (broker/client)
 * uses {@link ssg.legoflow.messaging.stomp.transport.StompFrameCodec} on top for
 * frame-level operations.
 *
 * <p>WebSocket provides message boundaries, so the NULL terminator is optional.
 * Use {@link StompFrameCodec} with {@code strictNull=false} when using this transport.
 */
public class WebSocketStompTransport implements StompTransport {

    private final WebSocketSession session;
    private final BlockingQueue<ByteBuffer> incomingQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile Consumer<WebSocketFrame> frameSink;

    /**
     * Creates a new STOMP transport adapter for the given WebSocket session.
     *
     * @param session the WebSocket session
     */
    public WebSocketStompTransport(WebSocketSession session) {
        this.session = session;
        session.onMessage(frame -> {
            if (open.get()) {
                String text = frame.getPayloadText();
                ByteBuffer buf = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                incomingQueue.offer(buf);
            }
        });
        session.onClose(frame -> open.set(false));
    }

    /**
     * Registers a consumer that receives outgoing WebSocket frames.
     *
     * @param sink the frame consumer
     */
    public void onFrame(Consumer<WebSocketFrame> sink) {
        this.frameSink = sink;
    }

    /**
     * Sends raw bytes through this transport. The bytes are sent as a single
     * WebSocket text frame.
     */
    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) throw new IllegalStateException("Transport is closed");
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        String text = new String(bytes, StandardCharsets.UTF_8);
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
    public int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;
        try {
            ByteBuffer data = incomingQueue.poll(timeout, unit);
            if (data == null || !open.get()) return -1;
            if (!data.hasRemaining()) return -1;
            data.flip();
            int count = Math.min(buffer.remaining(), data.remaining());
            buffer.put(data);
            return count;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void close() {
        open.set(false);
        session.close();
    }

    @Override
    public boolean isOpen() {
        return open.get() && session.isOpen();
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
