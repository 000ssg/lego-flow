package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A session that maintains a lease with automatic keep-alive.
 *
 * <p>Combines {@link EtcdLease} with a scheduled keep-alive timer,
 * providing a reliable session that survives transient network issues.
 * When the session is closed or the lease is revoked, all attached
 * resources are cleaned up.
 *
 * <p>Per etcd session semantics:
 * <ul>
 *   <li>Create a lease with the desired TTL</li>
 *   <li>Start automatic keep-alive every TTL/2</li>
 *   <li>Use the session to create distributed locks or register nodes</li>
 *   <li>Close the session to revoke the lease and release all resources</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class EtcdSession implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdSession.class);

    private final EtcdClient client;
    private final EtcdLease lease;
    private final int ttlSeconds;
    private final List<Consumer<EtcdSession>> closedListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> keepAliveTask;
    private volatile boolean active = true;

    /**
     * Creates a session with the given lease.
     *
     * @param client the etcd client
     * @param lease  the lease backing this session
     * @since 0.2.0
     */
    private EtcdSession(EtcdClient client, EtcdLease lease, int ttlSeconds) {
        this.client = Objects.requireNonNull(client);
        this.lease = Objects.requireNonNull(lease);
        this.ttlSeconds = ttlSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "etcd-session-keepalive-" + lease.id());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates and starts a new session.
     *
     * @param client the etcd client
     * @param ttlSeconds TTL in seconds
     * @return a future completed with the new session
     * @since 0.2.0
     */
    public static CompletableFuture<EtcdSession> create(EtcdClient client, int ttlSeconds) {
        Objects.requireNonNull(client);
        if (ttlSeconds <= 0)
            throw new IllegalArgumentException("TTL must be positive");

        return EtcdLease.grant(client, ttlSeconds).thenCompose(lease -> {
            EtcdSession session = new EtcdSession(client, lease, ttlSeconds);
            return lease.startKeepAlive()
                    .thenApply(v -> session)
                    .whenComplete((s, err) -> {
                        if (err != null) {
                            session.active = false;
                        }
                    });
        });
    }

    /**
     * Returns the backing lease.
     *
     * @since 0.2.0
     */
    public EtcdLease lease() {
        return lease;
    }

    /**
     * Returns the session TTL in seconds.
     *
     * @since 0.2.0
     */
    public int ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * Returns the TTL as a Duration.
     *
     * @since 0.2.0
     */
    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Returns whether this session is still active.
     *
     * @since 0.2.0
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Registers a callback invoked when the session is closed.
     *
     * @param listener the callback
     * @since 0.2.0
     */
    public void onClosed(Consumer<EtcdSession> listener) {
        closedListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Starts keep-alive manually (used for testing).
     *
     * @since 0.2.0
     */
    void startKeepAlive() {
        if (!active) return;

        Duration interval = ttl().dividedBy(2);
        keepAliveTask = scheduler.scheduleAtFixedRate(() -> {
            if (active && lease.isActive()) {
                LOG.trace("Session keep-alive for lease {}", lease.id());
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Closes the session, revoking the lease.
     *
     * @return a future completed when closed
     * @since 0.2.0
     */
    public CompletableFuture<Void> closeAsync() {
        if (!active) return CompletableFuture.completedFuture(null);
        active = false;

        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }

        scheduler.shutdownNow();

        return lease.revoke().whenComplete((v, err) -> {
            LOG.info("Session {} closed", lease.id());
            for (Consumer<EtcdSession> listener : closedListeners) {
                listener.accept(this);
            }
        });
    }

    @Override
    public void close() {
        try {
            closeAsync().join();
        } catch (Exception e) {
            LOG.warn("Error closing session {}", lease.id(), e);
        }
    }

    @Override
    public String toString() {
        return "EtcdSession{lease=" + lease.id() + ", active=" + active + '}';
    }
}
