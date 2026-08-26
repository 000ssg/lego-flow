package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
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
     * <p>The channel must be connected before sending data. When the channel
     * is created from {@link SocketChannel#open()}, call {@link #ensureConnected()}
     * before sending.
     *
     * @param channel the socket channel
     */
    public TcpTransport(SocketChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    /**
     * Ensures the socket is fully connected. After {@link SocketChannel#connect()},
     * this must be called before any send/receive to complete the handshake.
     */
    public void ensureConnected() throws IOException {
        if (!channel.isConnected() && channel.isConnectionPending()) {
            channel.finishConnect();
        }
    }

    @Override
    public void send(ByteBuffer data) {
        try {
            while (data.hasRemaining()) {
                channel.write(data);
            }
        } catch (IOException e) {
            LOG.warn("Error sending data", e);
            close();
            throw new AmqpException(AmqpError.CONNECTION_FORCED, "Send failed: " + e.getMessage());
        }
    }

    @Override
    public int receive(ByteBuffer buffer) {
        try {
            int n = channel.read(buffer);
            return n;
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
