package ssg.legoflow.http.transfer;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.header.EntityTag;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Handles the If-Range header per RFC 7233 §2.2.
 *
 * <p>The If-Range header allows a client to request a partial content response
 * only if the resource has not been modified. If the condition fails, the server
 * should return the full entity instead of a 206 Partial Content.
 *
 * <p>The If-Range value can be either an entity tag or an HTTP-date.
 *
 * @since 0.1.0
 */
public class IfRangeHandler {

    /**
     * Evaluates the If-Range precondition.
     *
     * <p>Returns true if the range request should proceed (resource matches),
     * false if the full entity should be returned instead.
     *
     * @param request       the HTTP request containing the If-Range header
     * @param currentETag   the current entity tag of the resource, or null
     * @param lastModified  the last modified time of the resource, or null
     * @return true if the range request should proceed, false for full entity
     */
    public boolean evaluateIfRange(HttpRequest request, String currentETag, Instant lastModified) {
        String ifRange = request.getHeaders().get(HttpHeaders.IF_RANGE);
        if (ifRange == null) {
            // No If-Range header means the range request should proceed normally
            return true;
        }

        // Check if the If-Range value is an entity tag or a date
        if (isEntityTag(ifRange)) {
            return matchesEntityTag(ifRange, currentETag);
        } else {
            return matchesDate(ifRange, lastModified);
        }
    }

    /**
     * Determines whether the given If-Range value is an entity tag.
     *
     * <p>Entity tags start with a quote or with W/ for weak tags.
     *
     * @param value the If-Range header value
     * @return true if the value is an entity tag
     */
    public boolean isEntityTag(String value) {
        String trimmed = value.strip();
        return trimmed.startsWith("\"") || trimmed.startsWith("W/") || trimmed.startsWith("w/");
    }

    private boolean matchesEntityTag(String ifRangeValue, String currentETag) {
        if (currentETag == null) {
            return false;
        }
        try {
            EntityTag ifRangeTag = EntityTag.parse(ifRangeValue);
            EntityTag current = EntityTag.parse(currentETag);
            // If-Range uses strong comparison per RFC 7233 §3.2
            return current.matches(ifRangeTag, true);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesDate(String ifRangeValue, Instant lastModified) {
        if (lastModified == null) {
            return false;
        }
        try {
            Instant ifRangeDate = ZonedDateTime.parse(ifRangeValue,
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            // The resource must not have been modified after the If-Range date
            return !lastModified.isAfter(ifRangeDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
