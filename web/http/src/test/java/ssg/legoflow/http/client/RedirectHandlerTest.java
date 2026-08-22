package ssg.legoflow.http.client;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class RedirectHandlerTest {

    private final RedirectHandler handler = new RedirectHandler(5);

    @Test
    void testIsRedirect301() {
        var response = HttpResponse.of(HttpStatus.MOVED_PERMANENTLY);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new");
        assertThat(handler.isRedirect(response)).isTrue();
    }

    @Test
    void testIsRedirect302() {
        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new");
        assertThat(handler.isRedirect(response)).isTrue();
    }

    @Test
    void testIsRedirect307() {
        var response = HttpResponse.of(HttpStatus.TEMPORARY_REDIRECT);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new");
        assertThat(handler.isRedirect(response)).isTrue();
    }

    @Test
    void testIsRedirect308() {
        var response = HttpResponse.of(HttpStatus.PERMANENT_REDIRECT);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new");
        assertThat(handler.isRedirect(response)).isTrue();
    }

    @Test
    void testIsNotRedirectWithoutLocation() {
        var response = HttpResponse.of(HttpStatus.MOVED_PERMANENTLY);
        assertThat(handler.isRedirect(response)).isFalse();
    }

    @Test
    void testIsNotRedirect200() {
        var response = HttpResponse.of(HttpStatus.OK);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com");
        assertThat(handler.isRedirect(response)).isFalse();
    }

    @Test
    void testResolveAbsoluteUri() {
        var response = HttpResponse.of(HttpStatus.MOVED_PERMANENTLY);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new-path");

        String resolved = handler.resolveRedirectUri("http://old.com/path", response);
        assertThat(resolved).isEqualTo("http://example.com/new-path");
    }

    @Test
    void testResolveRelativeUri() {
        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, "/new-path");

        String resolved = handler.resolveRedirectUri("http://example.com/old-path", response);
        assertThat(resolved).isEqualTo("http://example.com/new-path");
    }

    @Test
    void testGetRedirectMethod303AlwaysGET() {
        assertThat(handler.getRedirectMethod(HttpMethod.POST, 303)).isEqualTo(HttpMethod.GET);
        assertThat(handler.getRedirectMethod(HttpMethod.PUT, 303)).isEqualTo(HttpMethod.GET);
        assertThat(handler.getRedirectMethod(HttpMethod.DELETE, 303)).isEqualTo(HttpMethod.GET);
    }

    @Test
    void testGetRedirectMethod301PostBecomesGet() {
        assertThat(handler.getRedirectMethod(HttpMethod.POST, 301)).isEqualTo(HttpMethod.GET);
        assertThat(handler.getRedirectMethod(HttpMethod.GET, 301)).isEqualTo(HttpMethod.GET);
    }

    @Test
    void testGetRedirectMethod307PreservesMethod() {
        assertThat(handler.getRedirectMethod(HttpMethod.POST, 307)).isEqualTo(HttpMethod.POST);
        assertThat(handler.getRedirectMethod(HttpMethod.PUT, 307)).isEqualTo(HttpMethod.PUT);
        assertThat(handler.getRedirectMethod(HttpMethod.GET, 307)).isEqualTo(HttpMethod.GET);
    }

    @Test
    void testGetRedirectMethod308PreservesMethod() {
        assertThat(handler.getRedirectMethod(HttpMethod.POST, 308)).isEqualTo(HttpMethod.POST);
        assertThat(handler.getRedirectMethod(HttpMethod.DELETE, 308)).isEqualTo(HttpMethod.DELETE);
    }

    @Test
    void testCreateRedirectRequest() {
        // Given
        var original = HttpRequest.of(HttpMethod.GET, "http://example.com/old");
        original.getHeaders().set(HttpHeaders.ACCEPT, "text/html");
        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, "http://example.com/new");

        // When
        var redirect = handler.createRedirectRequest(original, response);

        // Then
        assertThat(redirect).isNotNull();
        assertThat(redirect.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(redirect.getUri()).isEqualTo("http://example.com/new");
        assertThat(redirect.getHeaders().get(HttpHeaders.ACCEPT)).isEqualTo("text/html");
    }

    @Test
    void testMaxRedirects() {
        assertThat(handler.getMaxRedirects()).isEqualTo(5);
    }

    @Test
    void testMaxRedirectsNegativeThrows() {
        assertThatThrownBy(() -> new RedirectHandler(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTooManyRedirectsException() {
        var ex = new RedirectHandler.TooManyRedirectsException(5,
                List.of("http://a.com", "http://b.com"));
        assertThat(ex.getRedirectCount()).isEqualTo(5);
        assertThat(ex.getRedirectUrls()).containsExactly("http://a.com", "http://b.com");
    }

    @Test
    void testRedirectResultWasRedirected() {
        var result = new RedirectHandler.RedirectResult(
                HttpResponse.of(HttpStatus.OK), List.of("http://a.com"), 1);
        assertThat(result.wasRedirected()).isTrue();

        var noRedirect = new RedirectHandler.RedirectResult(
                HttpResponse.of(HttpStatus.OK), List.of(), 0);
        assertThat(noRedirect.wasRedirected()).isFalse();
    }
}
