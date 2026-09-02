package ssg.legoflow.coap.client;

import ssg.legoflow.coap.codec.CoapCodec;
import ssg.legoflow.coap.observe.ObserveRelation;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * CoAP client for making requests to CoAP servers.
 *
 * <p>Supports GET, POST, PUT, DELETE operations with confirmable (CON) or
 * non-confirmable (NON) messages, automatic retransmission for CON,
 * observe subscriptions, and blockwise transfer.
 *
 * @since 0.1.0
 */
public final class CoapClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CoapClient.class);

    private final CoapClientConfig config;
    private final CoapCodec codec = new CoapCodec();
    private final AtomicInteger messageIdCounter = new AtomicInteger(
            ThreadLocalRandom.current().nextInt(0, 0xFFFF));
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Map<Integer, CompletableFuture<CoapMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<TokenKey, CoapObserveHandler> observeHandlers = new ConcurrentHashMap<>();

    private DatagramChannel channel;
    private Thread receiveThread;
    private boolean confirmable = true;

    /**
     * Creates a CoAP client with the given configuration.
     *
     * @param config the client configuration
     * @throws IOException if opening the UDP channel fails
     * @since 0.1.0
     */
    public CoapClient(CoapClientConfig config) throws IOException {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(true);
        this.channel.socket().setSoTimeout(500); // 500ms receive timeout for polling
        this.receiveThread = Thread.ofVirtual().name("coap-client-recv").start(this::receiveLoop);
    }

    /**
     * Creates a CoAP client targeting the given host with defaults.
     *
     * @param host the target host
     * @throws IOException if opening the UDP channel fails
     * @since 0.1.0
     */
    public CoapClient(String host) throws IOException {
        this(CoapClientConfig.defaults(host));
    }

    /**
     * Creates a CoAP client targeting the given host and port with defaults.
     *
     * @param host the target host
     * @param port the target port
     * @throws IOException if opening the UDP channel fails
     * @since 0.1.0
     */
    public CoapClient(String host, int port) throws IOException {
        this(CoapClientConfig.defaults(host, port));
    }

    /**
     * Sets whether requests should use confirmable (CON) messages.
     *
     * @param confirmable {@code true} for CON, {@code false} for NON
     * @return this client for chaining
     * @since 0.1.0
     */
    public CoapClient setConfirmable(boolean confirmable) {
        this.confirmable = confirmable;
        return this;
    }

    /**
     * Sends a GET request for the given URI path.
     *
     * @param uri the resource URI path (e.g. "/sensors/temperature")
     * @return the response
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public CoapResponse get(String uri) throws IOException {
        var message = buildRequest(CoapCode.GET, uri, null, -1);
        return sendAndReceive(message);
    }

    /**
     * Sends a POST request with payload.
     *
     * @param uri           the resource URI path
     * @param payload       the request payload
     * @param contentFormat the content format identifier
     * @return the response
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public CoapResponse post(String uri, byte[] payload, int contentFormat) throws IOException {
        var message = buildRequest(CoapCode.POST, uri, payload, contentFormat);
        return sendAndReceive(message);
    }

    /**
     * Sends a PUT request with payload.
     *
     * @param uri           the resource URI path
     * @param payload       the request payload
     * @param contentFormat the content format identifier
     * @return the response
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public CoapResponse put(String uri, byte[] payload, int contentFormat) throws IOException {
        var message = buildRequest(CoapCode.PUT, uri, payload, contentFormat);
        return sendAndReceive(message);
    }

    /**
     * Sends a DELETE request.
     *
     * @param uri the resource URI path
     * @return the response
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public CoapResponse delete(String uri) throws IOException {
        var message = buildRequest(CoapCode.DELETE, uri, null, -1);
        return sendAndReceive(message);
    }

    /**
     * Registers an observe relationship for the given URI.
     *
     * @param uri     the resource URI path to observe
     * @param handler the notification handler
     * @return the observe relation
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public ObserveRelation observe(String uri, CoapObserveHandler handler) throws IOException {
        Objects.requireNonNull(handler, "handler must not be null");

        byte[] token = generateToken();
        var message = CoapMessage.builder()
                .type(confirmable ? CoapType.CONFIRMABLE : CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(nextMessageId())
                .token(token)
                .uriPath(uri)
                .option(CoapOption.observe(0))
                .build();

        var target = new InetSocketAddress(config.host(), config.port());
        var relation = new ObserveRelation(token, uri, target);

        observeHandlers.put(new TokenKey(token), handler);
        sendMessage(message);

        return relation;
    }

    /**
     * Cancels an observe relationship.
     *
     * @param relation the relation to cancel
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public void cancelObserve(ObserveRelation relation) throws IOException {
        Objects.requireNonNull(relation, "relation must not be null");
        relation.cancel();
        observeHandlers.remove(new TokenKey(relation.token()));

        var message = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(nextMessageId())
                .token(relation.token())
                .uriPath(relation.resourcePath())
                .option(CoapOption.observe(1))
                .build();

        sendMessage(message);
    }

    // ---- Async variants ----

    /**
     * Sends an async GET request.
     *
     * @param uri the resource URI path
     * @return a future completing with the response
     * @since 0.1.0
     */
    public CompletableFuture<CoapResponse> getAsync(String uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get(uri);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, runnable -> Thread.ofVirtual().start(runnable));
    }

    /**
     * Sends an async POST request.
     *
     * @param uri           the resource URI path
     * @param payload       the request payload
     * @param contentFormat the content format identifier
     * @return a future completing with the response
     * @since 0.1.0
     */
    public CompletableFuture<CoapResponse> postAsync(String uri, byte[] payload, int contentFormat) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return post(uri, payload, contentFormat);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, runnable -> Thread.ofVirtual().start(runnable));
    }

    /**
     * Sends an async PUT request.
     *
     * @param uri           the resource URI path
     * @param payload       the request payload
     * @param contentFormat the content format identifier
     * @return a future completing with the response
     * @since 0.1.0
     */
    public CompletableFuture<CoapResponse> putAsync(String uri, byte[] payload, int contentFormat) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return put(uri, payload, contentFormat);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, runnable -> Thread.ofVirtual().start(runnable));
    }

    /**
     * Sends an async DELETE request.
     *
     * @param uri the resource URI path
     * @return a future completing with the response
     * @since 0.1.0
     */
    public CompletableFuture<CoapResponse> deleteAsync(String uri) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delete(uri);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, runnable -> Thread.ofVirtual().start(runnable));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (receiveThread != null) {
                receiveThread.interrupt();
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    LOG.warn("Error closing channel", e);
                }
            }
            pendingRequests.values().forEach(f -> f.cancel(true));
            pendingRequests.clear();
            observeHandlers.clear();
        }
    }

    private CoapMessage buildRequest(CoapCode code, String uri, byte[] payload, int contentFormat) {
        var builder = CoapMessage.builder()
                .type(confirmable ? CoapType.CONFIRMABLE : CoapType.NON_CONFIRMABLE)
                .code(code)
                .messageId(nextMessageId())
                .token(generateToken())
                .uriPath(uri);

        if (contentFormat >= 0) {
            builder.contentFormat(contentFormat);
        }
        if (payload != null) {
            builder.payload(payload);
        }
        return builder.build();
    }

    private CoapResponse sendAndReceive(CoapMessage message) throws IOException {
        var future = new CompletableFuture<CoapMessage>();
        pendingRequests.put(message.messageId(), future);

        try {
            sendMessage(message);

            // Wait with retransmission for CON
            if (message.type() == CoapType.CONFIRMABLE) {
                return waitForConfirmableResponse(message, future);
            } else {
                var response = future.get(config.ackTimeout(), TimeUnit.MILLISECONDS);
                return toResponse(response);
            }
        } catch (Exception e) {
            pendingRequests.remove(message.messageId());
            if (e instanceof IOException ioe) throw ioe;
            throw new IOException("Request failed: " + e.getMessage(), e);
        }
    }

    private CoapResponse waitForConfirmableResponse(CoapMessage message,
                                                     CompletableFuture<CoapMessage> future) throws IOException {
        long timeout = config.ackTimeout();
        for (int attempt = 0; attempt <= config.maxRetransmit(); attempt++) {
            try {
                var response = future.get(timeout, TimeUnit.MILLISECONDS);
                return toResponse(response);
            } catch (java.util.concurrent.TimeoutException e) {
                if (attempt < config.maxRetransmit()) {
                    LOG.debug("Retransmitting message {} (attempt {})", message.messageId(), attempt + 1);
                    sendMessage(message);
                    timeout *= 2; // Exponential backoff
                }
            } catch (Exception e) {
                throw new IOException("Request failed: " + e.getMessage(), e);
            }
        }
        pendingRequests.remove(message.messageId());
        throw new IOException("Request timed out after " + config.maxRetransmit() + " retransmissions");
    }

    private void sendMessage(CoapMessage message) throws IOException {
        var buffer = codec.encode(message);
        var target = new InetSocketAddress(config.host(), config.port());
        channel.send(buffer, target);
    }

    private void receiveLoop() {
        var buffer = ByteBuffer.allocate(2048);
        while (!closed.get()) {
            try {
                buffer.clear();
                var source = channel.receive(buffer);
                if (source == null) continue;

                buffer.flip();
                var message = codec.decode(buffer);

                // Check if this is an observe notification
                var observeOpt = message.getOption(CoapOption.OBSERVE);
                if (observeOpt != null) {
                    var handler = observeHandlers.get(new TokenKey(message.token()));
                    if (handler != null) {
                        handler.onNotification(toResponse(message));
                    }
                }

                // Complete pending request
                var future = pendingRequests.remove(message.messageId());
                if (future != null) {
                    future.complete(message);
                }
            } catch (java.net.SocketTimeoutException e) {
                // Expected timeout for polling, continue
            } catch (IOException e) {
                if (!closed.get()) {
                    LOG.debug("Receive error", e);
                }
            } catch (Exception e) {
                if (!closed.get()) {
                    LOG.debug("Error processing received message", e);
                }
            }
        }
    }

    private CoapResponse toResponse(CoapMessage message) {
        return new CoapResponse(message.code(), message.payload(), message.options(), message.type());
    }

    private int nextMessageId() {
        return messageIdCounter.incrementAndGet() & 0xFFFF;
    }

    private byte[] generateToken() {
        var token = new byte[4];
        ThreadLocalRandom.current().nextBytes(token);
        return token;
    }

    private record TokenKey(byte[] token) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TokenKey that)) return false;
            return java.util.Arrays.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(token);
        }
    }
}
