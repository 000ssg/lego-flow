package ssg.legoflow.network.syslog.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.syslog.protocol.SyslogCodec;
import ssg.legoflow.network.syslog.protocol.SyslogMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Syslog message collector using TLS transport (RFC 5425).
 *
 * <p>Accepts TLS connections and reads syslog messages using octet counting
 * framing as required by RFC 5425.
 *
 * @since 0.1.0
 */
public final class TlsCollector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TlsCollector.class);

    private final SSLServerSocket serverSocket;
    private volatile boolean running;

    /**
     * Creates a TLS collector bound to the given port using the default SSL context.
     *
     * @param port the port to listen on
     * @throws IOException if binding fails
     */
    public TlsCollector(int port) throws IOException {
        this(port, (SSLServerSocketFactory) SSLServerSocketFactory.getDefault());
    }

    /**
     * Creates a TLS collector with a custom SSL server socket factory.
     *
     * @param port    the port to listen on
     * @param factory the SSL server socket factory
     * @throws IOException if binding fails
     */
    public TlsCollector(int port, SSLServerSocketFactory factory) throws IOException {
        this.serverSocket = (SSLServerSocket) factory.createServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(port));
        LOG.debug("TLS collector bound to port {}", port);
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
     * Starts accepting TLS connections and delivering messages to the handler.
     *
     * @param handler the message handler
     */
    public void start(Consumer<SyslogMessage> handler) {
        if (running) {
            throw new IllegalStateException("Collector already running");
        }
        running = true;
        Thread.ofVirtual().name("syslog-tls-acceptor").start(() -> {
            while (running) {
                try {
                    SSLSocket client = (SSLSocket) serverSocket.accept();
                    Thread.ofVirtual().name("syslog-tls-client").start(() ->
                            handleConnection(client, handler));
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Error accepting TLS connection", e);
                    }
                }
            }
        });
        LOG.info("TLS collector started on port {}", serverSocket.getLocalPort());
    }

    private void handleConnection(SSLSocket client, Consumer<SyslogMessage> handler) {
        try (client; InputStream in = client.getInputStream()) {
            while (running && !client.isClosed()) {
                int first = in.read();
                if (first < 0) break;

                var lenBuf = new StringBuilder();
                lenBuf.append((char) first);
                int b;
                while ((b = in.read()) >= 0 && b != ' ') {
                    lenBuf.append((char) b);
                }
                int length = Integer.parseInt(lenBuf.toString());
                byte[] data = in.readNBytes(length);
                String text = new String(data, StandardCharsets.UTF_8);
                try {
                    SyslogMessage msg = SyslogCodec.decode(text);
                    handler.accept(msg);
                } catch (Exception e) {
                    LOG.warn("Error parsing syslog message", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("TLS connection error", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
        LOG.debug("TLS collector closed");
    }
}
