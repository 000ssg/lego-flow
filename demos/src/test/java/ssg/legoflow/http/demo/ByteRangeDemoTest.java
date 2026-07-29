package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.transfer.ByteRangeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ByteRangeDemoTest {

    private HttpServer server;
    private DefaultContext ctx;
    private static final String CONTENT = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @BeforeEach
    void setUp() {
        server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        server.setCompressionEnabled(false);
        ctx = new DefaultContext();

        server.getRouter().get("/file", (httpCtx, req) -> {
            var rangeHeader = req.getHeaders().get(HttpHeaders.RANGE);
            var body = ByteBuffer.wrap(CONTENT.getBytes(StandardCharsets.UTF_8));

            if (rangeHeader == null) {
                var response = HttpResponse.of(HttpStatus.OK, CONTENT);
                response.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
                return response;
            }

            var ranges = ByteRangeHandler.parseRangeHeader(rangeHeader, CONTENT.length());
            if (ranges.isEmpty()) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "Invalid range");
            }

            var range = ranges.getFirst();
            if (!ByteRangeHandler.isRangeSatisfiable(range, CONTENT.length())) {
                var response = HttpResponse.of(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                response.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + CONTENT.length());
                return response;
            }

            var partial = ByteRangeHandler.extractRange(body, range);
            var response = new HttpResponse(HttpStatus.PARTIAL_CONTENT, HttpVersion.HTTP_1_1, new HttpHeaders());
            response.setBody(partial);
            response.getHeaders().set(HttpHeaders.CONTENT_RANGE,
                    ByteRangeHandler.formatContentRange(range, CONTENT.length()));
            response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(range.length()));
            response.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
            return response;
        });
    }

    @Test
    void testFullGetReturnsEntireContent() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo(CONTENT);
        assertThat(response.getHeaders().get(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }

    @Test
    void testPartialGetReturns206() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=0-4");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBodyAsString()).isEqualTo("ABCDE");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 0-4/26");
    }

    @Test
    void testMiddleRangeRequest() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=10-14");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBodyAsString()).isEqualTo("KLMNO");
    }

    @Test
    void testSuffixRangeRequest() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=-5");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBodyAsString()).isEqualTo("VWXYZ");
    }

    @Test
    void testOpenEndedRangeRequest() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=23-");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBodyAsString()).isEqualTo("XYZ");
    }

    @Test
    void testUnsatisfiableRangeReturns416() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=100-200");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */26");
    }

    @Test
    void testContentLengthMatchesRange() {
        var request = HttpRequest.of(HttpMethod.GET, "/file");
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=0-9");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH)).isEqualTo("10");
    }
}
