package ssg.legoflow.email.imap.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * IMAP4rev2 server accepting TCP connections and dispatching to per-client sessions.
 *
 * <p>Uses virtual threads for scalable concurrency. Each client connection
 * is handled by an {@link ImapSession} on its own virtual thread.
 *
 * @since 0.1.0
 */
public final class ImapServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ImapServer.class);

    private final String host;
    private final int port;
    private final MailStore store;
    private final IdleNotifier idleNotifier;
    private final NamespaceConfig namespaceConfig;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    /**
     * Creates an IMAP server.
     *
     * @param host  the bind address
     * @param port  the port (0 for ephemeral)
     * @param store the mail store
     */
    public ImapServer(String host, int port, MailStore store) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.store = Objects.requireNonNull(store);
        this.idleNotifier = new IdleNotifier();
        this.namespaceConfig = NamespaceConfig.defaultConfig(store.delimiter());
    }

    /**
     * Creates an IMAP server with custom namespace configuration.
     *
     * @param host            the bind address
     * @param port            the port
     * @param store           the mail store
     * @param namespaceConfig the namespace configuration
     */
    public ImapServer(String host, int port, MailStore store, NamespaceConfig namespaceConfig) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.store = Objects.requireNonNull(store);
        this.idleNotifier = new IdleNotifier();
        this.namespaceConfig = Objects.requireNonNull(namespaceConfig);
    }

    /**
     * Starts the server and begins accepting connections.
     *
     * @throws IOException if binding fails
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already running");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));
        boundPort = serverSocket.getLocalPort();

        LOG.info("IMAP server listening on {}:{}", host, boundPort);

        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOG.debug("Accepted connection from {}", clientSocket.getRemoteSocketAddress());
                ImapSession session = new ImapSession(clientSocket, store, idleNotifier, namespaceConfig);
                executor.submit(session);
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    /**
     * Returns the port the server is bound to.
     *
     * @return the bound port
     */
    public int port() {
        return boundPort;
    }

    /**
     * Returns the idle notifier for this server.
     *
     * @return the idle notifier
     */
    public IdleNotifier idleNotifier() {
        return idleNotifier;
    }

    /**
     * Returns true if the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing server socket", e);
        }
        executor.shutdownNow();
    }
}
