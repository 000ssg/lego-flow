package ssg.legoflow.media.rtp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Objects;

/**
 * UDP transport for paired RTP/RTCP channels (RFC 3550 Section 11).
 *
 * <p>Manages two adjacent UDP ports: the RTP port (even) and the RTCP
 * port (RTP port + 1). Uses non-blocking {@link DatagramChannel} for
 * efficient I/O with virtual threads.
 *
 * @since 0.1.0
 */
public final class RtpTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RtpTransport.class);

    /** Maximum UDP datagram size for RTP. */
    public static final int MAX_PACKET_SIZE = 1500;

    private final DatagramChannel rtpChannel;
    private final DatagramChannel rtcpChannel;
    private final int rtpPort;
    private final int rtcpPort;
    private volatile boolean closed;

    /**
     * Creates an RTP transport bound to the specified port pair.
     *
     * <p>The RTP port must be even; the RTCP port is RTP port + 1.
     *
     * @param bindAddress the local address to bind to
     * @param rtpPort     the RTP port (must be even)
     * @return a new RTP transport
     * @throws IOException              if binding fails
     * @throws IllegalArgumentException if the RTP port is odd
     */
    public static RtpTransport bind(InetSocketAddress bindAddress, int rtpPort) throws IOException {
        if (rtpPort % 2 != 0) {
            throw new IllegalArgumentException("RTP port must be even: " + rtpPort);
        }

        var rtpChannel = DatagramChannel.open();
        var rtcpChannel = DatagramChannel.open();
        try {
            rtpChannel.bind(new InetSocketAddress(bindAddress.getAddress(), rtpPort));
            rtcpChannel.bind(new InetSocketAddress(bindAddress.getAddress(), rtpPort + 1));
            LOG.info("RTP transport bound to {}:{}/{}", bindAddress.getAddress(),
                    rtpPort, rtpPort + 1);
            return new RtpTransport(rtpChannel, rtcpChannel, rtpPort);
        } catch (IOException e) {
            rtpChannel.close();
            rtcpChannel.close();
            throw e;
        }
    }

    /**
     * Creates an RTP transport with pre-configured channels.
     *
     * @param rtpChannel  the RTP datagram channel
     * @param rtcpChannel the RTCP datagram channel
     * @param rtpPort     the RTP port number
     */
    public RtpTransport(DatagramChannel rtpChannel, DatagramChannel rtcpChannel, int rtpPort) {
        this.rtpChannel = Objects.requireNonNull(rtpChannel, "rtpChannel");
        this.rtcpChannel = Objects.requireNonNull(rtcpChannel, "rtcpChannel");
        this.rtpPort = rtpPort;
        this.rtcpPort = rtpPort + 1;
    }

    /**
     * Sends an RTP packet to the specified destination.
     *
     * @param data        the encoded RTP packet data
     * @param destination the destination address
     * @throws IOException if sending fails
     */
    public void sendRtp(ByteBuffer data, SocketAddress destination) throws IOException {
        checkNotClosed();
        rtpChannel.send(data, destination);
    }

    /**
     * Sends an RTCP packet to the specified destination.
     *
     * @param data        the encoded RTCP packet data
     * @param destination the destination address
     * @throws IOException if sending fails
     */
    public void sendRtcp(ByteBuffer data, SocketAddress destination) throws IOException {
        checkNotClosed();
        rtcpChannel.send(data, destination);
    }

    /**
     * Receives an RTP packet.
     *
     * @param buffer the buffer to receive into
     * @return the source address of the sender
     * @throws IOException if receiving fails
     */
    public SocketAddress receiveRtp(ByteBuffer buffer) throws IOException {
        checkNotClosed();
        return rtpChannel.receive(buffer);
    }

    /**
     * Receives an RTCP packet.
     *
     * @param buffer the buffer to receive into
     * @return the source address of the sender
     * @throws IOException if receiving fails
     */
    public SocketAddress receiveRtcp(ByteBuffer buffer) throws IOException {
        checkNotClosed();
        return rtcpChannel.receive(buffer);
    }

    /**
     * Returns the RTP datagram channel.
     *
     * @return the RTP channel
     */
    public DatagramChannel rtpChannel() {
        return rtpChannel;
    }

    /**
     * Returns the RTCP datagram channel.
     *
     * @return the RTCP channel
     */
    public DatagramChannel rtcpChannel() {
        return rtcpChannel;
    }

    /**
     * Returns the RTP port number.
     *
     * @return the RTP port
     */
    public int rtpPort() {
        return rtpPort;
    }

    /**
     * Returns the RTCP port number.
     *
     * @return the RTCP port (RTP port + 1)
     */
    public int rtcpPort() {
        return rtcpPort;
    }

    /**
     * Returns whether this transport is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        LOG.info("Closing RTP transport on ports {}/{}", rtpPort, rtcpPort);
        try {
            rtpChannel.close();
        } finally {
            rtcpChannel.close();
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("RTP transport is closed");
        }
    }
}
