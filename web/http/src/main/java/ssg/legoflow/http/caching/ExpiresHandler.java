package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Handles the Expires header per RFC 7234 §5.3.
 *
 * <p>The Expires header gives the date/time after which the response is
 * considered stale. A Cache-Control max-age or s-maxage directive takes
 * priority over Expires.
 *
 * @since 0.1.0
 */
public class ExpiresHandler {

    /**
     * Parses an Expires header value to an Instant.
     *
     * @param expiresValue the Expires header value in RFC 1123 format
     * @return the parsed instant, or null if the value is invalid
     */
    public Instant parseExpires(String expiresValue) {
        if (expiresValue == null || expiresValue.isBlank()) {
            return null;
        }
        // Per RFC 7234 §5.3: "0" or invalid dates represent a past time
        if ("0".equals(expiresValue.trim())) {
            return Instant.EPOCH;
        }
        try {
            return ZonedDateTime.parse(expiresValue.trim(),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            // Invalid dates are treated as expired (in the past)
            return Instant.EPOCH;
        }
    }

    /**
     * Formats an Instant as an HTTP-date for the Expires header.
     *
     * @param instant the instant to format
     * @return the formatted HTTP-date string
     */
    public String formatExpires(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /**
     * Determines if a response is fresh based on the Expires header.
     *
     * <p>Per RFC 7234 §5.3, Cache-Control max-age takes priority over Expires.
     * This method only evaluates the Expires header.
     *
     * @param expiresValue the Expires header value
     * @param now          the current time
     * @return true if the response is still fresh
     */
    public boolean isFresh(String expiresValue, Instant now) {
        Instant expires = parseExpires(expiresValue);
        if (expires == null) {
            return false;
        }
        return now.isBefore(expires);
    }

    /**
     * Calculates the freshness lifetime from the Expires and Date headers.
     *
     * <p>Freshness lifetime = expires_value - date_value (in seconds).
     *
     * @param expiresValue the Expires header value
     * @param dateValue    the Date header value
     * @return the freshness lifetime in seconds, or -1 if unable to calculate
     */
    public long calculateFreshnessLifetime(String expiresValue, String dateValue) {
        Instant expires = parseExpires(expiresValue);
        if (expires == null) {
            return -1;
        }
        if (dateValue == null) {
            return -1;
        }
        try {
            Instant date = ZonedDateTime.parse(dateValue.trim(),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            long lifetime = expires.getEpochSecond() - date.getEpochSecond();
            return Math.max(0, lifetime);
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    /**
     * Sets the Expires header on a response.
     *
     * @param response the HTTP response
     * @param expires  the expiration time
     */
    public void setExpires(HttpResponse response, Instant expires) {
        response.getHeaders().set(HttpHeaders.EXPIRES, formatExpires(expires));
    }

    /**
     * Determines the effective max-age considering both Cache-Control and Expires.
     *
     * <p>Per RFC 7234 §5.3, Cache-Control max-age takes priority.
     *
     * @param cacheControl the parsed Cache-Control, or null
     * @param expiresValue the Expires header value, or null
     * @param dateValue    the Date header value, or null
     * @return the effective max-age in seconds, or -1 if indeterminate
     */
    public long getEffectiveMaxAge(CacheControl cacheControl, String expiresValue, String dateValue) {
        if (cacheControl != null && cacheControl.getMaxAge() >= 0) {
            return cacheControl.getMaxAge();
        }
        if (cacheControl != null && cacheControl.getSMaxAge() >= 0) {
            return cacheControl.getSMaxAge();
        }
        return calculateFreshnessLifetime(expiresValue, dateValue);
    }
}
