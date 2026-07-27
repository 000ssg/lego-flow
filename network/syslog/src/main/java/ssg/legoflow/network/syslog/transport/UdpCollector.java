package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Syslog message collector using UDP transport (RFC 5426).
 *
 * <p>Listens on a UDP port and delivers received syslog messages to a
 * registered handler. Each datagram is expected to contain exactly one
 * syslog message.
 *
 * @since 1.0.0
 */
public final class UdpCollector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(UdpCollector.class);
    private static final int MAX_DATAGRAM_SIZE = 65535;

    private final DatagramSocket socket;
    private volatile boolean running;
    private Thread listenerThread;

    /**
     * Creates a UDP collector bound to the given port.
     *
     * @param port the port to listen on
     * @throws IOException if binding fails
     */
    public UdpCollector(int port) throws IOException {
        this.socket = new DatagramSocket(port);
        LOG.debug("UDP collector bound to port {}", port);
    }

    /**
     * Creates a UDP collector bound to the given address and port.
     *
     * @param address the bind address
     * @throws IOException if binding fails
     */
    public UdpCollector(InetSocketAddress address) throws IOException {
        this.socket = new DatagramSocket(address);
        LOG.debug("UDP collector bound to {}", address);
    }

    /**
     * Returns the local port this collector is listening on.
     *
     * @return the local port
     */
    public int localPort() {
        return socket.getLocalPort();
    }

    /**
     * Starts listening for syslog messages and delivering them to the handler.
     *
     * <p>This method starts a virtual thread that reads datagrams.
     * Call {@link #close()} to stop.
     *
     * @param handler the message handler
     */
    public void start(Consumer<SyslogMessage> handler) {
        if (running) {
            throw new IllegalStateException("Collector already running");
        }
        running = true;
        listenerThread = Thread.ofVirtual().name("syslog-udp-collector").start(() -> {
            byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
            while (running) {
                try {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String text = new String(packet.getData(), packet.getOffset(),
                            packet.getLength(), StandardCharsets.UTF_8);
                    SyslogMessage msg = SyslogCodec.decode(text);
                    handler.accept(msg);
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Error receiving datagram", e);
                    }
                } catch (Exception e) {
                    LOG.warn("Error processing syslog message", e);
                }
            }
        });
        LOG.info("UDP collector started on port {}", socket.getLocalPort());
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        LOG.debug("UDP collector closed");
    }
}
