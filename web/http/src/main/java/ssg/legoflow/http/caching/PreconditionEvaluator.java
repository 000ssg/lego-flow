package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.header.EntityTag;

/**
 * Evaluates HTTP preconditions per RFC 7232 §3.
 *
 * <p>Handles If-Match (§3.1), If-None-Match (§3.2), and the 412 Precondition
 * Failed response when a precondition is not met.
 *
 * @since 1.0.0
 */
public class PreconditionEvaluator {

    /**
     * Evaluates the If-Match precondition per RFC 7232 §3.1.
     *
     * <p>Returns true if the precondition is satisfied (the request can proceed).
     * Returns false if the precondition fails (should return 412).
     *
     * <p>Per the RFC:
     * <ul>
     *   <li>If-Match: * succeeds if any current entity exists</li>
     *   <li>If-Match: "tag" succeeds if the current ETag matches (strong comparison)</li>
     * </ul>
     *
     * @param request     the HTTP request
     * @param currentETag the current entity tag, or null if the resource does not exist
     * @return true if the precondition is met
     */
    public boolean evaluateIfMatch(HttpRequest request, String currentETag) {
        String ifMatch = request.getHeaders().get(HttpHeaders.IF_MATCH);
        if (ifMatch == null) {
            // No If-Match header, precondition is vacuously true
            return true;
        }

        // If-Match: * succeeds if any current representation exists
        if ("*".equals(ifMatch.trim())) {
            return currentETag != null;
        }

        if (currentETag == null) {
            return false;
        }

        // Parse and compare each tag in the If-Match field
        EntityTag current = EntityTag.parse(currentETag);
        for (String tagStr : ifMatch.split(",")) {
            String trimmed = tagStr.trim();
            if (trimmed.isEmpty()) continue;
            try {
                EntityTag ifMatchTag = EntityTag.parse(trimmed);
                // If-Match uses strong comparison
                if (current.matches(ifMatchTag, true)) {
                    return true;
                }
            } catch (Exception e) {
                // Skip malformed tags
            }
        }

        return false;
    }

    /**
     * Evaluates the If-None-Match precondition per RFC 7232 §3.2.
     *
     * <p>Returns true if the precondition is satisfied (the request can proceed).
     * Returns false if the precondition fails (should return 304 for GET/HEAD or 412 for others).
     *
     * @param request     the HTTP request
     * @param currentETag the current entity tag, or null if the resource does not exist
     * @return true if the precondition is met (no match found)
     */
    public boolean evaluateIfNoneMatch(HttpRequest request, String currentETag) {
        String ifNoneMatch = request.getHeaders().get(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch == null) {
            return true;
        }

        if ("*".equals(ifNoneMatch.trim())) {
            // Fails if any current representation exists
            return currentETag == null;
        }

        if (currentETag == null) {
            return true;
        }

        EntityTag current = EntityTag.parse(currentETag);
        for (String tagStr : ifNoneMatch.split(",")) {
            String trimmed = tagStr.trim();
            if (trimmed.isEmpty()) continue;
            try {
                EntityTag ifNoneMatchTag = EntityTag.parse(trimmed);
                // If-None-Match uses weak comparison
                if (current.matches(ifNoneMatchTag, false)) {
                    return false; // Match found, precondition fails
                }
            } catch (Exception e) {
                // Skip malformed tags
            }
        }
        return true;
    }

    /**
     * Creates a 412 Precondition Failed response.
     *
     * @return the 412 response
     */
    public HttpResponse preconditionFailed() {
        return HttpResponse.of(HttpStatus.PRECONDITION_FAILED);
    }

    /**
     * Evaluates all preconditions and returns an appropriate error response,
     * or null if all preconditions are met.
     *
     * <p>Evaluates in order per RFC 7232 §6:
     * <ol>
     *   <li>If-Match</li>
     *   <li>If-None-Match</li>
     * </ol>
     *
     * @param request     the HTTP request
     * @param currentETag the current entity tag, or null
     * @return an error response (412 or 304), or null if preconditions pass
     */
    public HttpResponse evaluatePreconditions(HttpRequest request, String currentETag) {
        // Step 1: If-Match
        if (!evaluateIfMatch(request, currentETag)) {
            return preconditionFailed();
        }

        // Step 2: If-None-Match
        if (!evaluateIfNoneMatch(request, currentETag)) {
            var method = request.getMethod();
            if (method == ssg.legoflow.http.core.HttpMethod.GET
                    || method == ssg.legoflow.http.core.HttpMethod.HEAD) {
                return HttpResponse.of(HttpStatus.NOT_MODIFIED);
            }
            return preconditionFailed();
        }

        return null; // All preconditions met
    }
}
