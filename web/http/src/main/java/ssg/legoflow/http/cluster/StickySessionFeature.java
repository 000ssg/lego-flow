package ssg.legoflow.http.cluster;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import ssg.legoflow.network.cluster.core.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP feature that provides sticky session support for web clusters.
 *
 * <p>When installed, this feature:
 * <ul>
 *   <li>Intercepts incoming requests to route to the correct node</li>
 *   <li>Sets the session affinity cookie on responses</li>
 *   <li>Handles node failures via the configured fallback strategy</li>
 * </ul>
 *
 * <p>Configuration parameters:
 * <ul>
 *   <li>{@code cookieName} — name of the session cookie (default: X-Session-Node)</li>
 *   <li>{@code maxAgeSeconds} — cookie lifetime in seconds (default: 3600)</li>
 *   <li>{@code secure} — restrict to HTTPS (default: false)</li>
 *   <li>{@code httpOnly} — block JavaScript access (default: true)</li>
 *   <li>{@code fallback} — REHASH | REDIRECT | ERROR (default: REHASH)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class StickySessionFeature implements HttpFeature {

    private static final Logger LOG = LoggerFactory.getLogger(StickySessionFeature.class);

    static final String NAME = "sticky-sessions";

    private volatile SessionAffinityConfig config;
    private volatile StickySessionHasher hasher;
    private volatile StickySessionRouter router;

    /**
     * Creates a feature with default configuration.
     */
    public StickySessionFeature() {
        this(SessionAffinityConfig.builder().build(), new StickySessionHasher());
    }

    /**
     * Creates a feature with the given configuration and hasher.
     *
     * @param config the session affinity config
     * @param hasher the consistent hasher
     */
    public StickySessionFeature(SessionAffinityConfig config, StickySessionHasher hasher) {
        this.config = Objects.requireNonNull(config);
        this.hasher = Objects.requireNonNull(hasher);
        this.router = new StickySessionRouter(config, hasher);
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

        var builder = config.fallback() == null
                ? SessionAffinityConfig.builder()
                : SessionAffinityConfig.builder()
                        .cookieName(config.cookieName())
                        .maxAge(config.maxAge())
                        .secure(config.secure())
                        .httpOnly(config.httpOnly())
                        .path(config.path())
                        .fallback(config.fallback());

        params.forEach((key, value) -> {
            switch (key) {
                case "cookieName" -> builder.cookieName(value.toString());
                case "maxAgeSeconds" -> {
                    long seconds = toLong(value);
                    builder.maxAge(java.time.Duration.ofSeconds(seconds));
                }
                case "secure" -> builder.secure(toBoolean(value));
                case "httpOnly" -> builder.httpOnly(toBoolean(value));
                case "path" -> builder.path(value.toString());
                case "fallback" -> {
                    String name = value.toString();
                    builder.fallback(SessionAffinityConfig.FallbackStrategy.valueOf(name.toUpperCase()));
                }
            }
        });

        this.config = builder.build();
        this.router = new StickySessionRouter(config, hasher);
        LOG.info("StickySessionFeature configured: cookieName={}, maxAge={}, fallback={}",
                config.cookieName(), config.maxAge(), config.fallback());
    }

    @Override
    public void install(HttpFeatureRegistry registry) {
        registry.register(this);
        LOG.info("StickySessionFeature installed in registry");
    }

    /**
     * Updates the available nodes for routing.
     *
     * @param nodes the current active nodes
     */
    public void updateNodes(Collection<ClusterNode> nodes) {
        Objects.requireNonNull(nodes);
        router.updateNodes(nodes);
    }

    /**
     * Routes a request to the appropriate node.
     *
     * @param request the incoming request
     * @return the target node, or null if unavailable
     */
    public ClusterNode route(HttpRequest request) {
        return router.route(request);
    }

    /**
     * Sets the session cookie on the response for the given node.
     *
     * @param response the response
     * @param node     the target node
     */
    public void setSessionCookie(HttpResponse response, ClusterNode node) {
        router.setCookieHeader(response, node);
    }

    /**
     * Returns the router instance for advanced usage.
     */
    public StickySessionRouter router() {
        return router;
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
