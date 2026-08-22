package ssg.legoflow.upnp.gena;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.net.httpserver.HttpServer;
/**
 * Manages GENA event subscriptions for UPnP services.
 *
 * <p>Handles subscribing to, renewing, and unsubscribing from UPnP service events.
 * Runs a local HTTP server to receive NOTIFY callbacks and dispatches event
 * notifications to registered listeners.
 *
 * <p>Supports automatic renewal of subscriptions before they expire.
 *
 * <p>This class is thread-safe.
 *
 * @since 0.1.0
 */
public class GenaSubscriber implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(GenaSubscriber.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(GenaConstants.DEFAULT_TIMEOUT_SECONDS);

    private final HttpClient httpClient;
    private final HttpServer callbackServer;
    private final int callbackPort;
    private final String callbackHost;
    private final Map<String, EventSubscription> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<GenaListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> renewalTask;

    /**
     * Creates a new {@code GenaSubscriber} with callback server on the specified port.
     *
     * @param callbackHost the hostname or IP for the callback URL
     * @param callbackPort the port for the callback HTTP server (0 for ephemeral)
     * @throws IOException          if the callback server cannot be started
     * @throws NullPointerException if {@code callbackHost} is {@code null}
     * @since 0.1.0
     */
    public GenaSubscriber(String callbackHost, int callbackPort) throws IOException {
        this.callbackHost = Objects.requireNonNull(callbackHost, "callbackHost must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.callbackServer = HttpServer.create(new InetSocketAddress(callbackPort), 0);
        this.callbackPort = callbackServer.getAddress().getPort();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("gena-renewal-scheduler");
            return t;
        });

        setupCallbackHandler();
    }

    /**
     * Creates a new {@code GenaSubscriber} with injected dependencies for testing.
     *
     * @param httpClient     the HTTP client for SUBSCRIBE/UNSUBSCRIBE requests
     * @param callbackServer the HTTP server for NOTIFY callbacks
     * @param callbackHost   the callback hostname
     * @since 0.1.0
     */
    GenaSubscriber(HttpClient httpClient, HttpServer callbackServer, String callbackHost) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.callbackServer = Objects.requireNonNull(callbackServer, "callbackServer must not be null");
        this.callbackHost = Objects.requireNonNull(callbackHost, "callbackHost must not be null");
        this.callbackPort = callbackServer.getAddress().getPort();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("gena-renewal-scheduler");
            return t;
        });
        setupCallbackHandler();
    }

    /**
     * Starts the callback server and subscription renewal scheduler.
     *
     * @since 0.1.0
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            callbackServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            callbackServer.start();
            renewalTask = scheduler.scheduleAtFixedRate(this::renewExpiring, 60, 60, TimeUnit.SECONDS);
            LOG.info("GENA subscriber started, callback on {}:{}", callbackHost, callbackPort);
        }
    }

    /**
     * Stops the callback server and renewal scheduler.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (renewalTask != null) {
                renewalTask.cancel(false);
            }
            callbackServer.stop(1);
            LOG.info("GENA subscriber stopped");
        }
    }

    /**
     * Subscribes to events from a UPnP service.
     *
     * @param eventSubUrl the event subscription URL on the remote service
     * @param serviceId   the service identifier
     * @param timeout     the requested subscription timeout
     * @return the event subscription
     * @throws IOException          if the subscription request fails
     * @throws InterruptedException if interrupted while waiting for response
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 0.1.0
     */
    public EventSubscription subscribe(URI eventSubUrl, String serviceId, Duration timeout)
            throws IOException, InterruptedException {
        Objects.requireNonNull(eventSubUrl, "eventSubUrl must not be null");
        Objects.requireNonNull(serviceId, "serviceId must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        var callbackUrl = URI.create("http://" + callbackHost + ":" + callbackPort + "/callback/" + serviceId);
        var timeoutHeader = GenaConstants.formatTimeout(timeout.toSeconds());

        var request = HttpRequest.newBuilder()
                .uri(eventSubUrl)
                .method("SUBSCRIBE", HttpRequest.BodyPublishers.noBody())
                .header(GenaConstants.HEADER_CALLBACK, GenaConstants.formatCallback(callbackUrl.toString()))
                .header(GenaConstants.HEADER_NT, GenaConstants.NT_UPNP_EVENT)
                .header(GenaConstants.HEADER_TIMEOUT, timeoutHeader)
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SUBSCRIBE failed with status " + response.statusCode());
        }

        var sid = response.headers().firstValue(GenaConstants.HEADER_SID.toLowerCase())
                .or(() -> response.headers().firstValue(GenaConstants.HEADER_SID))
                .orElseThrow(() -> new IOException("No SID in SUBSCRIBE response"));

        var actualTimeout = response.headers().firstValue(GenaConstants.HEADER_TIMEOUT.toLowerCase())
                .or(() -> response.headers().firstValue(GenaConstants.HEADER_TIMEOUT))
                .map(GenaConstants::parseTimeout)
                .map(Duration::ofSeconds)
                .orElse(timeout);

        var subscription = new EventSubscription(sid, callbackUrl, eventSubUrl,
                serviceId, actualTimeout, Instant.now().plus(actualTimeout));
        subscriptions.put(sid, subscription);

        LOG.info("Subscribed to {} with SID={}, timeout={}", eventSubUrl, sid, actualTimeout);
        return subscription;
    }

    /**
     * Renews an existing event subscription.
     *
     * @param subscription the subscription to renew
     * @return the renewed subscription with updated expiry
     * @throws IOException          if the renewal request fails
     * @throws InterruptedException if interrupted while waiting for response
     * @throws NullPointerException if {@code subscription} is {@code null}
     * @since 0.1.0
     */
    public EventSubscription renew(EventSubscription subscription)
            throws IOException, InterruptedException {
        Objects.requireNonNull(subscription, "subscription must not be null");

        var timeoutHeader = GenaConstants.formatTimeout(subscription.timeout().toSeconds());

        var request = HttpRequest.newBuilder()
                .uri(subscription.eventSubUrl())
                .method("SUBSCRIBE", HttpRequest.BodyPublishers.noBody())
                .header(GenaConstants.HEADER_SID, subscription.sid())
                .header(GenaConstants.HEADER_TIMEOUT, timeoutHeader)
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SUBSCRIBE renewal failed with status " + response.statusCode());
        }

        var actualTimeout = response.headers().firstValue(GenaConstants.HEADER_TIMEOUT.toLowerCase())
                .or(() -> response.headers().firstValue(GenaConstants.HEADER_TIMEOUT))
                .map(GenaConstants::parseTimeout)
                .map(Duration::ofSeconds)
                .orElse(subscription.timeout());

        var renewed = subscription.renewed(actualTimeout);
        subscriptions.put(renewed.sid(), renewed);

        LOG.debug("Renewed subscription SID={}", renewed.sid());
        return renewed;
    }

    /**
     * Unsubscribes from an event subscription.
     *
     * @param subscription the subscription to cancel
     * @throws IOException          if the unsubscribe request fails
     * @throws InterruptedException if interrupted while waiting for response
     * @throws NullPointerException if {@code subscription} is {@code null}
     * @since 0.1.0
     */
    public void unsubscribe(EventSubscription subscription)
            throws IOException, InterruptedException {
        Objects.requireNonNull(subscription, "subscription must not be null");

        var request = HttpRequest.newBuilder()
                .uri(subscription.eventSubUrl())
                .method("UNSUBSCRIBE", HttpRequest.BodyPublishers.noBody())
                .header(GenaConstants.HEADER_SID, subscription.sid())
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        subscriptions.remove(subscription.sid());

        LOG.info("Unsubscribed SID={}, status={}", subscription.sid(), response.statusCode());
    }

    /**
     * Adds a listener for GENA event notifications.
     *
     * @param listener the listener to add
     * @throws NullPointerException if {@code listener} is {@code null}
     * @since 0.1.0
     */
    public void addListener(GenaListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously added GENA listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removeListener(GenaListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns an unmodifiable snapshot of active subscriptions.
     *
     * @return a map of SID to subscription
     * @since 0.1.0
     */
    public Map<String, EventSubscription> getSubscriptions() {
        return Map.copyOf(subscriptions);
    }

    /**
     * Returns the callback port number.
     *
     * @return the callback server port
     * @since 0.1.0
     */
    public int getCallbackPort() {
        return callbackPort;
    }

    /**
     * Returns whether the subscriber is running.
     *
     * @return {@code true} if the subscriber is active
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() throws IOException {
        stop();
        scheduler.shutdownNow();
        subscriptions.clear();
        LOG.info("GENA subscriber closed");
    }

    private void setupCallbackHandler() {
        callbackServer.createContext("/callback/", exchange -> {
            try {
                if ("NOTIFY".equals(exchange.getRequestMethod())) {
                    var sid = exchange.getRequestHeaders().getFirst(GenaConstants.HEADER_SID);
                    var seqStr = exchange.getRequestHeaders().getFirst(GenaConstants.HEADER_SEQ);
                    var seq = seqStr != null ? Long.parseLong(seqStr) : 0L;

                    var body = new String(exchange.getRequestBody().readAllBytes());
                    var event = EventMessage.parseXml(sid != null ? sid : "", seq, body);

                    notifyListeners(event);

                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(405, 0);
                }
            } catch (Exception e) {
                LOG.warn("Error handling GENA callback", e);
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        });
    }

    private void notifyListeners(EventMessage event) {
        for (var listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.warn("GENA listener threw exception", e);
            }
        }
    }

    private void renewExpiring() {
        for (var entry : subscriptions.entrySet()) {
            var subscription = entry.getValue();
            if (subscription.shouldRenew() && !subscription.isExpired()) {
                try {
                    renew(subscription);
                } catch (Exception e) {
                    LOG.warn("Failed to renew subscription SID={}", subscription.sid(), e);
                }
            }
        }
    }
}
