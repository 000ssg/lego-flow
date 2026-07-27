package ssg.legoflow.network.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.Facility;
import ssg.legoflow.network.syslog.protocol.Severity;
import ssg.legoflow.network.syslog.protocol.StructuredData;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import ssg.legoflow.network.syslog.transport.FramingMode;
import ssg.legoflow.network.syslog.transport.TcpSender;
import ssg.legoflow.network.syslog.transport.TlsSender;
import ssg.legoflow.network.syslog.transport.UdpSender;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * High-level syslog sender that abstracts transport details.
 *
 * <p>Provides a convenient API for sending syslog messages over UDP, TCP,
 * or TLS transports. Automatically populates common message fields
 * (timestamp, hostname, appName).
 *
 * <p>Usage example:
 * <pre>{@code
 * try (var sender = SyslogSender.udp("syslog.example.com")) {
 *     sender.send(Facility.DAEMON, Severity.INFO, "Service started");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class SyslogSender implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SyslogSender.class);

    private final Transport transport;
    private final String hostname;
    private final String appName;

    /**
     * Transport abstraction for sending encoded messages.
     */
    private sealed interface Transport extends AutoCloseable
            permits UdpTransport, TcpTransport, TlsTransport {
        void send(SyslogMessage message) throws IOException;
    }

    private record UdpTransport(UdpSender sender) implements Transport {
        @Override public void send(SyslogMessage msg) throws IOException { sender.send(msg); }
        @Override public void close() { sender.close(); }
    }

    private record TcpTransport(TcpSender sender) implements Transport {
        @Override public void send(SyslogMessage msg) throws IOException { sender.send(msg); }
        @Override public void close() throws IOException { sender.close(); }
    }

    private record TlsTransport(TlsSender sender) implements Transport {
        @Override public void send(SyslogMessage msg) throws IOException { sender.send(msg); }
        @Override public void close() throws IOException { sender.close(); }
    }

    private SyslogSender(Transport transport, String hostname, String appName) {
        this.transport = transport;
        this.hostname = hostname;
        this.appName = appName;
    }

    /**
     * Creates a sender using UDP transport.
     *
     * @param host the target host
     * @return the sender
     * @throws IOException if creation fails
     */
    public static SyslogSender udp(String host) throws IOException {
        return udp(host, UdpSender.DEFAULT_PORT);
    }

    /**
     * Creates a sender using UDP transport.
     *
     * @param host the target host
     * @param port the target port
     * @return the sender
     * @throws IOException if creation fails
     */
    public static SyslogSender udp(String host, int port) throws IOException {
        return new SyslogSender(
                new UdpTransport(new UdpSender(host, port)),
                getLocalHostname(), null);
    }

    /**
     * Creates a sender using TCP transport with octet counting framing.
     *
     * @param host the target host
     * @param port the target port
     * @return the sender
     * @throws IOException if connection fails
     */
    public static SyslogSender tcp(String host, int port) throws IOException {
        return tcp(host, port, FramingMode.OCTET_COUNTING);
    }

    /**
     * Creates a sender using TCP transport.
     *
     * @param host        the target host
     * @param port        the target port
     * @param framingMode the framing mode
     * @return the sender
     * @throws IOException if connection fails
     */
    public static SyslogSender tcp(String host, int port, FramingMode framingMode) throws IOException {
        return new SyslogSender(
                new TcpTransport(new TcpSender(host, port, framingMode)),
                getLocalHostname(), null);
    }

    /**
     * Creates a sender using TLS transport.
     *
     * @param host the target host
     * @param port the target port
     * @return the sender
     * @throws IOException if connection fails
     */
    public static SyslogSender tls(String host, int port) throws IOException {
        return new SyslogSender(
                new TlsTransport(new TlsSender(host, port)),
                getLocalHostname(), null);
    }

    /**
     * Sets the default hostname for messages.
     *
     * @param hostname the hostname
     * @return a new sender with the hostname set
     */
    public SyslogSender withHostname(String hostname) {
        return new SyslogSender(transport, hostname, appName);
    }

    /**
     * Sets the default application name for messages.
     *
     * @param appName the application name
     * @return a new sender with the app name set
     */
    public SyslogSender withAppName(String appName) {
        return new SyslogSender(transport, hostname, appName);
    }

    /**
     * Sends a simple syslog message.
     *
     * @param facility the facility
     * @param severity the severity
     * @param message  the message text
     * @throws IOException if sending fails
     */
    public void send(Facility facility, Severity severity, String message) throws IOException {
        send(facility, severity, message, List.of());
    }

    /**
     * Sends a syslog message with structured data.
     *
     * @param facility       the facility
     * @param severity       the severity
     * @param message        the message text
     * @param structuredData the structured data elements
     * @throws IOException if sending fails
     */
    public void send(Facility facility, Severity severity, String message,
                     List<StructuredData> structuredData) throws IOException {
        var msg = SyslogMessage.builder(facility, severity)
                .timestamp(Instant.now())
                .hostname(hostname)
                .appName(appName)
                .structuredData(structuredData)
                .message(message)
                .build();
        transport.send(msg);
    }

    /**
     * Sends a pre-built syslog message.
     *
     * @param message the message
     * @throws IOException if sending fails
     */
    public void send(SyslogMessage message) throws IOException {
        transport.send(message);
    }

    @Override
    public void close() throws Exception {
        transport.close();
    }

    private static String getLocalHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
