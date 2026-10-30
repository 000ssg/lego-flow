package ssg.legoflow.service.channel;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link ByteTransport} pair for testing protocol implementations without TCP sockets.
 *
 * <p>Creates a pair of connected transports: data sent on one end is received on the
 * other. Uses a {@link BlockingQueue} per direction. No network, no threads, deterministic
 * ordering.
 *
 * <p>{@code receive()} blocks until data is available; it returns -1 only when the pair is
 * closed — a timeout is never an EOF. {@code close()} on either end wakes a blocked peer
 * reader (mirroring a socket close where the peer sees EOF).
 *
 * <p><b>TCP semantics (optional):</b> {@link #withTcpSemantics(TcpSemantics)} enables
 * configurable simulation of TCP-like behavior: chunked delivery, coalescing, backpressure,
 * and async delivery via background daemon threads that move data from outbound to inbound
 * queues (simulating TCP's async send/receive).
 *
 * @since 0.2.0
 */
public class InMemoryByteTransport implements ByteTransport {

    private final BlockingQueue<ByteBuffer> inbound;
    private final BlockingQueue<ByteBuffer> outbound;
    private final AtomicBoolean open = new AtomicBoolean(true);
    protected volatile InMemoryByteTransport peerRef;
    private volatile TcpSemantics semantics = TcpSemantics.DEFAULT;
    private volatile Thread deliveryThread;
    private final AtomicInteger outboundQueueSize = new AtomicInteger(0);

    protected InMemoryByteTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return two connected transports
     */
    public static InMemoryByteTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryByteTransport(q1, q2);
        var b = new InMemoryByteTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryByteTransport[]{a, b};
    }

    /**
     * Configures TCP-like semantics for this transport.
     *
     * @param semantics the semantics to apply, or null to use {@link TcpSemantics#DEFAULT}
     * @return this transport for chaining
     */
    public InMemoryByteTransport withTcpSemantics(TcpSemantics semantics) {
        this.semantics = semantics != null ? semantics : TcpSemantics.DEFAULT;
        return this;
    }

    /**
     * Returns the current TCP semantics configuration.
     */
    public TcpSemantics getTcpSemantics() {
        return semantics;
    }

    /**
     * Returns the current number of chunks in the outbound queue waiting for delivery.
     * Useful for testing async delivery timing.
     */
    public int getOutboundQueueSize() {
        return outboundQueueSize.get();
    }

    /**
     * Waits until all data sent via {@link #send(ByteBuffer)} has been delivered to the peer's
     * inbound queue. Only meaningful with async delivery semantics ({@link TcpSemantics#DEFAULT},
     * {@link TcpSemantics#COALESCING}, {@link TcpSemantics#BACKPRESSURE}).
     *
     * @param timeout maximum time to wait
     * @param unit time unit
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitForDelivery(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (outboundQueueSize.get() > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new InterruptedException("Timed out waiting for delivery");
            Thread.sleep(Math.min(10, TimeUnit.MILLISECONDS.convert(remaining, TimeUnit.NANOSECONDS)));
        }
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        if (semantics == TcpSemantics.NONE) {
            // Raw synchronous semantics: enqueue directly to peer's inbound
            enqueueToPeer(data);
            return;
        }
        // TCP-like semantics: split into chunks and enqueue to outbound queue
        // Background delivery thread will move them to peer's inbound asynchronously
        int maxChunkSize = semantics.chunkSize();
        int remaining = data.remaining();
        int chunks = Math.max(1, (remaining + maxChunkSize - 1) / maxChunkSize);
        for (int i = 0; i < chunks; i++) {
            int chunkSize = Math.min(maxChunkSize, remaining);
            byte[] chunk = new byte[chunkSize];
            data.get(chunk);
            outbound.offer(ByteBuffer.wrap(chunk));
            outboundQueueSize.incrementAndGet();
            remaining -= chunkSize;
        }
        // Start delivery thread if not already running (async semantics)
        if (semantics.asyncDelivery() && deliveryThread == null) {
            startDeliveryThread();
        }
    }

    /**
     * Enqueues data directly to peer's inbound queue (synchronous delivery).
     */
    private void enqueueToPeer(ByteBuffer data) {
        var p = peerRef;
        if (p != null) {
            p.inbound.offer(data);
        }
    }

    /**
     * Starts a daemon thread to asynchronously deliver data from outbound to peer's inbound.
     */
    private synchronized void startDeliveryThread() {
        if (deliveryThread != null) return;
        Thread t = new Thread(() -> {
            try {
                while (open.get()) {
                    ByteBuffer chunk = outbound.poll(100, TimeUnit.MILLISECONDS);
                    if (chunk != null) {
                        var p = peerRef;
                        if (p != null) {
                            p.inbound.offer(chunk);
                        }
                        outboundQueueSize.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "inmemory-delivery-" + System.identityHashCode(this));
        t.setDaemon(true);
        t.start();
        deliveryThread = t;
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
                // Re-queue the tail for the next read (partial read)
                inbound.offer(data);
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
            // Stop delivery thread if running
            stopDeliveryThread();
        }
    }

    /**
     * Stops the async delivery thread.
     */
    private synchronized void stopDeliveryThread() {
        if (deliveryThread != null) {
            deliveryThread.interrupt();
            try {
                deliveryThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            deliveryThread = null;
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    /**
     * TCP-like semantics configuration for in-memory transport.
     *
     * <p>Enables configurable simulation of TCP segmentation, partial reads, backpressure,
     * and async delivery via background threads.
     */
    public static final class TcpSemantics {
        /** No TCP semantics: raw synchronous queue behavior. */
        public static final TcpSemantics NONE = new TcpSemantics(0, Integer.MAX_VALUE, false, false);

        /** Default TCP semantics: chunked delivery (256-byte chunks), async delivery. */
        public static final TcpSemantics DEFAULT = new TcpSemantics(256, Integer.MAX_VALUE, false, true);

        /** Simulates TCP coalescing: up to 16KB per chunk, async delivery. */
        public static final TcpSemantics COALESCING = new TcpSemantics(16384, Integer.MAX_VALUE, false, true);

        /** Simulates backpressure: bounded queue of 4KB, send blocks when full, async delivery. */
        public static final TcpSemantics BACKPRESSURE = new TcpSemantics(256, 4096, true, true);

        private final int chunkSize;
        private final int maxQueueSize;
        private final boolean backpressure;
        private final boolean asyncDelivery;

        TcpSemantics(int chunkSize, int maxQueueSize, boolean backpressure, boolean asyncDelivery) {
            this.chunkSize = chunkSize;
            this.maxQueueSize = maxQueueSize;
            this.backpressure = backpressure;
            this.asyncDelivery = asyncDelivery;
        }

        /** Returns the maximum chunk size for segmentation. */
        public int chunkSize() {
            return chunkSize;
        }

        /** Returns the maximum outbound queue size (0 = unbounded). */
        public int maxQueueSize() {
            return maxQueueSize;
        }

        /** Returns whether backpressure is enabled. */
        public boolean backpressure() {
            return backpressure;
        }

        /** Returns whether async delivery via background thread is enabled. */
        public boolean asyncDelivery() {
            return asyncDelivery;
        }
    }
}
