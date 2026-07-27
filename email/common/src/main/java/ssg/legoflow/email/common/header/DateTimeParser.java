package ssg.legoflow.email.common.header;

import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 5322 date-time parser with support for common non-standard formats.
 *
 * <p>Parses date-time strings found in email Date headers, handling the standard
 * RFC 5322 format as well as many common deviations found in the wild.
 *
 * @since 1.0.0
 */
public final class DateTimeParser {

    /** RFC 5322 date-time format. */
    public static final DateTimeFormatter RFC_5322 = DateTimeFormatter.ofPattern(
            "[EEE, ]d MMM yyyy HH:mm[:ss] Z", Locale.US);

    /** Common date-time formats found in email headers. */
    private static final List<DateTimeFormatter> FORMATS = List.of(
            // RFC 5322: "Thu, 13 Feb 2020 15:30:00 +0000"
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z", Locale.US),
            // Without day of week
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.US),
            // Without seconds
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.US),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm Z", Locale.US),
            // Two-digit year
            DateTimeFormatter.ofPattern("EEE, d MMM yy HH:mm:ss Z", Locale.US),
            DateTimeFormatter.ofPattern("d MMM yy HH:mm:ss Z", Locale.US),
            // ISO 8601 variants sometimes seen
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME
    );

    /** Named timezone abbreviations to offsets. */
    private static final Map<String, String> TIMEZONE_MAP = Map.ofEntries(
            Map.entry("UT", "+0000"), Map.entry("UTC", "+0000"), Map.entry("GMT", "+0000"),
            Map.entry("EST", "-0500"), Map.entry("EDT", "-0400"),
            Map.entry("CST", "-0600"), Map.entry("CDT", "-0500"),
            Map.entry("MST", "-0700"), Map.entry("MDT", "-0600"),
            Map.entry("PST", "-0800"), Map.entry("PDT", "-0700"),
            Map.entry("CET", "+0100"), Map.entry("CEST", "+0200"),
            Map.entry("JST", "+0900"), Map.entry("IST", "+0530"),
            Map.entry("AEST", "+1000"), Map.entry("AEDT", "+1100")
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private DateTimeParser() {
    }

    /**
     * Parses a date-time string from an email header.
     *
     * @param dateStr the date-time string
     * @return the parsed date-time with offset
     * @throws DateTimeParseException if the date cannot be parsed
     */
    public static OffsetDateTime parse(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new DateTimeParseException("Empty date string", "", 0);
        }

        String cleaned = clean(dateStr);

        // Try each format
        for (DateTimeFormatter fmt : FORMATS) {
            try {
                return OffsetDateTime.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(cleaned, fmt);
                return zdt.toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        throw new DateTimeParseException("Unable to parse date: " + dateStr, dateStr, 0);
    }

    /**
     * Formats a date-time to RFC 5322 format.
     *
     * @param dateTime the date-time to format
     * @return the formatted string
     */
    public static String format(OffsetDateTime dateTime) {
        return DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US)
                .format(dateTime);
    }

    /**
     * Formats a date-time to RFC 5322 format using UTC.
     *
     * @param dateTime the date-time to format
     * @return the formatted string in UTC
     */
    public static String formatUtc(OffsetDateTime dateTime) {
        return format(dateTime.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static String clean(String dateStr) {
        // Remove comments like (PDT)
        String cleaned = COMMENT_PATTERN.matcher(dateStr).replaceAll("").trim();
        // Normalize whitespace
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        // Replace named timezone abbreviations
        for (var entry : TIMEZONE_MAP.entrySet()) {
            if (cleaned.endsWith(" " + entry.getKey())) {
                cleaned = cleaned.substring(0, cleaned.length() - entry.getKey().length())
                        + entry.getValue();
                break;
            }
        }
        return cleaned;
    }
}
