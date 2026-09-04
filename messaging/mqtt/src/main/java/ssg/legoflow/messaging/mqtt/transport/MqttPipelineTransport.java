package ssg.legoflow.messaging.mqtt.transport;

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
 * {@link MqttTransport} implementation backed by a {@link DataChannel} in the
 * service pipeline.
 *
 * <p>Ring buffer: one writer (onRead/add), one reader (receive/fetch).
 * <p>{@code add()} and {@code fetch()} are synchronized on the buffer.
 * <p>{@code peek()} is a lock-free estimate — good enough for deciding whether to wait.
 *
 * <p>One instance per connection.
 */
public final class MqttPipelineTransport implements MqttTransport {

    private static final Logger LOG = LoggerFactory.getLogger(MqttPipelineTransport.class);
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

    public MqttPipelineTransport(DataChannel channel) {
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
        return count;
    }

    /**
     * Called by the pipeline when the channel is writable.
     * Drains the outbound queue into the channel.
     */
    public void onWrite(DataChannel ch) {
        ByteBuffer buf;
        while ((buf = outboundQueue.poll()) != null) {
            buf.flip();
            try {
                while (buf.hasRemaining()) {
                    if (ch.write(buf) <= 0) break;
                }
            } catch (IOException e) {
                LOG.debug("Write error on channel", e);
                break;
            }
            if (buf.hasRemaining()) {
                buf.flip();
                outboundQueue.offer(buf);
                break;
            }
        }
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        data = data.duplicate();
        outboundQueue.offer(data);
        SelectionKey key = channel.getSelectionKey();
        if (key != null) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;
        if (count > 0) {
            return fetch(buffer);
        }
        try {
            if (!available.tryAcquire(timeout, unit)) return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return fetch(buffer);
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.debug("Error closing channel", e);
            }
            available.release(1); // Wake up any waiting receive()
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public DataChannel getChannel() {
        return channel;
    }
}
