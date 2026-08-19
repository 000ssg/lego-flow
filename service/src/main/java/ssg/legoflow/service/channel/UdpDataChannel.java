package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * A {@link DataChannel} implementation wrapping a {@link DatagramChannel} for UDP communication.
 *
 * <p>Supports both connected and unconnected UDP modes. In unconnected mode, use
 * {@link #sendTo(ByteBuffer, SocketAddress)} and {@link #receiveDatagram()} for explicit
 * addressing. In connected mode (after calling {@link #connect(SocketAddress)}),
 * {@link #read(ByteBuffer)} and {@link #write(ByteBuffer)} operate against the connected
 * remote address.
 *
 * <p>This class is thread-safe. All operations on the underlying channel are synchronized
 * where necessary.
 *
 * @since 0.1.0
 */
public class UdpDataChannel implements DataChannel {

    private static final Logger LOG = LoggerFactory.getLogger(UdpDataChannel.class);
    private static final int DEFAULT_MAX_PACKET_SIZE = 65535;

    private final DatagramChannel datagramChannel;
    private volatile SelectionKey selectionKey;
    private final AtomicBoolean bound = new AtomicBoolean(false);
    private final AtomicBoolean connectedUdp = new AtomicBoolean(false);
    private final AtomicInteger maxPacketSize = new AtomicInteger(DEFAULT_MAX_PACKET_SIZE);

    /**
     * Creates a new {@code UdpDataChannel} wrapping the given datagram channel
     * and registering it with the specified selector for {@link SelectionKey#OP_READ}.
     *
     * @param datagramChannel the underlying NIO datagram channel; must be in non-blocking mode
     * @param selector        the NIO selector for event registration
     * @throws IOException          if registration with the selector fails
     * @throws NullPointerException if {@code datagramChannel} or {@code selector} is {@code null}
     * @since 0.1.0
     */
    /**
     * Creates a new {@code UdpDataChannel} with deferred selector registration.
     *
     * <p>The channel is configured for non-blocking I/O but is not registered with
     * any selector. Call {@link #registerWith(Selector)} to complete registration.
     *
     * @param datagramChannel the underlying NIO datagram channel
     * @throws IOException          if configuring non-blocking mode fails
     * @throws NullPointerException if {@code datagramChannel} is {@code null}
     * @since 0.1.0
     */
    public UdpDataChannel(DatagramChannel datagramChannel) throws IOException {
        Objects.requireNonNull(datagramChannel, "datagramChannel must not be null");
        this.datagramChannel = datagramChannel;
        this.datagramChannel.configureBlocking(false);
        this.selectionKey = null;
    }

    public UdpDataChannel(DatagramChannel datagramChannel, Selector selector) throws IOException {
        this(datagramChannel);
        Objects.requireNonNull(selector, "selector must not be null");
        registerWith(selector);
    }

    /**
     * Registers this channel with the given selector for {@link SelectionKey#OP_READ}.
     *
     * @param selector the NIO selector for event registration
     * @return the selection key
     * @throws IOException           if registration fails
     * @throws IllegalStateException if this channel is already registered with a selector
     * @throws NullPointerException  if {@code selector} is {@code null}
     * @since 0.1.0
     */
    public SelectionKey registerWith(Selector selector) throws IOException {
        Objects.requireNonNull(selector, "selector must not be null");
        if (this.selectionKey != null) {
            throw new IllegalStateException("Channel is already registered with a selector");
        }
        this.selectionKey = this.datagramChannel.register(selector, SelectionKey.OP_READ);
        return this.selectionKey;
    }

    /**
     * Reads data from the datagram channel into the given buffer.
     *
     * <p>In connected mode, receives from the connected remote address.
     * In unconnected mode, receives from any sender (sender address is discarded;
     * use {@link #receiveDatagram()} to preserve sender information).
     *
     * @param buffer the buffer to read data into
     * @return the number of bytes read, or 0 if no datagram was available
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    @Override
    public int read(ByteBuffer buffer) throws IOException {
        if (connectedUdp.get()) {
            return datagramChannel.read(buffer);
        }
        var sender = datagramChannel.receive(buffer);
        return sender != null ? buffer.position() : 0;
    }

    /**
     * Writes data from the given buffer to the connected remote address.
     *
     * <p>The channel must be in connected mode (see {@link #connect(SocketAddress)}).
     *
     * @param buffer the buffer containing data to send
     * @return the number of bytes written
     * @throws IOException          if an I/O error occurs
     * @throws IllegalStateException if the channel is not connected
     * @since 0.1.0
     */
    @Override
    public int write(ByteBuffer buffer) throws IOException {
        if (!connectedUdp.get()) {
            throw new IllegalStateException("Channel is not connected; use sendTo() for unconnected UDP");
        }
        return datagramChannel.write(buffer);
    }

    /**
     * Sends a datagram to the specified target address.
     *
     * <p>This method can be used in both connected and unconnected modes.
     *
     * @param data   the buffer containing the datagram payload
     * @param target the destination socket address
     * @return the number of bytes sent
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if {@code data} or {@code target} is {@code null}
     * @since 0.1.0
     */
    public int sendTo(ByteBuffer data, SocketAddress target) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return datagramChannel.send(data, target);
    }

    /**
     * Receives a datagram and returns it with full sender information.
     *
     * <p>Allocates a receive buffer of {@link #getMaxPacketSize()} bytes.
     *
     * @return a {@link DatagramPacketInfo} with sender, data, and timestamp;
     *         or {@code null} if no datagram was available
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public DatagramPacketInfo receiveDatagram() throws IOException {
        var buffer = ByteBuffer.allocate(maxPacketSize.get());
        var sender = datagramChannel.receive(buffer);
        if (sender == null) {
            return null;
        }
        buffer.flip();
        return new DatagramPacketInfo(sender, buffer, System.nanoTime());
    }

    /**
     * Binds this channel's socket to the given local address.
     *
     * @param address the local address to bind to
     * @throws IOException          if an I/O error occurs or the socket is already bound
     * @throws NullPointerException if {@code address} is {@code null}
     * @since 0.1.0
     */
    public void bind(SocketAddress address) throws IOException {
        Objects.requireNonNull(address, "address must not be null");
        datagramChannel.bind(address);
        bound.set(true);
        LOG.debug("UDP channel bound to {}", address);
    }

    /**
     * Connects this channel to the given remote address for connected-mode UDP.
     *
     * <p>After connection, {@link #read(ByteBuffer)} and {@link #write(ByteBuffer)}
     * operate against the connected address, and datagrams from other sources are filtered.
     *
     * @param address the remote address to connect to
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if {@code address} is {@code null}
     * @since 0.1.0
     */
    public void connect(SocketAddress address) throws IOException {
        Objects.requireNonNull(address, "address must not be null");
        datagramChannel.connect(address);
        connectedUdp.set(true);
        LOG.debug("UDP channel connected to {}", address);
    }

    /**
     * Returns the local address this channel's socket is bound to.
     *
     * @return the local address, or {@code null} if unbound
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public SocketAddress getLocalAddress() throws IOException {
        return datagramChannel.getLocalAddress();
    }

    /**
     * Returns the remote address this channel is connected to.
     *
     * @return the remote address, or {@code null} if not connected
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public SocketAddress getRemoteAddress() throws IOException {
        return datagramChannel.getRemoteAddress();
    }

    /**
     * Returns whether this channel's socket is bound to a local address.
     *
     * @return {@code true} if the socket is bound
     * @since 0.1.0
     */
    public boolean isBound() {
        return bound.get();
    }

    /**
     * Returns whether this channel is in connected UDP mode.
     *
     * @return {@code true} if connected to a remote address
     * @since 0.1.0
     */
    public boolean isConnectedUdp() {
        return connectedUdp.get();
    }

    /**
     * Returns the maximum datagram packet size in bytes.
     *
     * @return the maximum packet size
     * @since 0.1.0
     */
    public int getMaxPacketSize() {
        return maxPacketSize.get();
    }

    /**
     * Sets the maximum datagram packet size in bytes.
     *
     * @param size the maximum packet size; must be positive
     * @throws IllegalArgumentException if {@code size} is not positive
     * @since 0.1.0
     */
    public void setMaxPacketSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Max packet size must be positive: " + size);
        }
        maxPacketSize.set(size);
    }

    /**
     * Returns whether the underlying datagram channel is open.
     *
     * @return {@code true} if the channel is open
     * @since 0.1.0
     */
    @Override
    public boolean isOpen() {
        return datagramChannel.isOpen();
    }

    /**
     * Returns the selection key for this channel's selector registration.
     *
     * @return the selection key
     * @since 0.1.0
     */
    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    /**
     * Closes the underlying datagram channel and cancels the selection key.
     *
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    @Override
    public void close() throws IOException {
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        datagramChannel.close();
        connectedUdp.set(false);
        bound.set(false);
        LOG.debug("UDP channel closed");
    }

    /**
     * Returns the underlying NIO {@link DatagramChannel}.
     *
     * @return the datagram channel
     * @since 0.1.0
     */
    public DatagramChannel getDatagramChannel() {
        return datagramChannel;
    }
}
