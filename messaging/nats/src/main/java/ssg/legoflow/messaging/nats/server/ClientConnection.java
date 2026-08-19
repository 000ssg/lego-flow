package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.protocol.*;
import ssg.legoflow.messaging.nats.server.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Per-client connection handler on the server side.
 *
 * <p>Manages the client lifecycle: INFO/CONNECT handshake, authentication,
 * PUB/SUB/UNSUB processing, PING/PONG keep-alive, and graceful shutdown.
 *
 * @since 0.1.0
 */
public final class ClientConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConnection.class);

    private final long id;
    private final Socket socket;
    private final NatsServer server;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Map<String, SubscriptionEntry> subscriptions = new ConcurrentHashMap<>();
    private final AtomicInteger unsubCounts = new AtomicInteger(0);

    private volatile ConnectOptions connectOptions;
    private volatile boolean authenticated;
    private volatile boolean running = true;
    private volatile boolean verbose;
    private volatile boolean echo = true;

    /**
     * Creates a new client connection.
     *
     * @param id     the client ID
     * @param socket the client socket
     * @param server the owning server
     * @throws IOException if stream creation fails
     */
    public ClientConnection(long id, Socket socket, NatsServer server) throws IOException {
        this.id = id;
        this.socket = socket;
        this.server = server;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Returns the client ID.
     *
     * @return the ID
     */
    public long id() {
        return id;
    }

    /**
     * Returns the client's connect options.
     *
     * @return the options, or null if not yet connected
     */
    public ConnectOptions connectOptions() {
        return connectOptions;
    }

    /**
     * Returns whether echo is enabled.
     *
     * @return true if echo is enabled
     */
    public boolean echoEnabled() {
        return echo;
    }

    /**
     * Returns all active subscriptions.
     *
     * @return the subscriptions map
     */
    public Map<String, SubscriptionEntry> subscriptions() {
        return subscriptions;
    }

    /**
     * Runs the client connection loop.
     */
    public void run() {
        try {
            // Send INFO
            var info = server.serverInfo().withClientId(id);
            send(NatsCodec.encodeInfo(info));

            // Read and process operations
            while (running) {
                var op = NatsCodec.readOp(reader);
                if (op == null) {
                    LOG.debug("Client {} disconnected (EOF)", id);
                    break;
                }
                processOp(op);
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("Client {} connection error: {}", id, e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    private void processOp(NatsCodec.ParsedOp op) throws IOException {
        switch (op) {
            case NatsCodec.ParsedOp.Connect connect -> handleConnect(connect);
            case NatsCodec.ParsedOp.Pub pub -> handlePub(pub);
            case NatsCodec.ParsedOp.Hpub hpub -> handleHpub(hpub);
            case NatsCodec.ParsedOp.Sub sub -> handleSub(sub);
            case NatsCodec.ParsedOp.Unsub unsub -> handleUnsub(unsub);
            case NatsCodec.ParsedOp.Ping() -> handlePing();
            case NatsCodec.ParsedOp.Pong() -> { /* client pong response, ignore */ }
            default -> sendErr("Unknown Protocol Operation");
        }
    }

    private void handleConnect(NatsCodec.ParsedOp.Connect connect) throws IOException {
        this.connectOptions = connect.options();
        this.verbose = connect.options().verbose();
        this.echo = connect.options().echo();

        // Authenticate if required
        Authenticator auth = server.authenticator();
        if (auth != null) {
            if (!auth.authenticate(connect.options())) {
                sendErr("Authorization Violation");
                running = false;
                return;
            }
        }

        authenticated = true;
        LOG.debug("Client {} connected (name={})", id,
                connect.options().name() != null ? connect.options().name() : "unnamed");

        if (verbose) {
            send(NatsCodec.encodeOk());
        }
    }

    private void handlePub(NatsCodec.ParsedOp.Pub pub) throws IOException {
        if (!checkAuth()) return;

        server.router().route(pub.subject(), pub.replyTo(), null,
                pub.payload(), this, echo);

        // Route to JetStream if applicable
        server.handleJetStreamPublish(pub.subject(), null, pub.payload(), this);

        if (verbose) send(NatsCodec.encodeOk());
    }

    private void handleHpub(NatsCodec.ParsedOp.Hpub hpub) throws IOException {
        if (!checkAuth()) return;

        server.router().route(hpub.subject(), hpub.replyTo(), hpub.headers(),
                hpub.payload(), this, echo);

        server.handleJetStreamPublish(hpub.subject(), hpub.headers(), hpub.payload(), this);

        if (verbose) send(NatsCodec.encodeOk());
    }

    private void handleSub(NatsCodec.ParsedOp.Sub sub) throws IOException {
        if (!checkAuth()) return;

        var entry = new SubscriptionEntry(this, sub.sid(), sub.subject(), sub.queueGroup());
        subscriptions.put(sub.sid(), entry);
        server.router().addSubscription(entry);

        if (verbose) send(NatsCodec.encodeOk());
    }

    private void handleUnsub(NatsCodec.ParsedOp.Unsub unsub) throws IOException {
        if (!checkAuth()) return;

        var entry = subscriptions.get(unsub.sid());
        if (entry != null) {
            if (unsub.maxMsgs() <= 0) {
                // Immediate unsubscribe
                subscriptions.remove(unsub.sid());
                server.router().removeSubscription(entry);
            }
            // Note: max_msgs auto-unsub is handled at client level
        }

        if (verbose) send(NatsCodec.encodeOk());
    }

    private void handlePing() throws IOException {
        send(NatsCodec.encodePong());
    }

    private boolean checkAuth() throws IOException {
        if (server.authenticator() != null && !authenticated) {
            sendErr("Authorization Violation");
            return false;
        }
        return true;
    }

    /**
     * Sends raw protocol data to this client.
     *
     * @param data the protocol data
     * @throws IOException if write fails
     */
    public void send(String data) throws IOException {
        synchronized (writer) {
            writer.write(data);
            writer.flush();
        }
    }

    /**
     * Sends a PING to this client.
     *
     * @throws IOException if write fails
     */
    public void sendPing() throws IOException {
        send(NatsCodec.encodePing());
    }

    private void sendErr(String message) throws IOException {
        send(NatsCodec.encodeErr(message));
    }

    private void cleanup() {
        server.router().removeAll(subscriptions.values());
        subscriptions.clear();
        server.removeClient(this);
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Error closing client {} socket", id, e);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Error closing client {} socket", id, e);
        }
    }
}
