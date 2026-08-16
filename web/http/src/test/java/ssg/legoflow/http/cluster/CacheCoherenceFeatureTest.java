package ssg.legoflow.http.cluster;

import ssg.legoflow.http.caching.ResponseCache;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CacheCoherenceFeature}.
 * Verifies spec: feature registration, configuration, initialization,
 * write handling, and invalidation event processing.
 */
class CacheCoherenceFeatureTest {

    private static final String NODE_A = "node-A";
    private static final String NODE_B = "node-B";

    // ── Test helpers ──

    private ResponseCache newCache() {
        return new InMemoryResponseCache();
    }

    // ── Constructor and basic tests ──

    @Test
    void default_constructor() {
        var feature = new CacheCoherenceFeature();
        assertThat(feature.getName()).isEqualTo("cache-coherence");
        assertThat(feature.getCategory()).isEqualTo(HttpFeatureCategory.CLUSTER);
        assertThat(feature.isCore()).isFalse();
        assertThat(feature.config().invalidationScope())
                .isEqualTo(CacheCoherenceConfig.InvalidationScope.PREFIX);
    }

    @Test
    void constructor_with_config() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .propagationTimeout(Duration.ofSeconds(10))
                .build();
        var feature = new CacheCoherenceFeature(config);

        assertThat(feature.config().invalidationScope()).isEqualTo(CacheCoherenceConfig.InvalidationScope.ALL);
        assertThat(feature.config().propagationTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void constructor_null_config_throws() {
        assertThatThrownBy(() -> new CacheCoherenceFeature((CacheCoherenceConfig) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── install test ──

    @Test
    void install_registers_feature() {
        var feature = new CacheCoherenceFeature();
        var registry = new HttpFeatureRegistry();
        feature.install(registry);

        assertThat(registry.getFeature("cache-coherence")).isSameAs(feature);
        assertThat(registry.isEnabled("cache-coherence")).isTrue();
    }

    @Test
    void install_in_category() {
        var feature = new CacheCoherenceFeature();
        var registry = new HttpFeatureRegistry();
        feature.install(registry);

        var clusterFeatures = registry.getByCategory(HttpFeatureCategory.CLUSTER);
        assertThat(clusterFeatures).contains(feature);
    }

    // ── configure tests ──

    @Test
    void configure_with_scope() {
        var feature = new CacheCoherenceFeature();
        feature.configure(Map.of("invalidationScope", "ALL"));

        assertThat(feature.config().invalidationScope())
                .isEqualTo(CacheCoherenceConfig.InvalidationScope.ALL);
    }

    @Test
    void configure_with_timeout() {
        var feature = new CacheCoherenceFeature();
        feature.configure(Map.of("propagationTimeoutMs", 3000L));

        assertThat(feature.config().propagationTimeout()).isEqualTo(Duration.ofMillis(3000));
    }

    @Test
    void configure_with_timeout_string() {
        var feature = new CacheCoherenceFeature();
        feature.configure(Map.of("propagationTimeoutMs", "5000"));

        assertThat(feature.config().propagationTimeout()).isEqualTo(Duration.ofMillis(5000));
    }

    @Test
    void configure_empty_map_noop() {
        var feature = new CacheCoherenceFeature();
        var originalScope = feature.config().invalidationScope();
        feature.configure(Map.of());

        assertThat(feature.config().invalidationScope()).isEqualTo(originalScope);
    }

    @Test
    void configure_null_noop() {
        var feature = new CacheCoherenceFeature();
        var originalScope = feature.config().invalidationScope();
        feature.configure(null);

        assertThat(feature.config().invalidationScope()).isEqualTo(originalScope);
    }

    // ── initialize tests ──

    @Test
    void initialize_null_cache_throws() {
        var feature = new CacheCoherenceFeature();
        assertThatThrownBy(() -> feature.initialize(null, NODE_A, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cache");
    }

    @Test
    void initialize_null_nodeId_throws() {
        var feature = new CacheCoherenceFeature();
        assertThatThrownBy(() -> feature.initialize(newCache(), null, e -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nodeId");
    }

    @Test
    void initialize_null_publisher_throws() {
        var feature = new CacheCoherenceFeature();
        assertThatThrownBy(() -> feature.initialize(newCache(), NODE_A, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventPublisher");
    }

    @Test
    void initialize_sets_cache() {
        var cache = newCache();
        var feature = new CacheCoherenceFeature();
        feature.initialize(cache, NODE_A, e -> {});

        assertThat(feature.cache()).isSameAs(cache);
    }

    // ── handleWrite tests ──

    @Test
    void handleWrite_without_initialization() {
        var feature = new CacheCoherenceFeature();
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        var result = feature.handleWrite(request);

        assertThat(result.isDone()).isTrue();
    }

    @Test
    void handleWrite_after_initialization() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var cache = newCache();
        var feature = new CacheCoherenceFeature();
        feature.initialize(cache, NODE_A, events::add);

        var request = HttpRequest.of(HttpMethod.PUT, "/api/users/42");
        feature.handleWrite(request).join();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).sourceNode()).isEqualTo(NODE_A);
    }

    @Test
    void handleWrite_GET_does_not_publish() {
        List<HttpCacheInvalidator.CacheInvalidationEvent> events = new CopyOnWriteArrayList<>();
        var cache = newCache();
        var feature = new CacheCoherenceFeature();
        feature.initialize(cache, NODE_A, events::add);

        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        feature.handleWrite(request).join();

        assertThat(events).isEmpty();
    }

    // ── handleInvalidationEvent tests ──

    @Test
    void handleInvalidationEvent_without_initialization() {
        var feature = new CacheCoherenceFeature();
        var event = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 1, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        // Should not throw
        feature.handleInvalidationEvent(event);
    }

    @Test
    void handleInvalidationEvent_after_initialization() {
        var cache = new InMemoryResponseCache();
        cache.put("/api/data", new ResponseCache.CachedResponse(
                HttpResponse.of(HttpStatus.OK, "cached"), System.currentTimeMillis(), 60));

        var feature = new CacheCoherenceFeature();
        feature.initialize(cache, NODE_A, e -> {});

        var event = new HttpCacheInvalidator.CacheInvalidationEvent(
                NODE_B, 1, List.of("/api/data"),
                CacheCoherenceConfig.InvalidationScope.PATH);
        feature.handleInvalidationEvent(event);

        assertThat(cache.get("/api/data")).isEmpty();
    }

    // ── Test helpers ──

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
