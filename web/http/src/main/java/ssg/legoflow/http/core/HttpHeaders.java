package ssg.legoflow.http.core;

import java.util.*;

public class HttpHeaders {

    // Standard header name constants
    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String HOST = "host";
    public static final String ACCEPT = "accept";
    public static final String AUTHORIZATION = "authorization";
    public static final String CACHE_CONTROL = "cache-control";
    public static final String CONNECTION = "connection";
    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String USER_AGENT = "user-agent";
    public static final String SERVER = "server";
    public static final String LOCATION = "location";
    public static final String ETAG = "etag";
    public static final String IF_MATCH = "if-match";
    public static final String IF_NONE_MATCH = "if-none-match";
    public static final String IF_MODIFIED_SINCE = "if-modified-since";
    public static final String LAST_MODIFIED = "last-modified";
    public static final String RANGE = "range";
    public static final String CONTENT_RANGE = "content-range";
    public static final String ACCEPT_RANGES = "accept-ranges";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String ACCEPT_CHARSET = "accept-charset";
    public static final String ACCEPT_LANGUAGE = "accept-language";
    public static final String UPGRADE = "upgrade";
    public static final String SEC_WEBSOCKET_KEY = "sec-websocket-key";
    public static final String SEC_WEBSOCKET_ACCEPT = "sec-websocket-accept";
    public static final String SEC_WEBSOCKET_VERSION = "sec-websocket-version";
    public static final String VARY = "vary";
    public static final String STRICT_TRANSPORT_SECURITY = "strict-transport-security";
    public static final String DATE = "date";
    public static final String EXPIRES = "expires";
    public static final String IF_RANGE = "if-range";
    public static final String EXPECT = "expect";
    public static final String ALLOW = "allow";
    public static final String SEC_WEBSOCKET_PROTOCOL = "sec-websocket-protocol";
    public static final String SEC_WEBSOCKET_EXTENSIONS = "sec-websocket-extensions";
    public static final String WWW_AUTHENTICATE = "www-authenticate";

    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    public HttpHeaders() {
    }

    public String get(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    public List<String> getAll(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return (values != null) ? Collections.unmodifiableList(values) : List.of();
    }

    public void set(String name, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name.toLowerCase(), values);
    }

    public void add(String name, String value) {
        headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
    }

    public void remove(String name) {
        headers.remove(name.toLowerCase());
    }

    public boolean contains(String name) {
        return headers.containsKey(name.toLowerCase());
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    public int size() {
        return headers.size();
    }

    public boolean isEmpty() {
        return headers.isEmpty();
    }

    public Map<String, List<String>> toMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
