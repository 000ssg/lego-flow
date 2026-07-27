package ssg.legoflow.http2.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.stream.Http2Stream;

public class Http2RequestAdapter {

    public HttpRequest adapt(Http2Stream stream) {
        var h2Headers = stream.headers();

        String method = h2Headers.get(":method");
        String path = h2Headers.get(":path");
        String scheme = h2Headers.get(":scheme");
        String authority = h2Headers.get(":authority");

        if (method == null || path == null) {
            throw new IllegalStateException("Missing required pseudo-headers :method and :path");
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method);
        var headers = new HttpHeaders();

        for (String name : h2Headers.names()) {
            if (!name.startsWith(":")) {
                for (String value : h2Headers.getAll(name)) {
                    headers.add(name, value);
                }
            }
        }

        if (authority != null && !headers.contains(HttpHeaders.HOST)) {
            headers.set(HttpHeaders.HOST, authority);
        }

        var request = new HttpRequest(httpMethod, path, HttpVersion.HTTP_2, headers);

        var data = stream.getAccumulatedData();
        if (data.hasRemaining()) {
            request.setBody(data);
            if (!headers.contains(HttpHeaders.CONTENT_LENGTH)) {
                headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.remaining()));
            }
        }

        return request;
    }

    public HttpHeaders adaptResponseHeaders(HttpResponse response) {
        var h2Headers = new HttpHeaders();
        h2Headers.set(":status", String.valueOf(response.getStatus().code()));

        for (String name : response.getHeaders().names()) {
            String lower = name.toLowerCase();
            if (!lower.equals("connection") && !lower.equals("transfer-encoding")
                    && !lower.equals("keep-alive")) {
                for (String value : response.getHeaders().getAll(name)) {
                    h2Headers.add(name, value);
                }
            }
        }

        return h2Headers;
    }
}
