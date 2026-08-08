package ssg.legoflow.database.redis.server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks key expiration times with lazy and active eviction.
 *
 * <p>Expiration times are stored as absolute millisecond timestamps.
 * Lazy expiration checks on access; active expiration runs periodically
 * to reclaim memory from expired but unaccessed keys.
 *
 * @since 0.1.0
 */
public final class KeyExpiration {

    private final Map<String, Long> expirations = new ConcurrentHashMap<>();

    /**
     * Sets the expiration time for a key.
     *
     * @param key          the key
     * @param expireAtMs   absolute expiration time in milliseconds
     */
    public void setExpiration(String key, long expireAtMs) {
        expirations.put(key, expireAtMs);
    }

    /**
     * Sets a TTL in seconds for a key.
     *
     * @param key     the key
     * @param seconds TTL in seconds
     */
    public void setTtlSeconds(String key, long seconds) {
        expirations.put(key, System.currentTimeMillis() + seconds * 1000);
    }

    /**
     * Sets a TTL in milliseconds for a key.
     *
     * @param key    the key
     * @param millis TTL in milliseconds
     */
    public void setTtlMillis(String key, long millis) {
        expirations.put(key, System.currentTimeMillis() + millis);
    }

    /**
     * Removes the expiration for a key (makes it persistent).
     *
     * @param key the key
     * @return true if an expiration was removed
     */
    public boolean persist(String key) {
        return expirations.remove(key) != null;
    }

    /**
     * Checks if a key has expired. Does NOT remove the key.
     *
     * @param key the key
     * @return true if expired
     */
    public boolean isExpired(String key) {
        Long expireAt = expirations.get(key);
        if (expireAt == null) {
            return false;
        }
        return System.currentTimeMillis() >= expireAt;
    }

    /**
     * Returns the TTL in seconds for a key.
     *
     * @param key the key
     * @return TTL in seconds, -1 if no expiry, -2 if expired
     */
    public long ttlSeconds(String key) {
        Long expireAt = expirations.get(key);
        if (expireAt == null) {
            return -1;
        }
        long remaining = (expireAt - System.currentTimeMillis()) / 1000;
        return remaining < 0 ? -2 : remaining;
    }

    /**
     * Returns the TTL in milliseconds for a key.
     *
     * @param key the key
     * @return TTL in milliseconds, -1 if no expiry, -2 if expired
     */
    public long ttlMillis(String key) {
        Long expireAt = expirations.get(key);
        if (expireAt == null) {
            return -1;
        }
        long remaining = expireAt - System.currentTimeMillis();
        return remaining < 0 ? -2 : remaining;
    }

    /**
     * Removes expiration tracking for a key (called when key is deleted).
     *
     * @param key the key
     */
    public void remove(String key) {
        expirations.remove(key);
    }

    /**
     * Returns all keys that have expired (for active expiration sweep).
     *
     * @return set of expired keys
     */
    public Set<String> getExpiredKeys() {
        long now = System.currentTimeMillis();
        Set<String> expired = ConcurrentHashMap.newKeySet();
        expirations.forEach((key, expireAt) -> {
            if (now >= expireAt) {
                expired.add(key);
            }
        });
        return expired;
    }

    /**
     * Returns whether the key has an expiration set.
     *
     * @param key the key
     * @return true if expiration is set
     */
    public boolean hasExpiration(String key) {
        return expirations.containsKey(key);
    }

    /**
     * Clears all expiration data.
     */
    public void clear() {
        expirations.clear();
    }

    /**
     * Returns the number of keys with expiration set.
     *
     * @return count
     */
    public int size() {
        return expirations.size();
    }
}
