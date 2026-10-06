package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory MQTT transport for testing without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received
 * on the other. Implements {@link MqttTransport} so it can be passed directly
 * to {@code MqttBroker.handleConnection()}.</p>
 *
 * @since 0.2.0
 */
public final class InMemoryMqttTransport implements MqttTransport {

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

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        var copy = ByteBuffer.allocate(data.remaining());
        copy.put(data);
        copy.flip();
        outbound.offer(copy);
    }

    @Override
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

    @Override
    public void close() {
        open.set(false);
        inbound.offer(ByteBuffer.allocate(0));
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public DataChannel getChannel() {
        return null;
    }
}
