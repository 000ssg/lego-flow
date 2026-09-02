package ssg.legoflow.service.channel;

import ssg.legoflow.service.manager.SelectableChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * {@link DataChannel} backed by a {@link ServerSocketChannel} for TCP server sockets.
 *
 * <p>Wraps the server socket channel and registers it with the
 * {@link SelectableChannelManager} selector for {@link SelectionKey#OP_ACCEPT} events.
 * When accepted, it returns a new {@link TcpDataChannel} for the client connection.
 *
 * @since 0.2.0
 */
public final class ServerDataChannel implements DataChannel {

    private static final Logger LOG = LoggerFactory.getLogger(ServerDataChannel.class);

    private final ServerSocketChannel serverSocketChannel;
    private volatile SelectionKey selectionKey;
    private volatile boolean closed;

    /**
     * Creates a new server data channel.
     *
     * @param serverSocketChannel the underlying server socket (must not be bound yet)
     */
    public ServerDataChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }

    /**
     * Registers this channel with the given selector for accept events.
     *
     * @param selector the selector to register with
     * @return the selection key
     * @throws IOException if registration fails
     */
    public SelectionKey registerWith(Selector selector) throws IOException {
        serverSocketChannel.configureBlocking(false);
        selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        LOG.debug("Registered server channel with selector: {}", selectionKey);
        return selectionKey;
    }

    /**
     * Accepts a new client connection and returns it as a {@link TcpDataChannel}.
     * The returned channel is opened in non-blocking mode.
     *
     * @return the client channel, or null if not ready
     * @throws IOException if accept fails
     */
    public TcpDataChannel accept() throws IOException {
        var clientSocket = serverSocketChannel.accept();
        if (clientSocket == null) return null;
        clientSocket.configureBlocking(false);
        return new TcpDataChannel(clientSocket);
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("Server channel does not support read");
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("Server channel does not support write");
    }

    @Override
    public boolean isOpen() {
        return !closed && serverSocketChannel.isOpen();
    }

    /** Returns true if this channel has been closed. */
    public boolean isClosed() {
        return closed || !serverSocketChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            var key = selectionKey;
            if (key != null) key.cancel();
            serverSocketChannel.close();
        }
    }

    /**
     * Returns the underlying {@link ServerSocketChannel}.
     */
    public ServerSocketChannel getServerSocketChannel() {
        return serverSocketChannel;
    }

    /**
     * Set the selection key (called by {@link SelectableChannelManager} after registration).
     */
    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }
}
