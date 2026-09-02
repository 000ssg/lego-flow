package ssg.legoflow.messaging.amqp.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory transport for testing the AMQP protocol without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received
 * on the other. Useful for unit testing the invariant core in isolation.
 *
 * @since 0.1.0
 */
public final class InMemoryTransport implements AmqpTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private InMemoryTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return an array of two connected transports
     */
    public static InMemoryTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        return new InMemoryTransport[]{
                new InMemoryTransport(q1, q2),
                new InMemoryTransport(q2, q1)
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
    public int receive(ByteBuffer buffer) {
        // In-memory: use take() — the queue's signal() wakes immediately on offer().
        // With virtual threads, this parks the VT and unparks it as soon as data arrives.
        return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
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
        // Wake up blocked receivers
        inbound.offer(ByteBuffer.allocate(0));
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }
}
