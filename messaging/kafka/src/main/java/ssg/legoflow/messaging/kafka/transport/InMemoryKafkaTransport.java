package ssg.legoflow.messaging.kafka.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory {@link KafkaTransport} pair for testing the Kafka protocol without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received on the
 * other. Uses a {@link BlockingQueue} per direction. No network, no threads, deterministic
 * ordering — the same shape as {@code InMemoryTransport} in stomp/mqtt/amqp.
 *
 * <p>Byte-level semantics: a {@code send} may be larger than the peer's read buffer; the
 * remainder stays at the <b>head</b> of the stream so subsequent {@code receive} calls finish
 * it <i>before</i> any later send — preserving byte order exactly like a real TCP byte stream.
 * (Re-queueing the tail would rotate it behind newer sends and corrupt framing.) A
 * {@code receive} blocks until data is available; it returns -1 only when the pair is closed
 * or the deadline passes — a timeout is never an EOF. {@code close()} on either end wakes a
 * blocked peer reader (mirroring a socket close where the peer sees buffered data, then EOF).
 *
 * @since 0.1.0
 */
public final class InMemoryKafkaTransport implements KafkaTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile InMemoryKafkaTransport peerRef;

    /**
     * A partially-read buffer held at the head of the stream. When a {@code receive} consumes
     * only part of a sent buffer, the remainder is kept here (not re-queued) so it is drained
     * before any later send — preserving byte-stream order. Confined to this transport's
     * reader thread; each instance has a single reader.
     */
    private ByteBuffer headBuffer;

    private InMemoryKafkaTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return two connected transports
     */
    public static InMemoryKafkaTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryKafkaTransport(q1, q2);
        var b = new InMemoryKafkaTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryKafkaTransport[]{a, b};
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
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            ByteBuffer data = headBuffer;
            if (data == null || !data.hasRemaining()) {
                // No (partially-read) head buffer: advance to the next queued buffer.
                headBuffer = null;
                data = inbound.poll();
                if (data == null) {
                    if (!open.get()) return -1; // closed and nothing buffered => EOF
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) return -1; // timed out
                    try {
                        data = inbound.poll(remaining, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return -1;
                    }
                    if (data == null) continue; // poll timed out; loop re-checks deadline/open
                }
                if (!data.hasRemaining()) return -1; // poison marker => closed
                headBuffer = data;
            }
            // Read up to the caller's capacity from the head buffer. Any remainder stays in
            // headBuffer (NOT re-queued) so the byte stream keeps its order.
            int n = Math.min(buffer.remaining(), data.remaining());
            int origLimit = data.limit();
            data.limit(data.position() + n);
            buffer.put(data);
            data.limit(origLimit);
            return n;
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            // Wake this side and the peer's blocked reader. Buffered bytes ahead of the poison
            // marker are still delivered before the EOF is observed.
            inbound.offer(ByteBuffer.allocate(0));
            var p = peerRef;
            if (p != null) {
                p.open.set(false);
                p.inbound.offer(ByteBuffer.allocate(0));
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }
}
