package ssg.legoflow.xmpp.server;

import ssg.legoflow.xmpp.core.Stanza;
import ssg.legoflow.xmpp.stream.XmppCodec;
import ssg.legoflow.xmpp.transport.XmppTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.Objects;

/**
 * XMPP server core — connection-agnostic, driven through the {@link XmppTransport} SPI.
 *
 * <p><b>Transport-agnostic:</b> the core opens <b>no sockets</b> and has <b>no accept loop</b>.
 * Accepted connections are handed in via {@link #handleConnection(XmppTransport)} — in
 * production the {@code SelectableChannelManager} (through {@code XmppServerService}) calls it
 * with a {@code PipelineXmppTransport} per accepted channel; in tests an
 * {@code InMemoryXmppTransport} is injected. This mirrors the NATS {@code NatsServer} and the
 * STOMP/MQTT/AMQP reference forms — the service layer owns all socket lifecycle.
 *
 * <p>Each client connection gets a per-connection {@link XmppCodec} instance (the codec
 * accumulates partial XML across reads) and runs its read loop on a virtual thread.
 *
 * @since 0.1.0
 */
public final class XmppServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(XmppServer.class);

    /** Default XMPP port (informational; the real listen port is owned by the service layer). */
    public static final int DEFAULT_PORT = 5222;

    private static final int RECV_BUFFER_SIZE = 8192;
    private static final long READ_TIMEOUT_MS = 500;

    private final int port;
    private final Map<String, Consumer<Stanza>> handlers = new ConcurrentHashMap<>();
    private final Map<Long, XmppTransport> connections = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong clientIdCounter = new AtomicLong(0);

    /** Creates an XMPP server on the default port. */
    public XmppServer() { this(DEFAULT_PORT); }

    /**
     * Creates an XMPP server.
     *
     * @param port the advertised port (informational; the real listen port is owned by the
     *             service layer that wraps this core)
     */
    public XmppServer(int port) {
        this.port = port;
    }

    /** Registers a stanza handler for the given component name. */
    public void addStanzaHandler(String name, Consumer<Stanza> handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        handlers.put(name, handler);
    }

    /**
     * Marks the server running (called by the service layer once the listener is bound).
     */
    public void start() {
        running.set(true);
        LOG.info("XMPP server core started");
    }

    /**
     * Handles a newly-accepted client connection over the given transport.
     *
     * <p>Runs the client read loop over the transport on a virtual thread — the seam the
     * service layer (production) and tests (in-memory) use to feed the core. No socket here.
     *
     * @param transport the client's byte-level transport
     */
    public void handleConnection(XmppTransport transport) {
        Objects.requireNonNull(transport);
        if (!running.get()) {
            running.set(true);
        }
        long clientId = clientIdCounter.incrementAndGet();
        connections.put(clientId, transport);
        var codec = new XmppCodec();
        executor.submit(() -> {
            var recv = ByteBuffer.allocate(RECV_BUFFER_SIZE);
            try {
                while (running.get() && transport.isOpen()) {
                    recv.clear();
                    int n = transport.receiveWithTimeout(recv, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (n == -1) {
                        if (transport.isOpen()) {
                            continue; // read timeout — not EOF; a silent peer is not a close
                        }
                        break; // EOF
                    }
                    recv.flip();
                    for (Stanza stanza : codec.decodeStanzas(recv)) {
                        handlers.values().forEach(h -> h.accept(stanza));
                    }
                }
                if (running.get()) {
                    LOG.debug("Client {} connection closed", clientId);
                }
            } catch (Exception e) {
                LOG.debug("Client {} read loop error: {}", clientId, e.getMessage());
            } finally {
                connections.remove(clientId);
                try {
                    transport.close();
                } catch (Exception ignored) {
                    // transport already closed or transport error during cleanup
                }
            }
        });
        LOG.debug("Client {} accepted", clientId);
    }

    /** Returns true while the server is running. */
    public boolean isRunning() { return running.get(); }

    /** Returns the configured (advertised) port. */
    public int port() { return port; }

    /** Returns the number of currently-connected clients. */
    public int clientCount() { return connections.size(); }

    @Override
    public void close() {
        running.set(false);
        for (var conn : connections.values()) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // transport already closed or transport error during cleanup
            }
        }
        connections.clear();
        executor.shutdown();
        LOG.info("XMPP server stopped");
    }

    @Override public String toString() { return "XmppServer[port=" + port + ", running=" + running + "]"; }
}
