package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketFrameCodec;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * MQTT transport over a WebSocket session.
 *
 * <p>Wraps a {@link WebSocketSession} and translates MQTT byte data to/from
 * WebSocket binary frames (RFC 6455). MQTT data is carried in binary frames;
 * control frames (PING/PONG/CLOSE) are handled at the WebSocket level.
 *
 * <p>Requires an outbound channel ({@link Consumer<ByteBuffer>}) to write
 * encoded WebSocket frames to the wire. The HTTP server layer provides this.
 *
 * @since 0.2.0
 */
public final class MqttWebSocketTransport implements MqttTransport {

    private final WebSocketSession session;
    private final WebSocketFrameCodec codec;
    private final Consumer<ByteBuffer> outbound;
    private final BlockingQueue<ByteBuffer> inbound = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a transport backed by the given WebSocket session.
     *
     * @param session  the WebSocket session (after upgrade)
     * @param outbound the consumer to write encoded WebSocket frames to the wire
     */
    public MqttWebSocketTransport(WebSocketSession session, Consumer<ByteBuffer> outbound) {
        this.session = session;
        this.codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);
        this.outbound = outbound;
        session.onMessage(this::handleBinaryFrame);
        session.onClose(frame -> closed.set(true));
        session.setFrameSender(this::sendEncodedFrame);
    }

    private void handleBinaryFrame(WebSocketFrame frame) {
        if (closed.get() || !session.isOpen()) return;
        var payload = frame.getPayload();
        if (payload.hasRemaining()) {
            inbound.offer(payload.duplicate());
        }
    }

    /** Sends a frame from the WebSocket layer (close handshake, etc.) by encoding and writing to wire. */
    private void sendEncodedFrame(WebSocketFrame frame) {
        if (closed.get()) return;
        var encoded = codec.encodeFrame(frame);
        outbound.accept(encoded);
    }

    @Override
    public void send(ByteBuffer data) {
        if (closed.get() || !data.hasRemaining()) return;
        var frame = WebSocketFrame.binary(data.duplicate());
        var encoded = codec.encodeFrame(frame);
        outbound.accept(encoded);
    }

    @Override
    public int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, 5000, TimeUnit.MILLISECONDS);
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (closed.get()) return -1;
        try {
            var data = inbound.poll(unit.toMillis(timeout), TimeUnit.MILLISECONDS);
            if (data == null) return -1;
            int toCopy = Math.min(buffer.remaining(), data.remaining());
            byte[] chunk = new byte[toCopy];
            data.get(chunk);
            buffer.put(chunk);
            return toCopy;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            session.close();
        }
    }

    @Override
    public boolean isOpen() {
        return !closed.get() && session.isOpen();
    }

    @Override
    public DataChannel getChannel() {
        return null;
    }
}
