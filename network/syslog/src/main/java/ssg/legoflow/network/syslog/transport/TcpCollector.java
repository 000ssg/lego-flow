package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Syslog message collector using TCP transport (RFC 6587).
 *
 * <p>Supports both octet counting and non-transparent framing. The framing
 * mode is auto-detected per connection: if the first byte is a digit, octet
 * counting is assumed; otherwise non-transparent framing with LF delimiter.
 *
 * @since 1.0.0
 */
public final class TcpCollector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TcpCollector.class);

    private final ServerSocket serverSocket;
    private volatile boolean running;

    /**
     * Creates a TCP collector bound to the given port.
     *
     * @param port the port to listen on
     * @throws IOException if binding fails
     */
    public TcpCollector(int port) throws IOException {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(port));
        LOG.debug("TCP collector bound to port {}", port);
    }

    /**
     * Returns the local port this collector is listening on.
     *
     * @return the local port
     */
    public int localPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Starts accepting connections and delivering messages to the handler.
     *
     * <p>Each connection is handled in its own virtual thread.
     *
     * @param handler the message handler
     */
    public void start(Consumer<SyslogMessage> handler) {
        if (running) {
            throw new IllegalStateException("Collector already running");
        }
        running = true;
        Thread.ofVirtual().name("syslog-tcp-acceptor").start(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    Thread.ofVirtual().name("syslog-tcp-client").start(() ->
                            handleConnection(client, handler));
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Error accepting connection", e);
                    }
                }
            }
        });
        LOG.info("TCP collector started on port {}", serverSocket.getLocalPort());
    }

    private void handleConnection(Socket client, Consumer<SyslogMessage> handler) {
        try (client; InputStream in = client.getInputStream()) {
            while (running && !client.isClosed()) {
                int first = in.read();
                if (first < 0) break;

                String messageText;
                if (first >= '0' && first <= '9') {
                    messageText = readOctetCounted(in, first);
                } else {
                    messageText = readNonTransparent(in, first);
                }

                if (messageText != null && !messageText.isEmpty()) {
                    try {
                        SyslogMessage msg = SyslogCodec.decode(messageText);
                        handler.accept(msg);
                    } catch (Exception e) {
                        LOG.warn("Error parsing syslog message: {}", messageText, e);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("Connection error", e);
            }
        }
    }

    private String readOctetCounted(InputStream in, int firstByte) throws IOException {
        var lenBuf = new StringBuilder();
        lenBuf.append((char) firstByte);
        int b;
        while ((b = in.read()) >= 0) {
            if (b == ' ') break;
            lenBuf.append((char) b);
        }
        int length = Integer.parseInt(lenBuf.toString());
        byte[] data = in.readNBytes(length);
        return new String(data, StandardCharsets.UTF_8);
    }

    private String readNonTransparent(InputStream in, int firstByte) throws IOException {
        var buf = new ByteArrayOutputStream();
        buf.write(firstByte);
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') break;
            buf.write(b);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        LOG.debug("TCP collector closed");
    }
}
