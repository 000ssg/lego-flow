package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.reverse.*;

import java.util.List;

/**
 * Reverse proxy demo with multiple backends.
 *
 * <p>Demonstrates setting up a reverse proxy with path-based routing
 * to different backend servers.</p>
 *
 * @since 0.1.0
 */
public class ReverseProxyDemo {

    private final ReverseProxy proxy;

    /**
     * Creates the demo with a pre-configured reverse proxy.
     *
     * @since 0.1.0
     */
    public ReverseProxyDemo() {
        var config = new ReverseProxyConfig();
        config.setProxyName("demo-reverse-proxy");

        this.proxy = new ReverseProxy(config);

        // API route
        var apiBackend = new BackendServer("api-server", 8081);
        proxy.addRoute(new ProxyRoute("/api", List.of(apiBackend),
                new RoundRobinBalancer(), true));

        // Static content route
        var staticBackend = new BackendServer("static-server", 8082);
        proxy.addRoute(new ProxyRoute("/static", List.of(staticBackend),
                new RoundRobinBalancer(), false));

        // Default route
        var defaultBackend = new BackendServer("web-server", 8080);
        proxy.addRoute(ProxyRoute.of("/", defaultBackend));
    }

    /**
     * Runs the demo by sending various requests through the reverse proxy.
     *
     * @return the results summary
     * @since 0.1.0
     */
    public String run() {
        var sb = new StringBuilder();

        // API request
        var req1 = HttpRequest.of(HttpMethod.GET, "/api/users");
        req1.getHeaders().set(HttpHeaders.HOST, "myapp.example.com");
        var resp1 = proxy.handleRequest(req1);
        sb.append("GET /api/users -> ").append(resp1.getStatus().code())
                .append(" body=").append(resp1.getBodyAsString()).append("\n");

        // Static request
        var req2 = HttpRequest.of(HttpMethod.GET, "/static/style.css");
        req2.getHeaders().set(HttpHeaders.HOST, "myapp.example.com");
        var resp2 = proxy.handleRequest(req2);
        sb.append("GET /static/style.css -> ").append(resp2.getStatus().code()).append("\n");

        // Default route
        var req3 = HttpRequest.of(HttpMethod.GET, "/index.html");
        req3.getHeaders().set(HttpHeaders.HOST, "myapp.example.com");
        var resp3 = proxy.handleRequest(req3);
        sb.append("GET /index.html -> ").append(resp3.getStatus().code()).append("\n");

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
}
