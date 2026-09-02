package ssg.legoflow.http.staticcontent;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
class StaticContentHandlerTest {

    @Test
    void testHandleResolvesContent() {
        var resolver = createResolver("hello.txt", "Hello!", MediaType.TEXT_PLAIN);
        var handler = new StaticContentHandler(resolver);
        var request = HttpRequest.of(HttpMethod.GET, "/static/hello.txt");

        var response = handler.handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello!");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
    }

    @Test
    void testHandleReturns404ForMissingContent() {
        var resolver = emptyResolver();
        var handler = new StaticContentHandler(resolver);
        var request = HttpRequest.of(HttpMethod.GET, "/static/missing.txt");

        var response = handler.handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testHandleSetsContentLength() {
        var content = "12345";
        var resolver = createResolver("data.txt", content, MediaType.TEXT_PLAIN);
        var handler = new StaticContentHandler(resolver);
        var request = HttpRequest.of(HttpMethod.GET, "/static/data.txt");

        var response = handler.handle(request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH))
                .isEqualTo(String.valueOf(content.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void testHandleSetsCacheControlHeader() {
        var resolver = createResolver("style.css", "body{}", new MediaType("text", "css"));
        var config = new StaticContentConfig();
        config.setCacheMaxAge(7200);
        var handler = new StaticContentHandler(resolver, config);
        var request = HttpRequest.of(HttpMethod.GET, "/static/style.css");

        var response = handler.handle(request);

        assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL)).contains("max-age=7200");
    }

    @Test
    void testHandleStripsUrlPrefix() {
        var resolver = createResolver("file.html", "<html></html>", MediaType.TEXT_HTML);
        var config = new StaticContentConfig();
        config.setUrlPrefix("/assets");
        var handler = new StaticContentHandler(resolver, config);
        var request = HttpRequest.of(HttpMethod.GET, "/assets/file.html");

        var response = handler.handle(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testGetResolverAndConfig() {
        var resolver = emptyResolver();
        var config = new StaticContentConfig();
        var handler = new StaticContentHandler(resolver, config);

        assertThat(handler.getResolver()).isSameAs(resolver);
        assertThat(handler.getConfig()).isSameAs(config);
    }

    private ContentResolver createResolver(String expectedPath, String content, MediaType mediaType) {
        return path -> {
            var cleanPath = path.startsWith("/") ? path.substring(1) : path;
            if (cleanPath.equals(expectedPath)) {
                return Optional.of(new ContentResolver.ResolvedContent(
                        ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)),
                        mediaType, System.currentTimeMillis()));
            }
            return Optional.empty();
        };
    }

    private ContentResolver emptyResolver() {
        return _ -> Optional.empty();
    }
}
