package ssg.legoflow.http.demo;

import ssg.legoflow.http.content.ContentNegotiator;
import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.header.LanguageTag;
import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Demonstrates HTTP content negotiation using the ContentNegotiator.
 * Tests media type selection with quality values, encoding negotiation,
 * language negotiation, and edge cases.
 */
class ContentNegotiationDemoTest {

    private ContentNegotiator negotiator;

    @BeforeEach
    void setUp() {
        negotiator = new ContentNegotiator();
    }

    @Test
    void testNegotiateMediaTypeWithQuality() {
        // Given: a client that prefers JSON over HTML
        var acceptHeader = "text/html;q=0.8, application/json;q=1.0";
        var available = List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);

        // When: negotiating the media type
        var result = negotiator.negotiateMediaType(acceptHeader, available);

        // Then: JSON is selected (higher quality)
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateMediaTypeWithWildcard() {
        // Given: a client that accepts anything
        var acceptHeader = "*/*";
        var available = List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);

        // When: negotiating the media type
        var result = negotiator.negotiateMediaType(acceptHeader, available);

        // Then: the first available type is selected
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void testNegotiateMediaTypeNoMatch() {
        // Given: a client that only accepts XML, but server only has JSON
        var acceptHeader = "application/xml";
        var available = List.of(MediaType.TEXT_HTML);

        // When: negotiating the media type
        var result = negotiator.negotiateMediaType(acceptHeader, available);

        // Then: no match is found
        assertThat(result).isEmpty();
    }

    @Test
    void testNegotiateMediaTypeNullHeader() {
        // Given: no Accept header
        var available = List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);

        // When: negotiating with null header
        var result = negotiator.negotiateMediaType(null, available);

        // Then: the first available type is returned as default
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(MediaType.TEXT_HTML);
    }

    @Test
    void testNegotiateEncodingPreference() {
        // Given: a client that prefers gzip over deflate
        var acceptEncodingHeader = "gzip;q=1.0, deflate;q=0.5, br;q=0.8";
        var available = List.of(ContentEncoding.GZIP, ContentEncoding.DEFLATE, ContentEncoding.BR);

        // When: negotiating encoding
        var result = negotiator.negotiateEncoding(acceptEncodingHeader, available);

        // Then: gzip is selected (highest quality)
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(ContentEncoding.GZIP);
    }

    @Test
    void testNegotiateEncodingFallsBackToIdentity() {
        // Given: no Accept-Encoding header
        var available = List.of(ContentEncoding.GZIP, ContentEncoding.DEFLATE);

        // When: negotiating encoding with null header
        var result = negotiator.negotiateEncoding(null, available);

        // Then: identity encoding is returned as default
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(ContentEncoding.IDENTITY);
    }

    @Test
    void testNegotiateLanguagePreference() {
        // Given: a client that prefers French, then English
        var acceptLanguageHeader = "fr;q=1.0, en;q=0.8, de;q=0.5";
        var available = List.of(
                LanguageTag.parse("en-US"),
                LanguageTag.parse("fr-FR"),
                LanguageTag.parse("de-DE"));

        // When: negotiating language
        var result = negotiator.negotiateLanguage(acceptLanguageHeader, available);

        // Then: French is selected (highest quality)
        assertThat(result).isPresent();
        assertThat(result.get().primaryTag()).isEqualTo("fr");
    }

    @Test
    void testNegotiateLanguageNoMatch() {
        // Given: a client that only accepts Japanese
        var acceptLanguageHeader = "ja";
        var available = List.of(
                LanguageTag.parse("en"),
                LanguageTag.parse("fr"));

        // When: negotiating language
        var result = negotiator.negotiateLanguage(acceptLanguageHeader, available);

        // Then: no match
        assertThat(result).isEmpty();
    }
}
