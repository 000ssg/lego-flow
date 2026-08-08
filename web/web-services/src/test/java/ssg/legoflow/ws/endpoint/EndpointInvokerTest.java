package ssg.legoflow.ws.endpoint;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EndpointInvoker}.
 *
 * @since 0.1.0
 */
class EndpointInvokerTest {

    private final HttpContext ctx = new HttpContext() {
        @Override public HttpRequest getRequest() { return null; }
        @Override public HttpResponse getResponse() { return null; }
        @Override public void setResponse(HttpResponse response) {}
        @Override public org.slf4j.Logger getLogger() { return org.slf4j.LoggerFactory.getLogger(EndpointInvokerTest.class); }
        @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() { return null; }
        @Override public void handleError(Throwable error) {}
        @Override public <T> T getAttribute(String key) { return null; }
        @Override public void setAttribute(String key, Object value) {}
        @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return null; }
        @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return null; }
        @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return null; }
        @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return null; }
        @Override public ssg.legoflow.service.user.ServiceUser getUser() { return ssg.legoflow.service.user.ServiceUser.anonymous(); }
        @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return false; }
        @Override public void checkPermission(String operation) {}
    };

    @Test
    void testInvokeMatchingEndpoint() {
        var endpoints = List.of(
                new Endpoint("/users", HttpMethod.GET,
                        (c, req) -> HttpResponse.of(HttpStatus.OK, "users list"))
        );
        var invoker = new EndpointInvoker(endpoints);
        var request = HttpRequest.of(HttpMethod.GET, "/users");

        var response = invoker.invoke(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("users list");
    }

    @Test
    void testInvokeNotFound() {
        var endpoints = List.of(
                new Endpoint("/users", HttpMethod.GET,
                        (c, req) -> HttpResponse.of(HttpStatus.OK, "users"))
        );
        var invoker = new EndpointInvoker(endpoints);
        var request = HttpRequest.of(HttpMethod.GET, "/missing");

        var response = invoker.invoke(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testInvokeWithQueryStringStripped() {
        var endpoints = List.of(
                new Endpoint("/search", HttpMethod.GET,
                        (c, req) -> HttpResponse.of(HttpStatus.OK, "search result"))
        );
        var invoker = new EndpointInvoker(endpoints);
        var request = HttpRequest.of(HttpMethod.GET, "/search?q=test&page=1");

        var response = invoker.invoke(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testInvokeWithNoEndpoints() {
        var invoker = new EndpointInvoker(List.of());
        var request = HttpRequest.of(HttpMethod.GET, "/any");

        var response = invoker.invoke(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testInvokeMultipleEndpoints() {
        var endpoints = List.of(
                new Endpoint("/a", HttpMethod.GET,
                        (c, req) -> HttpResponse.of(HttpStatus.OK, "A")),
                new Endpoint("/b", HttpMethod.POST,
                        (c, req) -> HttpResponse.of(HttpStatus.CREATED)),
                new Endpoint("/c", HttpMethod.DELETE,
                        (c, req) -> HttpResponse.of(HttpStatus.NO_CONTENT))
        );
        var invoker = new EndpointInvoker(endpoints);

        assertThat(invoker.invoke(ctx, HttpRequest.of(HttpMethod.GET, "/a")).getStatus())
                .isEqualTo(HttpStatus.OK);
        assertThat(invoker.invoke(ctx, HttpRequest.of(HttpMethod.POST, "/b")).getStatus())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void testGetEndpoints() {
        var eps = List.of(new Endpoint("/x", HttpMethod.GET, (c,r) -> HttpResponse.of(HttpStatus.OK)));
        var invoker = new EndpointInvoker(eps);
        assertThat(invoker.getEndpoints()).containsExactlyElementsOf(eps);
    }

    @Test
    void testInvokeMethodMismatch() {
        var endpoints = List.of(
                new Endpoint("/data", HttpMethod.POST,
                        (c, req) -> HttpResponse.of(HttpStatus.OK))
        );
        var invoker = new EndpointInvoker(endpoints);
        var request = HttpRequest.of(HttpMethod.GET, "/data");

        var response = invoker.invoke(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND); // method doesn't match
    }
}
