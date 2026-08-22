package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpResponse;
import java.util.Optional;
public interface ResponseCache {

    Optional<CachedResponse> get(String key);

    void put(String key, CachedResponse response);

    void remove(String key);

    /**
     * Invalidates a cache entry. Default implementation delegates to {@link #remove(String)}.
     *
     * @param key the cache entry key
     */
    default void invalidate(String key) { remove(key); }

    void clear();

    int size();

    record CachedResponse(HttpResponse response, long storedAt, int maxAge) {
        public boolean isExpired() {
            return System.currentTimeMillis() - storedAt > maxAge * 1000L;
        }
    }
}
