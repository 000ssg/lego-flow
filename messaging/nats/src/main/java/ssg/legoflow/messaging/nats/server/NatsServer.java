package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.jetstream.JetStreamManager;
import ssg.legoflow.messaging.nats.protocol.*;
import ssg.legoflow.messaging.nats.server.auth.Authenticator;
import ssg.legoflow.messaging.nats.transport.NatsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NATS server core — connection-agnostic, driven through the {@link NatsTransport} SPI.
 *
 * <p>Handles the NATS text protocol for each client connection, manages subscriptions and
 * message routing, supports authentication, queue groups, and JetStream persistent streaming.
 *
 * <p><b>Transport-agnostic:</b> the core opens <b>no sockets</b> and has <b>no accept loop</b>.
 * Accepted connections are handed in via {@link #handleConnection(NatsTransport)} — in
 * production the {@code SelectableChannelManager} (through {@code NatsServerService}) calls it
 * with a {@code PipelineNatsTransport} per accepted channel; in tests a
 * {@code InMemoryNatsTransport} is injected. This mirrors the STOMP {@code StompBroker.accept}
 * reference form — the service layer owns all socket lifecycle.
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

    private volatile Authenticator authenticator;

    /**
     * Creates a NATS server with default settings.
     */
    public NatsServer() {
        this(0);
    }

    /**
     * Creates a NATS server.
     *
     * @param port the advertised port (informational; the real listen port is owned by the
     *             service layer that wraps this core)
     */
    public NatsServer(int port) {
        String serverId = UUID.randomUUID().toString().substring(0, 20).toUpperCase();
        this.serverInfo = ServerInfo.withDefaults(serverId, "lego-flow-nats", port);
        this.jetStreamManager = new JetStreamManager(this);
    }

    /**
     * Marks the server running (called by the service layer once the listener is bound).
     */
    public void start() {
        running.set(true);
        LOG.info("NATS server core started");
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
     * Handles a newly-accepted client connection over the given transport.
     *
     * <p>Runs the client connection loop over the transport on a virtual thread — the seam the
     * service layer (production) and tests (in-memory) use to feed the core. No socket here.
     *
     * @param transport the client's byte-level transport
     */
    public void handleConnection(NatsTransport transport) {
        if (!running.get()) {
            running.set(true);
        }
        long clientId = clientIdCounter.incrementAndGet();
        var connection = new ClientConnection(clientId, transport, this);
        clients.put(clientId, connection);
        executor.submit(connection::run);
        LOG.debug("Client {} accepted", clientId);
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

    @Override
    public void close() {
        running.set(false);
        // Close all clients
        for (var client : clients.values()) {
            client.close();
        }
        clients.clear();
        executor.shutdown();
        LOG.info("NATS server stopped");
    }
}
