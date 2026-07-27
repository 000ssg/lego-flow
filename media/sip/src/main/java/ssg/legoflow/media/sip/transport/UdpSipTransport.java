package ssg.legoflow.media.sip.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.protocol.SipCodec;
import ssg.legoflow.media.sip.protocol.SipMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP SIP transport using {@link DatagramChannel}.
 *
 * <p>Implements connectionless datagram transport for SIP messages.
 * Each message is sent as a single UDP datagram. Incoming messages
 * are received on a virtual thread.
 *
 * @since 1.0.0
 */
public final class UdpSipTransport implements SipTransport {

    private static final Logger LOG = LoggerFactory.getLogger(UdpSipTransport.class);
    private static final int MAX_UDP_SIZE = 65535;

    private final DatagramChannel channel;
    private final InetSocketAddress localAddress;
    private final AtomicBoolean running;

    /**
     * Creates a UDP transport bound to the specified address.
     *
     * @param bindAddress the local address to bind to
     * @throws IOException if binding fails
     * @since 1.0.0
     */
    public UdpSipTransport(InetSocketAddress bindAddress) throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.bind(bindAddress);
        this.localAddress = (InetSocketAddress) channel.getLocalAddress();
        this.running = new AtomicBoolean(false);
        LOG.info("UDP SIP transport bound to {}", localAddress);
    }

    /**
     * Creates a UDP transport on any available port.
     *
     * @throws IOException if creation fails
     * @since 1.0.0
     */
    public UdpSipTransport() throws IOException {
        this(new InetSocketAddress(0));
    }

    @Override
    public void send(SipMessage message, InetSocketAddress destination) throws IOException {
        byte[] data = SipCodec.encode(message);
        if (data.length > MAX_UDP_SIZE) {
            throw new IOException("SIP message too large for UDP: " + data.length + " bytes");
        }
        channel.send(ByteBuffer.wrap(data), destination);
        LOG.debug("Sent {} bytes to {}", data.length, destination);
    }

    @Override
    public InetSocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public String protocol() {
        return "UDP";
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public void start(SipTransportListener listener) throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Transport already started");
        }

        Thread.ofVirtual().name("sip-udp-receiver").start(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_UDP_SIZE);
            while (running.get()) {
                try {
                    buffer.clear();
                    InetSocketAddress source = (InetSocketAddress) channel.receive(buffer);
                    if (source != null) {
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        try {
                            SipMessage message = SipCodec.decode(data);
                            listener.onMessage(message, source);
                        } catch (Exception e) {
                            LOG.warn("Failed to parse SIP message from {}: {}", source, e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        LOG.error("Error receiving UDP message", e);
                    }
                }
            }
        });
        LOG.info("UDP SIP transport started on {}", localAddress);
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        channel.close();
        LOG.info("UDP SIP transport closed");
    }

    @Override
    public String toString() {
        return "UdpSipTransport[" + localAddress + "]";
    }
}
