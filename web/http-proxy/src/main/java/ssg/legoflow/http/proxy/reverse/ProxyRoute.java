package ssg.legoflow.http.proxy.reverse;

import java.util.List;

/**
 * Defines a route mapping from a path prefix to one or more backend servers.
 *
 * <p>Routes are matched by path prefix. The longest matching prefix wins.
 * Each route has its own load balancer and list of backend servers.</p>
 *
 * @since 1.0.0
 */
public class ProxyRoute {

    private final String pathPrefix;
    private final List<BackendServer> backends;
    private final LoadBalancer loadBalancer;
    private final boolean stripPrefix;

    /**
     * Creates a new proxy route.
     *
     * @param pathPrefix the path prefix to match (e.g. "/api")
     * @param backends the backend servers for this route
     * @param loadBalancer the load balancer to use
     * @param stripPrefix whether to strip the prefix from forwarded requests
     * @since 1.0.0
     */
    public ProxyRoute(String pathPrefix, List<BackendServer> backends,
                      LoadBalancer loadBalancer, boolean stripPrefix) {
        this.pathPrefix = pathPrefix.endsWith("/") ? pathPrefix.substring(0, pathPrefix.length() - 1) : pathPrefix;
        this.backends = List.copyOf(backends);
        this.loadBalancer = loadBalancer;
        this.stripPrefix = stripPrefix;
    }

    /**
     * Creates a route with round-robin balancing and no prefix stripping.
     *
     * @param pathPrefix the path prefix
     * @param backends the backends
     * @return the route
     * @since 1.0.0
     */
    public static ProxyRoute of(String pathPrefix, List<BackendServer> backends) {
        return new ProxyRoute(pathPrefix, backends, new RoundRobinBalancer(), false);
    }

    /**
     * Creates a route with a single backend and no prefix stripping.
     *
     * @param pathPrefix the path prefix
     * @param backend the backend server
     * @return the route
     * @since 1.0.0
     */
    public static ProxyRoute of(String pathPrefix, BackendServer backend) {
        return new ProxyRoute(pathPrefix, List.of(backend), new RoundRobinBalancer(), false);
    }

    /**
     * Tests whether this route matches the given path.
     *
     * @param path the request path
     * @return true if the path starts with this route's prefix
     * @since 1.0.0
     */
    public boolean matches(String path) {
        if (pathPrefix.equals("/")) {
            return true;
        }
        return path.equals(pathPrefix) || path.startsWith(pathPrefix + "/");
    }

    /**
     * Rewrites the request path according to this route's configuration.
     * If prefix stripping is enabled, the prefix is removed.
     *
     * @param originalPath the original request path
     * @return the rewritten path
     * @since 1.0.0
     */
    public String rewritePath(String originalPath) {
        if (!stripPrefix || pathPrefix.equals("/")) {
            return originalPath;
        }
        String stripped = originalPath.substring(pathPrefix.length());
        if (stripped.isEmpty()) {
            return "/";
        }
        if (!stripped.startsWith("/")) {
            return "/" + stripped;
        }
        return stripped;
    }

    /**
     * Selects a backend server using this route's load balancer.
     *
     * @return the selected backend, or null if none available
     * @since 1.0.0
     */
    public BackendServer selectBackend() {
        return loadBalancer.select(backends);
    }

    /**
     * Returns the path prefix.
     *
     * @return the path prefix
     * @since 1.0.0
     */
    public String getPathPrefix() {
        return pathPrefix;
    }

    /**
     * Returns the backend servers.
     *
     * @return the backends
     * @since 1.0.0
     */
    public List<BackendServer> getBackends() {
        return backends;
    }

    /**
     * Returns the load balancer.
     *
     * @return the load balancer
     * @since 1.0.0
     */
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Returns whether prefix stripping is enabled.
     *
     * @return true if prefix is stripped
     * @since 1.0.0
     */
    public boolean isStripPrefix() {
        return stripPrefix;
    }

    @Override
    public String toString() {
        return "ProxyRoute{prefix='" + pathPrefix + "', backends=" + backends.size()
                + ", stripPrefix=" + stripPrefix + "}";
    }
}
