package ssg.legoflow.messaging.nats.cluster;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.client.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
/**
 * Cluster bus built on top of NATS for distributed messaging.
 *
 * <p>Provides request-reply, publish-subscribe, and health check
 * patterns tailored for cluster node communication.
 *
 * <p>Subject naming convention: {@code <clusterId>.<domain>.<action>}
 * Examples:
 * <ul>
 *   <li>{@code mycluster.heartbeat.node-A}</li>
 *   <li>{@code mycluster.events.NodeJoined}</li>
 *   <li>{@code mycluster.rpc.<method>}</li>
 * </ul>
 *
 * <p>All operations are wrapped to handle {@link IOException} internally,
 * returning {@link CompletableFuture} that completes exceptionally on I/O errors.
 *
 * @since 0.2.0
 */
public final class NatsClusterBus implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NatsClusterBus.class);

    private final NatsClusterConfig config;
    private final NatsClient client;
    private final ExecutorService executor;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Consumer<NatsMessage>> handlers = new ConcurrentHashMap<>();

    /**
     * Creates a cluster bus connected to the given NATS client.
     *
     * @param config the cluster configuration
     * @param client the NATS client (must be connected)
     */
    public NatsClusterBus(NatsClusterConfig config, NatsClient client) {
        this(config, client, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a cluster bus with a custom executor for async operations.
     *
     * @param config the cluster configuration
     * @param client the NATS client (must be connected)
     * @param executor executor for async request operations
     */
    public NatsClusterBus(NatsClusterConfig config, NatsClient client, ExecutorService executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Returns the cluster prefix for all subjects.
     */
    String clusterPrefix() {
        return config.clusterId();
    }

    /**
     * Publishes a message to the cluster (fire-and-forget).
     *
     * <p>This is a synchronous operation that wraps I/O errors in a
     * {@link CompletableFuture}.
     *
     * @param subject the cluster-relative subject (e.g., "events.NodeJoined")
     * @param payload the message payload
     * @return a future that completes when the publish succeeds
     */
    public CompletableFuture<Void> publish(String subject, byte[] payload) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        String fullSubject = clusterPrefix() + "." + subject;
        return CompletableFuture.runAsync(() -> {
            try {
                client.publish(fullSubject, payload);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    /**
     * Publishes a UTF-8 string message.
     *
     * @param subject the cluster-relative subject
     * @param payload the string payload
     * @return a future that completes when the publish succeeds
     */
    public CompletableFuture<Void> publish(String subject, String payload) {
        return publish(subject, payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends a request and waits for a reply asynchronously.
     *
     * @param subject the cluster-relative subject
     * @param payload the request payload
     * @return future completing with the reply bytes
     */
    public CompletableFuture<byte[]> request(String subject, byte[] payload) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        String fullSubject = clusterPrefix() + "." + subject;
        return CompletableFuture.supplyAsync(() -> {
            try {
                NatsMessage reply = client.request(fullSubject, payload, config.requestTimeout());
                if (reply == null) {
                    throw new RuntimeException(
                            new java.util.concurrent.TimeoutException(
                                    "Request timed out: " + subject));
                }
                return reply.payload();
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    /**
     * Sends a request with a string payload and returns a string reply.
     *
     * @param subject the cluster-relative subject
     * @param payload the request payload
     * @return future completing with the reply string
     */
    public CompletableFuture<String> request(String subject, String payload) {
        return request(subject, payload.getBytes(StandardCharsets.UTF_8))
                .thenApply(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Subscribes to a cluster subject.
     *
     * @param subject the cluster-relative subject (supports wildcards: * and >)
     * @param handler message handler
     * @return the subscription handle
     */
    public Subscription subscribe(String subject, Consumer<NatsMessage> handler) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        String fullSubject = clusterPrefix() + "." + subject;
        Subscription sub;
        try {
            sub = client.subscribe(fullSubject, handler);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        subscriptions.put(fullSubject, sub);
        handlers.put(fullSubject, handler);
        return sub;
    }

    /**
     * Registers a request handler for a subject.
     *
     * @param subject the cluster-relative subject
     * @param handler function mapping request payload to reply payload
     */
    public void handleRequests(String subject, Function<byte[], byte[]> handler) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        String fullSubject = clusterPrefix() + "." + subject;
        Subscription sub;
        try {
            sub = client.subscribe(fullSubject, msg -> {
            try {
                byte[] reply = handler.apply(msg.payload());
                if (msg.replyTo() != null && !msg.replyTo().isEmpty()) {
                    client.publish(msg.replyTo(), reply);
                }
            } catch (java.io.IOException e) {
                LOG.error("Error sending reply for subject {}", fullSubject, e);
            } catch (Exception e) {
                LOG.error("Error handling request for subject {}", fullSubject, e);
            }
        });
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        subscriptions.put(fullSubject, sub);
        handlers.put(fullSubject, msg -> handler.apply(msg.payload()));
    }

    /**
     * Broadcasts a message to all nodes in the cluster using the wildcard subject.
     *
     * @param subject the cluster-relative subject
     * @param payload the message payload
     * @return a future that completes when the broadcast succeeds
     */
    public CompletableFuture<Void> broadcast(String subject, byte[] payload) {
        return publish(subject, payload);
    }

    /**
     * Broadcasts a string message.
     *
     * @param subject the cluster-relative subject
     * @param payload the string payload
     * @return a future that completes when the broadcast succeeds
     */
    public CompletableFuture<Void> broadcast(String subject, String payload) {
        return publish(subject, payload);
    }

    /**
     * Returns the node ID from the config.
     */
    public String nodeId() {
        return config.nodeId();
    }

    /**
     * Returns the cluster ID from the config.
     */
    public String clusterId() {
        return config.clusterId();
    }

    /**
     * Returns the underlying NATS client.
     */
    public NatsClient client() {
        return client;
    }

    /**
     * Returns active subscriptions.
     */
    public List<Subscription> subscriptions() {
        return List.copyOf(subscriptions.values());
    }

    /**
     * Unsubscribes from a previously subscribed subject.
     *
     * @param fullSubject the full subject (including cluster prefix)
     * @return true if the subscription was removed
     */
    boolean unsubscribe(String fullSubject) {
        Subscription sub = subscriptions.remove(fullSubject);
        if (sub != null) {
            sub.unsubscribe();
            handlers.remove(fullSubject);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        subscriptions.values().forEach(Subscription::unsubscribe);
        subscriptions.clear();
        handlers.clear();
    }

    /**
     * Wrapper for checked IOException in unchecked context.
     */
    private static final class UncheckedIOException extends RuntimeException {
        UncheckedIOException(java.io.IOException cause) {
            super(cause);
        }
    }
}
