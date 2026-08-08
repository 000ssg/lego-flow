package ssg.legoflow.media.rtsp.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * RTSP header collection with case-insensitive header name lookup.
 *
 * <p>Preserves insertion order and supports multiple values per header name.
 *
 * @since 0.1.0
 */
public final class RtspHeaders {

    // Well-known header names
    public static final String CSEQ = "CSeq";
    public static final String SESSION = "Session";
    public static final String TRANSPORT = "Transport";
    public static final String RANGE = "Range";
    public static final String SCALE = "Scale";
    public static final String SPEED = "Speed";
    public static final String ACCEPT = "Accept";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String DATE = "Date";
    public static final String USER_AGENT = "User-Agent";
    public static final String SERVER = "Server";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String AUTHORIZATION = "Authorization";
    public static final String PUBLIC = "Public";
    public static final String REQUIRE = "Require";
    public static final String PROXY_REQUIRE = "Proxy-Require";

    private final Map<String, List<String>> headers;

    /**
     * Creates an empty header collection.
     */
    public RtspHeaders() {
        this.headers = new LinkedHashMap<>();
    }

    /**
     * Creates a header collection from existing entries.
     *
     * @param headers the initial headers
     */
    public RtspHeaders(Map<String, List<String>> headers) {
        this.headers = new LinkedHashMap<>();
        headers.forEach((name, values) ->
                this.headers.put(name.toLowerCase(), new ArrayList<>(values)));
    }

    /**
     * Sets a header value, replacing any existing values.
     *
     * @param name  the header name
     * @param value the header value
     * @return this headers instance for chaining
     */
    public RtspHeaders set(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        var list = new ArrayList<String>();
        list.add(value);
        headers.put(name.toLowerCase(), list);
        return this;
    }

    /**
     * Adds a header value without replacing existing values.
     *
     * @param name  the header name
     * @param value the header value
     * @return this headers instance for chaining
     */
    public RtspHeaders add(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        headers.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Gets the first value for a header.
     *
     * @param name the header name
     * @return the first value, or empty
     */
    public Optional<String> first(String name) {
        var values = headers.get(name.toLowerCase());
        return values != null && !values.isEmpty() ? Optional.of(values.getFirst()) : Optional.empty();
    }

    /**
     * Gets all values for a header.
     *
     * @param name the header name
     * @return all values, or empty list
     */
    public List<String> all(String name) {
        var values = headers.get(name.toLowerCase());
        return values != null ? Collections.unmodifiableList(values) : List.of();
    }

    /**
     * Returns true if the header is present.
     *
     * @param name the header name
     * @return true if the header exists
     */
    public boolean contains(String name) {
        return headers.containsKey(name.toLowerCase());
    }

    /**
     * Gets the CSeq value.
     *
     * @return the CSeq number
     * @throws IllegalStateException if CSeq is missing
     */
    public int cseq() {
        return first(CSEQ)
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalStateException("Missing CSeq header"));
    }

    /**
     * Gets the Session header value (session ID, without timeout parameter).
     *
     * @return the session ID, or empty
     */
    public Optional<String> sessionId() {
        return first(SESSION).map(s -> {
            int semi = s.indexOf(';');
            return semi >= 0 ? s.substring(0, semi).trim() : s.trim();
        });
    }

    /**
     * Gets the session timeout from the Session header.
     *
     * @return the timeout in seconds, or empty
     */
    public Optional<Integer> sessionTimeout() {
        return first(SESSION).flatMap(s -> {
            int idx = s.toLowerCase().indexOf("timeout=");
            if (idx < 0) return Optional.empty();
            String val = s.substring(idx + 8).trim();
            int end = val.indexOf(';');
            if (end >= 0) val = val.substring(0, end);
            return Optional.of(Integer.parseInt(val.trim()));
        });
    }

    /**
     * Gets the Content-Length value.
     *
     * @return the content length, or 0 if missing
     */
    public int contentLength() {
        return first(CONTENT_LENGTH).map(Integer::parseInt).orElse(0);
    }

    /**
     * Returns an unmodifiable view of all headers.
     *
     * @return all headers as a map
     */
    public Map<String, List<String>> toMap() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Returns the number of distinct header names.
     *
     * @return the header count
     */
    public int size() {
        return headers.size();
    }

    /**
     * Formats all headers as RTSP header lines (name: value\r\n).
     *
     * @return the formatted headers
     */
    public String format() {
        var sb = new StringBuilder();
        headers.forEach((name, values) -> {
            for (String value : values) {
                sb.append(formatName(name)).append(": ").append(value).append("\r\n");
            }
        });
        return sb.toString();
    }

    private String formatName(String lowerName) {
        // Capitalize first letter of each word separated by '-'
        var parts = lowerName.split("-");
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('-');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RtspHeaders[" + headers.size() + " headers]";
    }
}
