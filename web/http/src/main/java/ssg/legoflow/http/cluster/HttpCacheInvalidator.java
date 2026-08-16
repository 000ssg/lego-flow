package ssg.legoflow.http.cluster;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.caching.ResponseCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Handles cache invalidation in clustered HTTP environments.
 *
 * <p>When a write operation (PUT/POST/DELETE/PATCH) occurs, this class
 * extracts the affected paths and publishes invalidation events. On the
 * receiving side, it processes invalidation events and removes matching
 * entries from the local cache.
 *
 * <p>Invalidation events carry:
 * <ul>
 *   <li>Paths to invalidate (exact or prefix)</li>
 *   <li>Source node ID</li>
 *   <li>Sequence number for ordered processing</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class HttpCacheInvalidator {

    private static final Logger LOG = LoggerFactory.getLogger(HttpCacheInvalidator.class);

    private final CacheCoherenceConfig config;
    private final ResponseCache cache;
    private final String nodeId;
    private final Consumer<CacheInvalidationEvent> eventPublisher;
    private final Map<String, Long> processedSequences = new ConcurrentHashMap<>();
    private volatile long nextSequence = 1;

    /**
     * Creates an invalidator.
     *
     * @param config          the coherence configuration
     * @param cache           the local response cache
     * @param nodeId          this node's ID
     * @param eventPublisher  function to publish invalidation events
     */
    public HttpCacheInvalidator(CacheCoherenceConfig config,
                                  ResponseCache cache,
                                  String nodeId,
                                  Consumer<CacheInvalidationEvent> eventPublisher) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    /**
     * Checks if the given request should trigger cache invalidation.
     *
     * @param request the incoming HTTP request
     * @return true if the method is in the invalidation set
     */
    public boolean shouldInvalidate(HttpRequest request) {
        HttpMethod method = request.getMethod();
        return config.invalidationMethods().contains(method);
    }

    /**
     * Processes a write request: extracts paths and publishes invalidation.
     *
     * @param request the write request
     * @return future completing when the invalidation event is published
     */
    public CompletableFuture<Void> processWrite(HttpRequest request) {
        if (!shouldInvalidate(request)) {
            return CompletableFuture.completedFuture(null);
        }

        List<String> paths = extractPaths(request);
        long sequence = nextSequence++;

        CacheInvalidationEvent event = new CacheInvalidationEvent(
                nodeId, sequence, paths, config.invalidationScope());

        eventPublisher.accept(event);
        LOG.debug("Published invalidation event seq={} paths={}", sequence, paths);

        // Also invalidate locally
        invalidateLocal(paths);

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Processes a received invalidation event from another node.
     *
     * @param event the incoming invalidation event
     */
    public void processEvent(CacheInvalidationEvent event) {
        Objects.requireNonNull(event);

        // Skip own events
        if (event.sourceNode().equals(nodeId)) {
            return;
        }

        // Skip already-processed sequences
        Long lastSeq = processedSequences.get(event.sourceNode());
        if (lastSeq != null && event.sequence() <= lastSeq) {
            LOG.debug("Skipping duplicate event seq={} from {}",
                    event.sequence(), event.sourceNode());
            return;
        }
        processedSequences.put(event.sourceNode(), event.sequence());

        // Invalidate local cache entries
        invalidateLocal(event.paths());

        LOG.info("Processed invalidation from {} seq={} paths={}",
                event.sourceNode(), event.sourceNode(), event.paths());
    }

    /**
     * Checks if a cache key matches any of the given invalidation paths.
     *
     * @param cacheKey     the cache entry key (typically the URI)
     * @param invalidationPaths the paths from the invalidation event
     * @return true if the key should be invalidated
     */
    public boolean matches(String cacheKey, List<String> invalidationPaths) {
        CacheCoherenceConfig.InvalidationScope scope = config.invalidationScope();

        for (String path : invalidationPaths) {
            switch (scope) {
                case PATH -> {
                    if (cacheKey.equals(path)) return true;
                }
                case PREFIX -> {
                    if (cacheKey.startsWith(path)) return true;
                }
                case ALL -> {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the local cache reference.
     */
    public ResponseCache cache() {
        return cache;
    }

    private void invalidateLocal(List<String> paths) {
        for (String path : paths) {
            cache.invalidate(path);
        }
    }

    private List<String> extractPaths(HttpRequest request) {
        List<String> paths = new ArrayList<>();
        String uri = request.getUri();
        String path = uri;

        // Strip query string
        int queryIdx = uri.indexOf('?');
        if (queryIdx > 0) {
            path = uri.substring(0, queryIdx);
        }

        switch (config.invalidationScope()) {
            case PATH -> paths.add(path);
            case PREFIX -> {
                // Extract directory prefix
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    paths.add(path.substring(0, lastSlash + 1));
                } else {
                    paths.add(path);
                }
            }
            case ALL -> paths.add("/");
        }

        return paths;
    }

    /**
     * Invalidation event carrying paths, source, and sequence.
     *
     * @param sourceNode the originating node ID
     * @param sequence   monotonic sequence number
     * @param paths      affected paths
     * @param scope      the invalidation scope
     */
    public record CacheInvalidationEvent(
            String sourceNode,
            long sequence,
            List<String> paths,
            CacheCoherenceConfig.InvalidationScope scope
    ) {}
}
