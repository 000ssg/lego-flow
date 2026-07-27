package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.transfer.ContentEncodingCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class CompressionDemoTest {

    private HttpServer server;
    private DefaultContext ctx;

    @BeforeEach
    void setUp() {
        server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.getRouter().get("/data", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello, Compressed World! ".repeat(100)));
        ctx = new DefaultContext();
    }

    @Test
    void testGzipCompressionAppliedWhenClientAccepts() {
        // Given: a request with Accept-Encoding: gzip
        var request = HttpRequest.of(HttpMethod.GET, "/data");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");

        // When: processed through the server
        var response = server.handleRequest(ctx, request);

        // Then: the response is gzip-compressed with proper headers
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
        assertThat(response.getHeaders().get(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ACCEPT_ENCODING);

        // And: the compressed body is smaller than the original
        var compressedSize = response.getBody().remaining();
        var originalSize = "Hello, Compressed World! ".repeat(100).getBytes(StandardCharsets.UTF_8).length;
        assertThat(compressedSize).isLessThan(originalSize);

        // And: decompressing recovers the original content
        var decompressor = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.DECOMPRESS);
        ByteBuffer[] decompressed = decompressor.filter(ctx, response.getBody());
        var bytes = new byte[decompressed[0].remaining()];
        decompressed[0].get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("Hello, Compressed World! ".repeat(100));
    }

    @Test
    void testNoCompressionWithoutAcceptEncoding() {
        // Given: a request without Accept-Encoding header
        var request = HttpRequest.of(HttpMethod.GET, "/data");

        // When: processed through the server
        var response = server.handleRequest(ctx, request);

        // Then: no compression applied, but Vary still set for caching
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(response.getHeaders().get(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ACCEPT_ENCODING);
        assertThat(response.getBodyAsString())
                .isEqualTo("Hello, Compressed World! ".repeat(100));
    }

    @Test
    void testNoCompressionForEmptyBody() {
        // Given: an endpoint returning no body
        server.getRouter().get("/empty", (httpCtx, req) -> HttpResponse.of(HttpStatus.NO_CONTENT));
        var request = HttpRequest.of(HttpMethod.GET, "/empty");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        // When: processed through the server
        var response = server.handleRequest(ctx, request);

        // Then: no compression applied (no body to compress)
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
    }

    @Test
    void testCompressionCanBeDisabled() {
        // Given: compression is disabled
        server.setCompressionEnabled(false);
        var request = HttpRequest.of(HttpMethod.GET, "/data");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        // When: processed through the server
        var response = server.handleRequest(ctx, request);

        // Then: no compression even though client accepts gzip
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(response.getBodyAsString())
                .isEqualTo("Hello, Compressed World! ".repeat(100));
    }

    @Test
    void testAlreadyEncodedResponseNotDoubleCompressed() {
        // Given: an endpoint that already applies encoding
        server.getRouter().get("/pre-encoded", (httpCtx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "pre-encoded");
            response.getHeaders().set(HttpHeaders.CONTENT_ENCODING, "identity");
            return response;
        });
        var request = HttpRequest.of(HttpMethod.GET, "/pre-encoded");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        // When: processed through the server
        var response = server.handleRequest(ctx, request);

        // Then: existing encoding is preserved, not double-compressed
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("identity");
    }
}
