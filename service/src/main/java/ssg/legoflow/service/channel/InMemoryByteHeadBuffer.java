package ssg.legoflow.service.channel;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory byte transport for byte-stream semantics (Kafka, NATS, XMPP).
 *
 * <p>Unlike {@link InMemoryByteTransport}, partial reads are held at the <b>head</b>
 * of the stream (not re-queued) so subsequent receives drain them before any later send —
 * preserving byte order exactly like a real TCP byte stream.
 *
 * <p>{@code receive()} blocks until data is available; it returns -1 only when the pair is
 * closed — a timeout is never an EOF. {@code close()} on either end wakes a blocked peer
 * reader (mirroring a socket close where the peer sees EOF).
 *
 * @since 0.2.0
 */
public class InMemoryByteHeadBuffer implements ByteTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile InMemoryByteHeadBuffer peerRef;

    /**
     * A partially-read buffer held at the head of the stream. When a {@code receive} consumes
     * only part of a sent buffer, the remainder is kept here (not re-queued) so it is drained
     * before any later send — preserving byte-stream order. Confined to this transport's
     * reader thread; each instance has a single reader.
     */
    private ByteBuffer headBuffer;

    public InMemoryByteHeadBuffer(BlockingQueue<ByteBuffer> inbound,
                                  BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Sets the peer transport for pair-based close signaling.
     */
    protected void setPeer(InMemoryByteHeadBuffer peer) {
        this.peerRef = peer;
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
