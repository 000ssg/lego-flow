package ssg.legoflow.messaging.amqp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
/**
 * TCP socket adapter implementing {@link AmqpTransport}.
 *
 * <p>Wraps a {@link SocketChannel} to provide the byte-level I/O
 * required by the invariant AMQP core.
 *
 * @since 0.1.0
 */
public final class TcpTransport implements AmqpTransport {

    private static final Logger LOG = LoggerFactory.getLogger(TcpTransport.class);

    private final SocketChannel channel;

    /**
     * Creates a TCP transport wrapping the given socket channel.
     *
     * @param channel the socket channel (must be connected)
     */
    public TcpTransport(SocketChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    @Override
    public void send(ByteBuffer data) {
        try {
            while (data.hasRemaining()) {
                channel.write(data);
            }
        } catch (IOException e) {
            LOG.debug("Error sending data", e);
            close();
        }
    }

    @Override
    public int receive(ByteBuffer buffer) {
        try {
            return channel.read(buffer);
        } catch (IOException e) {
            LOG.debug("Error receiving data", e);
            close();
            return -1;
        }
    }

    @Override
    public void close() {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing channel", e);
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }
}
