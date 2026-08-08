package ssg.legoflow.network.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import ssg.legoflow.network.syslog.transport.TcpCollector;
import ssg.legoflow.network.syslog.transport.UdpCollector;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * High-level syslog collector that can listen on multiple transports.
 *
 * <p>Aggregates UDP and TCP collectors to receive syslog messages from
 * various sources and deliver them to a single handler.
 *
 * <p>Usage example:
 * <pre>{@code
 * var collector = SyslogCollector.builder()
 *     .udp(514)
 *     .tcp(514)
 *     .build();
 * collector.start(msg -> System.out.println(msg));
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SyslogCollector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SyslogCollector.class);

    private final UdpCollector udpCollector;
    private final TcpCollector tcpCollector;

    private SyslogCollector(UdpCollector udpCollector, TcpCollector tcpCollector) {
        this.udpCollector = udpCollector;
        this.tcpCollector = tcpCollector;
    }

    /**
     * Creates a collector listening on UDP only.
     *
     * @param port the UDP port
     * @return the collector
     * @throws IOException if binding fails
     */
    public static SyslogCollector udp(int port) throws IOException {
        return new SyslogCollector(new UdpCollector(port), null);
    }

    /**
     * Creates a collector listening on TCP only.
     *
     * @param port the TCP port
     * @return the collector
     * @throws IOException if binding fails
     */
    public static SyslogCollector tcp(int port) throws IOException {
        return new SyslogCollector(null, new TcpCollector(port));
    }

    /**
     * Returns a new builder for constructing a collector.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts listening for messages on all configured transports.
     *
     * @param handler the message handler
     */
    public void start(Consumer<SyslogMessage> handler) {
        if (udpCollector != null) {
            udpCollector.start(handler);
        }
        if (tcpCollector != null) {
            tcpCollector.start(handler);
        }
        LOG.info("Syslog collector started");
    }

    /**
     * Returns the UDP collector's local port, or -1 if no UDP collector.
     *
     * @return the UDP port
     */
    public int udpPort() {
        return udpCollector != null ? udpCollector.localPort() : -1;
    }

    /**
     * Returns the TCP collector's local port, or -1 if no TCP collector.
     *
     * @return the TCP port
     */
    public int tcpPort() {
        return tcpCollector != null ? tcpCollector.localPort() : -1;
    }

    @Override
    public void close() throws IOException {
        if (udpCollector != null) {
            udpCollector.close();
        }
        if (tcpCollector != null) {
            tcpCollector.close();
        }
        LOG.debug("Syslog collector closed");
    }

    /**
     * Builder for constructing syslog collectors.
     */
    public static final class Builder {
        private int udpPort = -1;
        private int tcpPort = -1;

        private Builder() {}

        /**
         * Adds UDP transport on the given port.
         *
         * @param port the UDP port
         * @return this builder
         */
        public Builder udp(int port) {
            this.udpPort = port;
            return this;
        }

        /**
         * Adds TCP transport on the given port.
         *
         * @param port the TCP port
         * @return this builder
         */
        public Builder tcp(int port) {
            this.tcpPort = port;
            return this;
        }

        /**
         * Builds the collector.
         *
         * @return the configured collector
         * @throws IOException if binding fails
         */
        public SyslogCollector build() throws IOException {
            UdpCollector udp = udpPort >= 0 ? new UdpCollector(udpPort) : null;
            TcpCollector tcp = tcpPort >= 0 ? new TcpCollector(tcpPort) : null;
            if (udp == null && tcp == null) {
                throw new IllegalStateException("At least one transport must be configured");
            }
            return new SyslogCollector(udp, tcp);
        }
    }
}
