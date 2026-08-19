package ssg.legoflow.email.smtp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * SMTP server implementation supporting RFC 5321 and extensions.
 *
 * <p>Accepts client connections on a configurable port, manages sessions, and handles
 * SMTP commands including ESMTP extensions. Uses virtual threads for concurrent
 * connection handling.
 *
 * <p>Supports:
 * <ul>
 *   <li>ESMTP extensions (EHLO negotiation)</li>
 *   <li>STARTTLS for connection upgrade to TLS</li>
 *   <li>SASL authentication (PLAIN, LOGIN, CRAM-MD5, XOAUTH2)</li>
 *   <li>SIZE, 8BITMIME, PIPELINING, CHUNKING, DSN, ENHANCEDSTATUSCODES</li>
 *   <li>Relay restrictions via {@link RelayConfig}</li>
 *   <li>Pluggable message storage via {@link MessageStore}</li>
 *   <li>Pluggable message handling via {@link SmtpHandler}</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 *   var store = new InMemoryMessageStore();
 *   try (var server = new SmtpServer("localhost", 2525)) {
 *       server.setMessageStore(store);
 *       server.setHandler(SmtpHandler.acceptAll());
 *       server.start();
 *       // server is now accepting connections on port 2525
 *   }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SmtpServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpServer.class);

    private final String hostname;
    private final int port;
    private volatile MessageStore messageStore;
    private volatile SmtpHandler handler = SmtpHandler.acceptAll();
    private volatile RelayConfig relayConfig = RelayConfig.openRelay();
    private volatile SSLContext sslContext;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final Map<String, SmtpSession> sessions = new ConcurrentHashMap<>();
    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    /**
     * Creates an SMTP server.
     *
     * @param hostname the server hostname (used in greeting and message IDs)
     * @param port     the port to listen on (0 for ephemeral)
     */
    public SmtpServer(String hostname, int port) {
        this.hostname = Objects.requireNonNull(hostname, "hostname");
        this.port = port;
    }

    /**
     * Sets the message store.
     *
     * @param messageStore the store for delivered messages
     */
    public void setMessageStore(MessageStore messageStore) {
        this.messageStore = Objects.requireNonNull(messageStore, "messageStore");
    }

    /**
     * Sets the message handler for delivery decisions.
     *
     * @param handler the handler
     */
    public void setHandler(SmtpHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Sets the relay configuration.
     *
     * @param relayConfig the relay restrictions
     */
    public void setRelayConfig(RelayConfig relayConfig) {
        this.relayConfig = Objects.requireNonNull(relayConfig, "relayConfig");
    }

    /**
     * Sets the SSL context for STARTTLS support.
     *
     * @param sslContext the SSL context
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Starts the server and binds to the configured port.
     *
     * @throws IOException if the server cannot bind
     */
    public void start() throws IOException {
        if (messageStore == null) {
            throw new IllegalStateException("MessageStore must be set before starting");
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(hostname.equals("localhost") ? "0.0.0.0" : hostname, port));
        boundPort = serverSocket.getLocalPort();
        running.set(true);
        LOG.info("SMTP server started on {}:{}", hostname, boundPort);
        executor.submit(this::acceptLoop);
    }

    /**
     * Stops the server and disconnects all clients.
     */
    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket", e);
            }
        }
        sessions.values().forEach(SmtpSession::close);
        sessions.clear();
        executor.shutdownNow();
        LOG.info("SMTP server stopped");
    }

    /**
     * Returns the port the server is bound to.
     *
     * @return the bound port
     */
    public int getPort() {
        return boundPort;
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the current number of active connections.
     *
     * @return the connection count
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }

    /**
     * Returns the hostname.
     *
     * @return the server hostname
     */
    public String getHostname() {
        return hostname;
    }

    @Override
    public void close() {
        stop();
    }

    // ---- Private ----

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionCount.incrementAndGet();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        var session = new SmtpSession(clientSocket, hostname, handler,
                messageStore, relayConfig, sslContext);
        sessions.put(session.sessionId(), session);
        try {
            session.run();
        } finally {
            sessions.remove(session.sessionId());
            connectionCount.decrementAndGet();
        }
    }
}
