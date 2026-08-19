package ssg.legoflow.http3.server;

import ssg.legoflow.http.core.*;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Adapts HTTP/3 pseudo-headers and body to standard {@link HttpRequest}
 * and {@link HttpResponse} objects.
 *
 * <p>Follows the same pattern as {@code Http2RequestAdapter}: converts
 * pseudo-headers ({@code :method}, {@code :scheme}, {@code :authority},
 * {@code :path}) to a standard HTTP request, and converts a standard
 * HTTP response back to HTTP/3 pseudo-headers plus body.</p>
 *
 * @since 0.1.0
 */
public class Http3RequestAdapter {

    /**
     * Adapts HTTP/3 headers and body into a standard {@link HttpRequest}.
     *
     * @param headers the decoded HTTP/3 headers (pseudo-headers + regular)
     * @param body    the request body, or {@code null}
     * @return the adapted HTTP request
     * @throws IllegalStateException if required pseudo-headers are missing
     * @since 0.1.0
     */
    public HttpRequest adaptRequest(Map<String, String> headers, ByteBuffer body) {
        var method = headers.get(":method");
        var path = headers.get(":path");
        var authority = headers.get(":authority");

        if (method == null || path == null) {
            throw new IllegalStateException("Missing required pseudo-headers :method and :path");
        }

        var httpMethod = HttpMethod.valueOf(method);
        var httpHeaders = new HttpHeaders();

        for (var entry : headers.entrySet()) {
            if (!entry.getKey().startsWith(":")) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        if (authority != null && !httpHeaders.contains(HttpHeaders.HOST)) {
            httpHeaders.set(HttpHeaders.HOST, authority);
        }

        var request = new HttpRequest(httpMethod, path, HttpVersion.HTTP_3, httpHeaders);

        if (body != null && body.hasRemaining()) {
            request.setBody(body);
            if (!httpHeaders.contains(HttpHeaders.CONTENT_LENGTH)) {
                httpHeaders.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.remaining()));
            }
        }

        return request;
    }

    /**
     * Adapts HTTP/3 header entries and body into a standard {@link HttpRequest}.
     *
     * @param headerEntries the decoded HTTP/3 header entries
     * @param body          the request body, or {@code null}
     * @return the adapted HTTP request
     * @since 0.1.0
     */
    public HttpRequest adaptRequest(List<Map.Entry<String, String>> headerEntries, ByteBuffer body) {
        var headersMap = new java.util.LinkedHashMap<String, String>();
        for (var entry : headerEntries) {
            headersMap.put(entry.getKey(), entry.getValue());
        }
        return adaptRequest(headersMap, body);
    }

    /**
     * Adapts a standard {@link HttpResponse} into HTTP/3 response headers and body.
     *
     * @param response the HTTP response
     * @return a list of header entries including the {@code :status} pseudo-header
     * @since 0.1.0
     */
    public List<Map.Entry<String, String>> adaptResponseHeaders(HttpResponse response) {
        var headers = new ArrayList<Map.Entry<String, String>>();
        headers.add(new AbstractMap.SimpleEntry<>(":status", String.valueOf(response.getStatus().code())));

        for (String name : response.getHeaders().names()) {
            var lower = name.toLowerCase();
            if (!lower.equals("connection") && !lower.equals("transfer-encoding")
                    && !lower.equals("keep-alive")) {
                for (String value : response.getHeaders().getAll(name)) {
                    headers.add(new AbstractMap.SimpleEntry<>(name, value));
                }
            }
        }

        return headers;
    }

    /**
     * Extracts the response body from an {@link HttpResponse}.
     *
     * @param response the HTTP response
     * @return the body buffer, or {@code null} if empty
     * @since 0.1.0
     */
    public ByteBuffer adaptResponseBody(HttpResponse response) {
        var body = response.getBody();
        if (body != null && body.hasRemaining()) {
            return body.duplicate();
        }
        return null;
    }
}
