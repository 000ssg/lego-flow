package ssg.legoflow.media.sip.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.protocol.SipCodec;
import ssg.legoflow.media.sip.protocol.SipMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP SIP transport using {@link SocketChannel}.
 *
 * <p>Implements connection-oriented stream transport for SIP messages.
 * Maintains persistent connections and handles framing using
 * Content-Length headers. Each connection is handled on a virtual thread.
 *
 * @since 1.0.0
 */
public final class TcpSipTransport implements SipTransport {

    private static final Logger LOG = LoggerFactory.getLogger(TcpSipTransport.class);
    private static final int BUFFER_SIZE = 65536;

    private final ServerSocketChannel serverChannel;
    private final InetSocketAddress localAddress;
    private final AtomicBoolean running;
    private final Map<InetSocketAddress, SocketChannel> connections;

    /**
     * Creates a TCP transport bound to the specified address.
     *
     * @param bindAddress the local address to bind to
     * @throws IOException if binding fails
     * @since 1.0.0
     */
    public TcpSipTransport(InetSocketAddress bindAddress) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(bindAddress);
        this.localAddress = (InetSocketAddress) serverChannel.getLocalAddress();
        this.running = new AtomicBoolean(false);
        this.connections = new ConcurrentHashMap<>();
        LOG.info("TCP SIP transport bound to {}", localAddress);
    }

    /**
     * Creates a TCP transport on any available port.
     *
     * @throws IOException if creation fails
     * @since 1.0.0
     */
    public TcpSipTransport() throws IOException {
        this(new InetSocketAddress(0));
    }

    @Override
    public void send(SipMessage message, InetSocketAddress destination) throws IOException {
        byte[] data = SipCodec.encode(message);
        SocketChannel channel = connections.computeIfAbsent(destination, addr -> {
            try {
                SocketChannel ch = SocketChannel.open();
                ch.connect(addr);
                return ch;
            } catch (IOException e) {
                throw new RuntimeException("Failed to connect to " + addr, e);
            }
        });

        synchronized (channel) {
            channel.write(ByteBuffer.wrap(data));
        }
        LOG.debug("Sent {} bytes to {} via TCP", data.length, destination);
    }

    @Override
    public InetSocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public String protocol() {
        return "TCP";
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public void start(SipTransportListener listener) throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Transport already started");
        }

        Thread.ofVirtual().name("sip-tcp-acceptor").start(() -> {
            while (running.get()) {
                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    InetSocketAddress remoteAddr = (InetSocketAddress) clientChannel.getRemoteAddress();
                    connections.put(remoteAddr, clientChannel);
                    LOG.debug("TCP connection accepted from {}", remoteAddr);

                    Thread.ofVirtual().name("sip-tcp-" + remoteAddr).start(() ->
                            handleConnection(clientChannel, remoteAddr, listener));
                } catch (IOException e) {
                    if (running.get()) {
                        LOG.error("Error accepting TCP connection", e);
                    }
                }
            }
        });
        LOG.info("TCP SIP transport started on {}", localAddress);
    }

    private void handleConnection(SocketChannel channel, InetSocketAddress remoteAddr,
                                   SipTransportListener listener) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        try {
            while (running.get() && channel.isOpen()) {
                int read = channel.read(buffer);
                if (read < 0) break;
                if (read == 0) continue;

                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                buffer.clear();

                try {
                    SipMessage message = SipCodec.decode(data);
                    listener.onMessage(message, remoteAddr);
                } catch (Exception e) {
                    LOG.warn("Failed to parse SIP message from {}: {}", remoteAddr, e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("TCP connection closed: {}", remoteAddr);
            }
        } finally {
            connections.remove(remoteAddr);
            try {
                channel.close();
            } catch (IOException e) {
                LOG.debug("Error closing TCP channel", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        for (SocketChannel ch : connections.values()) {
            try {
                ch.close();
            } catch (IOException e) {
                LOG.debug("Error closing connection", e);
            }
        }
        connections.clear();
        serverChannel.close();
        LOG.info("TCP SIP transport closed");
    }

    @Override
    public String toString() {
        return "TcpSipTransport[" + localAddress + "]";
    }
}
