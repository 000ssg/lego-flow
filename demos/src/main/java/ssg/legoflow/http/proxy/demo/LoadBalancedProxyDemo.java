package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.reverse.*;
import java.util.List;
/**
 * Load-balanced reverse proxy demo.
 *
 * <p>Demonstrates load balancing across multiple backend servers using
 * round-robin and least-connections strategies.</p>
 *
 * @since 0.1.0
 */
public class LoadBalancedProxyDemo {

    private final ReverseProxy proxy;
    private final BackendServer backend1;
    private final BackendServer backend2;
    private final BackendServer backend3;

    /**
     * Creates the demo with multiple backends and load balancing.
     *
     * @since 0.1.0
     */
    public LoadBalancedProxyDemo() {
        var config = new ReverseProxyConfig();
        config.setProxyName("demo-lb-proxy");

        this.proxy = new ReverseProxy(config);

        backend1 = new BackendServer("backend-1", 8081, 2);
        backend2 = new BackendServer("backend-2", 8082, 1);
        backend3 = new BackendServer("backend-3", 8083, 1);

        // Round-robin route for API
        proxy.addRoute(new ProxyRoute("/api",
                List.of(backend1, backend2, backend3),
                new RoundRobinBalancer(), true));

        // Least-connections route for WebSocket
        proxy.addRoute(new ProxyRoute("/ws",
                List.of(backend1, backend2),
                new LeastConnectionsBalancer(), false));
    }

    /**
     * Runs the demo showing request distribution across backends.
     *
     * @return the results summary
     * @since 0.1.0
     */
    public String run() {
        var sb = new StringBuilder();

        // Send multiple API requests to see round-robin distribution
        for (int i = 0; i < 8; i++) {
            var req = HttpRequest.of(HttpMethod.GET, "/api/resource");
            var resp = proxy.handleRequest(req);
            sb.append("API request ").append(i + 1).append(" -> ").append(resp.getStatus().code())
                    .append(" body=").append(resp.getBodyAsString()).append("\n");
        }

        sb.append("\nBackend distribution:\n");
        sb.append("  backend-1: ").append(backend1.getTotalRequests()).append(" requests\n");
        sb.append("  backend-2: ").append(backend2.getTotalRequests()).append(" requests\n");
        sb.append("  backend-3: ").append(backend3.getTotalRequests()).append(" requests\n");

        // Test with an unhealthy backend
        backend2.setHealthy(false);
        sb.append("\nAfter marking backend-2 unhealthy:\n");
        var req = HttpRequest.of(HttpMethod.GET, "/api/resource");
        var resp = proxy.handleRequest(req);
        sb.append("API request -> ").append(resp.getStatus().code()).append("\n");

        sb.append("Total requests: ").append(proxy.getRequestCount()).append("\n");

        return sb.toString();
    }

    /**
     * Returns the reverse proxy instance.
     *
     * @return the proxy
     * @since 0.1.0
     */
    public ReverseProxy getProxy() {
        return proxy;
    }

    /**
     * Returns backend server 1.
     *
     * @return backend 1
     * @since 0.1.0
     */
    public BackendServer getBackend1() {
        return backend1;
    }

    /**
     * Returns backend server 2.
     *
     * @return backend 2
     * @since 0.1.0
     */
    public BackendServer getBackend2() {
        return backend2;
    }

    /**
     * Returns backend server 3.
     *
     * @return backend 3
     * @since 0.1.0
     */
    public BackendServer getBackend3() {
        return backend3;
    }
}
