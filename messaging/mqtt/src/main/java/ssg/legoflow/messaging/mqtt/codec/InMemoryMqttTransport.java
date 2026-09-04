package ssg.legoflow.messaging.mqtt.codec;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory MQTT transport for testing without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received
 * on the other. Useful for unit testing the codec and protocol logic in isolation.
 *
 * @since 0.2.0
 */
public final class InMemoryMqttTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private InMemoryMqttTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return an array of two connected transports
     */
    public static InMemoryMqttTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        return new InMemoryMqttTransport[]{
                new InMemoryMqttTransport(q1, q2),
                new InMemoryMqttTransport(q2, q1)
        };
    }

    /**
     * Sends raw bytes through this transport.
     */
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        var copy = ByteBuffer.allocate(data.remaining());
        copy.put(data);
        copy.flip();
        outbound.offer(copy);
    }

    /**
     * Receives raw bytes, blocking until data is available.
     *
     * @param buffer the buffer to read into
     * @return bytes read, or -1 if closed
     */
    public int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Receives raw bytes with timeout.
     *
     * @param buffer  the buffer to read into
     * @param timeout how long to wait
     * @param unit    timeout unit
     * @return bytes read, or -1 if closed or timed out
     */
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;
        try {
            ByteBuffer data = inbound.poll(timeout, unit);
            if (data == null || !open.get()) return -1;
            if (!data.hasRemaining()) return -1; // Close signal
            int count = Math.min(buffer.remaining(), data.remaining());
            int limit = data.limit();
            data.limit(data.position() + count);
            buffer.put(data);
            data.limit(limit);
            if (data.hasRemaining()) {
                inbound.offer(data);
            }
            return count;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * Closes this transport.
     */
    public void close() {
        open.set(false);
        inbound.offer(ByteBuffer.allocate(0));
    }

    /**
     * Returns whether this transport is open.
     */
    public boolean isOpen() {
        return open.get();
    }
}
