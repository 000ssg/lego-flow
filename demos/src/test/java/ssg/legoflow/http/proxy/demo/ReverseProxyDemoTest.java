package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ReverseProxyDemoTest {

    private ReverseProxyDemo demo;

    @BeforeEach
    void setUp() {
        demo = new ReverseProxyDemo();
    }

    @Test
    void testDemoRuns() {
        String result = demo.run();
        assertThat(result).isNotEmpty();
        assertThat(result).contains("200");
    }

    @Test
    void testApiRouteForwards() {
        var request = HttpRequest.of(HttpMethod.GET, "/api/users");
        request.getHeaders().set(HttpHeaders.HOST, "myapp.com");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("api-server:8081");
    }

    @Test
    void testStaticRouteForwards() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/style.css");
        request.getHeaders().set(HttpHeaders.HOST, "myapp.com");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("static-server:8082");
    }

    @Test
    void testDefaultRoute() {
        var request = HttpRequest.of(HttpMethod.GET, "/index.html");
        request.getHeaders().set(HttpHeaders.HOST, "myapp.com");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("web-server:8080");
    }

    @Test
    void testRequestCountIncremented() {
        demo.run();
        assertThat(demo.getProxy().getRequestCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testRouteCount() {
        assertThat(demo.getProxy().getRoutes()).hasSize(3);
    }

    @Test
    void testProxyName() {
        assertThat(demo.getProxy().getConfig().getProxyName()).isEqualTo("demo-reverse-proxy");
    }

    @Test
    void testApiPathStripping() {
        // API route strips /api prefix
        var request = HttpRequest.of(HttpMethod.GET, "/api/users/123");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        // The response should contain the rewritten path without /api
        assertThat(response.getBodyAsString()).contains("/users/123");
    }

    @Test
    void testStaticPathNotStripped() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/js/app.js");
        var response = demo.getProxy().handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("/static/js/app.js");
    }

    @Test
    void testMultipleRequestsHandled() {
        for (int i = 0; i < 10; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/api/resource");
            var response = demo.getProxy().handleRequest(request);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        }
        assertThat(demo.getProxy().getRequestCount()).isEqualTo(10);
    }
}
