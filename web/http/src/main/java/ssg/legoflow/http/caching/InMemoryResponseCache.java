package ssg.legoflow.http.caching;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryResponseCache implements ResponseCache {

    private final Map<String, CachedResponse> cache;
    private final int maxEntries;

    public InMemoryResponseCache() {
        this(1000);
    }

    public InMemoryResponseCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedResponse> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public synchronized Optional<CachedResponse> get(String key) {
        var entry = cache.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public synchronized void put(String key, CachedResponse response) {
        cache.put(key, response);
    }

    @Override
    public synchronized void remove(String key) {
        cache.remove(key);
    }

    @Override
    public synchronized void clear() {
        cache.clear();
    }

    @Override
    public synchronized int size() {
        return cache.size();
    }
}
