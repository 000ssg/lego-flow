package ssg.legoflow.http.server;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OptionsTraceTest {

    private HttpRouter router;

    @BeforeEach
    void setUp() {
        router = new HttpRouter();
        router.get("/api/users", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "users"));
        router.post("/api/users", (ctx, req) -> HttpResponse.of(HttpStatus.CREATED, "created"));
        router.delete("/api/users", (ctx, req) -> HttpResponse.of(HttpStatus.NO_CONTENT));
    }

    @Test
    void testOptionsReturnsAllowedMethods() {
        // Given
        var request = HttpRequest.of(HttpMethod.OPTIONS, "/api/users");

        // When
        var response = router.dispatch(null, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String allow = response.getHeaders().get(HttpHeaders.ALLOW);
        assertThat(allow).isNotNull();
        assertThat(allow).contains("GET");
        assertThat(allow).contains("POST");
        assertThat(allow).contains("DELETE");
        assertThat(allow).contains("OPTIONS");
        assertThat(allow).contains("HEAD"); // GET implies HEAD
        assertThat(allow).contains("TRACE");
    }

    @Test
    void testOptionsWildcard() {
        // Given
        var request = HttpRequest.of(HttpMethod.OPTIONS, "*");

        // When
        var response = router.dispatch(null, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String allow = response.getHeaders().get(HttpHeaders.ALLOW);
        assertThat(allow).isNotNull();
        assertThat(allow).contains("GET");
    }

    @Test
    void testOptionsNonExistentPath() {
        // Given
        var request = HttpRequest.of(HttpMethod.OPTIONS, "/nonexistent");

        // When
        var response = router.dispatch(null, request);

        // Then — falls through to default handler
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testTraceEchosRequest() {
        // Given
        var request = HttpRequest.of(HttpMethod.TRACE, "/api/users");
        request.getHeaders().set(HttpHeaders.HOST, "example.com");

        // When
        var response = router.dispatch(null, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("message/http");
        String body = response.getBodyAsString();
        assertThat(body).contains("TRACE /api/users HTTP/1.1");
        assertThat(body).contains("host: example.com");
    }

    @Test
    void testTraceDisabled() {
        // Given
        router.setTraceEnabled(false);
        var request = HttpRequest.of(HttpMethod.TRACE, "/api/users");

        // When
        var response = router.dispatch(null, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void testTraceEnabledByDefault() {
        assertThat(router.isTraceEnabled()).isTrue();
    }

    @Test
    void testOptionsWithTraceDisabled() {
        // Given
        router.setTraceEnabled(false);
        var request = HttpRequest.of(HttpMethod.OPTIONS, "/api/users");

        // When
        var response = router.dispatch(null, request);

        // Then
        String allow = response.getHeaders().get(HttpHeaders.ALLOW);
        assertThat(allow).doesNotContain("TRACE");
    }

    @Test
    void testOptionsContentLengthZero() {
        // Given
        var request = HttpRequest.of(HttpMethod.OPTIONS, "/api/users");

        // When
        var response = router.dispatch(null, request);

        // Then
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH)).isEqualTo("0");
    }
}
