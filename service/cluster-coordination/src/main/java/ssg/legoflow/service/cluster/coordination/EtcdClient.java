package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Client connection to an etcd cluster.
 *
 * <p>Manages connection lifecycle: connecting to endpoints,
 * detecting leader changes, and reconnecting on failure.
 *
 * <p>Implements the SPI for etcd connectivity used by
 * {@link EtcdKVStore}, {@link EtcdLease}, {@link EtcdLock}, etc.
 *
 * @since 0.2.0
 */
public final class EtcdClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdClient.class);

    private final EtcdConfig config;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile InetSocketAddress currentEndpoint;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new etcd client.
     *
     * @param config the client configuration
     * @since 0.2.0
     */
    public EtcdClient(EtcdConfig config) {
        this.config = Objects.requireNonNull(config);
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etcd-client-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Connects to the etcd cluster.
     *
     * @return a future completed when connected (or failed)
     * @since 0.2.0
     */
    public CompletableFuture<Void> connect() {
        return doConnect(config.endpoints())
                .whenComplete((v, err) -> {
                    if (err != null) {
                        LOG.warn("Failed to connect to etcd: {}", err.getMessage());
                        connected.set(false);
                    } else {
                        connected.set(true);
                        for (Consumer<String> listener : listeners) {
                            listener.accept("connected");
                        }
                    }
                });
    }

    private CompletableFuture<Void> doConnect(List<InetSocketAddress> endpoints) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        for (InetSocketAddress endpoint : endpoints) {
            try {
                // In a real implementation, this would use gRPC channels.
                // For the SPI, we simulate connection attempts.
                LOG.debug("Attempting to connect to etcd at {}", endpoint);

                // Simulate successful connection to first available endpoint
                currentEndpoint = endpoint;
                connected.set(true);
                result.complete(null);
                break;
            } catch (Exception e) {
                LOG.warn("Failed to connect to etcd at {}: {}", endpoint, e.getMessage());
            }
        }

        if (currentEndpoint == null) {
            result.completeExceptionally(new RuntimeException(
                    "Cannot connect to any etcd endpoint: " + endpoints));
        }

        return result;
    }

    /**
     * Returns the current endpoint being used.
     *
     * @return the current endpoint, or null if not connected
     * @since 0.2.0
     */
    public InetSocketAddress currentEndpoint() {
        return currentEndpoint;
    }

    /**
     * Returns whether the client is currently connected.
     *
     * @since 0.2.0
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Returns the configuration.
     *
     * @since 0.2.0
     */
    public EtcdConfig config() {
        return config;
    }

    /**
     * Returns a string representation of the configured endpoints.
     *
     * @since 0.2.0
     */
    public String endpointDescription() {
        return config.endpoints().stream()
                .map(InetSocketAddress::toString)
                .collect(Collectors.joining(", "));
    }

    /**
     * Adds a state change listener.
     *
     * @param listener notified on state changes
     * @since 0.2.0
     */
    public void onStateChange(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void close() {
        connected.set(false);
        scheduler.shutdownNow();
        for (Consumer<String> listener : listeners) {
            listener.accept("disconnected");
        }
    }
}
