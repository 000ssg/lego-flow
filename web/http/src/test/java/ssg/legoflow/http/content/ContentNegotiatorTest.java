package ssg.legoflow.http.content;

import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ContentNegotiatorTest {

    private final ContentNegotiator negotiator = new ContentNegotiator();

    @Test
    void testNegotiateMediaTypeExactMatch() {
        // Given
        var available = List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);

        // When
        var result = negotiator.negotiateMediaType("application/json", available);

        // Then
        assertThat(result).isPresent().contains(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateMediaTypeWithQualityValues() {
        // Given
        var available = List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);

        // When - JSON has higher quality
        var result = negotiator.negotiateMediaType("text/html;q=0.5, application/json;q=1.0", available);

        // Then
        assertThat(result).isPresent().contains(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateMediaTypeWildcard() {
        // Given
        var available = List.of(MediaType.APPLICATION_JSON);

        // When
        var result = negotiator.negotiateMediaType("*/*", available);

        // Then
        assertThat(result).isPresent().contains(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateMediaTypeNoMatch() {
        // Given
        var available = List.of(MediaType.TEXT_HTML);

        // When
        var result = negotiator.negotiateMediaType("application/json", available);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testNegotiateMediaTypeNullHeader() {
        // Given
        var available = List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);

        // When
        var result = negotiator.negotiateMediaType(null, available);

        // Then
        assertThat(result).isPresent().contains(MediaType.TEXT_HTML);
    }

    @Test
    void testNegotiateEncodingExactMatch() {
        // Given
        var available = List.of(ContentEncoding.GZIP, ContentEncoding.DEFLATE);

        // When
        var result = negotiator.negotiateEncoding("gzip", available);

        // Then
        assertThat(result).isPresent().contains(ContentEncoding.GZIP);
    }

    @Test
    void testNegotiateEncodingWithQualityValues() {
        // Given
        var available = List.of(ContentEncoding.GZIP, ContentEncoding.DEFLATE);

        // When
        var result = negotiator.negotiateEncoding("deflate;q=1.0, gzip;q=0.5", available);

        // Then
        assertThat(result).isPresent().contains(ContentEncoding.DEFLATE);
    }

    @Test
    void testNegotiateEncodingNullHeaderReturnsIdentity() {
        // Given
        var available = List.of(ContentEncoding.GZIP);

        // When
        var result = negotiator.negotiateEncoding(null, available);

        // Then
        assertThat(result).isPresent().contains(ContentEncoding.IDENTITY);
    }
}
