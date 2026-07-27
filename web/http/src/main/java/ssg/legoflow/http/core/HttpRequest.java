package ssg.legoflow.http.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequest extends HttpMessage {

    private final HttpMethod method;
    private final String uri;

    public HttpRequest(HttpMethod method, String uri, HttpVersion version, HttpHeaders headers) {
        super(version, headers);
        this.method = method;
        this.uri = uri;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public static HttpRequest of(HttpMethod method, String uri) {
        return new HttpRequest(method, uri, HttpVersion.HTTP_1_1, new HttpHeaders());
    }

    public Map<String, String> getQueryParams() {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0 || queryStart == uri.length() - 1) {
            return Collections.emptyMap();
        }
        String query = uri.substring(queryStart + 1);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (!pair.isEmpty()) {
                String key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                params.put(key, "");
            }
        }
        return Collections.unmodifiableMap(params);
    }
}
