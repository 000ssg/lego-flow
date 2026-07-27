package ssg.legoflow.http.proxy.reverse;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyFilter;
import ssg.legoflow.http.proxy.ProxyHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReverseProxyTest {

    private ReverseProxy proxy;
    private ReverseProxyConfig config;

    @BeforeEach
    void setUp() {
        config = new ReverseProxyConfig();
        config.setProxyName("test-reverse-proxy");
        proxy = new ReverseProxy(config);
    }

    @Test
    void testBasicRouting() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/api", backend));

        var request = HttpRequest.of(HttpMethod.GET, "/api/users");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("backend:8080");
    }

    @Test
    void testNoRouteFound() {
        var request = HttpRequest.of(HttpMethod.GET, "/unknown");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testPathPrefixStripping() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(new ProxyRoute("/api", List.of(backend),
                new RoundRobinBalancer(), true));
        proxy.setRequestForwarder((req, be) ->
                HttpResponse.of(HttpStatus.OK, "path=" + req.getUri()));

        var request = HttpRequest.of(HttpMethod.GET, "/api/users");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("path=/users");
    }

    @Test
    void testPathPrefixNotStripped() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(new ProxyRoute("/api", List.of(backend),
                new RoundRobinBalancer(), false));
        proxy.setRequestForwarder((req, be) ->
                HttpResponse.of(HttpStatus.OK, "path=" + req.getUri()));

        var request = HttpRequest.of(HttpMethod.GET, "/api/users");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("path=/api/users");
    }

    @Test
    void testHostHeaderRewriting() {
        var backend = new BackendServer("backend-host", 9090);
        proxy.addRoute(ProxyRoute.of("/", backend));
        proxy.setRequestForwarder((req, be) -> {
            String host = req.getHeaders().get(HttpHeaders.HOST);
            return HttpResponse.of(HttpStatus.OK, "host=" + host);
        });

        var request = HttpRequest.of(HttpMethod.GET, "/page");
        request.getHeaders().set(HttpHeaders.HOST, "original.com");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("host=backend-host:9090");
    }

    @Test
    void testPreserveHostHeader() {
        config.setPreserveHostHeader(true);
        proxy = new ReverseProxy(config);
        var backend = new BackendServer("backend-host", 9090);
        proxy.addRoute(ProxyRoute.of("/", backend));
        proxy.setRequestForwarder((req, be) -> {
            String host = req.getHeaders().get(HttpHeaders.HOST);
            return HttpResponse.of(HttpStatus.OK, "host=" + host);
        });

        var request = HttpRequest.of(HttpMethod.GET, "/page");
        request.getHeaders().set(HttpHeaders.HOST, "original.com");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("host=original.com");
    }

    @Test
    void testForwardedHeadersAdded() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/", backend));
        proxy.setRequestForwarder((req, be) -> {
            String xff = req.getHeaders().get(ProxyHeaders.X_FORWARDED_HOST);
            return HttpResponse.of(HttpStatus.OK, "xfh=" + xff);
        });

        var request = HttpRequest.of(HttpMethod.GET, "/page");
        request.getHeaders().set(HttpHeaders.HOST, "frontend.com");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("xfh=frontend.com");
    }

    @Test
    void testViaHeaderAdded() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/", backend));

        var request = HttpRequest.of(HttpMethod.GET, "/page");
        var response = proxy.handleRequest(request);
        assertThat(ProxyHeaders.getVia(response.getHeaders())).contains("test-reverse-proxy");
    }

    @Test
    void testNoHealthyBackend() {
        var backend = new BackendServer("backend", 8080);
        backend.setHealthy(false);
        proxy.addRoute(ProxyRoute.of("/", backend));

        var request = HttpRequest.of(HttpMethod.GET, "/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void testWebSocketUpgrade() {
        var backend = new BackendServer("ws-backend", 8080);
        proxy.addRoute(ProxyRoute.of("/ws", backend));

        var request = HttpRequest.of(HttpMethod.GET, "/ws/chat");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");

        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
    }

    @Test
    void testQueryStringPreserved() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/", backend));
        proxy.setRequestForwarder((req, be) ->
                HttpResponse.of(HttpStatus.OK, "uri=" + req.getUri()));

        var request = HttpRequest.of(HttpMethod.GET, "/search?q=test&page=1");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("q=test&page=1");
    }

    @Test
    void testRequestCountIncremented() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/", backend));

        proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/1"));
        proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/2"));
        proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/3"));
        assertThat(proxy.getRequestCount()).isEqualTo(3);
    }

    @Test
    void testFilterApplied() {
        var backend = new BackendServer("backend", 8080);
        proxy.addRoute(ProxyRoute.of("/", backend));
        proxy.addFilter(new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) { return request; }
            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                response.getHeaders().set("x-filtered", "yes");
                return response;
            }
            @Override
            public String getName() { return "test"; }
        });

        var response = proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "/page"));
        assertThat(response.getHeaders().get("x-filtered")).isEqualTo("yes");
    }

    @Test
    void testLongestPrefixMatchWins() {
        var generalBackend = new BackendServer("general", 8080);
        var specificBackend = new BackendServer("specific", 8081);

        proxy.addRoute(ProxyRoute.of("/api", generalBackend));
        proxy.addRoute(ProxyRoute.of("/api/v2", specificBackend));

        var request = HttpRequest.of(HttpMethod.GET, "/api/v2/users");
        var response = proxy.handleRequest(request);
        assertThat(response.getBodyAsString()).contains("specific:8081");
    }

    @Test
    void testCloseProxy() {
        proxy.close();
        // Should not throw
        assertThat(proxy.getRequestCount()).isGreaterThanOrEqualTo(0);
    }
}
