package ssg.legoflow.http.server;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import static org.assertj.core.api.Assertions.*;
class DateHeaderGeneratorTest {

    private final DateHeaderGenerator generator = new DateHeaderGenerator();

    @Test
    void testAddDateHeader() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);

        // When
        generator.addDateHeader(response);

        // Then
        assertThat(response.getHeaders().get(HttpHeaders.DATE)).isNotNull();
    }

    @Test
    void testAddDateHeaderDoesNotOverwrite() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);
        response.getHeaders().set(HttpHeaders.DATE, "Existing Date");

        // When
        generator.addDateHeader(response);

        // Then — should not overwrite existing Date header
        assertThat(response.getHeaders().get(HttpHeaders.DATE)).isEqualTo("Existing Date");
    }

    @Test
    void testAddDateHeaderWithInstant() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);
        var instant = ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant();

        // When
        generator.addDateHeader(response, instant);

        // Then
        assertThat(response.getHeaders().get(HttpHeaders.DATE))
                .isEqualTo("Sun, 1 Dec 2024 16:00:00 GMT");
    }

    @Test
    void testFormatDate() {
        // Given
        var instant = ZonedDateTime.of(2024, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC).toInstant();

        // When
        String formatted = generator.formatDate(instant);

        // Then
        assertThat(formatted).contains("2024");
        assertThat(formatted).contains("GMT");
    }

    @Test
    void testParseDate() {
        // When
        Instant parsed = generator.parseDate("Sun, 1 Dec 2024 16:00:00 GMT");

        // Then
        assertThat(parsed).isNotNull();
        assertThat(parsed).isEqualTo(
                ZonedDateTime.of(2024, 12, 1, 16, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void testParseDateInvalid() {
        assertThat(generator.parseDate(null)).isNull();
        assertThat(generator.parseDate("")).isNull();
        assertThat(generator.parseDate("not a date")).isNull();
    }

    @Test
    void testShouldAddDateFor200() {
        var response = HttpResponse.of(HttpStatus.OK);
        assertThat(generator.shouldAddDate(response)).isTrue();
    }

    @Test
    void testShouldNotAddDateFor100() {
        var response = HttpResponse.of(HttpStatus.CONTINUE);
        assertThat(generator.shouldAddDate(response)).isFalse();
    }

    @Test
    void testShouldAddDateFor500() {
        var response = HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(generator.shouldAddDate(response)).isTrue();
    }
}
