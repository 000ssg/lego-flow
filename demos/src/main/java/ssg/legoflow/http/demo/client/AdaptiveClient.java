package ssg.legoflow.http.demo.client;

import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;

/**
 * Adaptive HTTP client demo that sets content negotiation and encoding headers
 * to adapt to server capabilities.
 *
 * <p>Creates requests with Accept, Accept-Encoding, and Accept-Charset headers
 * for automatic content negotiation.
 *
 * @since 1.0
 */
public class AdaptiveClient {

    private final HttpClient client;
    private String acceptTypes = "application/json, text/html;q=0.9, */*;q=0.8";
    private String acceptEncoding = "gzip, deflate, identity;q=0.5";
    private String acceptCharset = "utf-8, iso-8859-1;q=0.5";

    public AdaptiveClient() {
        this.client = new HttpClientBuilder().standard().build();
    }

    /**
     * Creates a GET request with content negotiation headers.
     *
     * @param path the request URI path
     * @return a new GET request with negotiation headers
     */
    public HttpRequest createAdaptiveGetRequest(String path) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.ACCEPT, acceptTypes);
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, acceptEncoding);
        request.getHeaders().set(HttpHeaders.ACCEPT_CHARSET, acceptCharset);
        return request;
    }

    /**
     * Creates a GET request that only accepts the specified media type.
     *
     * @param path      the request URI path
     * @param mediaType the desired media type
     * @return a new GET request with specific Accept header
     */
    public HttpRequest createTypedGetRequest(String path, String mediaType) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.ACCEPT, mediaType);
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, acceptEncoding);
        return request;
    }

    /**
     * Sets the accepted media types.
     *
     * @param types the Accept header value
     */
    public void setAcceptTypes(String types) {
        this.acceptTypes = types;
    }

    /**
     * Sets the accepted encodings.
     *
     * @param encoding the Accept-Encoding header value
     */
    public void setAcceptEncoding(String encoding) {
        this.acceptEncoding = encoding;
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
