package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyFilter;
import ssg.legoflow.http.proxy.ProxyHeaders;
import ssg.legoflow.http.proxy.auth.BasicProxyAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
class ForwardProxyTest {

    private ForwardProxy proxy;
    private ForwardProxyConfig config;

    @BeforeEach
    void setUp() {
        config = new ForwardProxyConfig();
        config.setProxyName("test-proxy");
        proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "upstream: " + target.host() + target.path());
            }
        };
    }

    @Test
    void testPlainHttpProxy() {
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("example.com");
    }

    @Test
    void testConnectMethod() {
        var request = HttpRequest.of(HttpMethod.CONNECT, "example.com:443");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(proxy.getTunnelCount()).isEqualTo(1);
    }

    @Test
    void testViaHeaderAdded() {
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = proxy.handleRequest(request);
        assertThat(ProxyHeaders.getVia(response.getHeaders())).contains("test-proxy");
    }

    @Test
    void testForwardedForHeader() {
        config.setAddForwardedHeaders(true);
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        // The upstream request will have X-Forwarded-For added
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testMethodNotAllowed() {
        config.setAllowedMethods(Set.of(HttpMethod.GET));
        proxy = new ForwardProxy(config);
        var request = HttpRequest.of(HttpMethod.POST, "http://example.com/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void testAccessControlDenied() {
        var accessControl = ProxyAccessControl.allowHosts(Set.of("allowed.com"));
        proxy = new ForwardProxy(config, accessControl) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "upstream");
            }
        };
        var request = HttpRequest.of(HttpMethod.GET, "http://blocked.com/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testAccessControlAllowed() {
        var accessControl = ProxyAccessControl.allowHosts(Set.of("allowed.com"));
        proxy = new ForwardProxy(config, accessControl) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "allowed");
            }
        };
        var request = HttpRequest.of(HttpMethod.GET, "http://allowed.com/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testParseAbsoluteUri() {
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com:8080/path?q=1");
        var target = proxy.parseTarget(request);
        assertThat(target).isNotNull();
        assertThat(target.host()).isEqualTo("example.com");
        assertThat(target.port()).isEqualTo(8080);
        assertThat(target.path()).isEqualTo("/path?q=1");
    }

    @Test
    void testParseConnectUri() {
        var request = HttpRequest.of(HttpMethod.CONNECT, "example.com:443");
        var target = proxy.parseTarget(request);
        assertThat(target).isNotNull();
        assertThat(target.host()).isEqualTo("example.com");
        assertThat(target.port()).isEqualTo(443);
    }

    @Test
    void testParseRelativeUriWithHost() {
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.HOST, "example.com:8080");
        var target = proxy.parseTarget(request);
        assertThat(target).isNotNull();
        assertThat(target.host()).isEqualTo("example.com");
        assertThat(target.port()).isEqualTo(8080);
        assertThat(target.path()).isEqualTo("/path");
    }

    @Test
    void testRequestCount() {
        proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "http://example.com/1"));
        proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "http://example.com/2"));
        assertThat(proxy.getRequestCount()).isEqualTo(2);
    }

    @Test
    void testFilterApplication() {
        proxy.addFilter(new ProxyFilter() {
            @Override
            public HttpRequest filterRequest(HttpRequest request) {
                request.getHeaders().set("x-test-filter", "applied");
                return request;
            }
            @Override
            public HttpResponse filterResponse(HttpResponse response) {
                response.getHeaders().set("x-response-filter", "applied");
                return response;
            }
            @Override
            public String getName() { return "test-filter"; }
        });
        var response = proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "http://example.com/page"));
        assertThat(response.getHeaders().get("x-response-filter")).isEqualTo("applied");
    }

    @Test
    void testAuthenticationRequired() {
        config.setAuthRequired(true);
        var auth = new BasicProxyAuth("test-realm");
        auth.addUser("user", "pass");
        proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "authenticated");
            }
        };
        proxy.setAuthenticator(auth);

        // Without credentials
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = proxy.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PROXY_AUTHENTICATION_REQUIRED);
    }

    @Test
    void testDisableViaHeader() {
        config.setAddViaHeader(false);
        proxy = new ForwardProxy(config) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                return HttpResponse.of(HttpStatus.OK, "no-via");
            }
        };
        var response = proxy.handleRequest(HttpRequest.of(HttpMethod.GET, "http://example.com/page"));
        assertThat(ProxyHeaders.getVia(response.getHeaders())).isNull();
    }

    @Test
    void testParseHttpsUri() {
        var request = HttpRequest.of(HttpMethod.GET, "https://secure.example.com/path");
        var target = proxy.parseTarget(request);
        assertThat(target).isNotNull();
        assertThat(target.host()).isEqualTo("secure.example.com");
        assertThat(target.port()).isEqualTo(443);
    }
}
