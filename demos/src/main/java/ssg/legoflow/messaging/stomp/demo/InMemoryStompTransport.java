package ssg.legoflow.messaging.stomp.demo;

import ssg.legoflow.messaging.stomp.transport.StompTransport;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory byte-level STOMP transport for testing and demos.
 *
 * <p>Uses a pair of blocking queues to connect two endpoints. Bytes sent by one
 * side are received by the other, enabling transport-agnostic testing of the
 * STOMP protocol without any network I/O. Mirrors the module's
 * {@code ssg.legoflow.messaging.stomp.transport.InMemoryTransport}.
 *
 * @since 0.1.0
 */
public final class InMemoryStompTransport implements StompTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private InMemoryStompTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return an array of two connected transports: [client-side, server-side]
     */
    public static InMemoryStompTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        return new InMemoryStompTransport[]{
                new InMemoryStompTransport(q1, q2),
                new InMemoryStompTransport(q2, q1)
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
        inbound.offer(ByteBuffer.allocate(0));
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }
}
