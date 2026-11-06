package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic pipeline transport for production use with any {@link ByteTransport}-based protocol.
 *
 * <p>Wraps a {@link DataChannel} and provides the transport interface for protocol implementations.
 * This eliminates the need for protocol-specific Pipeline implementations.
 *
 * <p>Usage:
 * <pre>
 * {@code
 * DataChannel channel = ...; // obtained from TcpDataChannel or similar
 * PipelineTransport transport = new PipelineTransport(channel);
 * }
 * </pre>
 *
 * @since 0.3.0
 */
public final class PipelineTransport implements ByteTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineTransport.class);

    private final DataChannel channel;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile ExecutorService executor;
    private volatile boolean reading;

    /**
     * Creates a new pipeline transport.
     *
     * @param channel the data channel to wrap
     */
    public PipelineTransport(DataChannel channel) {
        this.channel = channel;
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) {
            return;
        }
        try {
            channel.write(data);
        } catch (IOException e) {
            LOG.warn("Send error: {}", e.getMessage());
            close();
        }
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) {
            return -1;
        }
        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead == 0 && !reading) {
                // Start reading in background if no data available
                startReading();
                return 0;
            }
            return bytesRead;
        } catch (IOException e) {
            LOG.warn("Receive error: {}", e.getMessage());
            close();
            return -1;
        }
    }

    private void startReading() {
        if (reading) {
            return;
        }
        reading = true;
        if (executor == null) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
        }
        executor.submit(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (open.get()) {
                try {
                    int bytesRead = channel.read(buffer);
                    if (bytesRead > 0) {
                        buffer.flip();
                        // Notify waiting receivers
                        buffer.clear();
                    } else if (bytesRead < 0) {
                        break;
                    }
                } catch (IOException e) {
                    LOG.warn("Background read error: {}", e.getMessage());
                    break;
                }
            }
            reading = false;
            close();
        });
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.warn("Close error: {}", e.getMessage());
            }
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open.get() && channel.isOpen();
    }

    /**
     * Returns the underlying data channel.
     */
    public DataChannel getChannel() {
        return channel;
    }
}
