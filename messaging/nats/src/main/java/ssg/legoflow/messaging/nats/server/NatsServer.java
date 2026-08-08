package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.jetstream.JetStreamManager;
import ssg.legoflow.messaging.nats.protocol.*;
import ssg.legoflow.messaging.nats.server.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NATS server supporting TCP connections with virtual threads.
 *
 * <p>Accepts client connections, handles the NATS text protocol,
 * manages subscriptions and message routing, supports authentication,
 * queue groups, and JetStream persistent streaming.
 *
 * @since 0.1.0
 */
public final class NatsServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NatsServer.class);

    private final ServerInfo serverInfo;
    private final MessageRouter router = new MessageRouter();
    private final Map<Long, ClientConnection> clients = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong clientIdCounter = new AtomicLong(0);
    private final JetStreamManager jetStreamManager;

    private volatile ServerSocket serverSocket;
    private volatile int boundPort;
    private volatile Authenticator authenticator;

    /**
     * Creates a NATS server with default settings.
     */
    public NatsServer() {
        this(0);
    }

    /**
     * Creates a NATS server on the specified port.
     *
     * @param port the port (0 for ephemeral)
     */
    public NatsServer(int port) {
        String serverId = UUID.randomUUID().toString().substring(0, 20).toUpperCase();
        this.serverInfo = ServerInfo.withDefaults(serverId, "lego-flow-nats", port);
        this.jetStreamManager = new JetStreamManager(this);
    }

    /**
     * Sets the authenticator for client connections.
     *
     * @param authenticator the authenticator, or null for no auth
     */
    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Returns the authenticator.
     *
     * @return the authenticator, or null
     */
    public Authenticator authenticator() {
        return authenticator;
    }

    /**
     * Returns the server info.
     *
     * @return the server info
     */
    public ServerInfo serverInfo() {
        if (authenticator != null) {
            return serverInfo.withAuthRequired(true);
        }
        return serverInfo;
    }

    /**
     * Returns the message router.
     *
     * @return the router
     */
    public MessageRouter router() {
        return router;
    }

    /**
     * Returns the JetStream manager.
     *
     * @return the JetStream manager
     */
    public JetStreamManager jetStreamManager() {
        return jetStreamManager;
    }

    /**
     * Starts the server and binds to the configured port.
     *
     * @throws IOException if binding fails
     */
    public void start() throws IOException {
        start(serverInfo.port());
    }

    /**
     * Starts the server on the specified port.
     *
     * @param port the port (0 for ephemeral)
     * @throws IOException if binding fails
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
        boundPort = serverSocket.getLocalPort();
        running.set(true);

        LOG.info("NATS server started on port {}", boundPort);

        executor.submit(this::acceptLoop);
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
     * Returns whether the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the number of connected clients.
     *
     * @return the client count
     */
    public int clientCount() {
        return clients.size();
    }

    /**
     * Handles a JetStream publish if the subject matches a stream.
     *
     * @param subject the published subject
     * @param headers the message headers, or null
     * @param payload the message payload
     * @param publisher the publishing client
     */
    void handleJetStreamPublish(String subject, NatsHeaders headers,
                                 byte[] payload, ClientConnection publisher) {
        jetStreamManager.handlePublish(subject, headers, payload, publisher);
    }

    /**
     * Removes a client from the server's client registry.
     *
     * @param client the client to remove
     */
    void removeClient(ClientConnection client) {
        clients.remove(client.id());
        LOG.debug("Client {} removed, {} clients remaining", client.id(), clients.size());
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                long clientId = clientIdCounter.incrementAndGet();
                var connection = new ClientConnection(clientId, clientSocket, this);
                clients.put(clientId, connection);
                executor.submit(connection::run);
                LOG.debug("Client {} connected from {}", clientId, clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting client connection", e);
                }
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        // Close all clients
        for (var client : clients.values()) {
            client.close();
        }
        clients.clear();
        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing server socket", e);
        }
        executor.shutdown();
        LOG.info("NATS server stopped");
    }
}
