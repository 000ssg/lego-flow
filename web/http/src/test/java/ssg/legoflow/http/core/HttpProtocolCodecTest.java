package ssg.legoflow.http.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class HttpProtocolCodecTest {

    private final HttpProtocolCodec codec = new HttpProtocolCodec();

    @Test
    void testSerializeAndParseRequestRoundtrip() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/items?page=1");
        request.getHeaders().set("Host", "example.com");
        request.getHeaders().set("Accept", "application/json");

        // When
        ByteBuffer serialized = codec.serializeRequest(request);
        HttpRequest parsed = codec.parseRequest(serialized);

        // Then
        assertThat(parsed.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(parsed.getUri()).isEqualTo("/api/items?page=1");
        assertThat(parsed.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        assertThat(parsed.getHeaders().get("host")).isEqualTo("example.com");
        assertThat(parsed.getHeaders().get("accept")).isEqualTo("application/json");
    }

    @Test
    void testSerializeAndParseResponseRoundtrip() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK, "Hello World");

        // When
        ByteBuffer serialized = codec.serializeResponse(response);
        HttpResponse parsed = codec.parseResponse(serialized);

        // Then
        assertThat(parsed.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(parsed.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        assertThat(parsed.getBodyAsString()).isEqualTo("Hello World");
    }

    @Test
    void testSerializeRequestWithBody() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/api/data");
        ByteBuffer body = ByteBuffer.wrap("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        request.setBody(body);
        request.getHeaders().set("Content-Type", "application/json");

        // When
        ByteBuffer serialized = codec.serializeRequest(request);
        HttpRequest parsed = codec.parseRequest(serialized);

        // Then
        assertThat(parsed.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(parsed.getBodyAsString()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void testParseHeaders() {
        // Given
        String rawHeaders = "Content-Type: text/html\r\nHost: example.com\r\nAccept: */*";

        // When
        HttpHeaders headers = codec.parseHeaders(rawHeaders);

        // Then
        assertThat(headers.get("content-type")).isEqualTo("text/html");
        assertThat(headers.get("host")).isEqualTo("example.com");
        assertThat(headers.get("accept")).isEqualTo("*/*");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void testParseHeadersEmpty() {
        // When
        HttpHeaders headers = codec.parseHeaders("");

        // Then
        assertThat(headers.isEmpty()).isTrue();
    }

    @Test
    void testParseHeadersNull() {
        // When
        HttpHeaders headers = codec.parseHeaders(null);

        // Then
        assertThat(headers.isEmpty()).isTrue();
    }

    @Test
    void testSerializeResponseWithoutBody() {
        // Given
        var response = HttpResponse.of(HttpStatus.NO_CONTENT);

        // When
        ByteBuffer serialized = codec.serializeResponse(response);
        var bytes = new byte[serialized.remaining()];
        serialized.get(bytes);
        String str = new String(bytes, StandardCharsets.UTF_8);

        // Then
        assertThat(str).startsWith("HTTP/1.1 204 No Content\r\n");
    }

    @Test
    void testParseRequestWithQueryParameters() {
        // Given
        var original = HttpRequest.of(HttpMethod.GET, "/search?q=hello&lang=en");
        original.getHeaders().set("Host", "example.com");
        ByteBuffer serialized = codec.serializeRequest(original);

        // When
        HttpRequest parsed = codec.parseRequest(serialized);

        // Then
        assertThat(parsed.getUri()).isEqualTo("/search?q=hello&lang=en");
        assertThat(parsed.getQueryParams()).containsEntry("q", "hello");
        assertThat(parsed.getQueryParams()).containsEntry("lang", "en");
    }
}
