package ssg.legoflow.http.proxy.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default in-memory LRU cache store for the caching proxy.
 *
 * <p>Implements LRU eviction based on entry count and total size.
 * Thread-safe via read-write locking.</p>
 *
 * @since 0.1.0
 */
public class InMemoryProxyCacheStore implements ProxyCacheStore {

    private final int maxEntries;
    private final long maxSizeBytes;
    private final LinkedHashMap<String, CacheEntry> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong currentSizeBytes = new AtomicLong(0);
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    /**
     * Creates a new in-memory cache store with the specified limits.
     *
     * @param maxEntries the maximum number of entries
     * @param maxSizeBytes the maximum total size in bytes
     * @since 0.1.0
     */
    public InMemoryProxyCacheStore(int maxEntries, long maxSizeBytes) {
        this.maxEntries = maxEntries;
        this.maxSizeBytes = maxSizeBytes;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                if (size() > InMemoryProxyCacheStore.this.maxEntries) {
                    currentSizeBytes.addAndGet(-eldest.getValue().bodySize());
                    evictionCount.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Creates a cache store with default limits (10000 entries, 64 MB).
     *
     * @since 0.1.0
     */
    public InMemoryProxyCacheStore() {
        this(10_000, 64 * 1024 * 1024);
    }

    @Override
    public Optional<CacheEntry> get(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                missCount.incrementAndGet();
                return Optional.empty();
            }
            if (entry.isExpired()) {
                missCount.incrementAndGet();
                // Remove expired entry (upgrade to write lock)
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    CacheEntry current = cache.get(key);
                    if (current != null && current.isExpired()) {
                        cache.remove(key);
                        currentSizeBytes.addAndGet(-current.bodySize());
                    }
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
                return Optional.empty();
            }
            hitCount.incrementAndGet();
            return Optional.of(entry);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(String key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            // Remove old entry size if replacing
            CacheEntry old = cache.get(key);
            if (old != null) {
                currentSizeBytes.addAndGet(-old.bodySize());
            }

            // Evict if size limit would be exceeded
            while (currentSizeBytes.get() + entry.bodySize() > maxSizeBytes && !cache.isEmpty()) {
                var iterator = cache.entrySet().iterator();
                if (iterator.hasNext()) {
                    var eldest = iterator.next();
                    currentSizeBytes.addAndGet(-eldest.getValue().bodySize());
                    iterator.remove();
                    evictionCount.incrementAndGet();
                }
            }

            cache.put(key, entry);
            currentSizeBytes.addAndGet(entry.bodySize());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(String key) {
        lock.writeLock().lock();
        try {
            CacheEntry removed = cache.remove(key);
            if (removed != null) {
                currentSizeBytes.addAndGet(-removed.bodySize());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            currentSizeBytes.set(0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long sizeInBytes() {
        return currentSizeBytes.get();
    }

    /**
     * Returns the cache hit count.
     *
     * @return the hit count
     * @since 0.1.0
     */
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * Returns the cache miss count.
     *
     * @return the miss count
     * @since 0.1.0
     */
    public long getMissCount() {
        return missCount.get();
    }

    /**
     * Returns the cache eviction count.
     *
     * @return the eviction count
     * @since 0.1.0
     */
    public long getEvictionCount() {
        return evictionCount.get();
    }

    /**
     * Returns the cache hit ratio (0.0 to 1.0).
     *
     * @return the hit ratio
     * @since 0.1.0
     */
    public double getHitRatio() {
        long total = hitCount.get() + missCount.get();
        return total > 0 ? (double) hitCount.get() / total : 0.0;
    }

    /**
     * Returns the maximum number of entries.
     *
     * @return the max entries
     * @since 0.1.0
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * Returns the maximum size in bytes.
     *
     * @return the max size in bytes
     * @since 0.1.0
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }
}
