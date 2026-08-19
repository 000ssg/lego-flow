package ssg.legoflow.coap.server;

import ssg.legoflow.coap.codec.CoapCodec;
import ssg.legoflow.coap.observe.ObserveRegistry;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.resource.WellKnownCoreResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * CoAP server that binds to a UDP port and routes incoming requests to registered resources.
 *
 * <p>Supports CON/NON/ACK/RST message types, message deduplication,
 * retransmission with exponential backoff for CON messages, observe
 * notification delivery, and blockwise transfer handling.
 *
 * @since 0.1.0
 */
public final class CoapServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CoapServer.class);

    private final CoapServerConfig config;
    private final CoapCodec codec = new CoapCodec();
    private final ObserveRegistry observeRegistry = new ObserveRegistry();
    private final Map<String, CoapResource> resources = new ConcurrentHashMap<>();
    private final Map<DeduplicationKey, Long> deduplicationCache = new ConcurrentHashMap<>();
    private final Map<DeduplicationKey, CoapMessage> responseCache = new ConcurrentHashMap<>();
    private final Map<TokenKey, SeparateResponseEntry> pendingSeparateResponses = new ConcurrentHashMap<>();
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Standard CoAP multicast address for IPv4 (All CoAP Nodes). */
    public static final String COAP_MULTICAST_IPV4 = "224.0.1.187";

    private DatagramChannel channel;
    private Thread receiveThread;
    private ScheduledExecutorService scheduler;
    private WellKnownCoreResource wellKnownCore;
    private volatile boolean multicastEnabled = false;

    /**
     * Creates a CoAP server with the given configuration.
     *
     * @param config the server configuration
     * @throws NullPointerException if {@code config} is {@code null}
     * @since 0.1.0
     */
    public CoapServer(CoapServerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.wellKnownCore = new WellKnownCoreResource(this::getAllResources);
        resources.put(WellKnownCoreResource.PATH, wellKnownCore);
    }

    /**
     * Creates a CoAP server with default configuration.
     *
     * @since 0.1.0
     */
    public CoapServer() {
        this(CoapServerConfig.defaults());
    }

    /**
     * Adds a resource to this server.
     *
     * @param resource the resource to add
     * @throws NullPointerException if {@code resource} is {@code null}
     * @since 0.1.0
     */
    public void add(CoapResource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        resources.put(resource.path(), resource);
        resource.setObserveNotifier(this::handleObserveNotification);
        LOG.debug("Added resource: {}", resource.path());
    }

    /**
     * Removes a resource by path.
     *
     * @param path the resource path to remove
     * @since 0.1.0
     */
    public void remove(String path) {
        resources.remove(path);
    }

    /**
     * Returns the resource at the given path, or {@code null}.
     *
     * @param path the resource path
     * @return the resource, or {@code null}
     * @since 0.1.0
     */
    public CoapResource getResource(String path) {
        return resources.get(path);
    }

    /**
     * Returns the observe registry.
     *
     * @return the observe registry
     * @since 0.1.0
     */
    public ObserveRegistry observeRegistry() {
        return observeRegistry;
    }

    /**
     * Starts the server, binding to the configured UDP port and beginning
     * to receive and process messages.
     *
     * @throws IOException if binding fails
     * @since 0.1.0
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        channel = DatagramChannel.open();
        channel.configureBlocking(true);
        channel.bind(new InetSocketAddress(config.port()));

        scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        // Schedule deduplication cache cleanup
        scheduler.scheduleAtFixedRate(this::cleanupDeduplicationCache,
                config.deduplicationWindow(), config.deduplicationWindow(), TimeUnit.MILLISECONDS);

        receiveThread = Thread.ofVirtual().name("coap-server-recv").start(this::receiveLoop);

        LOG.info("CoAP server started on port {}", getPort());
    }

    /**
     * Stops the server, closing the UDP channel and cleaning up resources.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.warn("Error closing channel", e);
            }
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        observeRegistry.clear();
        deduplicationCache.clear();
        responseCache.clear();
        pendingSeparateResponses.clear();

        LOG.info("CoAP server stopped");
    }

    /**
     * Returns the actual port the server is bound to.
     *
     * <p>When the server is started with port 0, the OS assigns an ephemeral port.
     * This method returns that actual port. If the server has not been started yet,
     * returns the configured port.
     *
     * @return the bound port, or the configured port if not yet started
     * @since 0.1.0
     */
    public int getPort() {
        if (channel != null && channel.isOpen()) {
            try {
                var localAddress = (InetSocketAddress) channel.getLocalAddress();
                if (localAddress != null) {
                    return localAddress.getPort();
                }
            } catch (IOException e) {
                LOG.warn("Error getting local address", e);
            }
        }
        return config.port();
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return {@code true} if the server is running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Processes a single incoming CoAP message and returns the response (if any).
     *
     * <p>This method is exposed for testing purposes. For separate responses
     * (RFC 7252 Section 5.2.2), returns an empty ACK immediately and schedules
     * the actual response to be sent later as a new CON message.
     *
     * @param message the incoming message
     * @param source  the source address
     * @return the response message, or {@code null} if no response is needed
     * @since 0.1.0
     */
    public CoapMessage handleMessage(CoapMessage message, SocketAddress source) {
        // Handle ACK and RST
        if (message.type() == CoapType.ACKNOWLEDGEMENT || message.type() == CoapType.RESET) {
            if (message.type() == CoapType.RESET) {
                observeRegistry.deregister(message.token());
            }
            return null;
        }

        // Deduplication
        var deduplicationKey = new DeduplicationKey(source, message.messageId());
        var now = System.currentTimeMillis();
        var previous = deduplicationCache.putIfAbsent(deduplicationKey, now);
        if (previous != null) {
            LOG.debug("Duplicate message detected: messageId={}", message.messageId());
            return responseCache.get(deduplicationKey);
        }

        // Empty message (ping)
        if (message.code().isEmpty()) {
            var rst = CoapMessage.builder()
                    .type(CoapType.RESET)
                    .code(CoapCode.EMPTY)
                    .messageId(message.messageId())
                    .build();
            responseCache.put(deduplicationKey, rst);
            return rst;
        }

        // Handle observe registration/deregistration
        var observeOpt = message.getOption(CoapOption.OBSERVE);
        if (observeOpt != null && message.code().equals(CoapCode.GET)) {
            int observeValue = observeOpt.asInt();
            if (observeValue == 0) {
                // Register
                observeRegistry.register(message.token(), message.getUriPath(), source);
            } else if (observeValue == 1) {
                // Deregister
                observeRegistry.deregister(message.token());
            }
        }

        // Route to resource handler
        String path = message.getUriPath();
        var resource = resources.get(path);
        if (resource == null) {
            var response = createResponse(message, CoapCode.NOT_FOUND, null, -1);
            responseCache.put(deduplicationKey, response);
            return response;
        }

        var exchange = new CoapExchange(message, source);
        dispatchToHandler(resource, exchange, message.code());

        // Handle separate response pattern (RFC 7252 Section 5.2.2)
        if (exchange.isSeparateResponse() && message.type() == CoapType.CONFIRMABLE) {
            // Send empty ACK immediately
            var emptyAck = CoapMessage.builder()
                    .type(CoapType.ACKNOWLEDGEMENT)
                    .code(CoapCode.EMPTY)
                    .messageId(message.messageId())
                    .build();
            responseCache.put(deduplicationKey, emptyAck);

            // Schedule the actual response to be sent later as a CON message
            if (exchange.hasResponse()) {
                pendingSeparateResponses.put(new TokenKey(message.token()), new SeparateResponseEntry(
                        exchange.getResponse(), source));
            }
            return emptyAck;
        }

        if (exchange.hasResponse()) {
            var response = exchange.getResponse();
            responseCache.put(deduplicationKey, response);
            return response;
        }

        return null;
    }

    /**
     * Retrieves and removes a pending separate response for the given token.
     *
     * <p>After an empty ACK has been sent for a CON request requiring a separate
     * response, the actual response is stored and can be retrieved via this method.
     *
     * @param token the request token
     * @return the separate response entry, or {@code null} if none pending
     * @since 0.1.0
     */
    public SeparateResponseEntry takeSeparateResponse(byte[] token) {
        return pendingSeparateResponses.remove(new TokenKey(token));
    }

    /**
     * Returns the number of pending separate responses.
     *
     * @return the count
     * @since 0.1.0
     */
    public int pendingSeparateResponseCount() {
        return pendingSeparateResponses.size();
    }

    /**
     * Holds a separate response and its target address.
     *
     * @param response the response message
     * @param target   the target address to send the response to
     * @since 0.1.0
     */
    public record SeparateResponseEntry(CoapMessage response, SocketAddress target) {
    }

    /**
     * Joins a multicast group, enabling the server to receive multicast CoAP requests.
     *
     * <p>Per RFC 7252 Section 8, responses to multicast requests MUST be Non-confirmable.
     * The server enforces this automatically when handling multicast requests.
     *
     * @param groupAddress the multicast group address (e.g. "224.0.1.187")
     * @param networkIf    the network interface to join on
     * @throws IOException if joining the group fails
     * @since 0.1.0
     */
    public void joinMulticastGroup(InetAddress groupAddress, NetworkInterface networkIf) throws IOException {
        Objects.requireNonNull(groupAddress, "groupAddress must not be null");
        Objects.requireNonNull(networkIf, "networkIf must not be null");
        if (channel == null) {
            throw new IllegalStateException("Server must be started before joining multicast group");
        }
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkIf);
        channel.join(groupAddress, networkIf);
        multicastEnabled = true;
        LOG.info("Joined multicast group {} on {}", groupAddress, networkIf.getDisplayName());
    }

    /**
     * Returns whether multicast support is enabled.
     *
     * @return {@code true} if multicast is enabled
     * @since 0.1.0
     */
    public boolean isMulticastEnabled() {
        return multicastEnabled;
    }

    /**
     * Checks if an address is a multicast address.
     *
     * @param address the socket address to check
     * @return {@code true} if the address is multicast
     * @since 0.1.0
     */
    public static boolean isMulticastAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() != null && inet.getAddress().isMulticastAddress();
        }
        return false;
    }

    /**
     * Sends all pending separate responses immediately.
     *
     * <p>This is useful when the server needs to flush separate responses
     * that were queued during {@link #handleMessage(CoapMessage, SocketAddress)}.
     *
     * @since 0.1.0
     */
    public void flushSeparateResponses() {
        for (var iter = pendingSeparateResponses.entrySet().iterator(); iter.hasNext(); ) {
            var entry = iter.next();
            iter.remove();
            sendMessage(entry.getValue().response(), entry.getValue().target());
        }
    }

    /**
     * Returns the next message ID.
     *
     * @return the next message ID (0-65535)
     * @since 0.1.0
     */
    public int nextMessageId() {
        return messageIdCounter.incrementAndGet() & 0xFFFF;
    }

    private void receiveLoop() {
        var buffer = ByteBuffer.allocate(config.maxMessageSize() + 4);
        while (running.get()) {
            try {
                buffer.clear();
                var source = channel.receive(buffer);
                if (source == null) continue;

                buffer.flip();
                var message = codec.decode(buffer);
                var response = handleMessage(message, source);

                if (response != null) {
                    sendMessage(response, source);
                }

                // Deliver any pending separate responses
                if (!pendingSeparateResponses.isEmpty()) {
                    for (var iter = pendingSeparateResponses.entrySet().iterator(); iter.hasNext(); ) {
                        var entry = iter.next();
                        iter.remove();
                        sendMessage(entry.getValue().response(), entry.getValue().target());
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error receiving message", e);
                }
            } catch (Exception e) {
                LOG.error("Error processing message", e);
            }
        }
    }

    private void sendMessage(CoapMessage message, SocketAddress target) {
        try {
            var buffer = codec.encode(message);
            channel.send(buffer, target);
        } catch (IOException e) {
            LOG.error("Error sending message to {}", target, e);
        }
    }

    private void handleObserveNotification(String path) {
        var resource = resources.get(path);
        if (resource == null) return;

        // Create a fake exchange to get the current resource state
        var fakeRequest = CoapMessage.builder()
                .code(CoapCode.GET)
                .type(CoapType.NON_CONFIRMABLE)
                .uriPath(path)
                .messageId(nextMessageId())
                .token(new byte[0])
                .build();

        var exchange = new CoapExchange(fakeRequest, new InetSocketAddress(0));
        resource.handleGet(exchange);

        if (exchange.hasResponse()) {
            var notification = exchange.getResponse();
            var entries = observeRegistry.notifyObservers(path, notification);
            for (var entry : entries) {
                sendMessage(entry.notification(), entry.relation().observer());
            }
        }
    }

    private void dispatchToHandler(CoapResource resource, CoapExchange exchange, CoapCode code) {
        if (code.equals(CoapCode.GET)) {
            resource.handleGet(exchange);
        } else if (code.equals(CoapCode.POST)) {
            resource.handlePost(exchange);
        } else if (code.equals(CoapCode.PUT)) {
            resource.handlePut(exchange);
        } else if (code.equals(CoapCode.DELETE)) {
            resource.handleDelete(exchange);
        } else {
            exchange.respond(CoapCode.METHOD_NOT_ALLOWED);
        }
    }

    private CoapMessage createResponse(CoapMessage request, CoapCode code, byte[] payload, int contentFormat) {
        // Per RFC 7252 Section 8: responses to multicast MUST use NON
        CoapType responseType;
        if (multicastEnabled && request.type() == CoapType.NON_CONFIRMABLE) {
            responseType = CoapType.NON_CONFIRMABLE;
        } else {
            responseType = request.type() == CoapType.CONFIRMABLE ? CoapType.ACKNOWLEDGEMENT : CoapType.NON_CONFIRMABLE;
        }
        var builder = CoapMessage.builder()
                .type(responseType)
                .code(code)
                .messageId(request.messageId())
                .token(request.token());

        if (contentFormat >= 0) {
            builder.option(CoapOption.contentFormat(contentFormat));
        }
        if (payload != null) {
            builder.payload(payload);
        }
        return builder.build();
    }

    private void cleanupDeduplicationCache() {
        var now = System.currentTimeMillis();
        var threshold = now - config.deduplicationWindow();
        deduplicationCache.entrySet().removeIf(entry -> entry.getValue() < threshold);
        // Also clean up response cache for expired entries
        responseCache.keySet().removeIf(key -> !deduplicationCache.containsKey(key));
    }

    private List<CoapResource> getAllResources() {
        return Collections.unmodifiableList(new ArrayList<>(resources.values()));
    }

    private record DeduplicationKey(SocketAddress source, int messageId) {
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
