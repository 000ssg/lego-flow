package ssg.legoflow.http.server;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ExpectContinueHandlerTest {

    private final ExpectContinueHandler handler = new ExpectContinueHandler();

    @Test
    void testExpectsContinueTrue() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/upload");
        request.getHeaders().set(HttpHeaders.EXPECT, "100-continue");

        // Then
        assertThat(handler.expectsContinue(request)).isTrue();
    }

    @Test
    void testExpectsContinueCaseInsensitive() {
        var request = HttpRequest.of(HttpMethod.POST, "/upload");
        request.getHeaders().set(HttpHeaders.EXPECT, "100-Continue");
        assertThat(handler.expectsContinue(request)).isTrue();
    }

    @Test
    void testExpectsContinueFalse() {
        var request = HttpRequest.of(HttpMethod.POST, "/upload");
        assertThat(handler.expectsContinue(request)).isFalse();
    }

    @Test
    void testContinueResponse() {
        var response = handler.continueResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTINUE);
        assertThat(response.getStatus().code()).isEqualTo(100);
    }

    @Test
    void testExpectationFailedResponse() {
        var response = handler.expectationFailed();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.EXPECTATION_FAILED);
        assertThat(response.getStatus().code()).isEqualTo(417);
    }

    @Test
    void testEvaluateExpectationContinue() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/upload");
        request.getHeaders().set(HttpHeaders.EXPECT, "100-continue");

        // When
        var response = handler.evaluateExpectation(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTINUE);
    }

    @Test
    void testEvaluateExpectationUnknown() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/upload");
        request.getHeaders().set(HttpHeaders.EXPECT, "some-unknown-expectation");

        // When
        var response = handler.evaluateExpectation(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.EXPECTATION_FAILED);
    }

    @Test
    void testEvaluateExpectationNoHeader() {
        // Given
        var request = HttpRequest.of(HttpMethod.POST, "/upload");

        // When
        var response = handler.evaluateExpectation(request);

        // Then
        assertThat(response).isNull();
    }

    @Test
    void testIsValidExpectation() {
        assertThat(handler.isValidExpectation("100-continue")).isTrue();
        assertThat(handler.isValidExpectation("100-Continue")).isTrue();
        assertThat(handler.isValidExpectation("unknown")).isFalse();
        assertThat(handler.isValidExpectation(null)).isFalse();
    }
}
