package ssg.legoflow.http.server;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Auto-generates the Date header on HTTP responses per RFC 7231 §7.1.1.
 *
 * <p>Per the RFC, an origin server MUST send a Date header field in all
 * response messages, except for certain cases (1xx, 5xx when clock unavailable).
 *
 * @since 1.0.0
 */
public class DateHeaderGenerator {

    /**
     * Adds a Date header to the response if one is not already present.
     *
     * <p>The Date header uses the RFC 1123 date format as required by HTTP/1.1.
     *
     * @param response the HTTP response
     */
    public void addDateHeader(HttpResponse response) {
        if (!response.getHeaders().contains(HttpHeaders.DATE)) {
            response.getHeaders().set(HttpHeaders.DATE, formatDate(Instant.now()));
        }
    }

    /**
     * Adds a Date header with a specific instant.
     *
     * @param response the HTTP response
     * @param instant  the date to use
     */
    public void addDateHeader(HttpResponse response, Instant instant) {
        response.getHeaders().set(HttpHeaders.DATE, formatDate(instant));
    }

    /**
     * Formats an Instant as an HTTP-date (RFC 1123).
     *
     * @param instant the instant to format
     * @return the formatted date string
     */
    public String formatDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /**
     * Parses an HTTP-date string to an Instant.
     *
     * @param dateStr the HTTP-date string
     * @return the parsed instant, or null if invalid
     */
    public Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(dateStr.trim(),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determines whether a Date header should be added to the response.
     *
     * <p>Per RFC 7231 §7.1.1.2, the Date header is not required for
     * 1xx (Informational) responses or if the server has no clock.
     *
     * @param response the HTTP response
     * @return true if a Date header should be added
     */
    public boolean shouldAddDate(HttpResponse response) {
        int code = response.getStatus().code();
        // Don't add Date to 1xx responses
        return code >= 200;
    }
}
