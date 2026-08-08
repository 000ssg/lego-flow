package ssg.legoflow.media.sip.server;

import ssg.legoflow.media.sip.protocol.SipCodec;
import ssg.legoflow.media.sip.protocol.SipMessage;
import ssg.legoflow.media.sip.registration.SipRegistrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * SIP server that wraps the registrar with a TCP listener.
 * Uses virtual threads for connection handling.
 *
 * <p>Accepts incoming SIP messages on the configured port, decodes them
 * via SipCodec, and delegates to the registered message handler or registrar.
 *
 * @since 0.1.0
 */
public final class SipServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SipServer.class);

    /** Default SIP port. */
    public static final int DEFAULT_PORT = 5060;

    private final int port;
    private final String domain;
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executor;
    private volatile boolean running;
    private final SipRegistrar registrar;
    private Consumer<SipMessage> messageHandler;

    /** Creates a SIP server on the default port with domain "localhost". */
    public SipServer() { this(DEFAULT_PORT, "localhost"); }

    /** Creates a SIP server on the specified port. */
    public SipServer(int port) { this(port, "localhost"); }

    /** Creates a SIP server with the given port and domain. */
    public SipServer(int port, String domain) {
        this.port = port;
        this.domain = Objects.requireNonNullElse(domain, "localhost");
        this.registrar = new SipRegistrar(this.domain);
    }

    /** Sets a custom handler for SIP messages (optional). */
    public void setMessageHandler(Consumer<SipMessage> handler) { this.messageHandler = handler; }

    /** Returns the registrar instance for direct access. */
    public SipRegistrar getRegistrar() { return registrar; }

    /** Starts the server and begins accepting connections. */
    public void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        LOG.info("SIP server started on port {} (domain: {})", port, domain);
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                var clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) LOG.warn("Accept error", e);
            }
        }
    }

    private void handleClient(java.net.Socket socket) {
        try (socket) {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();
            byte[] buf = new byte[8192];
            int n;
            StringBuilder msgBuf = new StringBuilder();
            while (running && (n = in.read(buf)) > 0) {
                String chunk = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(buf, 0, n)).toString();
                msgBuf.append(chunk);
                // SIP messages are delimited by double CRLF
                if (msgBuf.toString().contains("\r\n\r\n")) {
                    String complete = msgBuf.toString();
                    msgBuf.setLength(0);
                    try {
                        SipMessage msg = SipCodec.decode(complete.getBytes(StandardCharsets.UTF_8));
                        if (messageHandler != null) messageHandler.accept(msg);
                    } catch (Exception e) {
                        LOG.debug("SIP decode error: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) LOG.debug("Client connection closed", e);
        }
    }

    /** Returns true while the server is running. */
    public boolean isRunning() { return running; }

    /** Returns the configured port. */
    public int port() { return port; }

    /** Returns the SIP domain. */
    public String domain() { return domain; }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        if (executor != null) { executor.shutdownNow(); }
        LOG.info("SIP server stopped");
    }

    @Override public String toString() { return "SipServer[port=" + port + ", domain=" + domain + "]"; }
}
