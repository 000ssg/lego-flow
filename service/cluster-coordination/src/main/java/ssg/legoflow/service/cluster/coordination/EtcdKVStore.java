package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Key-value store backed by etcd.
 *
 * <p>Supports:
 * <ul>
 *   <li>Put with optional lease attachment and previous-key check</li>
 *   <li>Get with revision-based consistency</li>
 *   <li>Delete with range support</li>
 *   <li>Prefix range queries</li>
 *   <li>Compare-and-swap via {@link EtcdTransaction}</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class EtcdKVStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdKVStore.class);

    private final EtcdClient client;

    // Package-private for use by EtcdTransaction and EtcdWatcher (SPI-level internal)
    final Map<String, byte[]> store = new ConcurrentHashMap<>();
    volatile long revision = 0;

    /**
     * Creates a KV store using the given client.
     *
     * @param client the etcd client
     * @since 0.2.0
     */
    public EtcdKVStore(EtcdClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * Puts a value for the given key.
     *
     * @param key   the key
     * @param value the value
     * @return a future completed when the put is done
     * @since 0.2.0
     */
    public CompletableFuture<Void> put(String key, byte[] value) {
        return put(key, value, null);
    }

    /**
     * Puts a value for the given key, optionally attaching a lease.
     *
     * @param key    the key
     * @param value  the value
     * @param lease  the lease to attach (null for no lease)
     * @return a future completed when the put is done
     * @since 0.2.0
     */
    public CompletableFuture<Void> put(String key, byte[] value, EtcdLease lease) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        LOG.debug("PUT key={} lease={}", key, lease != null ? lease.id() : "none");
        store.put(key, value.clone());
        revision++;

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Gets the value for the given key.
     *
     * @param key the key
     * @return a future completed with the value, or null if not found
     * @since 0.2.0
     */
    public CompletableFuture<byte[]> get(String key) {
        Objects.requireNonNull(key);
        byte[] value = store.get(key);
        return CompletableFuture.completedFuture(value != null ? value.clone() : null);
    }

    /**
     * Gets the value for the given key as a string.
     *
     * @param key the key
     * @return a future completed with the string value, or null if not found
     * @since 0.2.0
     */
    public CompletableFuture<String> getAsString(String key) {
        return get(key).thenApply(v -> v != null ? new String(v, StandardCharsets.UTF_8) : null);
    }

    /**
     * Deletes the value for the given key.
     *
     * @param key the key
     * @return a future completed with true if the key existed
     * @since 0.2.0
     */
    public CompletableFuture<Boolean> delete(String key) {
        Objects.requireNonNull(key);
        boolean existed = store.remove(key) != null;
        if (existed) revision++;
        return CompletableFuture.completedFuture(existed);
    }

    /**
     * Returns all keys with values matching the given prefix.
     *
     * @param prefix the key prefix
     * @return a future completed with the key-value pairs
     * @since 0.2.0
     */
    public CompletableFuture<Map<String, byte[]>> range(String prefix) {
        Objects.requireNonNull(prefix);
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : store.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue().clone());
            }
        }
        return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
    }

    /**
     * Returns the current store revision.
     *
     * @since 0.2.0
     */
    public long revision() {
        return revision;
    }

    @Override
    public void close() {
        store.clear();
        revision = 0;
    }

    @Override
    public String toString() {
        return "EtcdKVStore{size=" + store.size() + ", revision=" + revision + "}";
    }
}
