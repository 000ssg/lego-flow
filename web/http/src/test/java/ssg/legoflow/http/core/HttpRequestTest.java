package ssg.legoflow.http.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HttpRequestTest {

    @Test
    void testFactoryMethodOf() {
        // When
        var request = HttpRequest.of(HttpMethod.GET, "/index.html");

        // Then
        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUri()).isEqualTo("/index.html");
        assertThat(request.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        assertThat(request.getHeaders()).isNotNull();
        assertThat(request.getHeaders().isEmpty()).isTrue();
    }

    @Test
    void testConstructorWithAllParameters() {
        // Given
        var headers = new HttpHeaders();
        headers.set("Host", "example.com");

        // When
        var request = new HttpRequest(HttpMethod.POST, "/api/data", HttpVersion.HTTP_1_0, headers);

        // Then
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUri()).isEqualTo("/api/data");
        assertThat(request.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);
        assertThat(request.getHeaders().get("host")).isEqualTo("example.com");
    }

    @Test
    void testGetQueryParamsMultiple() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/search?q=hello&page=2&limit=10");

        // When
        var params = request.getQueryParams();

        // Then
        assertThat(params).hasSize(3);
        assertThat(params).containsEntry("q", "hello");
        assertThat(params).containsEntry("page", "2");
        assertThat(params).containsEntry("limit", "10");
    }

    @Test
    void testGetQueryParamsEmpty() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/index.html");

        // When
        var params = request.getQueryParams();

        // Then
        assertThat(params).isEmpty();
    }

    @Test
    void testGetQueryParamsWithTrailingQuestionMark() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/index.html?");

        // When
        var params = request.getQueryParams();

        // Then
        assertThat(params).isEmpty();
    }

    @Test
    void testGetQueryParamsUrlEncoded() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/search?q=hello+world&name=foo%20bar");

        // When
        var params = request.getQueryParams();

        // Then
        assertThat(params).containsEntry("q", "hello world");
        assertThat(params).containsEntry("name", "foo bar");
    }

    @Test
    void testGetQueryParamsKeyOnly() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/search?debug&verbose");

        // When
        var params = request.getQueryParams();

        // Then
        assertThat(params).containsEntry("debug", "");
        assertThat(params).containsEntry("verbose", "");
    }

    @Test
    void testGetQueryParamsReturnsUnmodifiableMap() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/search?q=test");

        // When/Then
        assertThatThrownBy(() -> request.getQueryParams().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
