package ssg.legoflow.messaging.stomp.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Case-sensitive header map for STOMP frames with standard header constants.
 *
 * <p>STOMP 1.2 headers are case-sensitive and ordered. If a header is repeated,
 * the first occurrence takes precedence (per STOMP 1.2 spec). This implementation
 * preserves insertion order via {@link LinkedHashMap}.
 *
 * @since 1.0.0
 */
public final class StompHeaders implements Iterable<Map.Entry<String, String>> {

    // --- CONNECT/STOMP headers ---
    /** The virtual host to connect to. */
    public static final String HOST = "host";
    /** Protocol versions the client accepts. */
    public static final String ACCEPT_VERSION = "accept-version";
    /** Login credential. */
    public static final String LOGIN = "login";
    /** Passcode credential. */
    public static final String PASSCODE = "passcode";
    /** Heart-beat settings (cx,cy). */
    public static final String HEART_BEAT = "heart-beat";

    // --- CONNECTED headers ---
    /** Negotiated protocol version. */
    public static final String VERSION = "version";
    /** Server identification. */
    public static final String SERVER = "server";
    /** Session identifier. */
    public static final String SESSION = "session";

    // --- SEND headers ---
    /** Destination for SEND, SUBSCRIBE, MESSAGE. */
    public static final String DESTINATION = "destination";
    /** MIME content type. */
    public static final String CONTENT_TYPE = "content-type";
    /** Body length in bytes. */
    public static final String CONTENT_LENGTH = "content-length";
    /** Transaction identifier. */
    public static final String TRANSACTION = "transaction";
    /** Receipt request. */
    public static final String RECEIPT = "receipt";

    // --- SUBSCRIBE headers ---
    /** Subscription identifier. */
    public static final String ID = "id";
    /** Acknowledgment mode: auto, client, client-individual. */
    public static final String ACK = "ack";

    // --- MESSAGE headers ---
    /** Unique message identifier (server-assigned). */
    public static final String MESSAGE_ID = "message-id";
    /** Subscription ID this message belongs to. */
    public static final String SUBSCRIPTION = "subscription";

    // --- RECEIPT headers ---
    /** Receipt identifier in RECEIPT frame. */
    public static final String RECEIPT_ID = "receipt-id";

    // --- ERROR headers ---
    /** Short error description. */
    public static final String MESSAGE_HEADER = "message";

    private final LinkedHashMap<String, String> headers;

    /**
     * Creates an empty headers instance.
     */
    public StompHeaders() {
        this.headers = new LinkedHashMap<>();
    }

    /**
     * Creates headers from the given map, preserving order.
     *
     * @param headers the initial headers
     */
    public StompHeaders(Map<String, String> headers) {
        this.headers = new LinkedHashMap<>(headers);
    }

    /**
     * Copy constructor.
     *
     * @param other the headers to copy
     */
    public StompHeaders(StompHeaders other) {
        this.headers = new LinkedHashMap<>(other.headers);
    }

    /**
     * Sets a header value. Replaces any existing value for the key.
     *
     * @param key   the header name
     * @param value the header value
     * @return this instance for chaining
     */
    public StompHeaders put(String key, String value) {
        headers.put(key, value);
        return this;
    }

    /**
     * Sets a header only if not already present (first occurrence wins per STOMP 1.2).
     *
     * @param key   the header name
     * @param value the header value
     * @return this instance for chaining
     */
    public StompHeaders putIfAbsent(String key, String value) {
        headers.putIfAbsent(key, value);
        return this;
    }

    /**
     * Returns the value of the given header, or {@code null} if not present.
     *
     * @param key the header name
     * @return the header value, or null
     */
    public String get(String key) {
        return headers.get(key);
    }

    /**
     * Returns the value of the given header, or the default if not present.
     *
     * @param key          the header name
     * @param defaultValue the default value
     * @return the header value, or the default
     */
    public String getOrDefault(String key, String defaultValue) {
        return headers.getOrDefault(key, defaultValue);
    }

    /**
     * Returns whether the given header is present.
     *
     * @param key the header name
     * @return {@code true} if the header exists
     */
    public boolean contains(String key) {
        return headers.containsKey(key);
    }

    /**
     * Removes a header.
     *
     * @param key the header name
     * @return the removed value, or null
     */
    public String remove(String key) {
        return headers.remove(key);
    }

    /**
     * Returns the number of headers.
     *
     * @return header count
     */
    public int size() {
        return headers.size();
    }

    /**
     * Returns whether there are no headers.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    /**
     * Returns an unmodifiable view of the underlying map.
     *
     * @return unmodifiable header map
     */
    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return Collections.unmodifiableMap(headers).entrySet().iterator();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StompHeaders other)) return false;
        return headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }
}
