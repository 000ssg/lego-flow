package ssg.legoflow.http.cluster;

import ssg.legoflow.http.caching.ResponseCache;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link HttpCacheInvalidator}.
 * Verifies spec: write interception, path extraction, event publishing,
 * local invalidation, duplicate filtering, and scope matching.
 */
class HttpCacheInvalidatorTest {

    private static final String NODE_A = "node-A";
    private static final String NODE_B = "node-B";

    // ── Test helpers ──

    private ResponseCache newCache() {
        return new InMemoryResponseCache();
    }

    private HttpCacheInvalidator invalidator(CacheCoherenceConfig config,
                                              ResponseCache cache,
                                              Consumer<HttpCacheInvalidator.CacheInvalidationEvent> publisher) {
        return new HttpCacheInvalidator(config, cache, NODE_A, publisher);
    }

    // ── Constructor tests ──

    @Test
    void constructor_null_config_throws() {
        assertThatThrownBy(() -> new HttpCacheInvalidator(null, newCache(), NODE_A, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void constructor_null_cache_throws() {
        var config = CacheCoherenceConfig.builder().build();
        assertThatThrownBy(() -> new HttpCacheInvalidator(config, null, NODE_A, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cache");
    }

    @Test
    void constructor_null_nodeId_throws() {
        var config = CacheCoherenceConfig.builder().build();
        assertThatThrownBy(() -> new HttpCacheInvalidator(config, newCache(), null, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nodeId");
    }

    @Test
    void constructor_null_publisher_throws() {
        var config = CacheCoherenceConfig.builder().build();
        assertThatThrownBy(() -> new HttpCacheInvalidator(config, newCache(), NODE_A, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventPublisher");
    }

    // ── shouldInvalidate tests ──

    @Test
    void shouldInvalidate_PUT() {
        var config = CacheCoherenceConfig.builder().build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.PUT, "/api/data"))).isTrue();
    }

    @Test
    void shouldInvalidate_POST() {
        var config = CacheCoherenceConfig.builder().build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.POST, "/api/data"))).isTrue();
    }

    @Test
    void shouldInvalidate_DELETE() {
        var config = CacheCoherenceConfig.builder().build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.DELETE, "/api/data"))).isTrue();
    }

    @Test
    void shouldInvalidate_PATCH() {
        var config = CacheCoherenceConfig.builder().build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.PATCH, "/api/data"))).isTrue();
    }

    @Test
    void shouldInvalidate_GET_returns_false() {
        var config = CacheCoherenceConfig.builder().build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.GET, "/api/data"))).isFalse();
    }

    @Test
    void shouldInvalidate_custom_methods() {
        var config = CacheCoherenceConfig.builder()
                .invalidationMethods(Set.of(HttpMethod.DELETE))
                .build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.PUT, "/api/data"))).isFalse();
        assertThat(invalidator.shouldInvalidate(HttpRequest.of(HttpMethod.DELETE, "/api/data"))).isTrue();
    }

    // ── processWrite tests ──

    @Test
    void processWrite_publishes_event() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var cache = newCache();
        var invalidator = invalidator(CacheCoherenceConfig.builder().build(), cache, events::add);

        var request = HttpRequest.of(HttpMethod.PUT, "/api/users/42");
        invalidator.processWrite(request).join();

        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.sourceNode()).isEqualTo(NODE_A);
        assertThat(event.sequence()).isGreaterThanOrEqualTo(1);
        assertThat(event.scope()).isEqualTo(CacheCoherenceConfig.InvalidationScope.PREFIX);
    }

    @Test
    void processWrite_with_PATH_scope() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, newCache(), events::add);

        var request = HttpRequest.of(HttpMethod.PUT, "/api/users/42");
        invalidator.processWrite(request).join();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).paths()).containsExactly("/api/users/42");
    }

    @Test
    void processWrite_with_PREFIX_scope() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PREFIX)
                .build();
        var invalidator = invalidator(config, newCache(), events::add);

        var request = HttpRequest.of(HttpMethod.PUT, "/api/users/42");
        invalidator.processWrite(request).join();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).paths()).containsExactly("/api/users/");
    }

    @Test
    void processWrite_with_ALL_scope() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .build();
        var invalidator = invalidator(config, newCache(), events::add);

        var request = HttpRequest.of(HttpMethod.DELETE, "/api/data");
        invalidator.processWrite(request).join();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).paths()).containsExactly("/");
    }

    @Test
    void processWrite_strips_query_string() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, newCache(), events::add);

        var request = HttpRequest.of(HttpMethod.PUT, "/api/data?version=2&force=true");
        invalidator.processWrite(request).join();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).paths()).containsExactly("/api/data");
    }

    @Test
    void processWrite_invalidates_local_cache() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var cache = new InMemoryResponseCache();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, cache, events::add);

        // Pre-populate the cache
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));
        assertThat(cache.get("/api/data")).isPresent();

        // Process write → should invalidate locally
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        invalidator.processWrite(request).join();

        assertThat(cache.get("/api/data")).isEmpty();
    }

    @Test
    void processWrite_GET_does_not_invalidate() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var invalidator = invalidator(CacheCoherenceConfig.builder().build(), newCache(), events::add);

        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        invalidator.processWrite(request).join();

        assertThat(events).isEmpty();
    }

    @Test
    void processWrite_sequences_are_monotonic() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var invalidator = invalidator(CacheCoherenceConfig.builder().build(), newCache(), events::add);

        invalidator.processWrite(HttpRequest.of(HttpMethod.PUT, "/a")).join();
        invalidator.processWrite(HttpRequest.of(HttpMethod.PUT, "/b")).join();
        invalidator.processWrite(HttpRequest.of(HttpMethod.PUT, "/c")).join();

        var seq1 = events.get(0).sequence();
        var seq2 = events.get(1).sequence();
        var seq3 = events.get(2).sequence();
        assertThat(seq2).isGreaterThan(seq1);
        assertThat(seq3).isGreaterThan(seq2);
    }

    // ── processEvent tests ──

    @Test
    void processEvent_skips_own_events() {
        var cache = new InMemoryResponseCache();
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));
        var invalidator = invalidator(CacheCoherenceConfig.builder().build(), cache, e -> {});

        var event = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_A, 1, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event);

        // Cache should NOT be invalidated (own event)
        assertThat(cache.get("/api/data")).isPresent();
    }

    @Test
    void processEvent_invalidates_from_other_node() {
        var cache = new InMemoryResponseCache();
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, cache, e -> {});

        var event = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 1, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event);

        assertThat(cache.get("/api/data")).isEmpty();
    }

    @Test
    void processEvent_skips_duplicate_sequence() {
        var cache = new InMemoryResponseCache();
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, cache, e -> {});

        // First event
        var event1 = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 5, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event1);

        // Put it back
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));

        // Duplicate with same or lower sequence
        var event2 = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 5, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event2);

        // Cache should NOT be invalidated again
        assertThat(cache.get("/api/data")).isPresent();
    }

    @Test
    void processEvent_accepts_higher_sequence() {
        var cache = new InMemoryResponseCache();
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        List<String> invalidated = new CopyOnWriteArrayList<>();

        var trackingCache = new ResponseCache() {
            private final ResponseCache delegate = cache;
            @Override public Optional<ResponseCache.CachedResponse> get(String key) { return delegate.get(key); }
            @Override public void put(String key, ResponseCache.CachedResponse r) { delegate.put(key, r); }
            @Override public void remove(String key) { invalidated.add(key); delegate.remove(key); }
            @Override public void clear() { delegate.clear(); }
            @Override public int size() { return delegate.size(); }
        };

        var invalidator = invalidator(config, trackingCache, e -> {});

        var event1 = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 3, List.of("/api/a"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event1);

        var event2 = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 5, List.of("/api/b"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        invalidator.processEvent(event2);

        assertThat(invalidated).containsExactly("/api/a", "/api/b");
    }

    @Test
    void processEvent_null_throws() {
        var invalidator = invalidator(CacheCoherenceConfig.builder().build(), newCache(), e -> {});
        assertThatThrownBy(() -> invalidator.processEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── matches tests ──

    @Test
    void matches_PATH_exact_match() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.matches("/api/users/42", List.of("/api/users/42"))).isTrue();
        assertThat(invalidator.matches("/api/users/43", List.of("/api/users/42"))).isFalse();
    }

    @Test
    void matches_PREFIX_startsWith() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PREFIX)
                .build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.matches("/api/users/42", List.of("/api/users/"))).isTrue();
        assertThat(invalidator.matches("/api/items/42", List.of("/api/users/"))).isFalse();
    }

    @Test
    void matches_ALL_always_true() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .build();
        var invalidator = invalidator(config, newCache(), e -> {});

        assertThat(invalidator.matches("/any/path", List.of("/"))).isTrue();
        assertThat(invalidator.matches("/different", List.of("/api/"))).isTrue();
    }

    @Test
    void matches_multiple_paths() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PREFIX)
                .build();
        var invalidator = invalidator(config, newCache(), e -> {});

        var paths = List.of("/api/users/", "/api/items/");
        assertThat(invalidator.matches("/api/users/42", paths)).isTrue();
        assertThat(invalidator.matches("/api/items/7", paths)).isTrue();
        assertThat(invalidator.matches("/api/orders/1", paths)).isFalse();
    }

    // ── InMemoryResponseCache helper ──

    private static class InMemoryResponseCache implements ResponseCache {
        private final java.util.Map<String, CachedResponse> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override public Optional<CachedResponse> get(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override public void put(String key, CachedResponse r) {
            store.put(key, r);
        }

        @Override public void remove(String key) {
            store.remove(key);
        }

        @Override public void clear() {
            store.clear();
        }

        @Override public int size() {
            return store.size();
        }
    }
}
