package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link AmqpTransport} implementation backed by a {@link DataChannel} in the
 * service pipeline.
 *
 * <p>This bridge allows blocking protocol code (AMQP client/container) to work
 * with the non-blocking NIO pipeline. The selector-driven {@link ssg.legoflow.service.channel.ProcessingThread}
 * reads bytes from the socket and delivers them via {@link #onRead(DataChannel, ByteBuffer)}.
 * The protocol thread calls {@link #receive(ByteBuffer)} which blocks until inbound
 * data is available in the buffer. Outbound writes are queued and flushed when the
 * selector fires {@link SelectionKey#OP_WRITE}.
 *
 * <p>One instance per connection. Not thread-safe for concurrent receivers, but
 * designed for the single-protocol-thread-per-connection model.
 *
 * @since 0.2.0
 */
public final class PipelineTransport implements AmqpTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineTransport.class);

    private final DataChannel channel;
    private final Queue<ByteBuffer> inboundFragments = new ConcurrentLinkedQueue<>();
    private final Queue<ByteBuffer> outboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicReference<ByteBuffer> outboundBuffer = new AtomicReference<>(null);
    private final Semaphore dataAvailable = new Semaphore(0);

    /**
     * Creates a pipeline transport for the given channel.
     *
     * @param channel the data channel (registered with SelectableChannelManager)
     */
    public PipelineTransport(DataChannel channel) {
        this.channel = channel;
    }

    /**
     * Called by the pipeline when data is read from the channel.
     * The bytes are buffered for {@link #receive(ByteBuffer)}.
     */
    public void onRead(DataChannel ch, ByteBuffer data) {
        if (!open.get()) return;
        data.flip();
        inboundFragments.offer(data);
        LOG.debug("Inbound: {} bytes buffered (queue size={})", data.remaining(), inboundFragments.size());
        dataAvailable.release();
    }

    /**
     * Called by the pipeline when the channel is writable.
     * Flushes the outbound buffer to the channel.
     */
    public void onWrite(DataChannel ch) {
        if (!open.get()) return;
        var buf = outboundBuffer.get();
        if (buf != null && buf.hasRemaining()) {
            try {
                int written = channel.write(buf);
                LOG.debug("Flushed {} bytes outbound", written);
                if (!buf.hasRemaining()) {
                    outboundBuffer.set(null);
                }
            } catch (IOException e) {
                LOG.warn("Outbound flush failed", e);
                close();
                return;
            }
        }
        if (!outboundQueue.isEmpty()) {
            var next = outboundQueue.poll();
            if (next != null) {
                outboundBuffer.set(next);
                try {
                    int written = channel.write(next);
                    LOG.debug("Flushed {} bytes outbound from queue", written);
                    if (!next.hasRemaining()) {
                        // Drain remaining items
                        while (!outboundQueue.isEmpty() && outboundBuffer.get() != null) {
                            var item = outboundQueue.poll();
                            if (item != null && item.hasRemaining()) {
                                outboundBuffer.set(item);
                                break;
                            }
                        }
                        if (outboundBuffer.get() == null && outboundQueue.isEmpty()) {
                            registerOps();
                        }
                    }
                } catch (IOException e) {
                    LOG.warn("Outbound queue flush failed", e);
                    close();
                }
            }
        }
        registerOps();
    }

    private void registerOps() {
        var key = channel.getSelectionKey();
        if (key == null) return;
        try {
            int ops = SelectionKey.OP_READ;
            if (outboundBuffer.get() != null || !outboundQueue.isEmpty()) {
                ops |= SelectionKey.OP_WRITE;
            }
            key.interestOps(ops);
        } catch (Exception e) {
            LOG.debug("Failed to update interest ops", e);
        }
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        var slice = data.slice();
        if (outboundBuffer.compareAndSet(null, slice)) {
            try {
                int written = channel.write(slice);
                LOG.debug("Immediate write: {} bytes", written);
                if (!slice.hasRemaining()) {
                    outboundBuffer.set(null);
                    registerOps();
                    return;
                }
            } catch (IOException e) {
                LOG.debug("Immediate write blocked, queuing", e);
            }
            registerOps();
        } else {
            outboundQueue.offer(slice);
            registerOps();
        }
    }

    @Override
    public int receive(ByteBuffer buffer) {
        if (!open.get() || !channel.isOpen()) return -1;

        // Try buffered data first (pipeline-driven)
        drainBuffer(buffer);
        if (buffer.position() > 0) return buffer.position();

        // Try direct read from channel — works for both blocking sockets (client on
        // virtual thread) and non-blocking sockets (server, where 0 means wait for selector).
        try {
            int n = channel.read(buffer);
            if (n > 0) return n;
            if (n < 0) {
                close();
                return -1;
            }
            // n == 0: non-blocking channel, no data yet — wait for selector callback
        } catch (IOException e) {
            LOG.debug("Receive read failed: {}", e.getMessage());
            close();
            return -1;
        }

        // Wait for pipeline to deliver data via onRead()
        try {
            if (!dataAvailable.tryAcquire(5, TimeUnit.SECONDS)) {
                if (!open.get()) return -1;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }

        // Drain any data that arrived while waiting
        drainBuffer(buffer);
        if (buffer.position() > 0) return buffer.position();

        return 0;
    }

    private void drainBuffer(ByteBuffer buffer) {
        while (buffer.hasRemaining() && !inboundFragments.isEmpty()) {
            var fragment = inboundFragments.peek();
            if (fragment == null || !fragment.hasRemaining()) {
                inboundFragments.poll();
                continue;
            }
            int toCopy = Math.min(buffer.remaining(), fragment.remaining());
            for (int i = 0; i < toCopy; i++) {
                buffer.put(fragment.get());
            }
            if (!fragment.hasRemaining()) {
                inboundFragments.poll();
            }
        }
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) return;
        try {
            channel.close();
        } catch (IOException e) {
            LOG.debug("Error closing channel", e);
        }
        inboundFragments.clear();
        outboundQueue.clear();
        outboundBuffer.set(null);
    }

    @Override
    public boolean isOpen() {
        return open.get() && channel.isOpen();
    }

    /** Returns the underlying data channel. */
    public DataChannel getChannel() {
        return channel;
    }
}
