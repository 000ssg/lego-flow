package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.header.EntityTag;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CacheValidator {

    public boolean isConditionalGet(HttpRequest request) {
        return request.getHeaders().contains(HttpHeaders.IF_NONE_MATCH)
                || request.getHeaders().contains(HttpHeaders.IF_MODIFIED_SINCE);
    }

    public boolean validateETag(HttpRequest request, String currentETag) {
        var ifNoneMatch = request.getHeaders().get(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch == null) return false;
        if ("*".equals(ifNoneMatch)) return true;
        var requestedTag = EntityTag.parse(ifNoneMatch);
        var current = EntityTag.parse(currentETag);
        return current.matches(requestedTag, false);
    }

    public boolean validateLastModified(HttpRequest request, Instant lastModified) {
        var ifModifiedSince = request.getHeaders().get(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince == null || lastModified == null) return false;
        try {
            var since = ZonedDateTime.parse(ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return !lastModified.isAfter(since);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public HttpResponse notModifiedResponse() {
        return HttpResponse.of(HttpStatus.NOT_MODIFIED);
    }
}
