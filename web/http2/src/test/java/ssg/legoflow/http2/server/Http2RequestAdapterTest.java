package ssg.legoflow.http2.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.stream.Http2Stream;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
class Http2RequestAdapterTest {

    @Test
    void testAdaptGetRequest() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/hello");
        stream.headers().set(":scheme", "https");
        stream.headers().set(":authority", "www.example.com");

        var request = adapter.adapt(stream);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUri()).isEqualTo("/hello");
        assertThat(request.getVersion()).isEqualTo(HttpVersion.parse("HTTP/2"));
        assertThat(request.getHeaders().get("host")).isEqualTo("www.example.com");
    }

    @Test
    void testAdaptPostRequestWithBody() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "POST");
        stream.headers().set(":path", "/api/data");
        stream.headers().set(":scheme", "https");
        stream.headers().set("content-type", "application/json");
        stream.addData(ByteBuffer.wrap("{\"key\":\"value\"}".getBytes()));

        var request = adapter.adapt(stream);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getHeaders().get("content-type")).isEqualTo("application/json");
        assertThat(request.getBodyAsString()).isEqualTo("{\"key\":\"value\"}");
        assertThat(request.getHeaders().get("content-length")).isNotNull();
    }

    @Test
    void testAdaptFiltersOutPseudoHeaders() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/");
        stream.headers().set(":scheme", "https");
        stream.headers().set("accept", "text/html");

        var request = adapter.adapt(stream);

        assertThat(request.getHeaders().contains(":method")).isFalse();
        assertThat(request.getHeaders().contains(":path")).isFalse();
        assertThat(request.getHeaders().contains(":scheme")).isFalse();
        assertThat(request.getHeaders().get("accept")).isEqualTo("text/html");
    }

    @Test
    void testAdaptMissingPseudoHeaders() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");

        assertThatThrownBy(() -> adapter.adapt(stream))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(":path");
    }

    @Test
    void testAdaptResponseHeaders() {
        var adapter = new Http2RequestAdapter();
        var response = HttpResponse.of(HttpStatus.OK, "body");
        response.getHeaders().set("content-type", "text/html");
        response.getHeaders().set("connection", "keep-alive");

        var h2Headers = adapter.adaptResponseHeaders(response);

        assertThat(h2Headers.get(":status")).isEqualTo("200");
        assertThat(h2Headers.get("content-type")).isEqualTo("text/html");
        assertThat(h2Headers.contains("connection")).isFalse();
    }

    @Test
    void testAdaptResponseFiltersConnectionHeaders() {
        var adapter = new Http2RequestAdapter();
        var response = HttpResponse.of(HttpStatus.OK);
        response.getHeaders().set("transfer-encoding", "chunked");
        response.getHeaders().set("keep-alive", "timeout=5");

        var h2Headers = adapter.adaptResponseHeaders(response);

        assertThat(h2Headers.contains("transfer-encoding")).isFalse();
        assertThat(h2Headers.contains("keep-alive")).isFalse();
    }

    @Test
    void testAuthorityMapsToHost() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/");
        stream.headers().set(":authority", "api.example.com");

        var request = adapter.adapt(stream);

        assertThat(request.getHeaders().get("host")).isEqualTo("api.example.com");
    }

    @Test
    void testExistingHostNotOverridden() {
        var adapter = new Http2RequestAdapter();
        var stream = new Http2Stream(1, 65535);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/");
        stream.headers().set(":authority", "api.example.com");
        stream.headers().set("host", "existing.example.com");

        var request = adapter.adapt(stream);

        assertThat(request.getHeaders().get("host")).isEqualTo("existing.example.com");
    }
}
