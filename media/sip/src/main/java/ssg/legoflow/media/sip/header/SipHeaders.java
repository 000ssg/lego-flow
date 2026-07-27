package ssg.legoflow.media.sip.header;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SIP header collection with case-insensitive header name lookup
 * and compact form support.
 *
 * <p>Preserves insertion order and supports multiple values per header name.
 * Compact forms (single-letter abbreviations) are automatically mapped to
 * their full names per RFC 3261 section 7.3.3.
 *
 * @since 1.0.0
 */
public final class SipHeaders {

    // Well-known header names
    public static final String VIA = "Via";
    public static final String FROM = "From";
    public static final String TO = "To";
    public static final String CALL_ID = "Call-ID";
    public static final String CSEQ = "CSeq";
    public static final String MAX_FORWARDS = "Max-Forwards";
    public static final String CONTACT = "Contact";
    public static final String ROUTE = "Route";
    public static final String RECORD_ROUTE = "Record-Route";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String ALLOW = "Allow";
    public static final String SUPPORTED = "Supported";
    public static final String REQUIRE = "Require";
    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    public static final String EXPIRES = "Expires";
    public static final String USER_AGENT = "User-Agent";
    public static final String SERVER = "Server";
    public static final String SUBJECT = "Subject";
    public static final String EVENT = "Event";
    public static final String REFER_TO = "Refer-To";
    public static final String ACCEPT = "Accept";
    public static final String MIN_EXPIRES = "Min-Expires";

    /** Compact form mappings (RFC 3261 section 7.3.3). */
    private static final Map<String, String> COMPACT_FORMS = Map.ofEntries(
            Map.entry("v", VIA),
            Map.entry("f", FROM),
            Map.entry("t", TO),
            Map.entry("i", CALL_ID),
            Map.entry("m", CONTACT),
            Map.entry("l", CONTENT_LENGTH),
            Map.entry("c", CONTENT_TYPE),
            Map.entry("e", "Content-Encoding"),
            Map.entry("k", SUPPORTED),
            Map.entry("s", SUBJECT),
            Map.entry("o", EVENT),
            Map.entry("r", REFER_TO)
    );

    private final Map<String, List<String>> headers;

    /**
     * Creates an empty header collection.
     *
     * @since 1.0.0
     */
    public SipHeaders() {
        this.headers = new LinkedHashMap<>();
    }

    /**
     * Creates a header collection from existing entries.
     *
     * @param headers the initial headers
     * @since 1.0.0
     */
    public SipHeaders(Map<String, List<String>> headers) {
        this.headers = new LinkedHashMap<>();
        headers.forEach((name, values) ->
                this.headers.put(normalizeName(name), new ArrayList<>(values)));
    }

    /**
     * Creates a deep copy of this header collection.
     *
     * @return a new copy of these headers
     * @since 1.0.0
     */
    public SipHeaders copy() {
        var copy = new SipHeaders();
        headers.forEach((name, values) ->
                copy.headers.put(name, new ArrayList<>(values)));
        return copy;
    }

    /**
     * Normalizes a header name by expanding compact forms and lowercasing.
     *
     * @param name the header name
     * @return the normalized name (lowercased)
     */
    private static String normalizeName(String name) {
        String lower = name.strip().toLowerCase();
        String expanded = COMPACT_FORMS.get(lower);
        return expanded != null ? expanded.toLowerCase() : lower;
    }

    /**
     * Sets a header value, replacing any existing values.
     *
     * @param name  the header name
     * @param value the header value
     * @return this headers instance for chaining
     * @since 1.0.0
     */
    public SipHeaders set(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        var list = new ArrayList<String>();
        list.add(value);
        headers.put(normalizeName(name), list);
        return this;
    }

    /**
     * Adds a header value without replacing existing values.
     *
     * @param name  the header name
     * @param value the header value
     * @return this headers instance for chaining
     * @since 1.0.0
     */
    public SipHeaders add(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        headers.computeIfAbsent(normalizeName(name), k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Gets the first value for a header.
     *
     * @param name the header name
     * @return the first value, or empty
     * @since 1.0.0
     */
    public Optional<String> first(String name) {
        var values = headers.get(normalizeName(name));
        return values != null && !values.isEmpty() ? Optional.of(values.getFirst()) : Optional.empty();
    }

    /**
     * Gets all values for a header.
     *
     * @param name the header name
     * @return all values, or empty list
     * @since 1.0.0
     */
    public List<String> all(String name) {
        var values = headers.get(normalizeName(name));
        return values != null ? Collections.unmodifiableList(values) : List.of();
    }

    /**
     * Returns true if the header is present.
     *
     * @param name the header name
     * @return true if the header exists
     * @since 1.0.0
     */
    public boolean contains(String name) {
        return headers.containsKey(normalizeName(name));
    }

    /**
     * Removes all values for a header.
     *
     * @param name the header name
     * @return this headers instance for chaining
     * @since 1.0.0
     */
    public SipHeaders remove(String name) {
        headers.remove(normalizeName(name));
        return this;
    }

    /**
     * Gets the Call-ID header value.
     *
     * @return the Call-ID
     * @throws IllegalStateException if Call-ID is missing
     * @since 1.0.0
     */
    public String callId() {
        return first(CALL_ID)
                .orElseThrow(() -> new IllegalStateException("Missing Call-ID header"));
    }

    /**
     * Gets the CSeq header value as a parsed record.
     *
     * @return the CSeq header
     * @throws IllegalStateException if CSeq is missing
     * @since 1.0.0
     */
    public CSeqHeader cseq() {
        return first(CSEQ)
                .map(CSeqHeader::parse)
                .orElseThrow(() -> new IllegalStateException("Missing CSeq header"));
    }

    /**
     * Gets the Content-Length value.
     *
     * @return the content length, or 0 if missing
     * @since 1.0.0
     */
    public int contentLength() {
        return first(CONTENT_LENGTH).map(Integer::parseInt).orElse(0);
    }

    /**
     * Gets the Max-Forwards value.
     *
     * @return the max-forwards, or empty
     * @since 1.0.0
     */
    public Optional<Integer> maxForwards() {
        return first(MAX_FORWARDS).map(Integer::parseInt);
    }

    /**
     * Gets the Expires value.
     *
     * @return the expires in seconds, or empty
     * @since 1.0.0
     */
    public Optional<Integer> expires() {
        return first(EXPIRES).map(Integer::parseInt);
    }

    /**
     * Parses the From header as an address header with tag.
     *
     * @return the From address header
     * @throws IllegalStateException if From is missing
     * @since 1.0.0
     */
    public AddressHeader from() {
        return first(FROM)
                .map(AddressHeader::parse)
                .orElseThrow(() -> new IllegalStateException("Missing From header"));
    }

    /**
     * Parses the To header as an address header with tag.
     *
     * @return the To address header
     * @throws IllegalStateException if To is missing
     * @since 1.0.0
     */
    public AddressHeader to() {
        return first(TO)
                .map(AddressHeader::parse)
                .orElseThrow(() -> new IllegalStateException("Missing To header"));
    }

    /**
     * Parses the first Via header.
     *
     * @return the top Via header
     * @throws IllegalStateException if Via is missing
     * @since 1.0.0
     */
    public ViaHeader topVia() {
        return first(VIA)
                .map(ViaHeader::parse)
                .orElseThrow(() -> new IllegalStateException("Missing Via header"));
    }

    /**
     * Returns the number of distinct header names.
     *
     * @return the header count
     * @since 1.0.0
     */
    public int size() {
        return headers.size();
    }

    /**
     * Returns an unmodifiable view of all headers.
     *
     * @return all headers as a map
     * @since 1.0.0
     */
    public Map<String, List<String>> toMap() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Formats all headers as SIP header lines (name: value\r\n).
     *
     * @return the formatted headers
     * @since 1.0.0
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

    /**
     * Capitalizes header name from lowercase key form.
     */
    private String formatName(String lowerName) {
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
        return "SipHeaders[" + headers.size() + " headers]";
    }
}
