package ssg.legoflow.http.demo.client;

import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.transfer.ByteRangeHandler;

/**
 * HTTP client demo for byte-range requests (partial content downloads).
 *
 * <p>Creates requests with Range headers for fetching specific byte ranges
 * from a resource, using {@link ByteRangeHandler} for range formatting.
 *
 * @since 0.1
 */
public class RangeClient {

    private final HttpClient client;

    public RangeClient() {
        this.client = new HttpClientBuilder().standard().build();
    }

    /**
     * Creates a request for a specific byte range.
     *
     * @param path  the request URI path
     * @param start the start byte offset (inclusive)
     * @param end   the end byte offset (inclusive)
     * @return a GET request with Range header
     */
    public HttpRequest createRangeRequest(String path, long start, long end) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=" + start + "-" + end);
        return request;
    }

    /**
     * Creates a request for the last N bytes of a resource.
     *
     * @param path         the request URI path
     * @param suffixLength the number of bytes from the end
     * @return a GET request with suffix Range header
     */
    public HttpRequest createSuffixRangeRequest(String path, long suffixLength) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=-" + suffixLength);
        return request;
    }

    /**
     * Creates a request for everything from a given offset onward.
     *
     * @param path  the request URI path
     * @param start the start byte offset
     * @return a GET request with open-ended Range header
     */
    public HttpRequest createOpenRangeRequest(String path, long start) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.RANGE, "bytes=" + start + "-");
        return request;
    }

    /**
     * Returns the underlying HttpClient instance.
     *
     * @return the client
     */
    public HttpClient getClient() {
        return client;
    }
}
