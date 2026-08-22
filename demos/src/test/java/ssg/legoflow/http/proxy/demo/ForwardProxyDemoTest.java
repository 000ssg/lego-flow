package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ForwardProxyDemoTest {

    private SimpleForwardProxyDemo demo;

    @BeforeEach
    void setUp() {
        demo = new SimpleForwardProxyDemo();
    }

    @Test
    void testDemoRuns() {
        String result = demo.run();
        assertThat(result).isNotEmpty();
        assertThat(result).contains("200");
    }

    @Test
    void testAllowedHostReturns200() {
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testBlockedHostReturns403() {
        var request = HttpRequest.of(HttpMethod.GET, "http://blocked.com/secret");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testConnectReturns200() {
        var request = HttpRequest.of(HttpMethod.CONNECT, "example.com:443");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRequestCountIncrements() {
        demo.run();
        assertThat(demo.getProxy().getRequestCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testTunnelCountIncrements() {
        demo.run();
        assertThat(demo.getProxy().getTunnelCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testLocalhostAllowed() {
        var request = HttpRequest.of(HttpMethod.GET, "http://localhost/test");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testApiSubdomainAllowed() {
        var request = HttpRequest.of(HttpMethod.GET, "http://api.example.com/data");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testProxyName() {
        assertThat(demo.getProxy().getConfig().getProxyName()).isEqualTo("demo-forward-proxy");
    }

    @Test
    void testResponseContainsProxiedContent() {
        var request = HttpRequest.of(HttpMethod.GET, "http://example.com/page");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getBodyAsString()).contains("example.com");
    }
}
