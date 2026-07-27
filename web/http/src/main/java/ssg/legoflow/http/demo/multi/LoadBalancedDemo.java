package ssg.legoflow.http.demo.multi;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo with multiple backend servers and a round-robin load-balancing client.
 *
 * <p>Creates several server instances, each identified by name, and distributes
 * requests across them using a simple round-robin counter.
 *
 * @since 1.0
 */
public class LoadBalancedDemo {

    private final List<HttpServer> backends;
    private final HttpClient client;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final DefaultContext ctx;

    public LoadBalancedDemo() {
        this(3, 9100);
    }

    public LoadBalancedDemo(int numBackends, int startPort) {
        this.backends = new ArrayList<>();
        this.client = new HttpClientBuilder().standard().build();
        this.ctx = new DefaultContext();

        for (int i = 0; i < numBackends; i++) {
            var config = new ServerConfig(StandardProfiles.serverStandard());
            config.setPort(startPort + i);
            var server = new HttpServer("backend-" + i, config);
            final int index = i;
            server.getRouter().get("/", (httpCtx, req) ->
                    HttpResponse.of(HttpStatus.OK, "Backend-" + index));
            server.getRouter().get("/health", (httpCtx, req) ->
                    HttpResponse.of(HttpStatus.OK, "{\"status\":\"UP\",\"backend\":" + index + "}"));
            backends.add(server);
        }
    }

    /**
     * Dispatches a request to the next backend in round-robin order.
     *
     * @param request the HTTP request
     * @return the HTTP response from the selected backend
     */
    public HttpResponse dispatch(HttpRequest request) {
        var index = counter.getAndIncrement() % backends.size();
        return backends.get(index).handleRequest(ctx, request);
    }

    /**
     * Returns the backend that would be selected for the given request index.
     *
     * @param requestIndex zero-based request counter
     * @return the backend index
     */
    public int getBackendIndex(int requestIndex) {
        return requestIndex % backends.size();
    }

    /**
     * Returns all backend servers.
     *
     * @return the backend server list
     */
    public List<HttpServer> getBackends() {
        return List.copyOf(backends);
    }

    /**
     * Returns the client instance.
     *
     * @return the client
     */
    public HttpClient getClient() {
        return client;
    }

    /**
     * Returns the current request counter value.
     *
     * @return the counter value
     */
    public int getRequestCount() {
        return counter.get();
    }
}
