package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.forward.ForwardProxy;
import ssg.legoflow.http.proxy.forward.ForwardProxyConfig;
import ssg.legoflow.http.proxy.reverse.BackendServer;
import ssg.legoflow.http.proxy.reverse.ProxyRoute;
import ssg.legoflow.http.proxy.reverse.ReverseProxy;
import ssg.legoflow.http.proxy.reverse.ReverseProxyConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ProxyHandlerTest {

    @Test
    void testForwardProxyHandler() {
        var fp = new ForwardProxy(new ForwardProxyConfig()) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "forwarded");
            }
        };
        var handler = ProxyHandler.forForwardProxy(fp);

        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = handler.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testReverseProxyHandler() {
        var rp = new ReverseProxy(new ReverseProxyConfig());
        rp.addRoute(ProxyRoute.of("/", new BackendServer("backend", 8080)));

        var handler = ProxyHandler.forReverseProxy(rp);
        var request = HttpRequest.of(HttpMethod.GET, "/page");
        var response = handler.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testGetForwardProxy() {
        var fp = new ForwardProxy(new ForwardProxyConfig());
        var handler = ProxyHandler.forForwardProxy(fp);
        assertThat(handler.getForwardProxy()).isSameAs(fp);
        assertThat(handler.getReverseProxy()).isNull();
    }

    @Test
    void testGetReverseProxy() {
        var rp = new ReverseProxy(new ReverseProxyConfig());
        var handler = ProxyHandler.forReverseProxy(rp);
        assertThat(handler.getReverseProxy()).isSameAs(rp);
        assertThat(handler.getForwardProxy()).isNull();
    }

    @Test
    void testForwardProxyHandlerError() {
        var fp = new ForwardProxy(new ForwardProxyConfig()) {
            @Override
            public HttpResponse handleRequest(HttpRequest request) {
                throw new RuntimeException("simulated error");
            }
        };
        var handler = ProxyHandler.forForwardProxy(fp);
        var response = handler.handle(null, HttpRequest.of(HttpMethod.GET, "http://example.com/"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void testForwardProxyHandlerConnectMethod() {
        var fp = new ForwardProxy(new ForwardProxyConfig()) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK);
            }
        };
        var handler = ProxyHandler.forForwardProxy(fp);
        var response = handler.handle(null, HttpRequest.of(HttpMethod.CONNECT, "example.com:443"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testReverseProxyHandlerNotFound() {
        var rp = new ReverseProxy(new ReverseProxyConfig());
        // No routes added
        var handler = ProxyHandler.forReverseProxy(rp);
        var response = handler.handle(null, HttpRequest.of(HttpMethod.GET, "/no-route"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testHandlerWithNullContext() {
        var fp = new ForwardProxy(new ForwardProxyConfig()) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "works");
            }
        };
        var handler = ProxyHandler.forForwardProxy(fp);
        // null context should be fine
        var response = handler.handle(null, HttpRequest.of(HttpMethod.GET, "http://example.com/"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }
}
