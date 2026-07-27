package ssg.legoflow.http.content;

import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MediaTypeRegistryTest {

    private final MediaTypeRegistry registry = new MediaTypeRegistry();

    @Test
    void testGetByExtensionHtml() {
        // When
        var result = registry.getByExtension("html");

        // Then
        assertThat(result).isPresent().contains(MediaType.TEXT_HTML);
    }

    @Test
    void testGetByExtensionJson() {
        // When
        var result = registry.getByExtension("json");

        // Then
        assertThat(result).isPresent().contains(MediaType.APPLICATION_JSON);
    }

    @Test
    void testGetByExtensionCaseInsensitive() {
        // When
        var result = registry.getByExtension("HTML");

        // Then
        assertThat(result).isPresent().contains(MediaType.TEXT_HTML);
    }

    @Test
    void testGetByExtensionUnknown() {
        // When
        var result = registry.getByExtension("xyz");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testGetByFilename() {
        // When
        var result = registry.getByFilename("index.html");

        // Then
        assertThat(result).isPresent().contains(MediaType.TEXT_HTML);
    }

    @Test
    void testGetByFilenameNoExtension() {
        // When
        var result = registry.getByFilename("Makefile");

        // Then
        assertThat(result).isPresent().contains(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void testRegisterCustomMediaType() {
        // Given
        var customType = new MediaType("application", "x-custom");

        // When
        registry.register("custom", customType);

        // Then
        assertThat(registry.getByExtension("custom")).isPresent().contains(customType);
    }

    @Test
    void testGetByFilenameWithMultipleDots() {
        // When
        var result = registry.getByFilename("archive.tar.gz");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().subtype()).isEqualTo("gzip");
    }
}
