package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.*;

class CacheValidatorTest {

    private final CacheValidator validator = new CacheValidator();

    @Test
    void testIsConditionalGetWithIfNoneMatch() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // Then
        assertThat(validator.isConditionalGet(request)).isTrue();
    }

    @Test
    void testIsConditionalGetWithIfModifiedSince() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MODIFIED_SINCE, "Tue, 15 Nov 1994 08:12:31 GMT");

        // Then
        assertThat(validator.isConditionalGet(request)).isTrue();
    }

    @Test
    void testIsConditionalGetWithoutHeaders() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");

        // Then
        assertThat(validator.isConditionalGet(request)).isFalse();
    }

    @Test
    void testValidateETagMatch() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // Then
        assertThat(validator.validateETag(request, "\"abc123\"")).isTrue();
    }

    @Test
    void testValidateETagNoMatch() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // Then
        assertThat(validator.validateETag(request, "\"different\"")).isFalse();
    }

    @Test
    void testValidateETagWildcard() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "*");

        // Then
        assertThat(validator.validateETag(request, "\"anything\"")).isTrue();
    }

    @Test
    void testValidateLastModifiedNotModified() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        var date = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        request.getHeaders().set(HttpHeaders.IF_MODIFIED_SINCE,
                date.format(DateTimeFormatter.RFC_1123_DATE_TIME));

        // Resource last modified before the if-modified-since date
        var lastModified = date.minusHours(1).toInstant();

        // Then
        assertThat(validator.validateLastModified(request, lastModified)).isTrue();
    }

    @Test
    void testNotModifiedResponse() {
        // When
        var response = validator.notModifiedResponse();

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }
}
