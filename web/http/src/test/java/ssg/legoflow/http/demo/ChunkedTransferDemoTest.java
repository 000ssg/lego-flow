package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ChunkedTransferDemoTest {

    private HttpServer server;
    private DefaultContext ctx;

    @BeforeEach
    void setUp() {
        server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        ctx = new DefaultContext();

        server.getRouter().get("/stream", (httpCtx, req) -> {
            var chunks = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                chunks.append("chunk-").append(i).append("\n");
            }
            var response = HttpResponse.of(HttpStatus.OK, chunks.toString());
            response.getHeaders().set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
            return response;
        });

        server.getRouter().get("/large", (httpCtx, req) -> {
            var largeBody = "X".repeat(10000);
            var response = HttpResponse.of(HttpStatus.OK, largeBody);
            response.getHeaders().set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            return response;
        });
    }

    @Test
    void testChunkedResponseContainsAllChunks() {
        var request = HttpRequest.of(HttpMethod.GET, "/stream");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        var body = response.getBodyAsString();
        for (int i = 0; i < 10; i++) {
            assertThat(body).contains("chunk-" + i);
        }
    }

    @Test
    void testChunkedResponseHasTransferEncodingHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/stream");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.TRANSFER_ENCODING)).isEqualTo("chunked");
    }

    @Test
    void testLargeChunkedResponse() {
        var request = HttpRequest.of(HttpMethod.GET, "/large");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString().length()).isEqualTo(10000);
    }

    @Test
    void testChunkedResponseContentType() {
        var request = HttpRequest.of(HttpMethod.GET, "/stream");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
    }

    @Test
    void testChunkedResponseWithCompression() {
        server.setCompressionEnabled(true);
        var request = HttpRequest.of(HttpMethod.GET, "/large");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
    }
}
