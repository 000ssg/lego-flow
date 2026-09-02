package ssg.legoflow.database.redis.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
/**
 * In-memory key-value store with TTL expiration support.
 *
 * <p>Each database stores values of different types (strings, lists, sets,
 * sorted sets, hashes, streams) in a single key namespace. Keys are expired
 * lazily on access and actively via periodic sweeps.
 *
 * @since 0.1.0
 */
public final class Database {

    private final int index;
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private final Map<String, DataType> types = new ConcurrentHashMap<>();
    private final KeyExpiration expiration = new KeyExpiration();
    private final Map<String, StreamStore> streams = new ConcurrentHashMap<>();

    /**
     * Creates a database with the given index.
     *
     * @param index the database index (0-15)
     */
    public Database(int index) {
        this.index = index;
    }

    /**
     * Returns the database index.
     *
     * @return index
     */
    public int index() {
        return index;
    }

    /**
     * Returns the key expiration tracker.
     *
     * @return expiration manager
     */
    public KeyExpiration expiration() {
        return expiration;
    }

    // ---- Key operations ----

    /**
     * Returns whether the key exists and is not expired.
     *
     * @param key the key
     * @return true if key exists and is valid
     */
    public boolean exists(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
            return false;
        }
        return data.containsKey(key);
    }

    /**
     * Returns the data type of the key, or NONE if it does not exist.
     *
     * @param key the key
     * @return the data type
     */
    public DataType type(String key) {
        if (!exists(key)) {
            return DataType.NONE;
        }
        return types.getOrDefault(key, DataType.NONE);
    }

    /**
     * Deletes a key and its associated expiration.
     *
     * @param key the key
     * @return true if the key existed
     */
    public boolean delete(String key) {
        expiration.remove(key);
        types.remove(key);
        streams.remove(key);
        return data.remove(key) != null;
    }

    /**
     * Renames a key.
     *
     * @param oldKey the old key name
     * @param newKey the new key name
     * @return true if the old key existed
     */
    public boolean rename(String oldKey, String newKey) {
        Object value = data.remove(oldKey);
        if (value == null) {
            return false;
        }
        DataType dt = types.remove(oldKey);
        data.put(newKey, value);
        if (dt != null) {
            types.put(newKey, dt);
        }
        // Transfer expiration if any
        long ttl = expiration.ttlMillis(oldKey);
        expiration.remove(oldKey);
        if (ttl > 0) {
            expiration.setTtlMillis(newKey, ttl);
        }
        // Transfer stream if any
        StreamStore stream = streams.remove(oldKey);
        if (stream != null) {
            streams.put(newKey, stream);
        }
        return true;
    }

    /**
     * Returns a random key from the database, or null if empty.
     *
     * @return a random key or null
     */
    public String randomKey() {
        expireActiveKeys();
        var keys = new ArrayList<>(data.keySet());
        if (keys.isEmpty()) {
            return null;
        }
        return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
    }

    /**
     * Returns all keys matching the given glob pattern.
     *
     * @param pattern glob pattern (*, ?, [])
     * @return matching keys
     */
    public Set<String> keys(String pattern) {
        expireActiveKeys();
        Pattern regex = globToRegex(pattern);
        Set<String> result = new LinkedHashSet<>();
        for (String key : data.keySet()) {
            if (regex.matcher(key).matches() && !expiration.isExpired(key)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Scans keys matching a pattern with cursor-based iteration.
     *
     * @param cursor  the cursor position
     * @param pattern glob pattern (may be null for all keys)
     * @param count   hint for number of results
     * @return a pair of [nextCursor, matchedKeys]
     */
    public ScanResult scan(int cursor, String pattern, int count) {
        expireActiveKeys();
        List<String> allKeys = new ArrayList<>(data.keySet());
        allKeys.removeIf(k -> expiration.isExpired(k));

        Pattern regex = pattern != null ? globToRegex(pattern) : null;
        List<String> matched = new ArrayList<>();
        int pos = cursor;
        int scanned = 0;

        while (pos < allKeys.size() && scanned < count) {
            String key = allKeys.get(pos);
            if (regex == null || regex.matcher(key).matches()) {
                matched.add(key);
            }
            pos++;
            scanned++;
        }

        int nextCursor = pos >= allKeys.size() ? 0 : pos;
        return new ScanResult(nextCursor, matched);
    }

    /**
     * Result of a SCAN operation.
     *
     * @param cursor  the next cursor position (0 = complete)
     * @param keys    the matched keys
     */
    public record ScanResult(int cursor, List<String> keys) {}

    // ---- String operations ----

    /**
     * Sets a string value.
     *
     * @param key   the key
     * @param value the value
     */
    public void setString(String key, byte[] value) {
        data.put(key, value);
        types.put(key, DataType.STRING);
    }

    /**
     * Gets a string value, or null if key doesn't exist or is expired.
     *
     * @param key the key
     * @return the value or null
     */
    public byte[] getString(String key) {
        if (!exists(key)) {
            return null;
        }
        Object v = data.get(key);
        return v instanceof byte[] bytes ? bytes : null;
    }

    // ---- List operations ----

    /**
     * Gets or creates a list for the key.
     *
     * @param key the key
     * @return the list
     */
    @SuppressWarnings("unchecked")
    public Deque<byte[]> getOrCreateList(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.LIST);
        return (Deque<byte[]>) data.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    }

    /**
     * Gets an existing list or null.
     *
     * @param key the key
     * @return the list or null
     */
    @SuppressWarnings("unchecked")
    public Deque<byte[]> getList(String key) {
        if (!exists(key)) return null;
        Object v = data.get(key);
        return v instanceof Deque ? (Deque<byte[]>) v : null;
    }

    // ---- Set operations ----

    /**
     * Gets or creates a set for the key.
     *
     * @param key the key
     * @return the set
     */
    @SuppressWarnings("unchecked")
    public Set<String> getOrCreateSet(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.SET);
        return (Set<String>) data.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Gets an existing set or null.
     *
     * @param key the key
     * @return the set or null
     */
    @SuppressWarnings("unchecked")
    public Set<String> getSet(String key) {
        if (!exists(key)) return null;
        Object v = data.get(key);
        return v instanceof Set ? (Set<String>) v : null;
    }

    // ---- Sorted set operations ----

    /**
     * Gets or creates a sorted set for the key.
     *
     * @param key the key
     * @return the sorted set (TreeMap of score -> members)
     */
    @SuppressWarnings("unchecked")
    public NavigableMap<Double, Set<String>> getOrCreateZSet(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.ZSET);
        return (NavigableMap<Double, Set<String>>) data.computeIfAbsent(key,
                k -> Collections.synchronizedNavigableMap(new TreeMap<Double, Set<String>>()));
    }

    /**
     * Gets an existing sorted set or null.
     *
     * @param key the key
     * @return the sorted set or null
     */
    @SuppressWarnings("unchecked")
    public NavigableMap<Double, Set<String>> getZSet(String key) {
        if (!exists(key)) return null;
        Object v = data.get(key);
        return v instanceof NavigableMap ? (NavigableMap<Double, Set<String>>) v : null;
    }

    // We also need a member -> score reverse index for ZSCORE/ZRANK
    private final Map<String, Map<String, Double>> zsetScores = new ConcurrentHashMap<>();

    /**
     * Gets or creates the score index for a sorted set key.
     *
     * @param key the key
     * @return map of member to score
     */
    public Map<String, Double> getOrCreateZSetScores(String key) {
        return zsetScores.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    /**
     * Gets the score index for a sorted set key.
     *
     * @param key the key
     * @return map of member to score, or null
     */
    public Map<String, Double> getZSetScores(String key) {
        return zsetScores.get(key);
    }

    // ---- Hash operations ----

    /**
     * Gets or creates a hash for the key.
     *
     * @param key the key
     * @return the hash map
     */
    @SuppressWarnings("unchecked")
    public Map<String, byte[]> getOrCreateHash(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.HASH);
        return (Map<String, byte[]>) data.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    /**
     * Gets an existing hash or null.
     *
     * @param key the key
     * @return the hash map or null
     */
    @SuppressWarnings("unchecked")
    public Map<String, byte[]> getHash(String key) {
        if (!exists(key)) return null;
        Object v = data.get(key);
        return v instanceof Map ? (Map<String, byte[]>) v : null;
    }

    // ---- Stream operations ----

    /**
     * Gets or creates a stream store for the key.
     *
     * @param key the key
     * @return the stream store
     */
    public StreamStore getOrCreateStream(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.STREAM);
        data.putIfAbsent(key, "stream");
        return streams.computeIfAbsent(key, k -> new StreamStore());
    }

    /**
     * Gets an existing stream store or null.
     *
     * @param key the key
     * @return the stream store or null
     */
    public StreamStore getStream(String key) {
        if (!exists(key)) return null;
        return streams.get(key);
    }

    // ---- HyperLogLog operations ----

    /**
     * Gets or creates a HyperLogLog for the key.
     *
     * @param key the key
     * @return the HyperLogLog
     */
    public HyperLogLog getOrCreateHyperLogLog(String key) {
        if (expiration.isExpired(key)) {
            delete(key);
        }
        types.putIfAbsent(key, DataType.HYPERLOGLOG);
        return (HyperLogLog) data.computeIfAbsent(key, k -> new HyperLogLog());
    }

    /**
     * Gets an existing HyperLogLog or null.
     *
     * @param key the key
     * @return the HyperLogLog or null
     */
    public HyperLogLog getHyperLogLog(String key) {
        if (!exists(key)) return null;
        Object v = data.get(key);
        return v instanceof HyperLogLog hll ? hll : null;
    }

    // ---- Database operations ----

    /**
     * Returns the number of keys in the database (excluding expired).
     *
     * @return key count
     */
    public int size() {
        expireActiveKeys();
        return data.size();
    }

    /**
     * Removes all keys from the database.
     */
    public void flush() {
        data.clear();
        types.clear();
        expiration.clear();
        zsetScores.clear();
        streams.clear();
    }

    /**
     * Runs active expiration: removes a batch of expired keys.
     */
    public void expireActiveKeys() {
        Set<String> expired = expiration.getExpiredKeys();
        for (String key : expired) {
            delete(key);
        }
    }

    /**
     * Removes the key if it is empty (list, set, hash with no elements).
     *
     * @param key the key
     */
    public void removeIfEmpty(String key) {
        Object v = data.get(key);
        boolean empty = false;
        if (v instanceof Deque<?> d) {
            empty = d.isEmpty();
        } else if (v instanceof Set<?> s) {
            empty = s.isEmpty();
        } else if (v instanceof Map<?, ?> m) {
            empty = m.isEmpty();
        } else if (v instanceof NavigableMap<?, ?> m) {
            empty = m.isEmpty();
        }
        if (empty) {
            delete(key);
        }
    }

    // ---- Utility ----

    static Pattern globToRegex(String glob) {
        var sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '[' -> sb.append('[');
                case ']' -> sb.append(']');
                case '\\' -> {
                    sb.append("\\\\");
                }
                case '.' -> sb.append("\\.");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '+' -> sb.append("\\+");
                case '^' -> sb.append("\\^");
                case '$' -> sb.append("\\$");
                case '{' -> sb.append("\\{");
                case '}' -> sb.append("\\}");
                case '|' -> sb.append("\\|");
                default -> sb.append(c);
            }
        }
        return Pattern.compile(sb.toString());
    }
}
