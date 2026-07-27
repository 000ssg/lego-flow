package ssg.legoflow.http3.server;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class Http3RequestAdapterTest {

    private final Http3RequestAdapter adapter = new Http3RequestAdapter();

    @Test
    void testAdaptRequestFromMap() {
        // Given
        var headers = new LinkedHashMap<String, String>();
        headers.put(":method", "GET");
        headers.put(":path", "/api/resource");
        headers.put(":scheme", "https");
        headers.put(":authority", "example.com");
        headers.put("accept", "application/json");

        // When
        var request = adapter.adaptRequest(headers, null);

        // Then
        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUri()).isEqualTo("/api/resource");
        assertThat(request.getVersion()).isEqualTo(HttpVersion.HTTP_3);
        assertThat(request.getHeaders().get(HttpHeaders.HOST)).isEqualTo("example.com");
        assertThat(request.getHeaders().get("accept")).isEqualTo("application/json");
    }

    @Test
    void testAdaptRequestFromEntries() {
        // Given
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "POST"),
                new AbstractMap.SimpleEntry<>(":path", "/data"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>("content-type", "application/json")
        );
        var body = ByteBuffer.wrap("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));

        // When
        var request = adapter.adaptRequest(headers, body);

        // Then
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUri()).isEqualTo("/data");
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().remaining()).isGreaterThan(0);
    }

    @Test
    void testAdaptRequestMissingPseudoHeaders() {
        // Given
        var headers = new LinkedHashMap<String, String>();
        headers.put(":scheme", "https");

        // When/Then
        assertThatThrownBy(() -> adapter.adaptRequest(headers, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(":method");
    }

    @Test
    void testAdaptResponseHeaders() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK, "body");
        response.getHeaders().set("content-type", "text/plain");

        // When
        var headers = adapter.adaptResponseHeaders(response);

        // Then
        assertThat(headers).isNotEmpty();
        assertThat(headers.get(0).getKey()).isEqualTo(":status");
        assertThat(headers.get(0).getValue()).isEqualTo("200");

        var contentType = headers.stream()
                .filter(e -> e.getKey().equals("content-type"))
                .findFirst();
        assertThat(contentType).isPresent();
    }

    @Test
    void testAdaptResponseExcludesConnectionHeaders() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);
        response.getHeaders().set("connection", "keep-alive");
        response.getHeaders().set("transfer-encoding", "chunked");
        response.getHeaders().set("keep-alive", "timeout=5");
        response.getHeaders().set("x-custom", "value");

        // When
        var headers = adapter.adaptResponseHeaders(response);

        // Then: connection-related headers should be excluded
        var names = headers.stream().map(Map.Entry::getKey).toList();
        assertThat(names).doesNotContain("connection", "transfer-encoding", "keep-alive");
        assertThat(names).contains(":status", "x-custom");
    }

    @Test
    void testAdaptResponseBody() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK, "response body");

        // When
        var body = adapter.adaptResponseBody(response);

        // Then
        assertThat(body).isNotNull();
        assertThat(body.remaining()).isGreaterThan(0);
    }

    @Test
    void testAdaptResponseBodyNull() {
        // Given
        var response = HttpResponse.of(HttpStatus.NO_CONTENT);

        // When
        var body = adapter.adaptResponseBody(response);

        // Then
        assertThat(body).isNull();
    }

    @Test
    void testAuthorityToHostMapping() {
        // Given
        var headers = new LinkedHashMap<String, String>();
        headers.put(":method", "GET");
        headers.put(":path", "/");
        headers.put(":authority", "myhost.com");

        // When
        var request = adapter.adaptRequest(headers, null);

        // Then
        assertThat(request.getHeaders().get(HttpHeaders.HOST)).isEqualTo("myhost.com");
    }
}
