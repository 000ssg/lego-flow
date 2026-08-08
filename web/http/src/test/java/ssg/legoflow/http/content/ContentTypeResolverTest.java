package ssg.legoflow.http.content;

import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ContentTypeResolverTest {

    @Test
    void testResolveHtmlFile() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("index.html", null);
        assertThat(result.toString()).isEqualTo("text/html");
    }

    @Test
    void testResolveJsonFile() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("data.json", null);
        assertThat(result.toString()).isEqualTo("application/json");
    }

    @Test
    void testResolveUnknownFileReturnsOctetStream() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("file.unknown", null);
        assertThat(result.toString()).isEqualTo("application/octet-stream");
    }

    @Test
    void testResolveWithNullAcceptHeader() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("test.html", null);
        assertThat(result).isNotNull();
    }

    @Test
    void testResolveWithBlankAcceptHeader() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("test.html", "   ");
        assertThat(result).isNotNull();
    }

    @Test
    void testGetRegistryReturnsNonNullable() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        assertThat(resolver.getRegistry()).isNotNull();
    }

    @Test
    void testResolveWithAcceptHeader() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("data.json", "application/json");
        assertThat(result.toString()).isEqualTo("application/json");
    }

    @Test
    void testResolveUnknownFileWithAcceptHeader() {
        // When file type is unknown but accept header matches, use the negotiated type
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("file.xyz", "*/*");
        assertThat(result).isNotNull();
    }

    @Test
    void testResolveCssFile() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("style.css", null);
        assertThat(result.toString()).isEqualTo("text/css");
    }

    @Test
    void testResolveJavaScriptFile() {
        ContentTypeResolver resolver = new ContentTypeResolver();
        MediaType result = resolver.resolve("app.js", null);
        assertThat(result.toString()).isEqualTo("application/javascript");
    }
}
