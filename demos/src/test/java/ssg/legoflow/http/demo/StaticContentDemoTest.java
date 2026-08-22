package ssg.legoflow.http.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.server.StaticFileServer;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.http.staticcontent.ContentResolver;
import ssg.legoflow.http.staticcontent.StaticContentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
class StaticContentDemoTest {

    private StaticFileServer fileServer;

    @BeforeEach
    void setUp() {
        var resolver = new TestContentResolver();
        var config = new StaticContentConfig();
        config.setUrlPrefix("/static");
        fileServer = new StaticFileServer(resolver, config, 8082);
    }

    @Test
    void testServeHtmlFileWithCorrectMimeType() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/index.html");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/html");
        assertThat(response.getBodyAsString()).contains("<html>");
    }

    @Test
    void testServeCssFileWithCorrectMimeType() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/style.css");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/css");
    }

    @Test
    void testServeJsonFileWithCorrectMimeType() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/data.json");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void testReturn404ForMissingFile() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/missing.txt");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testRootRouteReturnsOk() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var response = fileServer.getServer().getRouter().dispatch(null, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Static File Server");
    }

    @Test
    void testCacheControlHeaderIsSet() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/index.html");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL)).contains("max-age=");
    }

    @Test
    void testContentLengthHeaderIsSet() {
        var request = HttpRequest.of(HttpMethod.GET, "/static/index.html");

        var response = fileServer.getContentHandler().handle(request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH)).isNotNull();
        assertThat(Integer.parseInt(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH))).isGreaterThan(0);
    }

    private static class TestContentResolver implements ContentResolver {
        @Override
        public Optional<ResolvedContent> resolve(String path) {
            var cleanPath = path.startsWith("/") ? path.substring(1) : path;
            return switch (cleanPath) {
                case "index.html" -> Optional.of(content("<html><body>Hello</body></html>", MediaType.TEXT_HTML));
                case "style.css" -> Optional.of(content("body { color: black; }", new MediaType("text", "css")));
                case "data.json" -> Optional.of(content("{\"key\":\"value\"}", MediaType.APPLICATION_JSON));
                default -> Optional.empty();
            };
        }

        private ResolvedContent content(String text, MediaType mediaType) {
            return new ResolvedContent(
                    ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)),
                    mediaType, System.currentTimeMillis());
        }
    }
}
