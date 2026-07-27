package ssg.legoflow.http.server;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HttpRouterTest {

    // HttpContext is only passed through to handlers; our test handlers ignore it, so null is safe
    private final HttpContext ctx = null;

    @Test
    void testRouteDispatchGet() {
        // Given
        var router = new HttpRouter();
        router.get("/hello", (c, req) -> HttpResponse.of(HttpStatus.OK, "Hello"));

        var request = HttpRequest.of(HttpMethod.GET, "/hello");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello");
    }

    @Test
    void testRouteDispatchPost() {
        // Given
        var router = new HttpRouter();
        router.post("/data", (c, req) -> HttpResponse.of(HttpStatus.CREATED, "Created"));

        var request = HttpRequest.of(HttpMethod.POST, "/data");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void testRouteNotFoundReturns404() {
        // Given
        var router = new HttpRouter();
        var request = HttpRequest.of(HttpMethod.GET, "/nonexistent");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testMethodNotAllowedReturns405() {
        // Given
        var router = new HttpRouter();
        router.get("/hello", (c, req) -> HttpResponse.of(HttpStatus.OK));

        var request = HttpRequest.of(HttpMethod.DELETE, "/hello");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().get("allow")).contains("GET");
    }

    @Test
    void testHeadFallsBackToGet() {
        // Given
        var router = new HttpRouter();
        router.get("/hello", (c, req) -> HttpResponse.of(HttpStatus.OK, "content"));

        var request = HttpRequest.of(HttpMethod.HEAD, "/hello");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testCustomDefaultHandler() {
        // Given
        var router = new HttpRouter();
        router.setDefaultHandler((c, req) -> HttpResponse.of(HttpStatus.BAD_REQUEST, "Custom 400"));

        var request = HttpRequest.of(HttpMethod.GET, "/unknown");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testQueryParametersStrippedForRouting() {
        // Given
        var router = new HttpRouter();
        router.get("/search", (c, req) -> HttpResponse.of(HttpStatus.OK, "found"));

        var request = HttpRequest.of(HttpMethod.GET, "/search?q=test&page=1");

        // When
        var response = router.dispatch(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testGetRegisteredPaths() {
        // Given
        var router = new HttpRouter();
        router.get("/a", (c, req) -> HttpResponse.of(HttpStatus.OK));
        router.post("/b", (c, req) -> HttpResponse.of(HttpStatus.OK));

        // Then
        assertThat(router.getRegisteredPaths()).containsExactlyInAnyOrder("/a", "/b");
    }
}
