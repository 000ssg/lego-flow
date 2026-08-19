package ssg.legoflow.http.cluster;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.caching.ResponseCache;
import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
/**
 * HTTP feature for cross-node cache coherence in web clusters.
 *
 * <p>When installed, this feature:
 * <ul>
 *   <li>Intercepts write requests (PUT/POST/DELETE/PATCH)</li>
 *   <li>Extracts affected paths from the request URI</li>
 *   <li>Publishes cache invalidation events to the cluster bus</li>
 *   <li>Receives invalidation events from peers and purges local cache</li>
 * </ul>
 *
 * <p>The invalidation bus (NATS, gRPC, etc.) is configured externally.
 *
 * @since 0.2.0
 */
public final class CacheCoherenceFeature implements HttpFeature {

    private static final Logger LOG = LoggerFactory.getLogger(CacheCoherenceFeature.class);

    static final String NAME = "cache-coherence";

    private volatile CacheCoherenceConfig config;
    private volatile ResponseCache cache;
    private volatile String nodeId;
    private volatile Consumer<HttpCacheInvalidator.CacheInvalidationEvent> eventPublisher;
    private volatile HttpCacheInvalidator invalidator;

    /**
     * Creates a feature with default configuration.
     */
    public CacheCoherenceFeature() {
        this.config = CacheCoherenceConfig.builder().build();
    }

    /**
     * Creates a feature with the given configuration.
     *
     * @param config the cache coherence configuration
     */
    public CacheCoherenceFeature(CacheCoherenceConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public HttpFeatureCategory getCategory() {
        return HttpFeatureCategory.CLUSTER;
    }

    @Override
    public boolean isCore() {
        return false;
    }

    @Override
    public void configure(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;

        var builder = CacheCoherenceConfig.builder();

        params.forEach((key, value) -> {
            switch (key) {
                case "invalidationScope" -> {
                    String name = value.toString();
                    builder.invalidationScope(CacheCoherenceConfig.InvalidationScope.valueOf(name.toUpperCase()));
                }
                case "propagationTimeoutMs" -> {
                    long ms = toLong(value);
                    builder.propagationTimeout(java.time.Duration.ofMillis(ms));
                }
            }
        });

        this.config = builder.build();
        rebuildInvalidator();
        LOG.info("CacheCoherenceFeature configured: scope={}, timeout={}",
                config.invalidationScope(), config.propagationTimeout());
    }

    @Override
    public void install(HttpFeatureRegistry registry) {
        registry.register(this);
        LOG.info("CacheCoherenceFeature installed");
    }

    /**
     * Initializes the feature with runtime dependencies.
     *
     * @param cache           the local response cache
     * @param nodeId          this node's cluster ID
     * @param eventPublisher  function to publish invalidation events to the cluster
     */
    public void initialize(ResponseCache cache, String nodeId,
                            Consumer<HttpCacheInvalidator.CacheInvalidationEvent> eventPublisher) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        rebuildInvalidator();
    }

    /**
     * Handles an incoming write request by publishing cache invalidation.
     *
     * @param request the write request
     * @return future completing when invalidation is published
     */
    public CompletableFuture<Void> handleWrite(HttpRequest request) {
        if (invalidator == null) {
            return CompletableFuture.completedFuture(null);
        }
        return invalidator.processWrite(request);
    }

    /**
     * Processes a received invalidation event from a peer node.
     *
     * @param event the incoming invalidation event
     */
    public void handleInvalidationEvent(HttpCacheInvalidator.CacheInvalidationEvent event) {
        if (invalidator != null) {
            invalidator.processEvent(event);
        }
    }

    /**
     * Returns the local cache.
     */
    public ResponseCache cache() {
        return cache;
    }

    /**
     * Returns the current configuration.
     */
    public CacheCoherenceConfig config() {
        return config;
    }

    private void rebuildInvalidator() {
        if (cache != null && nodeId != null && eventPublisher != null) {
            this.invalidator = new HttpCacheInvalidator(config, cache, nodeId, eventPublisher);
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
