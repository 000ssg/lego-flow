package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
/**
 * Watches for key changes in etcd.
 *
 * <p>Implements the etcd v3 Watch API:
 * <ul>
 *   <li>Server-streaming watch for a key or prefix</li>
 *   <li>Events ordered by revision number</li>
 *   <li>Gap detection for missed events</li>
 * </ul>
 *
 * <p>In the SPI implementation, this polls the store for changes
 * and emits events. A real implementation would use gRPC streaming.
 *
 * @since 0.2.0
 */
public final class EtcdWatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdWatcher.class);

    private final EtcdKVStore store;
    private final String keyPrefix;
    private final List<Consumer<WatchEvent>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean watching = false;
    private volatile long lastRevision;

    /**
     * A watch event notifying of a key change.
     *
     * @param type      the event type
     * @param key       the affected key
     * @param value     the new value (null for delete)
     * @param revision  the revision at which this event occurred
     * @since 0.2.0
     */
    public record WatchEvent(EventType type, String key, byte[] value, long revision) {
        @Override
        public String toString() {
            return "WatchEvent{" + type + " key=" + key + " rev=" + revision + '}';
        }
    }

    /**
     * The type of change observed.
     *
     * @since 0.2.0
     */
    public enum EventType {
        /** Key was created or updated. */
        PUT,
        /** Key was deleted. */
        DELETE
    }

    /**
     * Creates a new watcher.
     *
     * @param store     the KV store to watch
     * @param keyPrefix the key prefix to watch (null for exact key)
     * @since 0.2.0
     */
    public EtcdWatcher(EtcdKVStore store, String keyPrefix) {
        this.store = Objects.requireNonNull(store);
        this.keyPrefix = keyPrefix;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etcd-watcher-" + keyPrefix);
            t.setDaemon(true);
            return t;
        });
        this.lastRevision = store.revision;
    }

    /**
     * Starts watching for changes.
     *
     * @since 0.2.0
     */
    public void start() {
        if (watching) return;
        watching = true;
        lastRevision = store.revision;

        LOG.debug("Watching prefix '{}', lastRevision={}", keyPrefix, lastRevision);

        scheduler.scheduleAtFixedRate(() -> {
            if (!watching) return;
            long currentRevision = store.revision;
            if (currentRevision > lastRevision) {
                // Detect the change by scanning the store
                detectChanges();
                lastRevision = currentRevision;
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Detects changes since the last revision.
     *
     * @since 0.2.0
     */
    private void detectChanges() {
        // SPI-level: emit a generic PUT event for the prefix
        // A real implementation uses etcd watch streaming with actual diffs
        String key = keyPrefix != null ? keyPrefix : "";
        WatchEvent event = new WatchEvent(EventType.PUT, key, null, store.revision);
        for (Consumer<WatchEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    /**
     * Registers a callback for watch events.
     *
     * @param listener the callback
     * @since 0.2.0
     */
    public void onEvent(Consumer<WatchEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Returns the key prefix being watched.
     *
     * @since 0.2.0
     */
    public String keyPrefix() {
        return keyPrefix;
    }

    /**
     * Returns the last seen revision.
     *
     * @since 0.2.0
     */
    public long lastRevision() {
        return lastRevision;
    }

    @Override
    public void close() {
        watching = false;
        scheduler.shutdownNow();
        listeners.clear();
    }
}
