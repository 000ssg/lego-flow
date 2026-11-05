package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;
import ssg.legoflow.service.channel.ByteTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * {@link ByteTransport} implementation backed by a {@link DataChannel} in the service pipeline.
 *
 * <p>Extends {@link PipelineByteTransport} with event-driven callback support.
 * When {@link #onReadWithCallback} is set, incoming data is immediately forwarded
 * to the callback instead of being queued in the ring buffer.
 *
 * @since 0.3.0
 */
public final class PipelineTransport extends PipelineByteTransport implements ByteTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineTransport.class);

    private volatile Consumer<ByteBuffer> readCallback;

    public PipelineTransport(DataChannel channel) {
        super(channel);
    }

    /**
     * Sets a callback to receive incoming data immediately.
     *
     * <p>When set, {@link #onRead} will forward data to this callback instead of
     * queuing it in the ring buffer. The callback is invoked from the selector thread.
     *
     * <p>Clear the callback with {@link #clearReadCallback()} to return to normal operation.
     *
     * @param callback consumer for incoming data
     */
    public void onReadWithCallback(Consumer<ByteBuffer> callback) {
        this.readCallback = callback;
    }

    /**
     * Clears the read callback, returning to normal ring buffer operation.
     */
    public void clearReadCallback() {
        this.readCallback = null;
    }

    /**
     * Called by the pipeline when data arrives from the channel.
     * If a callback is set, forwards data immediately; otherwise uses normal ring buffer.
     */
    @Override
    public void onRead(DataChannel ch, ByteBuffer data) {
        Consumer<ByteBuffer> callback = this.readCallback;
        if (callback != null && data != null && data.hasRemaining()) {
            callback.accept(data.duplicate());
        } else {
            super.onRead(ch, data);
        }
    }
}
