package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PreconditionEvaluatorTest {

    private final PreconditionEvaluator evaluator = new PreconditionEvaluator();

    @Test
    void testIfMatchWithMatchingETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "\"abc123\"");

        // Then — strong comparison, should match
        assertThat(evaluator.evaluateIfMatch(request, "\"abc123\"")).isTrue();
    }

    @Test
    void testIfMatchWithNonMatchingETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "\"abc123\"");

        // Then
        assertThat(evaluator.evaluateIfMatch(request, "\"different\"")).isFalse();
    }

    @Test
    void testIfMatchWildcard() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "*");

        // Then — * matches if resource exists
        assertThat(evaluator.evaluateIfMatch(request, "\"anything\"")).isTrue();
        assertThat(evaluator.evaluateIfMatch(request, null)).isFalse();
    }

    @Test
    void testIfMatchWeakTagFails() {
        // Given — strong comparison, weak tags should not match
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "W/\"abc\"");

        // Then
        assertThat(evaluator.evaluateIfMatch(request, "W/\"abc\"")).isFalse();
    }

    @Test
    void testIfMatchNoHeader() {
        // Given — no If-Match header means precondition is vacuously true
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");

        // Then
        assertThat(evaluator.evaluateIfMatch(request, "\"anything\"")).isTrue();
    }

    @Test
    void testIfMatchMultipleTags() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "\"tag1\", \"tag2\", \"tag3\"");

        // Then — should match if any tag matches
        assertThat(evaluator.evaluateIfMatch(request, "\"tag2\"")).isTrue();
        assertThat(evaluator.evaluateIfMatch(request, "\"tag4\"")).isFalse();
    }

    @Test
    void testIfNoneMatchWithMatchingETag() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // Then — match found, precondition fails
        assertThat(evaluator.evaluateIfNoneMatch(request, "\"abc123\"")).isFalse();
    }

    @Test
    void testIfNoneMatchWithNoMatch() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        // Then — no match, precondition succeeds
        assertThat(evaluator.evaluateIfNoneMatch(request, "\"different\"")).isTrue();
    }

    @Test
    void testPreconditionFailedResponse() {
        var response = evaluator.preconditionFailed();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void testEvaluatePreconditionsIfMatchFails() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "\"old\"");

        // When
        var response = evaluator.evaluatePreconditions(request, "\"new\"");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void testEvaluatePreconditionsIfNoneMatchFailsGET() {
        // Given
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc\"");

        // When
        var response = evaluator.evaluatePreconditions(request, "\"abc\"");

        // Then — GET/HEAD returns 304
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void testEvaluatePreconditionsIfNoneMatchFailsPUT() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_NONE_MATCH, "\"abc\"");

        // When
        var response = evaluator.evaluatePreconditions(request, "\"abc\"");

        // Then — non-GET/HEAD returns 412
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void testEvaluatePreconditionsAllPass() {
        // Given
        var request = HttpRequest.of(HttpMethod.PUT, "/api/data");
        request.getHeaders().set(HttpHeaders.IF_MATCH, "\"current\"");

        // When
        var response = evaluator.evaluatePreconditions(request, "\"current\"");

        // Then
        assertThat(response).isNull();
    }
}
