package ssg.legoflow.database.redis.server;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles MULTI/EXEC transactions with WATCH-based optimistic locking.
 *
 * <p>When a client issues MULTI, subsequent commands are queued rather than
 * executed immediately. EXEC atomically executes all queued commands and
 * returns their results as an array. If any WATCHed keys were modified
 * by another client between WATCH and EXEC, the transaction is aborted
 * and EXEC returns a null array.
 *
 * @since 0.1.0
 */
public final class TransactionExecutor {

    /**
     * Transaction state for a single client.
     */
    public static final class TransactionState {
        private final List<CommandArgs> queue = new ArrayList<>();
        private final Set<String> watchedKeys = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> watchedVersions = new ConcurrentHashMap<>();
        private boolean inTransaction = false;

        public boolean isInTransaction() { return inTransaction; }
        public void begin() { inTransaction = true; queue.clear(); }
        public void enqueue(CommandArgs args) { queue.add(args); }
        public List<CommandArgs> queue() { return queue; }
        public Set<String> watchedKeys() { return watchedKeys; }
        public Map<String, Long> watchedVersions() { return watchedVersions; }

        public void discard() {
            inTransaction = false;
            queue.clear();
        }

        public void reset() {
            inTransaction = false;
            queue.clear();
            watchedKeys.clear();
            watchedVersions.clear();
        }
    }

    // Global key version counter for optimistic locking
    private final Map<String, Long> keyVersions = new ConcurrentHashMap<>();

    /**
     * Returns the current version of a key (for WATCH).
     *
     * @param key the key
     * @return current version, or 0 if never modified
     */
    public long getKeyVersion(String key) {
        return keyVersions.getOrDefault(key, 0L);
    }

    /**
     * Increments the version of a key (called when a key is modified).
     *
     * @param key the key
     */
    public void touchKey(String key) {
        keyVersions.merge(key, 1L, Long::sum);
    }

    /**
     * Watches keys for a client, recording their current versions.
     *
     * @param state the client's transaction state
     * @param keys  the keys to watch
     */
    public void watch(TransactionState state, String... keys) {
        for (String key : keys) {
            state.watchedKeys().add(key);
            state.watchedVersions().put(key, getKeyVersion(key));
        }
    }

    /**
     * Checks whether any watched keys have been modified.
     *
     * @param state the client's transaction state
     * @return true if all watched keys are unmodified
     */
    public boolean validateWatch(TransactionState state) {
        for (var entry : state.watchedVersions().entrySet()) {
            if (getKeyVersion(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Executes a transaction: validates watches, runs all queued commands atomically.
     *
     * @param state    the client's transaction state
     * @param registry the command registry
     * @param client   the client connection
     * @return array of results, or null array if watch validation failed
     */
    public RespType exec(TransactionState state, CommandRegistry registry, ClientConnection client) {
        if (!validateWatch(state)) {
            state.reset();
            return RespType.Array.NULL;
        }

        List<RespType> results = new ArrayList<>();
        for (CommandArgs args : state.queue()) {
            try {
                RespType result = registry.dispatch(args, client);
                results.add(result);
            } catch (Exception e) {
                results.add(new RespType.Error("ERR", e.getMessage()));
            }
        }
        state.reset();
        return new RespType.Array(results);
    }
}
