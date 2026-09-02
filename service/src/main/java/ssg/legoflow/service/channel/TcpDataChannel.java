package ssg.legoflow.service.channel;

import ssg.legoflow.service.manager.SelectableChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * {@link DataChannel} backed by a {@link SocketChannel} for TCP connections.
 *
 * <p>Wraps the socket channel and registers it with the {@link SelectableChannelManager}
 * selector. Designed for use in the async I/O pipeline: the selector wakes on
 * readable/writable/connectable events, and {@link ProcessingThread} dispatches
 * them to the {@link ChannelPipeline}.
 *
 * <p>The channel is opened in non-blocking mode and connected asynchronously.
 * After {@link #connect(String, int)} is called, the connection completes when
 * the selector fires the connectable event.
 *
 * @since 0.2.0
 */
public final class TcpDataChannel implements DataChannel {

    private static final Logger LOG = LoggerFactory.getLogger(TcpDataChannel.class);

    private final SocketChannel socketChannel;
    private volatile SelectionKey selectionKey;
    private volatile boolean closed;

    /**
     * Creates a new TCP data channel.
     *
     * @param socketChannel the underlying socket (must be in non-blocking mode)
     */
    public TcpDataChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    /**
     * Registers this channel with the given selector.
     *
     * @param selector the selector to register with
     * @return the selection key
     * @throws IOException if registration fails
     */
    public SelectionKey registerWith(Selector selector) throws IOException {
        selectionKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
        LOG.debug("Registered TCP channel with selector: {}", selectionKey);
        return selectionKey;
    }

    /**
     * Initiates an asynchronous TCP connection.
     *
     * @param host the remote host
     * @param port the remote port
     * @throws IOException if the connection cannot be initiated
     */
    public void connect(String host, int port) throws IOException {
        boolean connected = socketChannel.connect(new java.net.InetSocketAddress(host, port));
        if (!connected) {
            // Registration happens externally. The selector will fire the connectable event.
        }
    }

    /**
     * Completes the connection after the selector fires the connectable event.
     *
     * @throws IOException if the connection failed
     */
    public void finishConnect() throws IOException {
        socketChannel.finishConnect();
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
        if (closed) throw new IOException("Channel closed");
        return socketChannel.read(buffer);
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        if (closed) throw new IOException("Channel closed");
        return socketChannel.write(buffer);
    }

    @Override
    public boolean isOpen() {
        return !closed && socketChannel.isOpen() && socketChannel.isConnected();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            var key = selectionKey;
            if (key != null) key.cancel();
            socketChannel.close();
        }
    }

    /**
     * Returns the underlying {@link SocketChannel}.
     */
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    /**
     * Set the selection key (called by {@link SelectableChannelManager} after registration).
     */
    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }
}
