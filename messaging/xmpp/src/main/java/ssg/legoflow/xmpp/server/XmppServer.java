package ssg.legoflow.xmpp.server;

import ssg.legoflow.xmpp.core.Stanza;
import ssg.legoflow.xmpp.stream.XmppCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.Map;
import java.util.Objects;
/**
 * Minimal XMPP server that listens on a TCP port and delegates incoming stanzas.
 * Uses virtual threads for connection handling.
 *
 * <p>Provides the listener pattern required by the DP/DF service wrapper.
 * Each client connection gets an XmppCodec instance for XML→Stanza decoding.
 *
 * @since 0.1.0
 */
public final class XmppServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(XmppServer.class);

    /** Default XMPP port. */
    public static final int DEFAULT_PORT = 5222;

    private final int port;
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executor;
    private volatile boolean running;
    private final Map<String, Consumer<Stanza>> handlers = new ConcurrentHashMap<>();
    private final XmppCodec codec;

    /** Creates an XMPP server on the default port. */
    public XmppServer() { this(DEFAULT_PORT); }

    /** Creates an XMPP server on the specified port. */
    public XmppServer(int port) {
        this.port = port;
        this.codec = new XmppCodec();
    }

    /** Registers a stanza handler for the given component name. */
    public void addStanzaHandler(String name, Consumer<Stanza> handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        handlers.put(name, handler);
    }

    /** Starts the server and begins accepting connections. */
    public void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        LOG.info("XMPP server started on port {}", port);
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                var clientSocket = serverSocket.accept();
                LOG.debug("New XMPP connection from {}", clientSocket.getRemoteSocketAddress());
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) LOG.warn("Accept error", e);
            }
        }
    }

    private void handleClient(java.net.Socket socket) {
        try (socket) {
            var in = socket.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while (running && (n = in.read(buf)) > 0) {
                var bb = ByteBuffer.wrap(buf, 0, n);
                try {
                    codec.decodeStanzas(bb).forEach(stanza -> {
                        handlers.values().forEach(h -> h.accept(stanza));
                    });
                } catch (Exception e) {
                    LOG.debug("Stanza decode error: {}", e.getMessage());
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

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        if (executor != null) { executor.shutdownNow(); }
        LOG.info("XMPP server stopped");
    }

    @Override public String toString() { return "XmppServer[port=" + port + ", running=" + running + "]"; }
}
