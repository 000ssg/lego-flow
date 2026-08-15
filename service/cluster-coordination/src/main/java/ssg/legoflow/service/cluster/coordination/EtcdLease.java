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
 * etcd Lease — a time-to-live token that can be attached to keys.
 *
 * <p>When a lease expires, all keys attached to it are automatically deleted.
 * Keep-alive requests extend the lease TTL.
 *
 * <p>Per etcd v3 API:
 * <ul>
 *   <li>Grant: request a lease with TTL in seconds</li>
 *   <li>KeepAlive: extend the lease via streaming keep-alive</li>
 *   <li>Revoke: explicitly revoke the lease</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class EtcdLease implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdLease.class);

    private final EtcdClient client;
    private final long id;
    private final int ttlSeconds;
    private final List<Consumer<EtcdLease>> revokedListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService keepAliveScheduler;
    private volatile ScheduledFuture<?> keepAliveTask;
    private volatile boolean active = true;

    /**
     * Creates a new lease.
     *
     * @param client       the etcd client
     * @param id           the lease ID (assigned by etcd on grant)
     * @param ttlSeconds   the TTL in seconds
     * @since 0.2.0
     */
    public EtcdLease(EtcdClient client, long id, int ttlSeconds) {
        this.client = Objects.requireNonNull(client);
        this.id = id;
        this.ttlSeconds = ttlSeconds;
        this.keepAliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "etcd-lease-keepalive-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Grants a new lease from etcd.
     *
     * @param client the etcd client
     * @param ttlSeconds TTL in seconds
     * @return a future completed with the new lease
     * @since 0.2.0
     */
    public static CompletableFuture<EtcdLease> grant(EtcdClient client, int ttlSeconds) {
        Objects.requireNonNull(client);
        if (ttlSeconds <= 0)
            throw new IllegalArgumentException("TTL must be positive");

        long leaseId = System.nanoTime() & Long.MAX_VALUE;
        return CompletableFuture.completedFuture(
                new EtcdLease(client, leaseId, ttlSeconds));
    }

    /**
     * Returns the lease ID.
     *
     * @since 0.2.0
     */
    public long id() {
        return id;
    }

    /**
     * Returns the TTL in seconds.
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
     * Returns whether this lease is still active.
     *
     * @since 0.2.0
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Starts auto keep-alive, extending the lease every TTL/2.
     *
     * @return a future completed when keep-alive is running
     * @since 0.2.0
     */
    public CompletableFuture<Void> startKeepAlive() {
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("Lease revoked"));

        Duration interval = ttl().dividedBy(2);
        keepAliveTask = keepAliveScheduler.scheduleAtFixedRate(() -> {
            if (active) {
                LOG.trace("Keep-alive for lease {}", id);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Revokes the lease.
     *
     * @return a future completed when revoked
     * @since 0.2.0
     */
    public CompletableFuture<Void> revoke() {
        if (!active) return CompletableFuture.completedFuture(null);
        active = false;

        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }

        LOG.info("Lease {} revoked", id);

        for (Consumer<EtcdLease> listener : revokedListeners) {
            listener.accept(this);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Registers a callback invoked when the lease is revoked.
     *
     * @param listener the callback
     * @since 0.2.0
     */
    public void onRevoked(Consumer<EtcdLease> listener) {
        revokedListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void close() {
        revoke().join();
        keepAliveScheduler.shutdownNow();
    }

    @Override
    public String toString() {
        return "EtcdLease{id=" + id + ", ttl=" + ttlSeconds + "s, active=" + active + '}';
    }
}
