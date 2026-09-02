package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
/**
 * Syslog message sender using UDP transport (RFC 5426).
 *
 * <p>Each syslog message is sent as a single UDP datagram. Messages that
 * exceed the maximum datagram size may be truncated by the network.
 *
 * @since 0.1.0
 */
public final class UdpSender implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(UdpSender.class);
    /** Default syslog UDP port. */
    public static final int DEFAULT_PORT = 514;

    private final InetSocketAddress target;
    private final DatagramSocket socket;

    /**
     * Creates a UDP sender targeting the given address.
     *
     * @param host the target hostname
     * @param port the target port
     * @throws IOException if the socket cannot be created
     */
    public UdpSender(String host, int port) throws IOException {
        this.target = new InetSocketAddress(host, port);
        this.socket = new DatagramSocket();
        LOG.debug("UDP sender created targeting {}:{}", host, port);
    }

    /**
     * Creates a UDP sender targeting the default syslog port.
     *
     * @param host the target hostname
     * @throws IOException if the socket cannot be created
     */
    public UdpSender(String host) throws IOException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends a syslog message.
     *
     * @param message the message to send
     * @throws IOException if sending fails
     */
    public void send(SyslogMessage message) throws IOException {
        byte[] data = SyslogCodec.encodeToBytes(message);
        var packet = new DatagramPacket(data, data.length, target);
        socket.send(packet);
        LOG.trace("Sent {} bytes to {}", data.length, target);
    }

    @Override
    public void close() {
        socket.close();
        LOG.debug("UDP sender closed");
    }
}
