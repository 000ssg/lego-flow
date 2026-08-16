package ssg.legoflow.http.cluster;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.network.cluster.core.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes HTTP requests to cluster nodes based on session affinity cookies.
 *
 * <p>Reads the session cookie from incoming requests to determine the target
 * node. If the indicated node is unavailable, applies the configured fallback
 * strategy (rehash, redirect, or error).
 *
 * <p>Uses consistent hashing as the fallback routing mechanism when the
 * session cookie is absent or the target node is down.
 *
 * @since 0.2.0
 */
public final class StickySessionRouter {

    private static final Logger LOG = LoggerFactory.getLogger(StickySessionRouter.class);

    private final SessionAffinityConfig config;
    private final SessionCookieBuilder cookieBuilder;

    /** Map of nodeId -> node for quick lookup. */
    private final Map<String, ClusterNode> nodeMap = new ConcurrentHashMap<>();

    /** Available active nodes for fallback routing. */
    private final java.util.Set<ClusterNode> activeNodes =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Consistent hasher for fallback routing. */
    private final StickySessionHasher hasher;

    /**
     * Creates a router with the given config and consistent hasher.
     *
     * @param config the session affinity configuration
     * @param hasher the consistent hasher for fallback routing
     */
    public StickySessionRouter(SessionAffinityConfig config, StickySessionHasher hasher) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.cookieBuilder = SessionCookieBuilder.fromConfig(config);
    }

    /**
     * Updates the available node set.
     *
     * @param nodes the current active nodes
     */
    public void updateNodes(Collection<ClusterNode> nodes) {
        Objects.requireNonNull(nodes);
        activeNodes.clear();
        nodeMap.clear();
        for (ClusterNode node : nodes) {
            activeNodes.add(node);
            nodeMap.put(node.id(), node);
        }
        // Rebuild the hasher ring with updated nodes
        hasher.updateNodes(nodes);
    }

    /**
     * Routes a request to the appropriate cluster node.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Read session cookie from request</li>
     *   <li>If cookie present and node is active → return that node</li>
     *   <li>If cookie present but node is inactive → apply fallback</li>
     *   <li>If no cookie → consistent hash based on request URI</li>
     * </ol>
     *
     * @param request the incoming HTTP request
     * @return the target node, or null if no available nodes
     */
    public ClusterNode route(HttpRequest request) {
        Objects.requireNonNull(request);

        if (activeNodes.isEmpty()) {
            LOG.warn("No active nodes available for routing");
            return null;
        }

        String nodeId = extractSessionCookie(request);

        if (nodeId != null && !nodeId.isEmpty()) {
            ClusterNode target = nodeMap.get(nodeId);
            if (target != null && activeNodes.contains(target)) {
                LOG.debug("Routing to sticky node: {}", nodeId);
                return target;
            }

            // Node is down — apply fallback
            LOG.info("Sticky node {} unavailable, applying fallback: {}",
                    nodeId, config.fallback());
            return applyFallback(request, nodeId);
        }

        // No cookie — consistent hash based on URI
        ClusterNode target = hasher.getNode(request.getUri());
        LOG.debug("No session cookie, hash-routed to: {}",
                target != null ? target.id() : "null");
        return target;
    }

    /**
     * Generates a Set-Cookie header for the given node.
     *
     * @param node the target node
     * @return the Set-Cookie header value
     */
    public String buildCookie(ClusterNode node) {
        Objects.requireNonNull(node);
        return cookieBuilder.build(node.id());
    }

    /**
     * Builds a response with the session cookie set.
     *
     * @param response the response to modify
     * @param node     the target node
     */
    public void setCookieHeader(HttpResponse response, ClusterNode node) {
        Objects.requireNonNull(response);
        Objects.requireNonNull(node);
        response.getHeaders().set("Set-Cookie", buildCookie(node));
    }

    private String extractSessionCookie(HttpRequest request) {
        String cookieHeader = request.getHeaders().get("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        String name = config.cookieName();
        // Parse "name=value; ..." format
        for (String pair : cookieHeader.split(";")) {
            pair = pair.trim();
            if (pair.startsWith(name + "=")) {
                return pair.substring(name.length() + 1).trim();
            }
        }
        return null;
    }

    private ClusterNode applyFallback(HttpRequest request, String staleNodeId) {
        return switch (config.fallback()) {
            case REHASH -> {
                ClusterNode target = hasher.getNode(request.getUri());
                yield target;
            }
            case REDIRECT -> {
                // For redirect we need the backend URL — rehash to find new node
                ClusterNode target = hasher.getNode(request.getUri());
                if (target != null) {
                    LOG.info("Redirect fallback: routing to new node {}", target.id());
                }
                yield target;
            }
            case ERROR -> null;
        };
    }

    /**
     * Returns the configured cookie name.
     */
    public String cookieName() {
        return config.cookieName();
    }

    /**
     * Returns the configured fallback strategy.
     */
    public SessionAffinityConfig.FallbackStrategy fallbackStrategy() {
        return config.fallback();
    }

    /**
     * Returns the number of active nodes.
     */
    public int activeNodeCount() {
        return activeNodes.size();
    }
}
