package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link AmqpTransport} implementation backed by a {@link DataChannel} in the
 * service pipeline.
 *
 * <p>Ring buffer: one writer (onRead/add), one reader (receive/fetch).
 * <p>{@code add()} and {@code fetch()} are synchronized on the buffer.
 * <p>{@code peek()} is a lock-free estimate — good enough for deciding whether to wait.
 *
 * <p>One instance per connection.
 */
public final class PipelineTransport implements AmqpTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineTransport.class);
    private static final int BUFFER_SIZE = 65536;

    private final DataChannel channel;

    // Ring buffer state
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int start;   // first available byte
    private int end;     // next write position
    private volatile int count;   // bytes in buffer (volatile for peek visibility)
    private final Semaphore available = new Semaphore(0);

    // Outbound queue — prevents data loss under backpressure
    private final LinkedBlockingQueue<ByteBuffer> outboundQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);

    public PipelineTransport(DataChannel channel) {
        this.channel = channel;
    }

    /**
     * Called by the pipeline when data arrives from the channel.
     * Appends bytes to the ring buffer and signals the consumer.
     */
    public void onRead(DataChannel ch, ByteBuffer data) {
        if (!open.get()) return;
        int n = add(data);
        if (n > 0) available.release(1);
    }

    /**
     * Add bytes from the source buffer into the ring buffer.
     * Atomic: updates start/end/count under a single lock.
     * @return number of bytes added
     */
    public synchronized int add(ByteBuffer src) {
        int n = src.remaining();
        if (n == 0) return 0;

        // Compact if not enough room
        if (count + n > buffer.length) {
            if (count > 0) {
                System.arraycopy(buffer, start, buffer, 0, count);
                end = count;
                start = 0;
            }
            if (count + n > buffer.length) {
                LOG.warn("Buffer overflow: {} + {} > {}", count, n, buffer.length);
                return 0;
            }
        }

        // Copy into buffer, handling wrap
        int first = Math.min(n, buffer.length - end);
        src.get(buffer, end, first);
        end = (end + first) % buffer.length;
        if (first < n) {
            src.get(buffer, 0, n - first);
            end = n - first;
        }
        count += n;
        return n;
    }

    /**
     * Fetch up to dst.remaining() bytes from the ring buffer.
     * Atomic: updates start/count under a single lock.
     * @return number of bytes fetched
     */
    public synchronized int fetch(ByteBuffer dst) {
        if (count == 0) return 0;
        int n = Math.min(count, dst.remaining());

        // Copy out, handling wrap
        int first = Math.min(n, buffer.length - start);
        dst.put(buffer, start, first);
        if (first < n) {
            dst.put(buffer, 0, n - first);
        }
        start = (start + n) % buffer.length;
        count -= n;
        return n;
    }

    /**
     * Lock-free estimate of bytes available for reading.
     * Used by receive() to decide whether to wait on the semaphore.
     * May be slightly stale by the time fetch() runs — that's fine,
     * fetch() will just return 0 and we wait again.
     */
    public int peek() {
        return count; // volatile not needed for single-writer, but count is volatile enough for estimate
    }

    /**
     * Called by the pipeline when the channel is writable.
     * Drains the outbound queue, writing each buffer in order.
     */
    public void onWrite(DataChannel ch) {
        if (!open.get()) return;
        ByteBuffer buf;
        while ((buf = outboundQueue.poll()) != null) {
            if (!buf.hasRemaining()) continue;
            try {
                channel.write(buf);
            } catch (IOException e) {
                LOG.warn("Outbound flush failed", e);
                close();
                return;
            }
            // If buffer still has data, re-queue it
            if (buf.hasRemaining()) {
                outboundQueue.offer(buf);
                break; // Wait for next writable event
            }
        }
        registerOps();
    }

    private void registerOps() {
        var key = channel.getSelectionKey();
        if (key == null) return;
        try {
            int ops = SelectionKey.OP_READ;
            if (!outboundQueue.isEmpty()) ops |= SelectionKey.OP_WRITE;
            key.interestOps(ops);
        } catch (Exception e) {
            LOG.debug("Failed to update interest ops", e);
        }
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        var slice = data.slice();
        // Try immediate write first — avoids waking selector for the common case
        try {
            int written = channel.write(slice);
            if (written == slice.remaining()) {
                return; // All data written immediately
            }
        } catch (IOException e) {
            LOG.debug("Immediate write failed: {}", e.getMessage());
        }
        // Enqueue remaining data — never drops frames
        outboundQueue.offer(slice);
        registerOps();
    }

    @Override
    public int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, 5, TimeUnit.SECONDS);
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        int fetched = 0;

        while (buffer.hasRemaining()) {
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) return fetched > 0 ? fetched : -1;

            // Fast path: data already in buffer
            if (peek() >= buffer.remaining()) {
                int n = fetch(buffer);
                fetched += n;
                if (n == 0) break;
                continue;
            }

            // Wait for data
            try {
                if (!available.tryAcquire(1, remainingMs, TimeUnit.MILLISECONDS)) {
                    return fetched > 0 ? fetched : -1;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fetched > 0 ? fetched : -1;
            }

            // Data arrived — fetch it
            int n = fetch(buffer);
            fetched += n;
            if (n == 0) continue; // semaphore false positive — retry
        }

        return fetched;
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) return;
        outboundQueue.clear();
        try {
            channel.close();
        } catch (IOException e) {
            LOG.debug("Error closing channel", e);
        }
    }

    @Override
    public boolean isOpen() {
        return open.get() && channel.isOpen();
    }

    public DataChannel getChannel() {
        return channel;
    }
}
