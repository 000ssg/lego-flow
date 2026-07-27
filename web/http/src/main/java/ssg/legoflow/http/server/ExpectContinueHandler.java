package ssg.legoflow.http.server;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;

/**
 * Handles the Expect: 100-continue mechanism per RFC 7231 §5.1.1.
 *
 * <p>When a client sends a request with {@code Expect: 100-continue}, it
 * expects the server to respond with {@code 100 Continue} before sending
 * the request body. This allows the server to reject the request early
 * (e.g., based on headers) without the client sending the entire body.
 *
 * @since 1.0.0
 */
public class ExpectContinueHandler {

    /**
     * Checks whether a request contains the Expect: 100-continue header.
     *
     * @param request the HTTP request
     * @return true if the request expects a 100 Continue response
     */
    public boolean expectsContinue(HttpRequest request) {
        String expect = request.getHeaders().get(HttpHeaders.EXPECT);
        return "100-continue".equalsIgnoreCase(expect != null ? expect.trim() : null);
    }

    /**
     * Creates a 100 Continue response.
     *
     * @return the 100 Continue interim response
     */
    public HttpResponse continueResponse() {
        return HttpResponse.of(HttpStatus.CONTINUE);
    }

    /**
     * Creates a 417 Expectation Failed response.
     *
     * <p>Sent when the server cannot meet the expectation indicated
     * by the Expect header.
     *
     * @return the 417 Expectation Failed response
     */
    public HttpResponse expectationFailed() {
        return HttpResponse.of(HttpStatus.EXPECTATION_FAILED);
    }

    /**
     * Evaluates the Expect header and returns the appropriate response.
     *
     * <p>If the request contains {@code Expect: 100-continue}, returns
     * a 100 Continue response. For unsupported expectations, returns 417.
     *
     * @param request the HTTP request
     * @return the appropriate response, or null if no Expect header is present
     */
    public HttpResponse evaluateExpectation(HttpRequest request) {
        String expect = request.getHeaders().get(HttpHeaders.EXPECT);
        if (expect == null) {
            return null;
        }
        if ("100-continue".equalsIgnoreCase(expect.trim())) {
            return continueResponse();
        }
        // Unknown expectation — return 417
        return expectationFailed();
    }

    /**
     * Checks whether the Expect header value is valid.
     *
     * @param expectValue the Expect header value
     * @return true if the value is a recognized expectation
     */
    public boolean isValidExpectation(String expectValue) {
        return "100-continue".equalsIgnoreCase(expectValue != null ? expectValue.trim() : null);
    }
}
