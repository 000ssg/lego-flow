package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class ExpiresHandlerTest {

    private final ExpiresHandler handler = new ExpiresHandler();

    @Test
    void testParseExpiresValidDate() {
        // Given — Dec 1 2024 is a Sunday
        Instant expected = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();
        String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(expected, ZoneOffset.UTC));

        // When
        Instant result = handler.parseExpires(expires);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testParseExpiresZeroIsEpoch() {
        // When
        Instant result = handler.parseExpires("0");

        // Then
        assertThat(result).isEqualTo(Instant.EPOCH);
    }

    @Test
    void testParseExpiresInvalidDateIsEpoch() {
        // When
        Instant result = handler.parseExpires("invalid-date");

        // Then
        assertThat(result).isEqualTo(Instant.EPOCH);
    }

    @Test
    void testParseExpiresNullReturnsNull() {
        assertThat(handler.parseExpires(null)).isNull();
        assertThat(handler.parseExpires("")).isNull();
    }

    @Test
    void testFormatExpires() {
        // Given
        Instant instant = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();

        // When
        String formatted = handler.formatExpires(instant);

        // Then
        assertThat(formatted).isEqualTo("Sun, 1 Dec 2024 16:00:00 GMT");
    }

    @Test
    void testIsFreshWhenNotExpired() {
        // Given
        Instant future = Instant.now().plusSeconds(3600);
        String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(future, ZoneOffset.UTC));

        // Then
        assertThat(handler.isFresh(expires, Instant.now())).isTrue();
    }

    @Test
    void testIsFreshWhenExpired() {
        // Given
        Instant past = Instant.now().minusSeconds(3600);
        String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(past, ZoneOffset.UTC));

        // Then
        assertThat(handler.isFresh(expires, Instant.now())).isFalse();
    }

    @Test
    void testCalculateFreshnessLifetime() {
        // Given — use proper RFC 1123 formatted dates
        Instant dateInstant = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant expiresInstant = dateInstant.plusSeconds(3600);
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(dateInstant, ZoneOffset.UTC));
        String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(expiresInstant, ZoneOffset.UTC));

        // When
        long lifetime = handler.calculateFreshnessLifetime(expires, date);

        // Then
        assertThat(lifetime).isEqualTo(3600);
    }

    @Test
    void testCalculateFreshnessLifetimeInvalidReturnsNegative() {
        assertThat(handler.calculateFreshnessLifetime(null, "Thu, 01 Dec 2024 16:00:00 GMT"))
                .isEqualTo(-1);
        assertThat(handler.calculateFreshnessLifetime("Thu, 01 Dec 2024 17:00:00 GMT", null))
                .isEqualTo(-1);
    }

    @Test
    void testSetExpires() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);
        Instant expires = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();

        // When
        handler.setExpires(response, expires);

        // Then
        assertThat(response.getHeaders().get(HttpHeaders.EXPIRES)).isNotNull();
    }

    @Test
    void testGetEffectiveMaxAgeWithCacheControl() {
        // Given
        var cc = CacheControl.parse("max-age=3600");

        // When
        long maxAge = handler.getEffectiveMaxAge(cc, "Thu, 01 Dec 2024 17:00:00 GMT",
                "Thu, 01 Dec 2024 16:00:00 GMT");

        // Then — Cache-Control max-age takes priority
        assertThat(maxAge).isEqualTo(3600);
    }

    @Test
    void testGetEffectiveMaxAgeFallsBackToExpires() {
        // Given
        Instant dateInstant = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant expiresInstant = dateInstant.plusSeconds(3600);
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(dateInstant, ZoneOffset.UTC));
        String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(expiresInstant, ZoneOffset.UTC));

        // When
        long maxAge = handler.getEffectiveMaxAge(null, expires, date);

        // Then
        assertThat(maxAge).isEqualTo(3600);
    }
}
