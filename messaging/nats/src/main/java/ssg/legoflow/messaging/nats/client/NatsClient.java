package ssg.legoflow.messaging.nats.client;

import ssg.legoflow.messaging.nats.protocol.*;
import ssg.legoflow.messaging.nats.transport.NatsTransport;
import ssg.legoflow.messaging.nats.transport.TransportStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * NATS client supporting connection, pub/sub, and request/reply.
 *
 * <p>Connects to a NATS server, handles protocol negotiation (INFO/CONNECT),
 * manages subscriptions, publishes messages, and supports the request/reply
 * pattern with automatic inbox management.
 *
 * <p><b>Transport-agnostic:</b> this client talks only to the {@link NatsTransport} SPI —
 * it never touches sockets or the wire. A transport is injected at construction:
 * {@code InMemoryNatsTransport} in unit tests, {@code PipelineNatsTransport} (driven by the
 * {@code SelectableChannelManager} in the service layer) in production. See
 * {@code NatsClientService} for the production socket path.
 *
 * <p>Uses virtual threads for the reader loop and request timeouts.
 *
 * @since 0.1.0
 */
public final class NatsClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NatsClient.class);

    private final NatsTransport transport;
    private final ConnectOptions connectOptions;
    private final InboxManager inboxManager = new InboxManager();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong sidCounter = new AtomicLong(0);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile TransportStreams streams;
    private volatile ServerInfo serverInfo;
    private volatile Future<?> readerFuture;

    /**
     * Creates a new NATS client over an injected transport.
     *
     * @param transport   the byte-level transport (no socket)
     * @param options     the connect options
     */
    public NatsClient(NatsTransport transport, ConnectOptions options) {
        this.transport = Objects.requireNonNull(transport);
        this.connectOptions = Objects.requireNonNull(options);
    }

    /**
     * Creates a new NATS client over an injected transport with default options.
     *
     * @param transport the byte-level transport (no socket)
     */
    public NatsClient(NatsTransport transport) {
        this(transport, ConnectOptions.withDefaults("lego-flow-client"));
    }

    /**
     * Connects to the NATS server over the injected transport.
     *
     * <p>Runs the INFO/CONNECT/PING handshake (the transport must already be established by
     * the caller — e.g. the service layer once the manager fires the connect event).
     *
     * @throws IOException if the handshake fails
     */
    public void connect() throws IOException {
        this.streams = new TransportStreams(transport);
        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(streams.inputStream(), StandardCharsets.UTF_8));

        // Read INFO from server
        var op = NatsCodec.readOp(reader);
        if (op instanceof NatsCodec.ParsedOp.Info info) {
            this.serverInfo = info.serverInfo();
        } else {
            throw new IOException("Expected INFO from server, got: " + op);
        }

        // Send CONNECT
        send(NatsCodec.encodeConnect(connectOptions));

        // Send PING and wait for PONG to confirm connection
        send(NatsCodec.encodePing());
        var pongOp = NatsCodec.readOp(reader);
        if (pongOp instanceof NatsCodec.ParsedOp.Err err) {
            throw new IOException("Connection rejected: " + err.message());
        }

        connected.set(true);
        LOG.info("Connected to NATS server");

        // Start reader loop
        readerFuture = executor.submit(() -> readLoop(reader));
    }

    /**
     * Publishes a message to a subject.
     *
     * @param subject the subject
     * @param data    the payload
     * @throws IOException if send fails
     */
    public void publish(String subject, byte[] data) throws IOException {
        checkConnected();
        send(NatsCodec.encodePub(subject, null, data));
    }

    /**
     * Publishes a string message to a subject.
     *
     * @param subject the subject
     * @param data    the string payload
     * @throws IOException if send fails
     */
    public void publish(String subject, String data) throws IOException {
        publish(subject, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Publishes a message with headers.
     *
     * @param subject the subject
     * @param headers the headers
     * @param data    the payload
     * @throws IOException if send fails
     */
    public void publish(String subject, NatsHeaders headers, byte[] data) throws IOException {
        checkConnected();
        send(NatsCodec.encodeHpub(subject, null, headers, data));
    }

    /**
     * Publishes a message with a reply-to subject.
     *
     * @param subject the subject
     * @param replyTo the reply-to subject
     * @param data    the payload
     * @throws IOException if send fails
     */
    public void publish(String subject, String replyTo, byte[] data) throws IOException {
        checkConnected();
        send(NatsCodec.encodePub(subject, replyTo, data));
    }

    /**
     * Subscribes to a subject with a message handler.
     *
     * @param subject the subject pattern
     * @param handler the message handler
     * @return the subscription
     * @throws IOException if send fails
     */
    public Subscription subscribe(String subject, Consumer<NatsMessage> handler) throws IOException {
        return subscribe(subject, null, handler);
    }

    /**
     * Subscribes to a subject with a queue group and message handler.
     *
     * @param subject    the subject pattern
     * @param queueGroup the queue group, or null
     * @param handler    the message handler
     * @return the subscription
     * @throws IOException if send fails
     */
    public Subscription subscribe(String subject, String queueGroup, Consumer<NatsMessage> handler) throws IOException {
        checkConnected();
        String sid = String.valueOf(sidCounter.incrementAndGet());
        var sub = new Subscription(sid, subject, queueGroup, handler);
        subscriptions.put(sid, sub);
        send(NatsCodec.encodeSub(subject, queueGroup, sid));
        return sub;
    }

    /**
     * Unsubscribes a subscription.
     *
     * @param subscription the subscription to remove
     * @throws IOException if send fails
     */
    public void unsubscribe(Subscription subscription) throws IOException {
        checkConnected();
        subscription.unsubscribe();
        subscriptions.remove(subscription.sid());
        send(NatsCodec.encodeUnsub(subscription.sid(), -1));
    }

    /**
     * Sets auto-unsubscribe on a subscription.
     *
     * @param subscription the subscription
     * @param maxMessages  the max messages before auto-unsubscribe
     * @throws IOException if send fails
     */
    public void autoUnsubscribe(Subscription subscription, int maxMessages) throws IOException {
        checkConnected();
        subscription.setAutoUnsubscribe(maxMessages);
        send(NatsCodec.encodeUnsub(subscription.sid(), maxMessages));
    }

    /**
     * Sends a request and waits for a reply.
     *
     * @param subject the subject
     * @param data    the request payload
     * @param timeout the timeout
     * @return the reply message, or null if timeout
     * @throws IOException if send fails
     */
    public NatsMessage request(String subject, byte[] data, Duration timeout) throws IOException {
        checkConnected();
        String inbox = inboxManager.newInbox();
        var future = new CompletableFuture<NatsMessage>();

        // Subscribe to inbox, auto-unsub after 1 message
        var sub = subscribe(inbox, msg -> future.complete(msg));
        autoUnsubscribe(sub, 1);

        // Publish with reply-to
        publish(subject, inbox, data);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            unsubscribe(sub);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new IOException("Request failed", e.getCause());
        }
    }

    /**
     * Sends a request with a string payload.
     *
     * @param subject the subject
     * @param data    the request data
     * @param timeout the timeout
     * @return the reply message, or null if timeout
     * @throws IOException if send fails
     */
    public NatsMessage request(String subject, String data, Duration timeout) throws IOException {
        return request(subject, data.getBytes(StandardCharsets.UTF_8), timeout);
    }

    /**
     * Returns the server info received during connection.
     *
     * @return the server info
     */
    public ServerInfo serverInfo() {
        return serverInfo;
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Returns the transport this client is attached to.
     *
     * @return the transport
     */
    public NatsTransport transport() {
        return transport;
    }

    /**
     * Returns the inbox manager.
     *
     * @return the inbox manager
     */
    public InboxManager inboxManager() {
        return inboxManager;
    }

    @Override
    public void close() {
        connected.set(false);
        if (readerFuture != null) {
            readerFuture.cancel(true);
        }
        try {
            if (streams != null) {
                streams.close();
            }
        } catch (Exception e) {
            LOG.debug("Error closing transport", e);
        }
        executor.shutdown();
        LOG.info("NATS client disconnected");
    }

    private void readLoop(java.io.BufferedReader reader) {
        try {
            while (connected.get()) {
                var op = NatsCodec.readOp(reader);
                if (op == null) {
                    LOG.info("Server closed connection");
                    connected.set(false);
                    break;
                }
                handleOp(op);
            }
        } catch (IOException e) {
            if (connected.get()) {
                LOG.error("Error reading from server", e);
                connected.set(false);
            }
        }
    }

    private void handleOp(NatsCodec.ParsedOp op) throws IOException {
        switch (op) {
            case NatsCodec.ParsedOp.Msg msg -> deliverMessage(
                    msg.sid(), msg.subject(), msg.replyTo(), null, msg.payload());
            case NatsCodec.ParsedOp.Hmsg hmsg -> deliverMessage(
                    hmsg.sid(), hmsg.subject(), hmsg.replyTo(), hmsg.headers(), hmsg.payload());
            case NatsCodec.ParsedOp.Ping() -> send(NatsCodec.encodePong());
            case NatsCodec.ParsedOp.Pong() -> { /* keep-alive acknowledged */ }
            case NatsCodec.ParsedOp.Ok() -> { /* verbose mode ack */ }
            case NatsCodec.ParsedOp.Err err -> LOG.error("Server error: {}", err.message());
            default -> LOG.warn("Unexpected operation in client reader: {}", op);
        }
    }

    private void deliverMessage(String sid, String subject, String replyTo,
                                 NatsHeaders headers, byte[] payload) {
        var sub = subscriptions.get(sid);
        if (sub != null && sub.isActive()) {
            var msg = new NatsMessage(subject, replyTo, headers, payload);
            boolean stillActive = sub.deliver(msg);
            if (!stillActive) {
                subscriptions.remove(sid);
            }
        }
    }

    private void send(String data) throws IOException {
        if (streams == null) {
            throw new IOException("Not connected to NATS server");
        }
        transport.send(TransportStreams.bytes(data));
    }

    private void checkConnected() throws IOException {
        if (!connected.get()) {
            throw new IOException("Not connected to NATS server");
        }
    }
}
