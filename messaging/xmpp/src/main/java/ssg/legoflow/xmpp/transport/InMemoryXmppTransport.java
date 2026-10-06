package ssg.legoflow.xmpp.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory {@link XmppTransport} pair for testing the XMPP protocol without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received on the
 * other. Uses a {@link BlockingQueue} per direction. No network, no threads, deterministic
 * ordering — the same shape as the NATS in-memory transport.
 *
 * <p>{@code receive()} blocks until data is available; it returns -1 only when the pair is
 * closed — a timeout is never an EOF. {@code close()} on either end wakes a blocked peer
 * reader (mirroring a socket close where the peer sees EOF).
 *
 * @since 0.1.0
 */
public final class InMemoryXmppTransport implements XmppTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile InMemoryXmppTransport peerRef;

    private InMemoryXmppTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return two connected transports
     */
    public static InMemoryXmppTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryXmppTransport(q1, q2);
        var b = new InMemoryXmppTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryXmppTransport[]{a, b};
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
            // Grab queued data first — even after the pair is closed, buffered bytes are
            // still readable (mirrors a socket delivering buffered data before EOF).
            ByteBuffer data = inbound.poll();
            if (data == null) {
                if (!open.get()) return -1; // closed and queue empty => EOF
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
            int n = Math.min(buffer.remaining(), data.remaining());
            int limit = data.limit();
            data.limit(data.position() + n);
            buffer.put(data);
            data.limit(limit);
            if (data.hasRemaining()) {
                inbound.offer(data); // re-queue the tail for the next read
            }
            return n;
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            // Wake this side and the peer's blocked reader.
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
