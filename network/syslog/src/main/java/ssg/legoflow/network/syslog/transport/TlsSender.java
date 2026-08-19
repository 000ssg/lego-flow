package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
/**
 * Syslog message sender using TLS transport (RFC 5425).
 *
 * <p>Sends syslog messages over a TLS-encrypted TCP connection using
 * octet counting framing as required by RFC 5425.
 *
 * @since 0.1.0
 */
public final class TlsSender implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TlsSender.class);
    /** Default syslog TLS port. */
    public static final int DEFAULT_PORT = 6514;

    private final SSLSocket socket;
    private final OutputStream out;

    /**
     * Creates a TLS sender connected to the given host and port using the default SSL context.
     *
     * @param host the target hostname
     * @param port the target port
     * @throws IOException if connection fails
     */
    public TlsSender(String host, int port) throws IOException {
        this(host, port, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    /**
     * Creates a TLS sender with a custom SSL socket factory.
     *
     * @param host    the target hostname
     * @param port    the target port
     * @param factory the SSL socket factory
     * @throws IOException if connection fails
     */
    public TlsSender(String host, int port, SSLSocketFactory factory) throws IOException {
        this.socket = (SSLSocket) factory.createSocket(host, port);
        this.socket.startHandshake();
        this.out = socket.getOutputStream();
        LOG.debug("TLS sender connected to {}:{}", host, port);
    }

    /**
     * Sends a syslog message using octet counting framing over TLS.
     *
     * @param message the message to send
     * @throws IOException if sending fails
     */
    public void send(SyslogMessage message) throws IOException {
        byte[] data = SyslogCodec.encodeToBytes(message);
        byte[] header = (data.length + " ").getBytes(StandardCharsets.US_ASCII);
        out.write(header);
        out.write(data);
        out.flush();
        LOG.trace("Sent {} bytes via TLS", data.length);
    }

    @Override
    public void close() throws IOException {
        socket.close();
        LOG.debug("TLS sender closed");
    }
}
