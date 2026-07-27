package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Syslog message sender using TCP transport (RFC 6587).
 *
 * <p>Supports two framing methods:
 * <ul>
 *   <li><b>Octet counting</b>: {@code N message} where N is the byte length</li>
 *   <li><b>Non-transparent</b>: {@code message LF}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class TcpSender implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpSender.class);
    /** Default syslog TCP port. */
    public static final int DEFAULT_PORT = 514;

    private final Socket socket;
    private final OutputStream out;
    private final FramingMode framingMode;

    /**
     * Creates a TCP sender connected to the given host and port.
     *
     * @param host        the target hostname
     * @param port        the target port
     * @param framingMode the framing mode to use
     * @throws IOException if connection fails
     */
    public TcpSender(String host, int port, FramingMode framingMode) throws IOException {
        this.socket = new Socket(host, port);
        this.out = socket.getOutputStream();
        this.framingMode = framingMode;
        LOG.debug("TCP sender connected to {}:{} with {} framing", host, port, framingMode);
    }

    /**
     * Creates a TCP sender with octet counting framing.
     *
     * @param host the target hostname
     * @param port the target port
     * @throws IOException if connection fails
     */
    public TcpSender(String host, int port) throws IOException {
        this(host, port, FramingMode.OCTET_COUNTING);
    }

    /**
     * Sends a syslog message.
     *
     * @param message the message to send
     * @throws IOException if sending fails
     */
    public void send(SyslogMessage message) throws IOException {
        byte[] data = SyslogCodec.encodeToBytes(message);
        switch (framingMode) {
            case OCTET_COUNTING -> {
                byte[] header = (data.length + " ").getBytes(StandardCharsets.US_ASCII);
                out.write(header);
                out.write(data);
            }
            case NON_TRANSPARENT -> {
                out.write(data);
                out.write('\n');
            }
        }
        out.flush();
        LOG.trace("Sent {} bytes via TCP", data.length);
    }

    @Override
    public void close() throws IOException {
        socket.close();
        LOG.debug("TCP sender closed");
    }
}
