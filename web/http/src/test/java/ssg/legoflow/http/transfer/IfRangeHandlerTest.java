package ssg.legoflow.http.transfer;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.*;

class IfRangeHandlerTest {

    private final IfRangeHandler handler = new IfRangeHandler();

    @Test
    void testNoIfRangeHeaderProceedsWithRange() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");

        // Then — no If-Range means proceed with range request
        assertThat(handler.evaluateIfRange(request, "\"abc\"", Instant.now())).isTrue();
    }

    @Test
    void testIfRangeWithMatchingETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE, "\"abc123\"");

        // Then
        assertThat(handler.evaluateIfRange(request, "\"abc123\"", null)).isTrue();
    }

    @Test
    void testIfRangeWithNonMatchingETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE, "\"abc123\"");

        // Then — ETag doesn't match, should return full entity
        assertThat(handler.evaluateIfRange(request, "\"different\"", null)).isFalse();
    }

    @Test
    void testIfRangeWithWeakETagFails() {
        // Given — If-Range uses strong comparison, weak tags should fail
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE, "W/\"abc123\"");

        // Then
        assertThat(handler.evaluateIfRange(request, "W/\"abc123\"", null)).isFalse();
    }

    @Test
    void testIfRangeWithMatchingDate() {
        // Given
        var lastModified = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE,
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        ZonedDateTime.ofInstant(lastModified, ZoneOffset.UTC)));

        // Then
        assertThat(handler.evaluateIfRange(request, null, lastModified)).isTrue();
    }

    @Test
    void testIfRangeWithOlderDateFails() {
        // Given — resource was modified after the If-Range date
        var ifRangeDate = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        var lastModified = ifRangeDate.plusSeconds(3600); // 1 hour later
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE,
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        ZonedDateTime.ofInstant(ifRangeDate, ZoneOffset.UTC)));

        // Then
        assertThat(handler.evaluateIfRange(request, null, lastModified)).isFalse();
    }

    @Test
    void testIfRangeWithNullCurrentETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/file.txt");
        request.getHeaders().set(HttpHeaders.IF_RANGE, "\"abc123\"");

        // Then — no current ETag means we can't match
        assertThat(handler.evaluateIfRange(request, null, null)).isFalse();
    }

    @Test
    void testIsEntityTag() {
        assertThat(handler.isEntityTag("\"abc123\"")).isTrue();
        assertThat(handler.isEntityTag("W/\"abc123\"")).isTrue();
        assertThat(handler.isEntityTag("Mon, 15 Jan 2024 10:00:00 GMT")).isFalse();
    }
}
