package ssg.legoflow.email.common.header;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DateTimeParser}.
 */
class DateTimeParserTest {

    @Test
    void testParseRfc5322Standard() {
        OffsetDateTime dt = DateTimeParser.parse("Thu, 13 Feb 2020 15:30:00 +0000");
        assertThat(dt.getYear()).isEqualTo(2020);
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(13);
        assertThat(dt.getHour()).isEqualTo(15);
        assertThat(dt.getMinute()).isEqualTo(30);
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void testParseWithTimezone() {
        OffsetDateTime dt = DateTimeParser.parse("Mon, 1 Jan 2024 09:00:00 -0500");
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void testParseWithoutDayOfWeek() {
        OffsetDateTime dt = DateTimeParser.parse("13 Feb 2020 15:30:00 +0000");
        assertThat(dt.getYear()).isEqualTo(2020);
        assertThat(dt.getDayOfMonth()).isEqualTo(13);
    }

    @Test
    void testParseWithComment() {
        OffsetDateTime dt = DateTimeParser.parse("Thu, 13 Feb 2020 15:30:00 +0000 (UTC)");
        assertThat(dt.getYear()).isEqualTo(2020);
    }

    @Test
    void testParseWithNamedTimezone() {
        OffsetDateTime dt = DateTimeParser.parse("Thu, 13 Feb 2020 15:30:00 GMT");
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void testParseWithNamedTimezoneEST() {
        OffsetDateTime dt = DateTimeParser.parse("Thu, 13 Feb 2020 10:30:00 EST");
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> DateTimeParser.parse(null))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void testParseEmptyThrows() {
        assertThatThrownBy(() -> DateTimeParser.parse(""))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void testParseGarbageThrows() {
        assertThatThrownBy(() -> DateTimeParser.parse("not a date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void testFormat() {
        OffsetDateTime dt = OffsetDateTime.of(2020, 2, 13, 15, 30, 0, 0, ZoneOffset.UTC);
        String formatted = DateTimeParser.format(dt);
        assertThat(formatted).contains("13 Feb 2020");
        assertThat(formatted).contains("15:30:00");
        assertThat(formatted).contains("+0000");
    }

    @Test
    void testFormatUtc() {
        OffsetDateTime dt = OffsetDateTime.of(2020, 2, 13, 15, 30, 0, 0, ZoneOffset.ofHours(5));
        String formatted = DateTimeParser.formatUtc(dt);
        assertThat(formatted).contains("10:30:00");
        assertThat(formatted).contains("+0000");
    }

    @Test
    void testRoundTrip() {
        OffsetDateTime original = OffsetDateTime.of(2024, 6, 15, 10, 30, 45, 0, ZoneOffset.ofHours(-7));
        String formatted = DateTimeParser.format(original);
        OffsetDateTime parsed = DateTimeParser.parse(formatted);
        assertThat(parsed.toInstant()).isEqualTo(original.toInstant());
    }

    @Test
    void testParseSingleDigitDay() {
        OffsetDateTime dt = DateTimeParser.parse("Mon, 1 Jan 2024 09:00:00 +0000");
        assertThat(dt.getDayOfMonth()).isEqualTo(1);
    }
}
