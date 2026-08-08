package ssg.legoflow.http.demo.client;

import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.config.ClientConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;

/**
 * Minimal HTTP client demo with factory methods for creating requests.
 *
 * <p>Uses the {@code clientMinimal} profile which includes core HTTP features
 * and fixed-length transfer encoding.
 *
 * @since 0.1
 */
public class MinimalClient {

    private final HttpClient client;

    public MinimalClient() {
        var config = new ClientConfig(StandardProfiles.clientMinimal());
        this.client = new HttpClient(config);
    }

    /**
     * Creates a GET request for the given path.
     *
     * @param path the request URI path
     * @return a new GET HttpRequest
     */
    public HttpRequest createGetRequest(String path) {
        return HttpRequest.of(HttpMethod.GET, path);
    }

    /**
     * Creates a POST request for the given path with a text body.
     *
     * @param path the request URI path
     * @param body the request body text
     * @return a new POST HttpRequest with body set
     */
    public HttpRequest createPostRequest(String path, String body) {
        var request = HttpRequest.of(HttpMethod.POST, path);
        request.setBody(java.nio.ByteBuffer.wrap(body.getBytes()));
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
